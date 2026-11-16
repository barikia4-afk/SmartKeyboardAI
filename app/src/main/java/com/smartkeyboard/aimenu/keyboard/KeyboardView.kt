package com.smartkeyboard.aimenu.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.smartkeyboard.aimenu.R
import com.smartkeyboard.aimenu.data.KeyActionType
import com.smartkeyboard.aimenu.data.KeyModel
import com.smartkeyboard.aimenu.data.KeyboardMode
import com.smartkeyboard.aimenu.data.KeyboardPreferences
import com.smartkeyboard.aimenu.data.KeyboardThemeType
import com.smartkeyboard.aimenu.data.ShiftState
import kotlin.math.max

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val prefs = KeyboardPreferences(context)
    private val hapticManager = HapticFeedbackManager(context)

    var keyboardMode: KeyboardMode = KeyboardMode.ENGLISH
        set(value) {
            field = value
            rebuildLayout()
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            field = value
            rebuildLayout()
            invalidate()
        }

    var enterActionLabel: String = "↵"
        set(value) {
            field = value
            invalidate()
        }

    var onKeyPressListener: ((KeyModel) -> Unit)? = null

    private var rows: List<List<KeyModel>> = emptyList()
    private val keyBounds = mutableMapOf<KeyModel, RectF>()
    private var pressedKey: KeyModel? = null

    // Visual Paints
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = Color.parseColor("#94A3B8")
    }

    // Colors cache
    private var normalKeyColor = Color.parseColor("#222A3B")
    private var specialKeyColor = Color.parseColor("#181F2C")
    private var pressedKeyColor = Color.parseColor("#3D4A66")
    private var strokeColor = Color.parseColor("#33FFFFFF")
    private var textColor = Color.WHITE

    // Backspace auto-repeat handler
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var isRepeating = false
    private val repeatRunnable = object : Runnable {
        override fun run() {
            pressedKey?.let {
                if (it.actionType == KeyActionType.BACKSPACE) {
                    onKeyPressListener?.invoke(it)
                    hapticManager.performKeyFeedback(isSpecial = true)
                    repeatHandler.postDelayed(this, 50)
                }
            }
        }
    }

    init {
        updateThemeColors()
        rebuildLayout()
    }

    fun updateThemeColors() {
        when (prefs.theme) {
            KeyboardThemeType.LIGHT_GLASS -> {
                normalKeyColor = ContextCompat.getColor(context, R.color.glass_key_bg_light)
                specialKeyColor = ContextCompat.getColor(context, R.color.glass_key_special_light)
                pressedKeyColor = ContextCompat.getColor(context, R.color.glass_key_bg_pressed_light)
                strokeColor = ContextCompat.getColor(context, R.color.glass_kb_border_light)
                textColor = ContextCompat.getColor(context, R.color.glass_key_text_light)
            }
            KeyboardThemeType.DARK_GLASS -> {
                normalKeyColor = ContextCompat.getColor(context, R.color.glass_key_bg_dark)
                specialKeyColor = ContextCompat.getColor(context, R.color.glass_key_special_dark)
                pressedKeyColor = ContextCompat.getColor(context, R.color.glass_key_bg_pressed_dark)
                strokeColor = ContextCompat.getColor(context, R.color.glass_kb_border_dark)
                textColor = ContextCompat.getColor(context, R.color.glass_key_text_dark)
            }
            KeyboardThemeType.OLED_GLASS -> {
                normalKeyColor = ContextCompat.getColor(context, R.color.glass_key_bg_oled)
                specialKeyColor = ContextCompat.getColor(context, R.color.glass_key_special_oled)
                pressedKeyColor = ContextCompat.getColor(context, R.color.glass_key_bg_pressed_oled)
                strokeColor = ContextCompat.getColor(context, R.color.glass_kb_border_oled)
                textColor = ContextCompat.getColor(context, R.color.glass_key_text_oled)
            }
        }
        textPaint.color = textColor
        keyStrokePaint.color = strokeColor
        invalidate()
    }

    private fun rebuildLayout() {
        rows = KeyboardLayoutManager.getLayoutRows(keyboardMode, shiftState)
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyBounds(w, h)
    }

    private fun calculateKeyBounds(width: Int, height: Int) {
        keyBounds.clear()
        if (rows.isEmpty() || width <= 0 || height <= 0) return

        val paddingH = 6f
        val paddingV = 6f
        val usableWidth = width - (paddingH * 2)
        val usableHeight = height - (paddingV * 2)
        val numRows = rows.size
        val rowHeight = usableHeight / numRows
        val keyMargin = 4f

        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            val totalWeight = row.map { it.widthWeight }.sum()
            val totalMargins = (row.size - 1) * keyMargin
            val availableKeyWidth = usableWidth - totalMargins

            var currentX = paddingH
            val currentY = paddingV + (rowIndex * rowHeight)

            for (key in row) {
                val keyWidth = (key.widthWeight / totalWeight) * availableKeyWidth
                val rect = RectF(
                    currentX,
                    currentY + keyMargin,
                    currentX + keyWidth,
                    currentY + rowHeight - keyMargin
                )
                keyBounds[key] = rect
                currentX += keyWidth + keyMargin
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cornerRadius = 14f
        val textSize = height * 0.075f
        textPaint.textSize = textSize
        subTextPaint.textSize = textSize * 0.55f

        for ((key, rect) in keyBounds) {
            val isPressed = (key == pressedKey)

            // Background color
            keyPaint.color = when {
                isPressed -> pressedKeyColor
                key.isSpecial -> specialKeyColor
                else -> normalKeyColor
            }

            // Draw rounded glass key surface
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyPaint)
            // Draw subtle glass border
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyStrokePaint)

            // Label
            val label = if (key.actionType == KeyActionType.ENTER && enterActionLabel != "↵") {
                enterActionLabel
            } else {
                key.primaryLabel
            }

            // Center text baseline
            val fontMetrics = textPaint.fontMetrics
            val baseline = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2

            // Enter / Action keys accent highlight
            if (key.actionType == KeyActionType.ENTER) {
                textPaint.color = Color.parseColor("#00E5FF")
            } else {
                textPaint.color = textColor
            }

            canvas.drawText(label, rect.centerX(), baseline, textPaint)

            // Sub-label (if any)
            key.subLabel?.let { sub ->
                canvas.drawText(sub, rect.right - 8f, rect.top + 16f, subTextPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKeyAt(x, y)
                if (key != null) {
                    pressedKey = key
                    hapticManager.performKeyFeedback(key.isSpecial)
                    invalidate()

                    if (key.actionType == KeyActionType.BACKSPACE) {
                        isRepeating = false
                        repeatHandler.postDelayed(repeatRunnable, 350)
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val currentKey = findKeyAt(x, y)
                if (currentKey != pressedKey) {
                    pressedKey = currentKey
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                repeatHandler.removeCallbacks(repeatRunnable)
                pressedKey?.let { key ->
                    if (findKeyAt(x, y) == key) {
                        onKeyPressListener?.invoke(key)
                    }
                }
                pressedKey = null
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                repeatHandler.removeCallbacks(repeatRunnable)
                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    private fun findKeyAt(x: Float, y: Float): KeyModel? {
        for ((key, rect) in keyBounds) {
            if (rect.contains(x, y)) {
                return key
            }
        }
        return null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        repeatHandler.removeCallbacks(repeatRunnable)
    }
}
