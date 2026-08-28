package com.jusdots.jusicons.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Device-generated pack — Option A from README.
 * Real icons from PackageManager -> MonoProcessor -> vector path -> res/drawable XML
 * Keeps MonoProcessor untouched; only wraps it with PackageManager extraction + vector emission.
 * Glyph-only (no #121212 circle) — launcher handles bg, like Lawnicons/Arcticons.
 */
class IconPackGenerator(private val context: Context, private val renderer: JusIconsRenderer) {

    data class Generated(val pkg: String, val drawableName: String, val pathData: String, val component: String)

    suspend fun generateForInstalled(
        maxApps: Int = 40,
        outDir: File,
        onProgress: (Int, Int) -> Unit = {_,_ ->}
    ): List<Generated> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER) }
        val resolves = pm.queryIntentActivities(intent, 0).sortedBy { it.loadLabel(pm).toString().lowercase() }.take(maxApps)
        val out = mutableListOf<Generated>()
        // Reuse synthetic vectorizer from build-time script (rect per run)
        for ((idx, ri) in resolves.withIndex()) {
            onProgress(idx + 1, resolves.size)
            val pkg = ri.activityInfo.packageName
            val component = "ComponentInfo{$pkg/${ri.activityInfo.name}}"
            val drawableName = "jus_" + pkg.replace(".", "_").replace("-", "_").lowercase().take(40)
            try {
                val ai: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                val original: Drawable = pm.getApplicationIcon(ai)
                // Run hybrid renderer to get mono bitmap (respects curated + generic)
                // Instead of drawableToMono, we run full hybrid and extract mono
                val sizePx = 192
                val opts = RenderOptions(showBackground = false, forensicScale = false, binary = true) // glyph-only for pack
                // Use renderer's internal mono extraction via debug sink to get 07_cropped
                var monoBmp: Bitmap? = null
                val debug = object : MonoProcessor.DebugSink {
                    override fun onStage(name: String, bitmap: Bitmap) {
                        if (name == "07_cropped" || name == "02_curated") {
                            monoBmp = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        }
                    }
                }
                // Trigger rendering to capture mono
                renderer.renderWithDebug(pkg, original, sizePx, opts, debug)
                val mono = monoBmp ?: continue
                val pathData = vectorize(mono)
                mono.recycle()
                if (pathData.isEmpty()) continue
                // Write vector drawable to outDir
                val xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#FFFFFFFF" android:pathData="$pathData" />
</vector>
"""
                File(outDir, "$drawableName.xml").writeText(xml)
                out.add(Generated(pkg, drawableName, pathData, component))
            } catch (_: Exception) {}
        }
        // Write appfilter.xml + drawable.xml for this generated set
        val appfilter = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources>\n")
            for (g in out) append("    <item component=\"${g.component}\" drawable=\"${g.drawableName}\" />\n")
            append("</resources>\n")
        }
        File(outDir, "appfilter_generated.xml").writeText(appfilter)
        val drawableXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<resources>\n    <version>1</version>\n")
            for (g in out) append("    <item drawable=\"${g.drawableName}\" />\n")
            append("</resources>\n")
        }
        File(outDir, "drawable_generated.xml").writeText(drawableXml)
        out
    }

    private fun vectorize(mono: Bitmap): String {
        val n = mono.width
        val viewport = 48f
        val scale = viewport / n
        val paths = mutableListOf<String>()
        for (y in 0 until n) {
            var x = 0
            while (x < n) {
                val a = (mono.getPixel(x, y) ushr 24) and 0xFF
                if (a > 127) {
                    val x0 = x
                    while (x < n && ((mono.getPixel(x, y) ushr 24) and 0xFF) > 127) x++
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
}
