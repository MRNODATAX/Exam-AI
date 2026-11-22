package com.example.examai

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.examai.network.AiApiHelper
import com.example.examai.service.FloatingWidgetService
import com.example.examai.service.FormAutoAccessibilityService
import com.example.examai.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Tabs
    private lateinit var tabHome: View
    private lateinit var tabAutoAnswer: View
    private lateinit var tabChat: View
    private lateinit var tabLaporan: View
    private lateinit var tabSettings: View

    // Nav buttons
    private lateinit var navHome: Button
    private lateinit var navAuto: Button
    private lateinit var navChat: Button
    private lateinit var navLaporan: Button
    private lateinit var navSettings: Button

    // Home views
    private lateinit var tvLiveModel: TextView
    private lateinit var tvLiveProvider: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAutoAnswerDetailStatus: TextView
    private lateinit var btnRequestOverlay: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnToggleService: Button

    // Chat views
    private lateinit var scrollChat: ScrollView
    private lateinit var tvChatHistory: TextView
    private lateinit var etChatInput: EditText
    private lateinit var btnSendChat: Button

    // Laporan views
    private lateinit var tvHistoryList: TextView
    private lateinit var btnClearHistory: Button

    // Settings views
    private lateinit var spinnerProvider: Spinner
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var layoutBaseUrl: LinearLayout
    private lateinit var etBaseUrl: EditText
    private lateinit var btnSaveConfig: Button
    private lateinit var btnTestApi: Button
    private lateinit var tvApiTestResult: TextView
    private lateinit var tvOpacityLabel: TextView
    private lateinit var seekOpacity: SeekBar

    private val providers = listOf(
        PrefsManager.PROVIDER_GEMINI,
        PrefsManager.PROVIDER_OMNIROUTE,
        PrefsManager.PROVIDER_STANDARD
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        applyBrandPolish()
        setupSplash()
        setupNavigation()
        setupProviderSpinner()
        loadSavedConfig()
        setupListeners()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionAndServiceUi()
        refreshHistoryUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun initViews() {
        tabHome = findViewById(R.id.tabHome)
        tabAutoAnswer = findViewById(R.id.tabAutoAnswer)
        tabChat = findViewById(R.id.tabChat)
        tabLaporan = findViewById(R.id.tabLaporan)
        tabSettings = findViewById(R.id.tabSettings)

        navHome = findViewById(R.id.navHome)
        navAuto = findViewById(R.id.navAuto)
        navChat = findViewById(R.id.navChat)
        navLaporan = findViewById(R.id.navLaporan)
        navSettings = findViewById(R.id.navSettings)

        tvLiveModel = findViewById(R.id.tvLiveModel)
        tvLiveProvider = findViewById(R.id.tvLiveProvider)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvAutoAnswerDetailStatus = findViewById(R.id.tvAutoAnswerDetailStatus)
        btnRequestOverlay = findViewById(R.id.btnRequestOverlay)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        btnToggleService = findViewById(R.id.btnToggleService)

        scrollChat = findViewById(R.id.scrollChat)
        tvChatHistory = findViewById(R.id.tvChatHistory)
        etChatInput = findViewById(R.id.etChatInput)
        btnSendChat = findViewById(R.id.btnSendChat)

        tvHistoryList = findViewById(R.id.tvHistoryList)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        spinnerProvider = findViewById(R.id.spinnerProvider)
        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        layoutBaseUrl = findViewById(R.id.layoutBaseUrl)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)
        btnTestApi = findViewById(R.id.btnTestApi)
        tvApiTestResult = findViewById(R.id.tvApiTestResult)
        tvOpacityLabel = findViewById(R.id.tvOpacityLabel)
        seekOpacity = findViewById(R.id.seekOpacity)
    }

    private fun setupSplash() {
        val splash = findViewById<View>(R.id.splashOverlay)
        val nameGate = findViewById<View>(R.id.nameGateOverlay)
        val etUserName = findViewById<EditText>(R.id.etUserName)
        val btnEnterApp = findViewById<Button>(R.id.btnEnterApp)
        val tvHeaderGreeting = findViewById<TextView>(R.id.tvHeaderGreeting)

        val ivSplashLogo = findViewById<View>(R.id.ivSplashLogo)
        val tvSplashWordmark = findViewById<TextView>(R.id.tvSplashWordmark)
        applyGradientText(tvSplashWordmark)

        ivSplashLogo.scaleX = 0.7f
        ivSplashLogo.scaleY = 0.7f
        ivSplashLogo.alpha = 0f
        ivSplashLogo.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(550)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        tvSplashWordmark.translationY = 18f
        tvSplashWordmark.alpha = 0f
        tvSplashWordmark.animate()
            .translationY(0f)
            .alpha(1f)
            .setStartDelay(200)
            .setDuration(450)
            .start()

        val savedName = PrefsManager.getUserName(this)
        if (savedName.isNotEmpty()) {
            tvHeaderGreeting.text = "by nodata · Halo, $savedName"
        }

        btnEnterApp.setOnClickListener {
            val name = etUserName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Masukkan nama terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsManager.saveUserName(this, name)
            tvHeaderGreeting.text = "by nodata · Halo, $name"
            nameGate.visibility = View.GONE
        }

        fun finishSplash() {
            splash.visibility = View.GONE
            if (PrefsManager.getUserName(this).isEmpty()) {
                nameGate.visibility = View.VISIBLE
            }
        }

        splash.setOnClickListener { finishSplash() }
        splash.postDelayed({
            splash.animate().alpha(0f).setDuration(350).withEndAction {
                finishSplash()
            }.start()
        }, 1400)
    }

    private fun setupNavigation() {
        val tabs = listOf(tabHome, tabAutoAnswer, tabChat, tabLaporan, tabSettings)
        val navs = listOf(navHome, navAuto, navChat, navLaporan, navSettings)

        fun selectTab(index: Int) {
            tabs.forEachIndexed { i, v ->
                if (i == index) {
                    v.visibility = View.VISIBLE
                    v.alpha = 0f
                    v.translationY = 14f
                    v.animate().alpha(1f).translationY(0f).setDuration(220).start()
                } else {
                    v.visibility = View.GONE
                }
            }
            navs.forEachIndexed { i, b ->
                b.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (i == index) R.color.txt else R.color.dim
                    )
                )
            }
            if (index == 3) refreshHistoryUi()
        }

        navHome.setOnClickListener { selectTab(0) }
        navAuto.setOnClickListener { selectTab(1) }
        navChat.setOnClickListener { selectTab(2) }
        navLaporan.setOnClickListener { selectTab(3) }
        navSettings.setOnClickListener { selectTab(4) }
    }

    private fun setupProviderSpinner() {
        val labels = listOf(
            "Gemini API (Google)",
            "OmniRoute API",
            "Standard OpenAI-Compatible"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProvider.adapter = adapter

        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = providers[position]
                layoutBaseUrl.visibility = if (selected == PrefsManager.PROVIDER_GEMINI) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadSavedConfig() {
        val savedProvider = PrefsManager.getProvider(this)
        val index = providers.indexOf(savedProvider).coerceAtLeast(0)
        spinnerProvider.setSelection(index)

        etApiKey.setText(PrefsManager.getApiKey(this))
        etModel.setText(PrefsManager.getModel(this))
        etBaseUrl.setText(PrefsManager.getBaseUrl(this))

        val opacity = PrefsManager.getOpacity(this)
        seekOpacity.progress = opacity - 30
        tvOpacityLabel.text = "Transparansi Floating Button: $opacity%"
    }

    private fun setupListeners() {
        btnRequestOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Izin Overlay sudah aktif", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnSaveConfig.setOnClickListener {
            saveCurrentInputs()
        }

        btnTestApi.setOnClickListener {
            if (!saveCurrentInputs()) return@setOnClickListener
            tvApiTestResult.text = "Menguji koneksi API..."
            tvApiTestResult.setTextColor(ContextCompat.getColor(this, R.color.warn))
            uiScope.launch {
                val res = AiApiHelper.testConnection(this@MainActivity)
                res.onSuccess { reply ->
                    tvApiTestResult.text = "Berhasil terhubung! Respons: $reply"
                    tvApiTestResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ok))
                }.onFailure { err ->
                    tvApiTestResult.text = "Gagal: ${err.localizedMessage}"
                    tvApiTestResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bad))
                }
            }
        }

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 30
                tvOpacityLabel.text = "Transparansi Floating Button: $value%"
                PrefsManager.saveOpacity(this@MainActivity, value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSendChat.setOnClickListener {
            val prompt = etChatInput.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener
            etChatInput.setText("")
            appendChat("Anda: $prompt\nExamAI: Berpikir...")
            uiScope.launch {
                val res = AiApiHelper.askChat(this@MainActivity, prompt)
                res.onSuccess { reply ->
                    appendChat("ExamAI: $reply\n")
                }.onFailure { err ->
                    appendChat("Error: ${err.localizedMessage}\n")
                }
            }
        }

        btnClearHistory.setOnClickListener {
            PrefsManager.clearHistory(this)
            refreshHistoryUi()
            Toast.makeText(this, "Riwayat dihapus", Toast.LENGTH_SHORT).show()
        }

        btnToggleService.setOnClickListener {
            if (FloatingWidgetService.isRunning) {
                stopService(Intent(this, FloatingWidgetService::class.java))
                updatePermissionAndServiceUi()
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Aktifkan izin Overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (PrefsManager.getApiKey(this).isBlank()) {
                    Toast.makeText(this, "Simpan API Key di tab Atur terlebih dahulu!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(this, FloatingWidgetService::class.java)
                ContextCompat.startForegroundService(this, intent)
                btnToggleService.postDelayed({ updatePermissionAndServiceUi() }, 200)
            }
        }
    }

    private fun saveCurrentInputs(): Boolean {
        val provider = providers[spinnerProvider.selectedItemPosition]
        val apiKey = etApiKey.text.toString().trim()
        val model = etModel.text.toString().trim()
        val baseUrl = etBaseUrl.text.toString().trim()

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "API Key tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return false
        }

        PrefsManager.saveConfig(this, provider, apiKey, model, baseUrl)
        updatePermissionAndServiceUi()
        Toast.makeText(this, "Konfigurasi disimpan", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun appendChat(line: String) {
        val current = tvChatHistory.text.toString()
        tvChatHistory.text = if (current.startsWith("ExamAI Chat siap")) line else "$current\n$line"
        scrollChat.post { scrollChat.fullScroll(View.FOCUS_DOWN) }
    }

    private fun refreshHistoryUi() {
        val items = PrefsManager.getHistory(this)
        if (items.isEmpty()) {
            tvHistoryList.text = "Belum ada riwayat jawaban."
        } else {
            tvHistoryList.text = items.joinToString("\n\n") { item ->
                "[${item.timestamp}] ${item.question}\n→ Jawaban: ${item.answer}"
            }
        }
    }

    private fun updatePermissionAndServiceUi() {
        val hasKey = PrefsManager.getApiKey(this).isNotBlank()
        tvLiveModel.text = if (hasKey) "Model AI Online" else "API Key Belum Diisi"
        tvLiveProvider.text = "${PrefsManager.getProvider(this)} · ${PrefsManager.getModel(this)}"

        val overlayGranted = Settings.canDrawOverlays(this)
        tvOverlayStatus.text = if (overlayGranted) "Izin Overlay: Aktif" else "Izin Overlay: Belum Aktif"
        tvOverlayStatus.setTextColor(
            ContextCompat.getColor(this, if (overlayGranted) R.color.ok else R.color.bad)
        )

        val accessibilityActive = FormAutoAccessibilityService.instance != null
        tvAccessibilityStatus.text = if (accessibilityActive) {
            "Accessibility Service: Aktif"
        } else {
            "Accessibility Service: Belum Aktif"
        }
        tvAccessibilityStatus.setTextColor(
            ContextCompat.getColor(this, if (accessibilityActive) R.color.ok else R.color.bad)
        )

        tvAutoAnswerDetailStatus.text = if (accessibilityActive) {
            "Status: Siap membaca Google Form di Chrome"
        } else {
            "Status: Menunggu aktivasi Accessibility"
        }
        tvAutoAnswerDetailStatus.setTextColor(
            ContextCompat.getColor(this, if (accessibilityActive) R.color.ok else R.color.warn)
        )

        btnToggleService.text = if (FloatingWidgetService.isRunning) {
            "Hentikan Floating Widget"
        } else {
            "Jalankan Floating Widget"
        }
    }

    private fun applyBrandPolish() {
        val tvHeaderWordmark = findViewById<TextView>(R.id.tvHeaderWordmark)
        applyGradientText(tvHeaderWordmark)

        val dotLive = findViewById<View>(R.id.dotLive)
        ObjectAnimator.ofPropertyValuesHolder(
            dotLive,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.45f, 1f)
        ).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun applyGradientText(tv: TextView) {
        tv.post {
            val width = tv.paint.measureText(tv.text.toString())
            if (width > 0f) {
                tv.paint.shader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(
                        Color.parseColor("#A79BFF"),
                        Color.parseColor("#C77DF0"),
                        Color.parseColor("#F27BC0")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                tv.invalidate()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
