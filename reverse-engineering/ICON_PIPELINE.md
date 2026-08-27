# ICON_PIPELINE.md — Phase 4: Real Icon Pipeline (End-to-End)

> Replaces placeholder diagram. Every stage cites `jadx` source + dex strings. Unknown steps marked explicitly.

## 1. Pipeline diagram (proven)

```
Android PackageManager
  │  ActivityInfo / ApplicationInfo / ComponentName
  ▼
d7.h (IconPackManager) — feature gate  ──→  h.f16811p.a().y()/v()/u(pkg)  ─┐
  │  checks c7.b, ib.a device model, NOTHING_ICON_FORCE_RENDER flag        │
  ▼                                                                        │
d7.l (ThemedIconProvider, extends IconProvider)                            │
  │  h(): parse R$xml.nt_grayscale_icon_map → Map<String, ThemeData>      │
  │  getThemeDataForPackage(pkg) → ThemeData | null                       │
  ├─► IconProvider.getIcon(PackageItemInfo, ApplicationInfo, density, Supplier)
  │     branch 1: calendar/clock → loadCalendarDrawable / ClockDrawableWrapper
  │     branch 2: supplier icon (LauncherActivityInfo) or loadPackageIcon()
  │     branch 3: if ATLEAST_T && adaptive && themeData != null → wrap with ThemeData.loadPaddedDrawable()
  │     branch 4: legacy icon → return as-is (no monochrome)
  ▼                                                                        │
BaseIconFactory (factory for bitmap raster)  ◄─────────────────────────────┘
  │  ctor: mIconBitmapSize, mMonochromeGenerator = d7.f(iconBitmapSize), mThemeController = ThemeManager.getThemeController() (=MonoIconThemeController)
  │  createIconBitmap(Drawable, float scale, int mode) → Bitmap
  │  Path deps: IconNormalizer.getScale(), ShadowGenerator, ShapeDelegate (mask), GraphicsUtils
  ▼
k7.h (MonoInfo) — per-icon pre-check
  │  new h(context, drawable, bitmap) → monoFlag {null|1|2}, b7.a.a(drawable) fallback
  │  g(iconFactory, bitmapInfo):  e()==true && flag !=null ? iconFactory.b(this) → m7.c
  ▼
d7.f (MonochromeGenerator) — THE core transform
  │  g(Bitmap icon, boolean z10) → m7.c | null
  │    0. size check → o7.a.l(Drawable→Bitmap, size) if needed
  │    1. j(bitmap) → o7.k.G(bitmap, 50, pixels[], grayArray[], alphaArray[][]) → (dominantGray, fillRatio)
  │       z10 && fillRatio <0.6 → null (reject unsuitable icons)
  │    2. r(bitmap, width) → fills pixels/gray/alpha arrays, calls o7.k.G
  │    3. d(width, e(dominantGray, width, z10)) → contrast/threshold remap of alphaArray via f()/i()
  │    4. o7.d.f() gate → null if d() returns illegal
  │    5. s(bitmap, width) → Rect via o7.k.r() bounding box of alpha>0, bitmap.setPixels()
  │    6. o7.a.h(bitmap, Rect, iconBitmapSize, 0.3888f, ALPHA_8) → cropped ALPHA_8 Bitmap
  │    7. optional light variant: o7.k.f(bitmapH) → Bitmap | null, stored as second slot in m7.c
  │  returns m7.c(mono=ALPHA_8, monoLight, normal/light holder)
  ▼
BitmapInfo.setThemedBitmap(MonoThemedBitmap)  (extends ThemedBitmap)
  │  new MonoThemedBitmap(mono, monoLight, monoFlag|4?, whiteShadowLayer=getWhiteShadowLayer(), colorProvider)
  ▼
Cache layer — cache.BaseIconCache / IconCache
  │  IconDB: SQLite "favorites" ALTER TABLE ... iconPackage, COLUMN_MONO_FLAG, COLUMN_MONO_ICON
  │  CacheEntry: BitmapInfo + label + contentDescription
  │  Lookup: getIcon(ComponentName, CacheLookupFlag) → memCache → db → IconProvider + BaseIconFactory → put
  │  ThemedBitmap.serialize() → ByteBuffer (ALPHA_8 bytes) persisted in DB, decode() via MonoIconThemeController.decode()
  ▼
Launcher UI — BubbleTextView / FolderIcon / BigFolder
  │  BubbleTextView.setIcon(FastBitmapDrawable)
  │  FastBitmapDrawable.isThemed() ? ThemedIconDrawable : BitmapInfo.newBitmapDrawable()
  ▼
ThemedIconDrawable (FastBitmapDrawable) — final pixels
  │  fields: monoIcon (ALPHA_8), bgBitmap (whiteShadowLayer), colorFg/Bg (int[] from ThemedIconDrawable.Companion.getColors(str, context) via c7.b)
  │  drawInternal(Canvas, Rect):
  │    canvas.drawBitmap(bgBitmap, null, bounds, Paint(SRC_IN, colorBg))
  │    canvas.drawBitmap(monoIcon, null, bounds, Paint(SRC_IN, colorFg))
  ▼
Canvas / GPU (hardware accel)
  final composited icon in workspace / all-apps / folder / drawer
```

## 2. Stages detail

### 2.1 Acquisition — `IconProvider / d7.l`

- **Entry:** `IconCache.getTitleAndIcon()` → `IconProvider.getIcon(PackageItemInfo, ApplicationInfo, density, Supplier)` (`jadx:IconProvider.java:180-260`).
- **Special icons:** `R$string.calendar_component_name` / `clock_component_name` → `loadCalendarDrawable()` (bundle `dynamic_icons` array, `TypedArray.getResourceId(day)`) or `ClockDrawableWrapper.forPackage()`.
- **Adaptive branch:** `if (ATLEAST_T && drawable instanceof AdaptiveIconDrawable && themeData != null)` → returns `new AdaptiveIconDrawable(bg, fg, themeData.loadPaddedDrawable())` where `ThemeData.loadPaddedDrawable()` wraps the monochrome drawable with `InsetDrawable(0.2f + extraInsetFraction)` padding (`jadx:IconProvider.java:70-80`). This is how Nothing injects monochrome into the *platform* `AdaptiveIconDrawable` contract.
- **Grayscale map:** `d7.l:h()` lazily parses `R$xml.nt_grayscale_icon_map` (`xml` with `<icon package="..." drawable="..."/>`) into `ArrayMap<String, ThemeData>` (`jadx:d7/l.java:60-100`). Guarded by `FeatureFlags.USE_LOCAL_ICON_OVERRIDES` + `h.f16811p.a().y()`.

### 2.2 Normalization — `BaseIconFactory` + `IconNormalizer`

- `BaseIconFactory.normalizeNonAdaptiveIcon(Drawable, float[] outScale, IconNormalizer)` (`jadx:BaseIconFactory.java:240-260`): if not adaptive, wraps in `AdaptiveIconDrawable(ColorDrawable(-1), EmptyWrapper(drawable))`, calls `IconNormalizer.getScale()` to compute legacy scale, rescales via `createScaledDrawable(LEGACY_ICON_SCALE)`.
- `IconNormalizer.getScale(Drawable, Path mask, boolean[] outClip)` measures drawable bounds vs mask circle; returns scale that fits.
- `createNormalizedBitmap()` ensures output `Bitmap i11 xi11` sized.
- **Constants:** `LEGACY_ICON_SCALE = 1/(2*extraInsetFraction+1) *0.7`, `mIconBitmapSize` from `InvariantDeviceProfile`.

### 2.3 Mono generation — `d7.f + o7.*` (the most Nothing-specific stage)

Full trace `d7.f:g(Bitmap, boolean) → m7.c` (`jadx:d7/f.java:120-280`):

| Step | Code | Evidence |
|---|---|---|
| Size gate | `if (icon.getWidth()!=mIconBitmapSize ...) icon=o7.a.l(new BitmapDrawable(icon), mIconBitmapSize, ...)` | `jadx:d7/f.java:220` |
| Pixel prep `r()` | allocates `pixels[]`, `grayArray[]`, `alphaArray[][]` size `i10*i10`, calls `o7.k.G(bitmap, 50, pixels, grayArray, alphaArray)` | `jadx:d7/f.java:180-210` |
| `o7.k.G` | loops pixels, `Color.alpha`, `d.g(pixel)` gray, fills `grayArray`, counts fillRatio inside inscribed circle, returns `(dominantGray, fillRatio)` via `k7.c` | `jadx:o7/k.java: G method (~line 200-300)` |
| Threshold reject | `if (z10 && fillRatio<0.6) return null` | `jadx:d7/f.java:130` |
| Gray/alpha transform | `d(width, e(dominantGray, width, z10))` where `e()` does `f(iArr[i], 255-gray)` remap + `i(alpha, mappedGray, min)` ; `d()` does `alphaArray[i][j] = clamp(((gray*gray - 61504)*210/(61504 - gray^2))+255)` | `jadx:d7/f.java:40-110` — exact formula `f10 = 210/(61504 - i11*i11)` |
| Validity gate | `!o7.d.f(dResult)` → null | `jadx:d7/f.java:135` |
| Bounding box | `s(bitmap, width) → Rect via o7.k.r(alphaArray, false)` computes min/max rows/cols where `d.f(alpha)` true, then `bitmap.setPixels(pixels, ...)` | `jadx:d7/f.java:150-170`, `o7/k.w/y/t/A/C` helpers |
| Crop to ALPHA_8 | `o7.a.h(bitmap, Rect, mIconBitmapSize, 0.3888f, ALPHA_8)` | `jadx:o7/a.java: h method` — creates `Bitmap ALPHA_8 mIconBitmapSize` with `RectF` centered `0.3888f` factor |
| Light variant | `z10 ? o7.k.f(monoBitmap) : null` → stored | `jadx:d7/f.java:165` |

Output `m7.c(mono ALPHA_8, monoLight | null)` is handed to `k7.h.g()` which builds `MonoThemedBitmap`.

**Dithering note:** `triangleNoise(p * in_pixelDensity)/255` string appears in `classes4.dex` inside a shader-like fragment (likely `*.fs`/`*.glsl` embedded as string for `RenderEffect` or `ColorFilter` dithering). Not yet mapped to a Java class; marked **UNKNOWN** whether applied in `o7.k.g()`/`o7.a` path or in GPU `ThemedIconDrawable` post. See `RENDERING_ALGORITHM.md`.

### 2.4 Themed drawable construction — `MonoThemedBitmap → ThemedIconDrawable`

- `k7.h:g(BaseIconFactory, BitmapInfo)` (`jadx:k7/h.java:60-90`): if `e()` (flag 1 or 2), calls `iconFactory.b(this)` (`d7.f` via `BaseIconFactory.mMonochromeGenerator`) → `m7.c`. If `monoLight != null` then `flag|=4`. Builds `MonoThemedBitmap(mono, monoLight, flag, whiteShadowLayer, colorProvider)`, attaches via `bitmapInfo.setThemedBitmap(...)`.

- `MonoThemedBitmap.newDrawable(BitmapInfo, Context, String)` (`jadx:mono/MonoThemedBitmap.java:40-60`): fetches `colors = ThemedIconDrawable.Companion.getColors(str, context)` (`c7.b` → `[bg, fg]` where `f16798l.a/b` pick `mono_nothing_background_color` / `mono_nothing_foreground_color` vs `mono_color_*` depending on `h.f16811p.a().v()`), creates `ThemedConstantState(info, mono, monoLight, whiteShadowLayer, bg, fg)` → `ThemedIconDrawable`.

- `ThemedIconDrawable.drawInternal` (`jadx:icons/mono/ThemedIconDrawable.java:130-145`): draws `whiteShadowLayer` tinted `colorBg` (SRC_IN), then `monoIcon` tinted `colorFg` (SRC_IN). `m7.c.f20293c.a(mono, monoLight, colorFg)` picks `monoLight` when `o7.d.e(colorFg)` (light theme detection) else `mono`.

### 2.5 Caching — `BaseIconCache`/`IconCache`/`LauncherIcons.IconPool`

- **Memory:** `SparseArray<CacheEntry>` + `LauncherIcons.IconPool` (Lru).
- **Disk:** `BaseIconCache.IconDB` SQLite `favorites` table columns `COLUMN_MONO_FLAG`, `COLUMN_MONO_ICON` (ALPHA_8 bytes), `iconPackage` (dex strings: `ALTER TABLE favorites DROP COLUMN iconPackage`, `ATTR_ICON_PACKAGE`, `INDEX_MONO_FLAG`).
- **Serialization:** `MonoThemedBitmap.serialize() → byte[] monoAlphaBytes` via `ByteBuffer`, `MonoIconThemeController.decode()` reconstructs `ALPHA_8 Bitmap`.
- **Invalidation:** `IconPackManager` `H(str)` / `J(system, "SYSTEM_ICONS")` + `NOTHING_ICON_FORCE_RENDER_CHANGED` broadcast trigger `IconCacheUpdateHandler.SerializedIconUpdateTask`.

### 2.6 Layout / final render — `BubbleTextView`, `FolderIcon`, `CellLayout`

- `BubbleTextView.applyIcon()` calls `appInfo.getIcon()` → `BitmapInfo.newBitmapDrawable()` (which returns `ThemedIconDrawable` when themed) → `ImageView`/custom `FastBitmapDrawable` in cell. `IconThemeController.createThemedAdaptiveIcon()` path creates platform `AdaptiveIconDrawable(ColorDrawable(bg), monochrome)` for system consumption (widget previews, notification dots).

## 3. Threading

- `d7.f.g()` and `k7.h.g()` run off-UI: `IconCache.getTitleAndIcon()` is called on `LauncherModel` worker thread (`Executors.MODEL_EXECUTOR`), with `CancellableTask` cancellation and `LooperExecutor` dispatch to UI for `BubbleTextView` bind.
- `d7.h` uses Kotlin `Flow x2.b + e1.b` on `Dispatchers.IO` for pack install detection.

## 4. Unknowns

| Step | Status | Missing |
|---|---|---|
| Exact luminance formula `d.g(pixel)` | **UNKNOWN** — dex `d.g()` not yet decompiled (`o7/d.java` stub). Likely `0.299R+0.587G+0.114B` but `r()` grayArray length check suggests possible `max(R,G,B)` or `HSP`. |
| `triangleNoise` dithering attachment point | **UNKNOWN** — string in `classes4.dex` but no Java caller yet; may be Renderscript/AGSL shader loaded via `RenderEffect`. |
| Dot-matrix pattern (if any) vs simple threshold | **UNKNOWN** — no `dot.matrix` string; `DotRenderer` is notification dots only. Nothing marketing suggests dot-matrix but code shows threshold via `e()/d()`, not ordered dithering. |

## 5. Confidence per stage

| Stage | Status | Evidence |
|---|---|---|
| Acquisition (`IconProvider`/`d7.l`) | **CONFIRMED** | `jadx:IconProvider.java + d7/l.java` + manifest |
| Normalization (`BaseIconFactory`/`IconNormalizer`) | **CONFIRMED** | `jadx:BaseIconFactory.java` |
| Mono generation (`d7.f` + `o7.k/a`) | **STRONGLY SUPPORTED** | `jadx:d7/f.java` complete trace; `o7.k/a` partial but formula captured |
| Drawable construction (`MonoThemedBitmap→ThemedIconDrawable`) | **CONFIRMED** | `jadx:mono/*.java` + `k7/h.java` |
| Caching (`BaseIconCache`/`IconDB`) | **STRONGLY SUPPORTED** | dex strings + `jadx:IconCache.java` |
| Layout/render (`BubbleTextView` → `Canvas`) | **LIKELY** | Standard Launcher3; Nothing adds only `ThemedIconDrawable.drawInternal` |
| Dithering/dot-matrix | **UNKNOWN** | No shader source yet |

## 6. Diagram replacing placeholder (copy-paste ready)

Use the ASCII diagram in §1 as the authoritative flow. For docs, prefer the short form:

```
PackageInfo → IconProvider(d7.l, nt_grayscale_icon_map) → BaseIconFactory(normalize) → d7.f mono (o7.k/a, m7.c) → MonoThemedBitmap → ThemedIconDrawable(tint bg/fg) → IconCache(DB + ThemedBitmap serialize) → BubbleTextView/CellLayout → Canvas
```

Pack `com.nothing.icon` only gates the flow via `d7.h` presence check, not the pixels.
