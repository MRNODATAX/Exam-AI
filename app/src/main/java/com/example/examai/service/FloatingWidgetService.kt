package com.example.examai.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.examai.R
import com.example.examai.service.FormAutoAccessibilityService.ProcessStatus
import kotlin.math.abs

class FloatingWidgetService : Service() {

    companion object {
        var isRunning = false
            private set
        private const val CHANNEL_ID = "exam_ai_overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var ivIcon: ImageView
    private lateinit var tvStatus: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

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
                "Exam AI Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exam AI Assistant Aktif")
            .setContentText("Ketuk ikon melayang di Chrome untuk membaca & menjawab soal.")
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

        val params = WindowManager.LayoutParams(
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

        view.setOnTouchListener { _, event ->
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
                        onFloatingButtonClicked()
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(view, params)
        updateStatusUi(ProcessStatus.IDLE)
    }

    private fun onFloatingButtonClicked() {
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
        floatingView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
        floatingView = null
    }
}
