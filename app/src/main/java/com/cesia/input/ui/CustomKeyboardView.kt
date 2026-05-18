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
        color = 0xFF888888.toInt()
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
                // 调大符号：0.35f，最大18f
                val textSize = (key.height * 0.35f).coerceIn(12f, 18f)
                subsidiaryPaint.textSize = textSize

                // 往左下方移动：离右边和底部都有足够距离
                val x = key.x + key.width - 10f
                val y = key.y + key.height - 4f

                canvas.drawText(symbol, x, y, subsidiaryPaint)
            }
        }
    }
}
