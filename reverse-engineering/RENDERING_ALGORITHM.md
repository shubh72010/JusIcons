# RENDERING_ALGORITHM.md — Phase 8: Rendering Algorithm (Evidence-Based)

> Do not invent formulas. Every pseudocode line cites `jadx` + dex strings. Unknowns marked.

## 1. Inputs / outputs

```
input  Drawable (from PackageManager → IconProvider) , int iconBitmapSize (e.g., InvariantDeviceProfile.iconBitmapSize ≈ 192–240), boolean suitableCheck (z10)
  ↓
output m7.c { Bitmap mono ALPHA_8 (iconBitmapSize×iconBitmapSize, centered, scale 0.3888), Bitmap | null monoLight }
  ↓ stored as MonoThemedBitmap → serialized ALPHA_8 bytes → ThemedIconDrawable draws tinted via BlendMode SRC_IN
```

## 2. Full pseudocode (clean-room from `d7/f.java`, `o7/k.java`, `o7/a.java`, `o7/d.java`, `k7/c.java`)

```kotlin
// Ponytail-style: shortest correct representation. This is the discovered behavior, re-phrased.

fun monochromeGenerator(icon: Bitmap, suitableCheck: Boolean, iconSize: Int): Mono? {
    // 0. size normalization — jadx:d7/f.java:220-230
    var bmp = icon
    if (bmp.width != iconSize || bmp.height != iconSize) {
        // o7.a.l packs arbitrary Drawable into iconSize bitmap
        bmp = rasterDrawableToBitmap(bmp.asDrawable(), iconSize) // o7.a.l
    }
    // pooled + try/finally recycle — e7.b.n cache
    val cacheBmp = acquireTempBitmap(50)
    try {
        // 1. pixel analysis — jadx:d7/f.java: j() → o7.k.G
        //    o7.k.G fills: pixels[], grayArray[], alphaArray[][], returns (dominantGray, fillRatio)
        val (dominantGray, fillRatio) = analyze(bmp, size = 50, pixels, grayArray, alphaArray)
        //    analyze: for each pixel: alpha = Color.alpha(p), gray = o7.d.g(p) when alpha>0,
        //    histogram k7.c.a(alpha, gray) bucketed by gray/8 when alpha>110, dominant = argmax bucket*8-1
        //    fillRatio = inside inscribed circle (r=25) count of alpha>0 / total inside
        if (suitableCheck && fillRatio < 0.6f) return null  // jadx:d7/f.java:130

        // 2. allocate/resize working arrays to iconSize — jadx:d7/f.java: r()
        prepareArrays(bmp, iconSize) // pixels, grayArray, alphaArray iconSize×iconSize via o7.k.G full-size pass

        // 3. threshold/contrast stage — jadx:d7/f.java: e() then d()
        // 3a. e(dominantGray, size, suitable) — maps each gray via f()
        //     f(a,b) = if (a+b>255) 510-(a+b) else a+b  // jadx:d7/f.java:178 — reflect around 255
        //     i(alpha, mappedGray, minGray) = if (alpha<=110 || mappedGray>=minGray) minGray else mappedGray
        //     tracks running min via i(); returns minGray
        //     also zeroes out transparent: if suitable && 255-dominantGray >80 then threshold=0
        val minGray = eStage(dominantGray, iconSize, suitableCheck)

        // 3b. d(size, minGray) — per-pixel alpha remap with quadratic curve
        //     f10 = 210f / (61504 - minGray*minGray)  // 61504 = 248^2, 210 is magic contrast gain — jadx:d7/f.java:95
        //     for each cell:
        //        a = alphaArray[row][col]; g = grayArray[idx]
        //        if (a>110 && minGray<248) a = clamp( (int)((g*g - 61504)*f10 + 255), 0..255 )
        //        track maxAlpha across all cells, write back alphaArray[row][col]=a, pixels[idx]=a<<24
        //     returns maxAlpha — jadx:d7/f.java:90-135
        val maxAlpha = dStage(iconSize, minGray)

        // 4. validity gate — jadx:d7/f.java: o7.d.f(maxAlpha) >40 ?
        if (!isValidAlpha(maxAlpha)) return null // o7.d.f: maxAlpha>40

        // 5. bounding box crop — jadx:d7/f.java: s() → o7.k.r() → o7.k.w/y/t/A/C
        //    find minRow/maxRow/minCol/maxCol where alphaArray has d.f(alpha) (alpha>40)
        //    Rect rect = (left, top, right, bottom) with padding tweak (z10 ? ±2)
        //    copy pixels rect into bmp via setPixels
        val rect = boundingRect(bmp, iconSize) // via o7.k.r → w/y/t/A/C, q(), v()
        if (rect.isEmpty) return null
        // 6. centered ALPHA_8 create — jadx:d7/f.java: o7.a.h(bmp, rect, iconSize, 0.3888f, ALPHA_8)
        //    creates iconSize×iconSize ALPHA_8, draws src rect centered with scale 0.3888*iconSize
        //    mapping: RectF((iconSize-width)/2 ... ) via Canvas.drawBitmap(src, srcRect, dstRect, Paint(FILTER))
        val mono = createAlphaBitmap(bmp, rect, iconSize, scale = 0.3888889f, config = ALPHA_8)

        // 7. light variant (optional) — jadx:d7/f.java: o7.k.f(mono)
        //    o7.k.f computes highlight bitmap for light themes: similar analysis but for light bg
        val monoLight = if (suitableCheck) lightVariant(mono) else null // may be null

        return Mono(mono, monoLight)
    } finally {
        cacheBmp.recycle(); bmp.recycleIfNeeded(pool)
    }
}

// Helpers captured:
fun luminanceG(pixel: Int): Int = // o7.d.g
    (Color.red(pixel)*0.3 + Color.green(pixel)*0.59 + Color.blue(pixel)*0.11).toInt()
    // NOTE: this is the "gray" used for generation (candidate). Separate from bg/fg tint luminance e().

fun isLightColor(color: Int): Boolean = // o7.d.e
    Color.red(c)*0.2126 + Color.green(c)*0.7152 + Color.blue(c)*0.0722 > 128

fun isValidAlpha(v: Int): Boolean = v > 40 // o7.d.f

fun dominantHistogramAdd(alpha:Int, gray:Int) { if(alpha>110) hist[gray/8]++ } // k7.c.a
fun dominantHistogramResult(): Int = (argmax(hist) +1)*8 -1 // k7.c.b

fun pickMono(mono: Bitmap, monoLight: Bitmap?, fgColor:Int): Bitmap =
    if (isLightColor(fgColor) || monoLight==null) mono else monoLight // m7.c.f20293c.a
```

## 3. Coloring / drawing (post-generation)

Mono bitmaps are **alpha-only** (`ALPHA_8`). Coloring happens at draw time, not generation:

- `MonoThemedBitmap.newDrawable()` → `ThemedIconDrawable` holds `colors = c7.b.getColors(str, context)` → `[bg, fg]` where `d7.f.f16798l.a/b` pick `mono_nothing_*` vs `mono_color_*` based on `h.f16811p.a().v()` (jadx:d7/f.java:30-45).
- `ThemedIconDrawable.drawInternal()` draws `whiteShadowLayer` (from `BaseIconFactory.getWhiteShadowLayer()` / `ShadowGenerator`) tinted `colorBg` SRC_IN, then `monoIcon` tinted `colorFg` SRC_IN (jadx:mono/ThemedIconDrawable.java:130-145).
- `isLightColor(fgColor)` at draw time picks `monoLight` variant if present (otherwise `mono`).

## 4. What is *not* in the pipeline (and where uncertainty remains)

| Claim | Status | Evidence |
|---|---|---|
| Ordered dithering / dot-matrix halftone | **NOT FOUND** — no `Bayer`, `dotMatrix` strings; `DotRenderer` is notification dots only | `strings` grep across all DEX. Marketing dot aesthetic is reproduced via **threshold + quadratic remap** + **ALPHA_8 centered crop**, not halftone. |
| `triangleNoise` dithering | **STRING EXISTS** in `classes4.dex` (`triangleNoise(p * in_pixelDensity)/255.`, `color += dither.rrr`, `colorLayer += dither.rrr`, `Skip dithering`) but **no Java caller** yet | Likely **AGSL/RenderEffect shader** for wallpaper/theme preview (maybe `ThemeManager` preview renderer), not icon mono path. Mark UNKNOWN for icon pipeline. |
| Grayscale formula `d.g` | **CONFIRMED** `0.3R+0.59G+0.11B` for generation gray | `jadx:o7/d.java:g()`. Distinct from `o7.d.e` light check `0.2126R+0.7152G+0.0722B` (sRGB luminance) used only for fg pick. |
| Blur/sharpen/morphology | **NONE** — pipeline is scale → threshold → crop → α-bitmap | No `BlurMaskFilter`, `Convolve`, `erode/dilate` strings. |

## 5. Constants to port

| Name | Value | Source |
|---|---|---|
| `iconBitmapSize` | 50 for analysis, `mIconBitmapSize` (device) for output (IconSize ≈ device dpi-scaled) | `d7.f.:50` hardcoded, `BaseIconFactory ctor` |
| `fillRatio threshold` | `0.6` when `suitableCheck` true | `jadx:d7/f.java:130` |
| `d() alpha gate` | `>110` enters remap, `>40` validity | `jadx:d7/f.java:110`, `o7.d.f` |
| `e() threshold clamp` | `248` (61504=248²) | `jadx:d7/f.java:95` |
| `contrast gain` | `210f / (61504 - minGray²)` | `jadx:d7/f.java:95` |
| `hist buckets` | 32, bucket `gray/8`, dominant `(maxIdx+1)*8-1` | `jadx:k7/c.java` |
| `crop scale` | `0.3888889f` (≈7/18) center scale inside ALPHA_8 canvas | `jadx:d7/f.java:230` |
| `LEGACY_ICON_SCALE` | `1/(2*extraInsetFraction+1)*0.7` | `jadx:BaseIconFactory.java` |
| `light threshold` | `isLightColor >128` via `0.2126R+0.7152G+0.0722B` | `jadx:o7/d.java:e` |
| `valid maxAlpha` | `>40` | `jadx:o7/d.java:f` |

## 6. Confidence

| Part | Level | Basis |
|---|---|---|
| Overall pipeline stages (raster → analyze → e/d → bbox → ALPHA_8) | **CONFIRMED** | Full `jadx:d7/f.java` trace, each call resolved. |
| Formulas (`f()`, `d()` quadratic, `k7.c` histogram, `o7.d.g` gray) | **STRONGLY SUPPORTED** | Decompiled source; constants match across `d7/f` and `o7/d`. |
| Light variant `o7.k.f` | **LIKELY** | Signature seen, body not fully dumped; call site confirms optional light path. |
| `triangleNoise` dithering as part of mono | **UNKNOWN** — string exists but unlinked to mono Java path |
| Dot-matrix pattern | **UNKNOWN** (likely not present) | Absence of evidence; threshold path is the observed method. |
