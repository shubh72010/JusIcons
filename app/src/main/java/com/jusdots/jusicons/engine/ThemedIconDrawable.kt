package com.jusdots.jusicons.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build

class ThemedIconDrawable(
    private val mono: Bitmap,
    private val bgBitmap: Bitmap?,
    private val colorBg: Int,
    private val colorFg: Int
) : Drawable() {
    private val monoPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = tintFilter(colorFg) }
    private val bgPaint: Paint? = bgBitmap?.let { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = tintFilter(colorBg) } }
    private fun tintFilter(color: Int): ColorFilter =
        if (Build.VERSION.SDK_INT >= 29) BlendModeColorFilter(color, BlendMode.SRC_IN)
        else PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    override fun draw(canvas: Canvas) {
        val bounds: Rect = bounds
        if (bounds.isEmpty) return
        // forensic: bg is whiteShadowLayer circular shadow; if null → transparent (glyph-only, most Nothing-like in debug UI)
        bgBitmap?.let { bg -> bgPaint?.let { canvas.drawBitmap(bg, null, bounds, it) } }
        canvas.drawBitmap(mono, null, bounds, monoPaint)
    }
    override fun setAlpha(alpha: Int) { monoPaint.alpha = alpha; bgPaint?.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = mono.width
    override fun getIntrinsicHeight(): Int = mono.height
}
