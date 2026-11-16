package com.example.examai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.examai.service.FloatingWidgetService
import com.example.examai.service.FormAutoAccessibilityService
import com.example.examai.util.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnRequestOverlay: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var spinnerProvider: Spinner
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var layoutBaseUrl: LinearLayout
    private lateinit var etBaseUrl: EditText
    private lateinit var btnSaveConfig: Button
    private lateinit var btnToggleService: Button

    private val providers = listOf(
        PrefsManager.PROVIDER_GEMINI,
        PrefsManager.PROVIDER_OMNIROUTE,
        PrefsManager.PROVIDER_STANDARD
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupProviderSpinner()
        loadSavedConfig()
        setupListeners()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionAndServiceUi()
    }

    private fun initViews() {
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnRequestOverlay = findViewById(R.id.btnRequestOverlay)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        spinnerProvider = findViewById(R.id.spinnerProvider)
        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        layoutBaseUrl = findViewById(R.id.layoutBaseUrl)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)
        btnToggleService = findViewById(R.id.btnToggleService)
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
            val provider = providers[spinnerProvider.selectedItemPosition]
            val apiKey = etApiKey.text.toString().trim()
            val model = etModel.text.toString().trim()
            val baseUrl = etBaseUrl.text.toString().trim()

            if (apiKey.isEmpty()) {
                Toast.makeText(this, "API Key tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            PrefsManager.saveConfig(this, provider, apiKey, model, baseUrl)
            Toast.makeText(this, "Konfigurasi berhasil disimpan", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Simpan API Key terlebih dahulu!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(this, FloatingWidgetService::class.java)
                ContextCompat.startForegroundService(this, intent)
                btnToggleService.postDelayed({ updatePermissionAndServiceUi() }, 200)
            }
        }
    }

    private fun updatePermissionAndServiceUi() {
        val overlayGranted = Settings.canDrawOverlays(this)
        tvOverlayStatus.text = if (overlayGranted) {
            "Izin Overlay: Aktif"
        } else {
            "Izin Overlay: Belum Aktif"
        }
        tvOverlayStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (overlayGranted) R.color.status_success else R.color.status_error
            )
        )

        val accessibilityActive = FormAutoAccessibilityService.instance != null
        tvAccessibilityStatus.text = if (accessibilityActive) {
            "Accessibility Service: Aktif"
        } else {
            "Accessibility Service: Belum Aktif"
        }
        tvAccessibilityStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (accessibilityActive) R.color.status_success else R.color.status_error
            )
        )

        if (FloatingWidgetService.isRunning) {
            btnToggleService.text = "Hentikan Floating Widget"
            btnToggleService.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.status_error)
        } else {
            btnToggleService.text = "Jalankan Floating Widget"
            btnToggleService.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.status_success)
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
