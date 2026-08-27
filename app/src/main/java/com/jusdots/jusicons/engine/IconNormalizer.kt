package com.jusdots.jusicons.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build

object IconNormalizer {
    fun toBitmap(source: Drawable, sizePx: Int): Bitmap {
        val drawable = source.mutate()
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (drawable is AdaptiveIconDrawable) {
            // Nothing's mono uses foreground only — background (often solid white) would become white square in mono
            // For forensics, draw foreground on transparent, background ignored
            val fg = drawable.foreground
            if (fg != null) {
                fg.setBounds(0, 0, sizePx, sizePx)
                try { fg.draw(canvas) } catch (_: Exception) { fg.draw(canvas) }
            } else {
                drawable.setBounds(0, 0, sizePx, sizePx)
                try { drawable.draw(canvas) } catch (_: Exception) {
                    drawable.background?.let { it.bounds = drawable.bounds; it.draw(canvas) }
                    drawable.foreground?.let { it.bounds = drawable.bounds; it.draw(canvas) }
                }
            }
        } else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
            val ratio = w.toFloat() / h
            val dstW: Int
            val dstH: Int
            if (w > h) { dstH = (sizePx * 0.93f).toInt(); dstW = (dstH * ratio).toInt().coerceAtMost(sizePx) }
            else { dstW = (sizePx * 0.93f).toInt(); dstH = (dstW / ratio).toInt().coerceAtMost(sizePx) }
            val left = (sizePx - dstW) / 2
            val top = (sizePx - dstH) / 2
            drawable.setBounds(left, top, left + dstW, top + dstH)
            if (drawable is BitmapDrawable && drawable.bitmap?.density == 0 && Build.VERSION.SDK_INT >= 24) {
                drawable.setTargetDensity(canvas.maximumBitmapWidth)
            }
            drawable.draw(canvas)
        }
        return out
    }
}
