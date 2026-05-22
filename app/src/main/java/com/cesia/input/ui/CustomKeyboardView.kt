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
                // 调大符号：0.45f，最大28f
                val textSize = (key.height * 0.45f).coerceIn(20f, 28f)
                subsidiaryPaint.textSize = textSize

                // 右上角：右边留6px，顶部留4px+文字高度偏移
                val x = key.x + key.width - 6f
                val y = key.y + textSize + 4f

                canvas.drawText(symbol, x, y, subsidiaryPaint)
            }
        }
    }
}
