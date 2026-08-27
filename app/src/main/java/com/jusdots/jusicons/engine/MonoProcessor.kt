package com.jusdots.jusicons.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Forensic port of d7.f + o7.k/G + o7.d/g + k7.c + o7.a.h
 * Evidence: RENDERING_ALGORITHM.md & jadx: d7/f.java, o7/k.java, o7/d.java, o7/a.java
 * - Input: normalized Bitmap size iconSize (192), ARGB_8888
 * - Output: MonoResult with mono ARGB_8888 (alpha channel is monochrome, color black) sized iconSize, centered 0.3888
 * - Alpha semantics: alpha = monochrome opacity (0 transparent, 255 opaque), color 0,0,0 ignored at draw (tinted via SRC_IN)
 * - No halftone/dither — continuous alpha
 */
object MonoProcessor {
    private const val ANALYZE_SIZE = 50
    private const val VALID_ALPHA = 40 // o7.d.f
    private const val ALPHA_GATE = 110 // o7.d threshold for histogram & d() gate
    private const val CLAMP_GRAY = 248 // 61504 = 248*248
    private const val CLAMP_SQ = 61504
    private const val GAIN = 210f // d7.f: f10 = 210 / (61504 - minGray*minGray)
    private const val FILL_RATIO_GATE = 0.6f // j() fillRatio inside circle
    const val CROP_SCALE_FORENSIC = 0.3888889f // o7.a.h forensic exact
    const val CROP_SCALE_VISUAL = 0.72f // visual: matches 07 cropped look (user: 07 most similar)
    private const val BUCKETS = 32

    data class MonoResult(val mono: Bitmap, val monoLight: Bitmap? = null)

    // Debug callback for forensic export
    interface DebugSink {
        fun onStage(name: String, bitmap: Bitmap)
    }

    fun process(source: Bitmap, iconSize: Int, enableMonoCheck: Boolean, debug: DebugSink? = null, scale: Float = CROP_SCALE_FORENSIC, binary: Boolean = true): MonoResult? {
        if (source.width != iconSize || source.height != iconSize) {
            val scaled = Bitmap.createScaledBitmap(source, iconSize, iconSize, true)
            val r = processInternal(scaled, iconSize, enableMonoCheck, debug, scale, binary)
            if (scaled !== source) scaled.recycle()
            return r
        }
        return processInternal(source, iconSize, enableMonoCheck, debug, scale, binary)
    }

    private fun processInternal(bmp: Bitmap, iconSize: Int, enableMonoCheck: Boolean, debug: DebugSink?, scale: Float = CROP_SCALE_FORENSIC, binary: Boolean = true): MonoResult? {
        // 01 original
        debug?.onStage("01_original", bmp.copy(Bitmap.Config.ARGB_8888, false))
        // 02 normalized (same as 01 for now, raster is normalized)
        debug?.onStage("02_normalized", bmp.copy(Bitmap.Config.ARGB_8888, false))

        // Step 1: analyze at 50x50 -> dominantGray + fillRatio + histogram (o7.k.G at 50)
        val tmp = Bitmap.createScaledBitmap(bmp, ANALYZE_SIZE, ANALYZE_SIZE, true)
        val analysis = analyzeWithHist(tmp)
        tmp.recycle()
        val dominantGray = analysis.dominant
        val fillRatio = analysis.fillRatio
        if (enableMonoCheck && fillRatio < FILL_RATIO_GATE) return null

        // Step 2: full-size arrays (r() -> k.G at iconSize)
        val n = iconSize
        val pixels = IntArray(n * n)
        val grayArray = IntArray(n * n)
        val alphaArray = Array(n) { IntArray(n) }
        bmp.getPixels(pixels, 0, n, 0, 0, n, n)
        for (i in pixels.indices) {
            val p = pixels[i]
            grayArray[i] = grayLuminance(p)
            alphaArray[i / n][i % n] = Color.alpha(p)
        }
        // 03 grayscale (must be before 04 for correct 01-08 order)
        debug?.let {
            val gBmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            for (i in grayArray.indices) {
                val g = grayArray[i].coerceIn(0, 255)
                val a = alphaArray[i / n][i % n]
                gBmp.setPixel(i % n, i / n, Color.argb(a, g, g, g))
            }
            it.onStage("03_grayscale", gBmp)
        }
        // 04 analysis: histogram (32 bars) + dominant swatch + fill bar
        debug?.let {
            val aBmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            val c = Canvas(aBmp)
            c.drawColor(Color.rgb(18, 18, 18))
            val max = (analysis.hist.maxOrNull() ?: 1).toFloat()
            val barW = 192f / 32f
            for (i in 0 until 32) {
                val h = (analysis.hist[i] / max) * 140f
                val left = i * barW
                val top = 150f - h
                val p = Paint().apply { color = if (i == analysis.bestIdx) Color.RED else Color.rgb(180, 180, 180) }
                c.drawRect(left + 1, top, left + barW - 1, 150f, p)
            }
            c.drawRect(10f, 160f, 40f, 180f, Paint().apply { color = Color.rgb(dominantGray, dominantGray, dominantGray) })
            val barW2 = (120 * fillRatio).toInt()
            c.drawRect(50f, 165f, (50 + barW2).toFloat(), 175f, Paint().apply { color = Color.WHITE })
            c.drawRect((50 + barW2).toFloat(), 165f, 170f, 175f, Paint().apply { color = Color.DKGRAY })
            it.onStage("04_analysis", aBmp)
        }

        // Step 3a: e() -> minGray, mutates grayArray via fCombine
        val minGray = eStage(dominantGray, n, grayArray, alphaArray, enableMonoCheck)
        // Step 3b: d() -> quadratic remap, returns maxAlpha, mutates alphaArray & pixels (a<<24)
        var maxAlpha = dStage(n, grayArray, alphaArray, pixels, minGray)
        if (maxAlpha <= VALID_ALPHA) return null // o7.d.f gate

        // Binarize for Nothing full B&W (no gradient) — user: stage 7 closest, final reintroduces bg if continuous
        if (binary) {
            for (i in pixels.indices) {
                val a = Color.alpha(pixels[i])
                val b = if (a > 127) 255 else 0
                pixels[i] = b shl 24
                val r = i / n; val c = i % n
                alphaArray[r][c] = b
            }
            maxAlpha = if (pixels.any { Color.alpha(it) == 255 }) 255 else 0
        }

        // 05 remapped alpha visualization (binary if binary=true, continuous otherwise)
        debug?.let {
            val aBmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            for (i in pixels.indices) {
                val a = Color.alpha(pixels[i])
                aBmp.setPixel(i % n, i / n, Color.argb(255, a, a, a))
            }
            it.onStage("05_remapped", aBmp)
        }

        // Step 5: s() -> centered SQUARE rect via o7.k.r/w/y/C/A/t (not tight bbox)
        val rect = centeredSquareRect(alphaArray) ?: return null // must be square centered
        if (rect.isEmpty) return null

        // 06 alpha with rect overlay
        debug?.let {
            val cropDbg = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            for (i in pixels.indices) {
                val a = Color.alpha(pixels[i])
                cropDbg.setPixel(i % n, i / n, Color.argb(a, 0, 0, 0))
            }
            val c = Canvas(cropDbg)
            val p = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f }
            c.drawRect(rect, p)
            it.onStage("06_alpha", cropDbg)
        }

        // Copy pixels back into bitmap at same location (s() does setPixels with offset)
        val croppedSrc = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        croppedSrc.eraseColor(Color.TRANSPARENT)
        croppedSrc.setPixels(pixels, 0, n, 0, 0, n, n)

        // 07 cropped source (before final scale)
        debug?.onStage("07_cropped", croppedSrc.copy(Bitmap.Config.ARGB_8888, false))

        // Step 6: o7.a.h -> final scaled mono (will be 08_final tinted in IconRenderer)
        val mono = createAlphaBitmap(croppedSrc, rect, n, scale)
        croppedSrc.recycle()
        // Do NOT emit 08 here; IconRenderer emits 08_final tinted (forensic final Canvas)

        return MonoResult(mono, null) // light variant TODO(RE)
    }

    private data class AnalysisResult(val hist: IntArray, val bestIdx: Int, val dominant: Int, val fillRatio: Float)
    private fun analyzeWithHist(scaled50: Bitmap): AnalysisResult {
        val n = ANALYZE_SIZE
        val pixels = IntArray(n * n)
        scaled50.getPixels(pixels, 0, n, 0, 0, n, n)
        val hist = IntArray(BUCKETS)
        var inside = 0; var filled = 0
        val r = n / 2; val r2 = r * r
        for (i in pixels.indices) {
            val alpha = Color.alpha(pixels[i])
            val gray = grayLuminance(pixels[i])
            if (alpha > ALPHA_GATE) hist[gray / 8]++
            val col = i % n; val row = i / n
            val dx = col - r; val dy = row - r
            if (dx*dx + dy*dy <= r2) { inside++; if (alpha > 0) filled++ }
        }
        var bestIdx = 0; var bestCnt = -1
        for (i in hist.indices) if (hist[i] > bestCnt) { bestCnt = hist[i]; bestIdx = i }
        val dominant = (bestIdx + 1) * 8 - 1
        val fillRatio = if (inside > 0) filled.toFloat() / inside else 0f
        return AnalysisResult(hist, bestIdx, dominant, fillRatio)
    }
    private fun analyze(scaled50: Bitmap): Pair<Int, Float> {
        val r = analyzeWithHist(scaled50)
        return r.dominant to r.fillRatio
    }

    private fun grayLuminance(pixel: Int): Int = (Color.red(pixel)*0.3 + Color.green(pixel)*0.59 + Color.blue(pixel)*0.11).toInt()

    private fun eStage(dominantGray: Int, n: Int, grayArray: IntArray, alphaArray: Array<IntArray>, suitable: Boolean): Int {
        var threshold = 255 - dominantGray
        if (suitable && threshold > 80) threshold = 0
        var min = 255
        for (i in grayArray.indices) {
            val row = i / n; val col = i % n
            val alpha = alphaArray[row][col]
            if (alpha == 0) continue
            val mapped = fCombine(grayArray[i], threshold) // reflect around 255
            min = iPick(alpha, mapped, min)
            grayArray[i] = mapped
        }
        return min
    }
    private fun fCombine(a: Int, b: Int): Int { val s = a+b; return if (s>255) 510-s else s }
    private fun iPick(alpha: Int, mapped: Int, curMin: Int): Int = if (alpha<=ALPHA_GATE || mapped>=curMin) curMin else mapped

    private fun dStage(n: Int, grayArray: IntArray, alphaArray: Array<IntArray>, pixels: IntArray, minGray: Int): Int {
        val f10 = if (CLAMP_SQ - minGray*minGray != 0) GAIN / (CLAMP_SQ - minGray*minGray) else 0f
        var maxA = 0
        for (idx in grayArray.indices) {
            val row = idx / n; val col = idx % n
            var a = alphaArray[row][col]
            val g = grayArray[idx]
            if (a > ALPHA_GATE && minGray < CLAMP_GRAY) {
                a = ((g*g - CLAMP_SQ)*f10 + 255).toInt().coerceIn(0,255)
            }
            if (a > maxA) maxA = a
            alphaArray[row][col] = a
            pixels[idx] = (a shl 24) // black with alpha = mono opacity (continuous 0..255, NOT binary)
        }
        return maxA
    }

    // Exact: centered square via o7.k.w(y,C,A,t) -> min distance to edge where alpha>40, then square pads
    private fun centeredSquareRect(alphaArray: Array<IntArray>): Rect? {
        val n = alphaArray.size
        // Find first opaque from each side where alpha>40
        var left = n; for (c in 0 until n) { for (r in 0 until n) if (alphaArray[r][c] > VALID_ALPHA) { left = c; break }; if (left != n) break }
        var right = -1; for (c in n-1 downTo 0) { for (r in 0 until n) if (alphaArray[r][c] > VALID_ALPHA) { right = c; break }; if (right != -1) break }
        var top = n; for (r in 0 until n) { for (c in 0 until n) if (alphaArray[r][c] > VALID_ALPHA) { top = r; break }; if (top != n) break }
        var bottom = -1; for (r in n-1 downTo 0) { for (c in 0 until n) if (alphaArray[r][c] > VALID_ALPHA) { bottom = r; break }; if (bottom != -1) break }
        if (left > right || top > bottom) return null
        // w() logic: take min distance to border, make centered square
        val distLeft = left
        val distTop = top
        val distRight = n - 1 - right
        val distBottom = n - 1 - bottom
        val pad = minOf(distLeft, distTop, distRight, distBottom)
        // Also apply -2 tweak when not z10? Original z10=false in s() so no tweak. Keep as is.
        return Rect(pad, pad, n - pad, n - pad)
    }

    private fun createAlphaBitmap(src: Bitmap, srcRect: Rect, outSize: Int, scale: Float): Bitmap {
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(out)
        val w = (outSize * scale).toInt(); val h = w
        val dst = RectF((outSize-w)/2f, (outSize-h)/2f, (outSize+w)/2f, (outSize+h)/2f)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, srcRect, dst, paint)
        return out
    }
}
