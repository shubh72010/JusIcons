<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="128" alt="JusIcons" />
  <h1>JusIcons</h1>
  <p><b>Pixel-perfect Nothing OS icon renderer — forensic port, not a clone.</b></p>
</div>

<div align="center">

[![Release](https://img.shields.io/github/v/release/shubh72010/JusIcons?label=release)](https://github.com/shubh72010/JusIcons/releases/tag/v0.1.0-alpha)
[![CI](https://github.com/shubh72010/JusIcons/actions/workflows/ci.yml/badge.svg)](https://github.com/shubh72010/JusIcons/actions/workflows/ci.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
[![Min SDK 24](https://img.shields.io/badge/minSdk-24-brightgreen)](#)
[![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2.10-purple)](./gradle/libs.versions.toml)
[![AGP 9.3](https://img.shields.io/badge/AGP-9.3.2-blue)](#)

</div>

> **Nothing's mono isn't a filter. It's a pipeline.** JusIcons traces `Drawable → IconNormalizer → d7.f (210/(61504−minGray²)) → centered square → ALPHA_8 → white-on-black circle` and re-implements it as a tiny, dependency-free Android library.

<div align="center">
<h3>Quick Install</h3>

```bash
adb install https://github.com/shubh72010/JusIcons/releases/download/v0.1.0-alpha/app-debug.apk
```

**Or build from source:**

```bash
git clone git@github.com:shubh72010/JusIcons.git && cd JusIcons
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

</div>

---

## TL;DR

**The Problem:** Nothing OS icons look unmistakably glyph-only, high-contrast, and oddly *small* — but every “Nothing icon pack” online is either a static dump of 500 PNGs or a naive `grayscale()` that smears details and leaks the adaptive white background as a square.

**The Solution:** JusIcons is a forensic port of Nothing Launcher `4.0.20` + Icon Pack `1.0.2`. We extracted the APKM, decompiled `d7.f`/`o7.k`/`k7.c`, and rebuilt the **generic mono pipeline** as an independent Kotlin engine that takes any `Drawable` and returns a `ThemedIconDrawable` (white glyph on `#121212` circle, foreground-only adaptive).

### Why JusIcons?

| Feature | What it does |
|---------|--------------|
| **Forensic pipeline** | `0.3R+0.59G+0.11B` gray, `>110` hist 32 buckets, `minGray` via `510-(a+b)`, quadratic `210/(61504−minGray²)`, `>40` gate, centered-square `pad=min(left,top,192-right,192-bottom)`, `0.3888` (forensic) / `0.72` (visual) |
| **No curated dump** | `nt_grayscale_icon_map` and `appfilter.xml` are empty in this build — every package hits the *same* generic path, proven in `ICON_PACK_RELATIONSHIP.md` |
| **Adaptive-correct** | `AdaptiveIconDrawable` → foreground-only on transparent, so YouTube red bg doesn't become a white square |
| **B&W, not gradient** | Binary `>127` option gives Nothing's full black/white, no gray wash |
| **8-stage trace** | `01_original → 08_final` PNGs in `cache/debug-output/` + tap-to-trace UI |
| **Zero Nothing deps** | Only `Canvas`/`Bitmap`/`BlendMode.SRC_IN`, works on AOSP 24+ (AGP 9.3, Kotlin 2.2) |

---

## Quick Example

```kotlin
val renderer = JusIconsRenderer(context) // in app/src/main/java/.../engine/
val opts = RenderOptions(
    bgColor = 0xFF121212.toInt(), // Image 2's dark circle
    fgColor = Color.WHITE,
    enableMonoCheck = false,      // don't fallback to square
    forensicScale = false,        // 0.72 visual (07-like), true = 0.3888 forensic
    showBackground = true,        // black circle vs glyph-only
    binary = false                // false = continuous detail, true = hard B&W
)
val themed: Drawable = renderer.render(originalDrawable, 192, opts)
imageView.setImageDrawable(themed)

// Debug the pipeline for one icon:
renderer.renderWithDebug(drawable, 192, opts, object : MonoProcessor.DebugSink {
    override fun onStage(name: String, bitmap: Bitmap) {
        File(cacheDir, "debug-output/$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
})
// → 01_original, 02_normalized, 03_grayscale, 04_analysis (32-bar hist), 05_remapped, 06_alpha, 07_cropped, 08_final
```

---

## Design Philosophy

1. **Evidence before synthesis.** Every constant is cited to `jadx:d7/f.java:95` etc. `RENDERING_ALGORITHM.md` is the spec.
2. **No proprietary transplant.** Clean-room Kotlin, no `com.nothing.*`/`com.android.launcher3.*` imports in `engine/`.
3. **Boring over clever.** `Canvas` + `Bitmap` + `SRC_IN` beats custom shaders; Bayer was removed once we proved `triangleNoise` is wallpaper turbulence, not icon halftone.
4. **Fewest files wins.** Engine is 5 files; `app` folding avoids AGP plugin dupe.
5. **Debug is a feature.** Tap any row → 8-stage carousel; `adb pull` the PNGs.

---

## How JusIcons Compares

| Feature | JusIcons | Static PNG packs | Naive grayscale | Nothing Launcher (stock) |
|---------|----------|------------------|-----------------|--------------------------|
| Per-package curated | ❌ (map empty, proven) | ✅ hand-drawn 500 | ❌ | ⚠️ generic `d7.f` for unmapped (this build) |
| Generic fidelity | ✅ `210`/`61504`/`0.3888` | ❌ | ❌ `0.299/0.587/0.114` once | ✅ native |
| Adaptive bg leak | ✅ foreground-only | ⚠️ often square | ❌ square | ✅ |
| Gradient vs B&W | ✅ toggle `binary` | ✅ B&W | ❌ gray wash | ✅ B&W |
| Min SDK | 24 | 21 | 21 | 36 (Nothing OS) |
| Size | ~12 MB apk | 20-80 MB | ~5 MB | 57 MB base |

**Use JusIcons when:** you want Nothing's *generated* look on any AOSP/Lawnchair/Nova without flashing Nothing OS.

**Not ideal when:** you need hand-curated YouTube/Chromium glyphs (those would require a curated `appfilter` — not in this APKM).

---

## Installation

### From Release (recommended)

```bash
# via adb
adb install https://github.com/shubh72010/JusIcons/releases/download/v0.1.0-alpha/app-debug.apk
# or download and tap the APK on device
```

### From Source

```bash
git clone git@github.com:shubh72010/JusIcons.git
cd JusIcons
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17+, Android SDK 37, `sdk.dir` in `local.properties`.

---

## Quick Start

1. Open JusIcons → `ORIGINAL | JUSICONS` list (40 launcher apps, sorted)
2. Tap any row → forensic strip on top (`01`→`08`) + `cache/debug-output/*.png`
3. Toggle `Forensic 0.3888 / Visual 0.72` and `BG` to match Image 1 (tiny square) vs Image 2 (large circle)
4. Pull traces:
   ```bash
   adb exec-out run-as com.jusdots.jusicons tar -c cache/debug-output | tar -x -C debug-output/
   ```

---

## Configuration

```kotlin
data class RenderOptions(
    @ColorInt val bgColor: Int = 0xFF121212.toInt(),
    @ColorInt val fgColor: Int = Color.WHITE,
    val enableMonoCheck: Boolean = false, // true = fillRatio<0.6 → fallback square
    val forensicScale: Boolean = false,   // true = 0.3888 exact, false = 0.72 visual
    val showBackground: Boolean = true,   // black circle vs transparent glyph-only
    val binary: Boolean = false,          // true = hard 0/255, false = continuous preserves inner lines
)
```

Themed colors come from `MonoThemedBitmap` → `whiteShadowLayer` + `SRC_IN`; for wallpaper-aware tinting, pass wallpaper-derived `bg/fg`.

---

## Architecture

```
PackageManager (ActivityInfo/ApplicationInfo)
        ↓
d7.l (ThemedIconProvider, nt_grayscale_icon_map empty → no ThemeData)
        ↓
IconNormalizer.toBitmap(192, adaptive? foreground-only on transparent)
        ↓
MonoProcessor.process (d7.f):
  50×50 analyze → dominantGray + fillRatio (k7.c 32 buckets, >110, 0.6)
  192×192 gray (0.3/0.59/0.11) + alpha
  e() fCombine 510-(a+b) + iPick (>110) → minGray
  d() 210/(61504-minGray²) → a=(g²-61504)*f+255 (>110 && <248) → maxAlpha>40?
  centered square pad=min(edges) → setPixels
  o7.a.h 192×192 ARGB 0.3888 centered
  binarize? (>127)
        ↓
ThemedIconDrawable (white glyph SRC_IN fg, black circle bg SRC_IN bg)
        ↓
IconCache (ALPHA_8 serialize) → BubbleTextView/CellLayout → Canvas
```

See `reverse-engineering/` for the full trace: `RE.md`, `APK_LAYOUT.md`, `CLASS_MAP.md`, `ICON_PIPELINE.md`, `ICON_PACK_RELATIONSHIP.md`, `NOTHING_DEPENDENCIES.md`, `RENDERING_ALGORITHM.md`.

---

## Troubleshooting

### `apktool`/`jadx` not found
```bash
./gradlew :app:assembleDebug # does not need them
# For RE only:
curl -L https://github.com/skylot/jadx/releases/download/v1.5.2/jadx-1.5.2.zip -o /tmp/jadx.zip
curl -L https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.12.0.jar -o /tmp/apktool.jar
```

### Icons still look like black squares
`enableMonoCheck=true` triggers fallback for sparse icons → solid square. The app defaults to `false`; if you changed `RenderOptions`, set `enableMonoCheck=false`.

### White square behind adaptive icons
Old `IconNormalizer` drew adaptive bg. Current is foreground-only — rebuild with `./gradlew :app:assembleDebug`.

### `04_analysis` blank
Fixed in `v0.1.0-alpha`: now 32-bar histogram + dominant swatch. Pull new APK.

---

## Limitations

- **No curated icons.** `nt_grayscale_icon_map`/`appfilter` empty in `4.0.20/1.0.2`; hand-drawn Nothing icons would need a separate `res/xml` map (not shipped).
- **Light variant `o7.k.f` not yet** — dark mono only; light theme would reuse same.
- **`whiteShadowLayer` blur approximated** — solid circle + `BlurMaskFilter 0.02*size` vs Nothing's RenderEffect.
- **No runtime wallpaper tint** — `bg/fg` are fixed `#121212`/white; wire to `WallpaperColors` for stock feel.

---

## FAQ

**Why does `07_cropped` look most similar?**
`08_final` shrinks the square crop to `38.8%` centered — forensic exact but tiny. `07` is the pre-shrink square, so larger. Toggle `Forensic off` for `0.72` visual.

**Why no gradient?**
Nothing's `d()` is continuous `0..255`, but the launcher displays it as B&W (`binary` hard threshold mimics that). Set `binary=false` to keep inner lines.

**Does it need Nothing OS?**
No — `NOTHING_DEPENDENCIES.md` shows 14 Nothing gates (`ib.a NTF_*`, `h.f16811p`, `OS.V2_0`) are all stubbed; engine runs on AOSP 24+.

**How to contribute a new icon mapping?**
Add `<icon package="com.example" drawable="@drawable/custom_mono" />` to `res/xml/nt_grayscale_icon_map.xml` and implement the `ThemeData` branch — no engine change.

---

## Contributing

PRs welcome — please run `./gradlew :app:assembleDebug :app:testDebugUnitTest` and include a `cache/debug-output` screenshot for visual changes.

## License

GPL-3.0 — see [LICENSE](./LICENSE). Nothing Launcher/Icon Pack remain property of Nothing Technology Limited; this is an independent re-implementation for interoperability.

---

<div align="center">
<sub>Forensic docs in <code>reverse-engineering/</code> — every claim cites <code>jadx:file:line</code>.</sub>
</div>
