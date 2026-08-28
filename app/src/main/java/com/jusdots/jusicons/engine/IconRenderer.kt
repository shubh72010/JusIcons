package com.jusdots.jusicons.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

interface IconRenderer {
    fun render(source: Drawable, sizePx: Int, options: RenderOptions = RenderOptions()): Drawable
}

class JusIconsRenderer(private val context: Context) : IconRenderer {
    private val themedProvider by lazy { ThemedIconProvider(context) }

    override fun render(source: Drawable, sizePx: Int, options: RenderOptions): Drawable {
        return renderWithDebug(source, sizePx, options, null)
    }

    fun renderForPackage(packageName: String, source: Drawable, sizePx: Int, options: RenderOptions = RenderOptions()): Drawable {
        return renderWithDebug(packageName, source, sizePx, options, null)
    }

    fun renderWithDebug(source: Drawable, sizePx: Int, options: RenderOptions, debug: MonoProcessor.DebugSink?): Drawable {
        return renderWithDebug("", source, sizePx, options, debug)
    }

    fun renderWithDebug(packageName: String, source: Drawable, sizePx: Int, options: RenderOptions, debug: MonoProcessor.DebugSink?): Drawable {
        // 1) Curated ThemeData — replaces source drawable entirely (cannot be synthesized via d7/f)
        if (packageName.isNotEmpty()) {
            val themeData = themedProvider.getThemeDataForPackage(packageName)
            themeData?.loadPaddedDrawable()?.let { curated ->
                debug?.let {
                    try { it.onStage("01_original", IconNormalizer.toBitmap(source, sizePx)) } catch (_: Exception) {}
                    try { it.onStage("02_curated", IconNormalizer.toBitmap(curated, sizePx)) } catch (_: Exception) {}
                }
                val mono = drawableToMono(curated, sizePx)
                val bg = if (options.showBackground) createCircularBg(sizePx) else null
                val drawable = ThemedIconDrawable(mono, bg, options.bgColor, options.fgColor)
                debug?.let {
                    try { it.onStage("07_cropped", mono.copy(Bitmap.Config.ARGB_8888, false)) } catch (_: Exception) {}
                    val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    val c = Canvas(out); drawable.setBounds(0, 0, sizePx, sizePx); drawable.draw(c)
                    it.onStage("08_final", out)
                    // emit empty placeholders for 03-06 so UI strip stays 01..08 ordered (no-op bitmaps)
                    val placeholder = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    placeholder.eraseColor(android.graphics.Color.TRANSPARENT)
                    it.onStage("03_grayscale", placeholder)
                    it.onStage("04_analysis", placeholder)
                    it.onStage("05_remapped", placeholder)
                    it.onStage("06_alpha", placeholder)
                }
                return drawable
            }
        }
        // 2) AdaptiveIconDrawable.monochrome — DISABLED for forensic fidelity.
        // Nothing's IconProvider.java:180 only injects ThemeData when getMonochrome()==null;
        // it does NOT force monochrome-through-JusIcons for every AdaptiveIcon. We added this
        // as Android-correct but it's more aggressive than Nothing, wrecking JusIcons own icon
        // and others where monochrome != Nothing glyph. Keep curated→generic only:
        // ponytail: disabled monochrome branch, re-enable behind flag if proven via decompiled path.
        // if (source is AdaptiveIconDrawable) { source.monochrome?.let { ... } }
        val normalized: Bitmap = try { IconNormalizer.toBitmap(source, sizePx) } catch (_: Exception) { rasterFallback(source, sizePx) }
        val scale = if (options.forensicScale) MonoProcessor.CROP_SCALE_FORENSIC else MonoProcessor.CROP_SCALE_VISUAL
        val result = MonoProcessor.process(normalized, sizePx, options.enableMonoCheck, debug, scale, options.binary)
        normalized.recycle()
        val drawable = if (result != null) {
            val bg = if (options.showBackground) createCircularBg(sizePx) else null
            val mono = pickMono(result.mono, result.monoLight, options.fgColor)
            ThemedIconDrawable(mono, bg, options.bgColor, options.fgColor)
        } else fallbackDrawable(source, sizePx, options)
        // 08_final: rendered with colors (for forensic prove SRC_IN preserves alpha)
        debug?.let {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(c)
            it.onStage("08_final", out)
        }
        return drawable
    }
    private fun drawableToMono(drawable: Drawable, sizePx: Int): Bitmap {
        val bmp = try { IconNormalizer.toBitmap(drawable, sizePx) } catch (_: Exception) { rasterFallback(drawable, sizePx) }
        val mono = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (y in 0 until sizePx) for (x in 0 until sizePx) {
            val p = bmp.getPixel(x, y)
            val a = Color.alpha(p)
            mono.setPixel(x, y, (a shl 24)) // black with alpha = stroke opacity
        }
        bmp.recycle()
        return mono
    }
    private fun pickMono(mono: Bitmap, monoLight: Bitmap?, fg: Int): Bitmap {
        if (monoLight == null) return mono
        val isLight = (Color.red(fg)*0.2126 + Color.green(fg)*0.7152 + Color.blue(fg)*0.0722) > 128
        return if (isLight) monoLight else mono
    }
    private fun createBgBitmap(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        return bmp
    }
    private fun createCircularBg(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // whiteShadowLayer is blurred circular shadow — approximate with radial gradient + blur for pixel-perfect Nothing
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val radius = sizePx * 0.44f
        c.drawCircle(sizePx/2f, sizePx/2f, radius, p)
        // soft edge: blur via mask filter (Nothing's shadow has ~2px blur)
        p.maskFilter = android.graphics.BlurMaskFilter(sizePx * 0.02f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.drawCircle(sizePx/2f, sizePx/2f, radius * 0.98f, p)
        return bmp
    }
    private fun rasterFallback(source: Drawable, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); source.setBounds(0,0,sizePx,sizePx); source.draw(c); return bmp
    }
    private fun fallbackDrawable(source: Drawable, sizePx: Int, options: RenderOptions): Drawable {
        val bmp = IconNormalizer.toBitmap(source, sizePx)
        val mono = Bitmap.createBitmap(sizePx, sizePx, if (android.os.Build.VERSION.SDK_INT >= 26) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888)
        val grayBmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val gc = Canvas(grayBmp); source.setBounds(0,0,sizePx,sizePx); source.draw(gc)
        for (y in 0 until sizePx) for (x in 0 until sizePx) {
            val p = grayBmp.getPixel(x,y)
            val a = Color.alpha(p)
            if (a==0) continue
            val g = (Color.red(p)*0.3 + Color.green(p)*0.59 + Color.blue(p)*0.11).toInt()
            val outA = if (g < 128) 255 else 0
            mono.setPixel(x,y, outA shl 24)
        }
        grayBmp.recycle(); bmp.recycle()
        return ThemedIconDrawable(mono, createBgBitmap(sizePx), options.bgColor, options.fgColor)
    }
}
