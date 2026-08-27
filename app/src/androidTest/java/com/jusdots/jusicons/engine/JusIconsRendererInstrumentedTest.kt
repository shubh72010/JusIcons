package com.jusdots.jusicons.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class JusIconsRendererInstrumentedTest {
    private fun solidBitmap(color: Int, size: Int = 192): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return b
    }
    private fun gradientBitmap(size: Int = 192): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        for (y in 0 until size) {
            p.color = Color.rgb(y * 255 / size, 0, 255 - y * 255 / size)
            c.drawLine(0f, y.toFloat(), size.toFloat(), y.toFloat(), p)
        }
        return b
    }
    private fun circleBitmap(size: Int = 192): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
        c.drawCircle(size/2f, size/2f, size*0.4f, p)
        return b
    }

    @Test fun renders_withoutCrash_andProducesDrawable() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val r = JusIconsRenderer(ctx)
        val size = 192
        val cases = listOf(
            "legacy_red" to ColorDrawable(Color.RED),
            "transparent" to ColorDrawable(Color.TRANSPARENT),
            "black" to ColorDrawable(Color.BLACK),
            "white" to ColorDrawable(Color.WHITE),
            "colorful" to ColorDrawable(Color.MAGENTA),
            "adaptive_sim" to ColorDrawable(Color.GREEN), // IconNormalizer handles adaptive separately; ColorDrawable covers legacy path
        )
        for ((name, d) in cases) {
            val out = r.render(d, size)
            assertNotNull("$name rendered null", out)
            assertTrue("$name width", out.intrinsicWidth > 0)
        }
        // gradient via bitmap drawable
        val grad = gradientBitmap(size)
        val gradOut = r.render(android.graphics.drawable.BitmapDrawable(ctx.resources, grad), size)
        assertNotNull(gradOut)

        // Write PNGs to device's cache for visual inspection (clearly ignored in git via /build, but also debug-output)
        val outDir = File(ctx.cacheDir, "jusicons_test_output").apply { mkdirs() }
        for ((name, d) in listOf("circle" to circleBitmap(size), "gradient" to gradientBitmap(size), "solid_red" to solidBitmap(Color.RED, size))) {
            val rendered = r.render(android.graphics.drawable.BitmapDrawable(ctx.resources, d), size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            rendered.setBounds(0, 0, size, size)
            rendered.draw(canvas)
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            d.recycle(); bmp.recycle()
        }
        assertTrue(outDir.listFiles()?.isNotEmpty() == true)
    }
}
