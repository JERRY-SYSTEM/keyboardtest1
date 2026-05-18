package com.cesia.input.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

class CustomKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : KeyboardView(context, attrs, defStyleAttr) {

    private val subsidiaryPaint = Paint().apply {
        color = 0xFF999999.toInt()
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val keys = keyboard?.keys ?: return
        for (key in keys) {
            if (!key.popupCharacters.isNullOrEmpty()) {
                val symbol = key.popupCharacters[0].toString()
                // 调大符号：从0.22f改为0.30f，最大16f
                val textSize = (key.height * 0.30f).coerceIn(10f, 16f)
                subsidiaryPaint.textSize = textSize

                // 往左下方移动：x从右边往左移8dp，y从顶部往下移到按键下半部分
                val x = key.x + key.width - 8f
                val y = key.y + key.height - 6f

                canvas.drawText(symbol, x, y, subsidiaryPaint)
            }
        }
    }
}
