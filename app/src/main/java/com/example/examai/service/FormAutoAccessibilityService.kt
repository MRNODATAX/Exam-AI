package com.example.examai.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.examai.network.AiApiHelper
import com.example.examai.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FormAutoAccessibilityService : AccessibilityService() {

    enum class ProcessStatus {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    companion object {
        var instance: FormAutoAccessibilityService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showToast("Exam AI Accessibility aktif")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pemrosesan dipicu manual dari FloatingWidgetService saat tombol diklik
    }

    override fun onInterrupt() {
        // Tidak perlu aksi khusus
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    fun processCurrentScreen(onStatus: (ProcessStatus) -> Unit) {
        if (isProcessing) {
            showToast("Sedang memproses soal...")
            return
        }

        val rootNode = getChromeRootNode()
        if (rootNode == null) {
            onStatus(ProcessStatus.ERROR)
            showToast("Layar Chrome tidak terbaca. Pastikan Google Chrome terbuka.")
            return
        }

        val screenTextBuilder = StringBuilder()
        extractVisibleText(rootNode, screenTextBuilder)
        val screenText = screenTextBuilder.toString().trim()

        if (screenText.isBlank()) {
            onStatus(ProcessStatus.ERROR)
            showToast("Tidak ada teks soal yang ditemukan di layar.")
            return
        }

        isProcessing = true
        onStatus(ProcessStatus.LOADING)

        serviceScope.launch {
            try {
                val result = AiApiHelper.askAi(this@FormAutoAccessibilityService, screenText)
                result.onSuccess { aiAnswer ->
                    val freshRoot = getChromeRootNode() ?: rootNode
                    val applied = applyAnswerToScreen(freshRoot, aiAnswer)
                    if (applied) {
                        PrefsManager.addHistory(
                            this@FormAutoAccessibilityService,
                            screenText.lines().firstOrNull().orEmpty(),
                            aiAnswer
                        )
                        onStatus(ProcessStatus.SUCCESS)
                        showToast("Dipilih: $aiAnswer")
                    } else {
                        onStatus(ProcessStatus.ERROR)
                        showToast("Jawaban AI ($aiAnswer) tidak ditemukan di layar.")
                    }
                }.onFailure { error ->
                    onStatus(ProcessStatus.ERROR)
                    showToast("Gagal AI: ${error.localizedMessage ?: "Error tidak diketahui"}")
                }
            } catch (e: Exception) {
                onStatus(ProcessStatus.ERROR)
                showToast("Terjadi kesalahan: ${e.localizedMessage}")
            } finally {
                isProcessing = false
            }
        }
    }

    private fun getChromeRootNode(): AccessibilityNodeInfo? {
        val activeRoot = rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString() == "com.android.chrome") {
            return activeRoot
        }
        for (window in windows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() == "com.android.chrome") {
                return root
            }
        }
        return activeRoot
    }

    private fun extractVisibleText(node: AccessibilityNodeInfo?, out: StringBuilder) {
        if (node == null || !node.isVisibleToUser) return

        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrEmpty()) {
            out.append(text).append("\n")
        } else if (!contentDesc.isNullOrEmpty()) {
            out.append(contentDesc).append("\n")
        }

        for (i in 0 until node.childCount) {
            extractVisibleText(node.getChild(i), out)
        }
    }

    private fun applyAnswerToScreen(root: AccessibilityNodeInfo, answer: String): Boolean {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        // 1. Cari exact match (case-insensitive)
        val exactMatch = allNodes.firstOrNull { node ->
            val t = node.text?.toString()?.trim()
            val d = node.contentDescription?.toString()?.trim()
            t.equals(answer, ignoreCase = true) || d.equals(answer, ignoreCase = true)
        }
        if (exactMatch != null && clickNodeOrParent(exactMatch)) {
            return true
        }

        // 2. Cari contains match
        val containsMatch = allNodes.firstOrNull { node ->
            val t = node.text?.toString()?.trim().orEmpty()
            val d = node.contentDescription?.toString()?.trim().orEmpty()
            (t.isNotEmpty() && (t.contains(answer, ignoreCase = true) || answer.contains(t, ignoreCase = true))) ||
                (d.isNotEmpty() && (d.contains(answer, ignoreCase = true) || answer.contains(d, ignoreCase = true)))
        }
        if (containsMatch != null && clickNodeOrParent(containsMatch)) {
            return true
        }

        // 3. Jika soal berupa isian teks (EditText / isEditable), isi dengan ACTION_SET_TEXT
        val editableNode = allNodes.firstOrNull { it.isEditable && it.isVisibleToUser }
        if (editableNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, answer)
            }
            return editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        return false
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null || !node.isVisibleToUser) return
        list.add(node)
        for (i in 0 until node.childCount) {
            collectAllNodes(node.getChild(i), list)
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable || current.isCheckable) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
