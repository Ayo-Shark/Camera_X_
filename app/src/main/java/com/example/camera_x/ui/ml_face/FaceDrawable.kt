package com.example.camera_x.ui.ml_face

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class FaceDrawable(private val faceViewModel: FaceViewModel) : Drawable() {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.GREEN
        strokeWidth = 10f
        alpha = 200
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 40f
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(faceViewModel.boundingRect, paint)
        faceViewModel.smilingProbability?.let { prob ->
            val text = "Smile: ${(prob * 100).toInt()}%"
            canvas.drawText(
                text,
                faceViewModel.boundingRect.left.toFloat(),
                faceViewModel.boundingRect.top - 10f,
                textPaint
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}