package com.example.examai.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import androidx.core.app.NotificationCompat
import com.example.examai.R
import com.example.examai.network.AiApiHelper
import com.example.examai.service.FormAutoAccessibilityService.ProcessStatus
import com.example.examai.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingWidgetService : Service() {

    companion object {
        var isRunning = false
            private set
        private const val CHANNEL_ID = "exam_ai_overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var floatingView: View? = null
    private lateinit var ivIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var layoutMiniPanel: LinearLayout
    private lateinit var layoutFloatingWebView: LinearLayout
    private lateinit var webViewGoogle: WebView
    private lateinit var tvMiniChatReply: TextView
    private lateinit var etMiniChat: EditText
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundServiceNotification()
        setupFloatingWindow()
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ExamAI Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ExamAI Assistant Aktif")
            .setContentText("Ketuk ikon melayang di Chrome untuk Auto-Answer atau Mini Chat.")
            .setSmallIcon(R.drawable.ic_ai_assistant)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val view = floatingView ?: return
        ivIcon = view.findViewById(R.id.ivFloatingIcon)
        tvStatus = view.findViewById(R.id.tvFloatingStatus)
        layoutMiniPanel = view.findViewById(R.id.layoutMiniPanel)
        layoutFloatingWebView = view.findViewById(R.id.layoutFloatingWebView)
        webViewGoogle = view.findViewById(R.id.webViewGoogle)
        webViewGoogle.settings.javaScriptEnabled = true
        webViewGoogle.webViewClient = WebViewClient()
        tvMiniChatReply = view.findViewById(R.id.tvMiniChatReply)
        etMiniChat = view.findViewById(R.id.etMiniChat)

        val fabContainer = view.findViewById<View>(R.id.fabContainer)
        val btnCloseMini = view.findViewById<View>(R.id.btnCloseMini)
        val btnCloseWebView = view.findViewById<View>(R.id.btnCloseWebView)
        val btnMiniAutoAnswer = view.findViewById<Button>(R.id.btnMiniAutoAnswer)
        val btnMiniScreenshotGoogle = view.findViewById<Button>(R.id.btnMiniScreenshotGoogle)
        val btnMiniSend = view.findViewById<Button>(R.id.btnMiniSend)

        val opacity = PrefsManager.getOpacity(this) / 100f
        fabContainer.alpha = opacity

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        fabContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        toggleMiniPanel()
                    }
                    true
                }

                else -> false
            }
        }

        btnCloseMini.setOnClickListener { closeMiniPanel() }
        btnCloseWebView.setOnClickListener {
            layoutFloatingWebView.visibility = View.GONE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            floatingView?.let { windowManager.updateViewLayout(it, params) }
        }

        btnMiniAutoAnswer.setOnClickListener {
            closeMiniPanel()
            triggerAutoAnswer()
        }

        btnMiniScreenshotGoogle.setOnClickListener {
            closeMiniPanel()
            triggerScreenshotAndGoogle()
        }

        btnMiniSend.setOnClickListener {
            val prompt = etMiniChat.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            etMiniChat.setText("")
            tvMiniChatReply.text = "Berpikir..."
            serviceScope.launch {
                val res = AiApiHelper.askChat(this@FloatingWidgetService, prompt)
                res.onSuccess { reply ->
                    tvMiniChatReply.text = reply
                }.onFailure { err ->
                    tvMiniChatReply.text = "Error: ${err.localizedMessage}"
                }
            }
        }

        windowManager.addView(view, params)
        updateStatusUi(ProcessStatus.IDLE)
    }

    private fun toggleMiniPanel() {
        if (layoutMiniPanel.visibility == View.VISIBLE) {
            closeMiniPanel()
        } else {
            layoutMiniPanel.visibility = View.VISIBLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            floatingView?.let { windowManager.updateViewLayout(it, params) }
        }
    }

    private fun closeMiniPanel() {
        layoutMiniPanel.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        floatingView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun triggerAutoAnswer() {
        val accessibilityService = FormAutoAccessibilityService.instance
        if (accessibilityService == null) {
            updateStatusUi(ProcessStatus.ERROR)
            Toast.makeText(
                applicationContext,
                "Accessibility Service belum aktif! Aktifkan di Pengaturan.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        accessibilityService.processCurrentScreen { status ->
            mainHandler.post {
                updateStatusUi(status)
            }
        }
    }

    private fun triggerScreenshotAndGoogle() {
        val accessibilityService = FormAutoAccessibilityService.instance
        if (accessibilityService == null) {
            updateStatusUi(ProcessStatus.ERROR)
            Toast.makeText(applicationContext, "Accessibility Service belum aktif!", Toast.LENGTH_SHORT).show()
            return
        }
        updateStatusUi(ProcessStatus.LOADING)
        accessibilityService.captureScreenBitmap { bitmap ->
            if (bitmap == null) {
                mainHandler.post { updateStatusUi(ProcessStatus.ERROR) }
                return@captureScreenBitmap
            }
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            serviceScope.launch {
                val res = AiApiHelper.extractQuestionFromImage(this@FloatingWidgetService, base64)
                res.onSuccess { query ->
                    updateStatusUi(ProcessStatus.SUCCESS)
                    val url = "https://www.google.com/search?q=" + Uri.encode(query)
                    layoutFloatingWebView.visibility = View.VISIBLE
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    floatingView?.let { windowManager.updateViewLayout(it, params) }
                    webViewGoogle.loadUrl(url)
                }.onFailure { err ->
                    updateStatusUi(ProcessStatus.ERROR)
                    Toast.makeText(applicationContext, "Gagal Vision: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateStatusUi(status: ProcessStatus) {
        mainHandler.removeCallbacksAndMessages(null)
        when (status) {
            ProcessStatus.IDLE -> {
                ivIcon.setBackgroundResource(R.drawable.bg_floating_idle)
                tvStatus.text = "Idle"
            }

            ProcessStatus.LOADING -> {
                ivIcon.setBackgroundResource(R.drawable.bg_floating_loading)
                tvStatus.text = "Berpikir..."
            }

            ProcessStatus.SUCCESS -> {
                ivIcon.setBackgroundResource(R.drawable.bg_floating_success)
                tvStatus.text = "Selesai"
                mainHandler.postDelayed({ updateStatusUi(ProcessStatus.IDLE) }, 2500)
            }

            ProcessStatus.ERROR -> {
                ivIcon.setBackgroundResource(R.drawable.bg_floating_error)
                tvStatus.text = "Error"
                mainHandler.postDelayed({ updateStatusUi(ProcessStatus.IDLE) }, 2500)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        floatingView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
        floatingView = null
    }
}
