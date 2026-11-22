package com.example.examai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.examai.R
import kotlin.math.max
import kotlin.math.min

class CropOverlayView(
    context: Context,
    private val fullBitmap: Bitmap,
    private val onCropConfirmed: (Bitmap, CropAction) -> Unit,
    private val onCancelled: () -> Unit
) : FrameLayout(context) {

    enum class CropAction {
        ASK_AI,
        SEARCH_GOOGLE
    }

    private val scrimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#7C6CF6")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val clearPaint = Paint().apply {
        color = Color.parseColor("#227C6CF6")
        style = Paint.Style.FILL
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var hasSelection = false

    private val actionBar: LinearLayout

    private val drawView = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            if (hasSelection) {
                val rect = getSelectionRectF()
                canvas.drawRect(rect, clearPaint)
                canvas.drawRect(rect, borderPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = event.x
                    endY = event.y
                    hasSelection = true
                    actionBar.visibility = GONE
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    endX = event.x
                    endY = event.y
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    endX = event.x
                    endY = event.y
                    val rect = getSelectionRectF()
                    if (rect.width() > 30 && rect.height() > 30) {
                        actionBar.visibility = VISIBLE
                    } else {
                        hasSelection = false
                    }
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    init {
        addView(drawView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 24)
            visibility = GONE
        }

        val btnAskAi = Button(context).apply {
            text = "🤖 Tanya AI"
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_btn_gradient)
            setPadding(28, 12, 28, 12)
            setOnClickListener { confirmCrop(CropAction.ASK_AI) }
        }

        val btnGoogle = Button(context).apply {
            text = "🔍 Cari Google"
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_input)
            setPadding(28, 12, 28, 12)
            setOnClickListener { confirmCrop(CropAction.SEARCH_GOOGLE) }
        }

        val btnCancel = Button(context).apply {
            text = "✕"
            setTextColor(Color.LTGRAY)
            setBackgroundResource(R.drawable.bg_input)
            setPadding(24, 12, 24, 12)
            setOnClickListener { onCancelled() }
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 12 }

        actionBar.addView(btnAskAi, lp)
        actionBar.addView(btnGoogle, lp)
        actionBar.addView(btnCancel)

        val barParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        addView(actionBar, barParams)
    }

    private fun getSelectionRectF(): RectF {
        return RectF(
            min(startX, endX),
            min(startY, endY),
            max(startX, endX),
            max(startY, endY)
        )
    }

    private fun confirmCrop(action: CropAction) {
        val viewW = drawView.width.coerceAtLeast(1).toFloat()
        val viewH = drawView.height.coerceAtLeast(1).toFloat()
        val scaleX = fullBitmap.width / viewW
        val scaleY = fullBitmap.height / viewH

        val sel = getSelectionRectF()
        val rect = Rect(
            (sel.left * scaleX).toInt().coerceIn(0, fullBitmap.width - 1),
            (sel.top * scaleY).toInt().coerceIn(0, fullBitmap.height - 1),
            (sel.right * scaleX).toInt().coerceIn(1, fullBitmap.width),
            (sel.bottom * scaleY).toInt().coerceIn(1, fullBitmap.height)
        )

        val w = (rect.width()).coerceAtLeast(1)
        val h = (rect.height()).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(fullBitmap, rect.left, rect.top, w, h)
        onCropConfirmed(cropped, action)
    }
}
