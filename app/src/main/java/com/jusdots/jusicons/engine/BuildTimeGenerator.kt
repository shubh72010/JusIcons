package com.jusdots.jusicons.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import java.io.File

// Standalone generator for build-time — uses same MonoProcessor as runtime
object BuildTimeGenerator {
    fun generateForDrawable(drawable: Drawable, sizePx: Int = 192): Bitmap? {
        val opts = RenderOptions(showBackground = false, forensicScale = false, binary = true, enableMonoCheck = false)
        // Use JusIconsRenderer to get mono
        // We need a Context, but we can directly call MonoProcessor
        val bmp = try { IconNormalizer.toBitmap(drawable, sizePx) } catch(_:Exception){ return null }
        val scale = if (opts.forensicScale) MonoProcessor.CROP_SCALE_FORENSIC else MonoProcessor.CROP_SCALE_VISUAL
        val result = MonoProcessor.process(bmp, sizePx, opts.enableMonoCheck, null, scale, opts.binary)
        bmp.recycle()
        return result?.mono
    }

    fun vectorize(mono: Bitmap, viewport: Int = 48): String {
        val n = mono.width
        val scale = viewport.toFloat() / n
        val paths = mutableListOf<String>()
        for (y in 0 until n) {
            var x = 0
            while (x < n) {
                val a = Color.alpha(mono.getPixel(x, y))
                if (a > 127) {
                    val x0 = x
                    while (x < n && Color.alpha(mono.getPixel(x, y)) > 127) x++
                    val x1 = x
                    val vx0 = x0 * scale
                    val vy0 = y * scale
                    val vw = (x1 - x0) * scale
                    val vh = 1 * scale
                    paths.add("M%.2f,%.2fh%.2fv%.2fh%.2fZ".format(vx0, vy0, vw, vh, -vw))
                } else x++
            }
        }
        return paths.joinToString(" ")
    }

    fun toVectorXml(pathData: String, withBg: Boolean = true): String {
        val bg = if (withBg) """<path android:fillColor="#FF121212" android:pathData="M24,2 A22,22 0 1,0 24,46 A22,22 0 1,0 24,2 Z" />
    """ else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    $bg<path android:fillColor="#FFFFFFFF" android:pathData="$pathData" />
</vector>
"""
    }
}
