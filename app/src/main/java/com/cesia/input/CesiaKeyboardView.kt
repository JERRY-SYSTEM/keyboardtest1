package com.cesia.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.util.TypedValue

/**
 * 自定义 KeyboardView — 在功能键右上角显示长按副功能文字（灰色小字）
 * 同时支持数字键盘的 T9 副字符标签
 */
class CesiaKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = context.resources.getIdentifier("keyboardViewStyle", "attr", "android").takeIf { it != 0 } ?: 0
) : KeyboardView(context, attrs, defStyleAttr) {

    // 副功能文字映射：primaryCode -> 显示文字
    private var functionalLabels = mapOf<Int, String>()

    // 副功能文字画笔
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.RIGHT
        color = 0xFF999999.toInt()
    }

    private var labelTextSize = 9f

    // 数字键盘的 T9 副字符标签
    private var t9Labels = mapOf<Int, String>()

    fun setFunctionalLabels(labels: Map<Int, String>) {
        functionalLabels = labels
        invalidateAllKeys()
    }

    fun setT9Labels(labels: Map<Int, String>) {
        t9Labels = labels
        invalidateAllKeys()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val spSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, labelTextSize, resources.displayMetrics
        )
        labelPaint.textSize = spSize

        val keyboard = keyboard ?: return
        for (key in keyboard.keys) {
            val code = key.codes?.firstOrNull() ?: continue
            val label = functionalLabels[code] ?: t9Labels[code] ?: continue
            if (key.label != null) {
                val x = key.x + key.width - 3f
                val y = key.y + spSize + 1f
                canvas.drawText(label, x, y, labelPaint)
            }
        }
    }
}
