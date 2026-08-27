# CLASS_MAP.md — Phase 3: Package / Class Map

> Evidence from `jadx -d /tmp/jadx_launcher` (47 errors, mostly Kotlin synthetic), `apktool` smali, `aapt2`/`apkanalyzer`, `dexdump`, `strings`. Cited as `jadx:<path>:<line>` or `dex:strings` where relevant. Keep this concise — full decompilation not dumped.

## 1. Launcher overview (base.apk 59 MB, 5 DEX)

### 1.1 `com.android.launcher3.*` — Launcher3 fork core (AOSP upstream)

| Package | Role | Key classes/methods |
|---|---|---|
| `com.android.launcher3.icons` | Icon factory/cache/normalization | `BaseIconFactory` (factory), `IconNormalizer` (scale), `IconCache`/`cache.BaseIconCache` (caching), `LauncherIcons` (pool), `IconProvider` / `d7.l` (grayscale map), `MonoThemedBitmap`/`ThemedIconDrawable` (mono) |
| `com.android.launcher3.graphics` | Preview & theme orchestration | `ThemeManager` (singleton, holds `MonoIconThemeController`), `GridCustomizationsProxy`, `LauncherPreviewRenderer` |
| `com.android.launcher3.model` | Model/persistence | `ModelInitializer`, `NothingIconApplyStatusUpdateTask` (Nothing-specific), `AppInfo`, `WorkspaceItemInfo` |

### 1.2 `com.android.launcher3.icons` — icon pipeline detail

| Class | File | Input → Processing → Output |
|---|---|---|
| `BaseIconFactory` | `jadx:com/android/launcher3/icons/BaseIconFactory.java:50-650` | `Drawable + float scale + int mode → Bitmap` . `createIconBitmap(Drawable, float, int)` wraps adaptive, applies `mMonochromeGenerator (d7.f)`, shadows, clipping. Holds `mThemeController`, `mWhiteShadowLayer`. |
| `IconProvider` | `jadx:IconProvider.java:40-280` | `PackageItemInfo → Drawable` . `getIcon(PackageItemInfo, ApplicationInfo, int, Supplier)` — resolves calendar/clock, loads package icon, applies `ThemeData` (monochrome padding) for adaptive icons. |
| `d7.l` (obfuscated `ThemedIconProvider`) | `jadx:d7/l.java:30-180` | Extends `IconProvider`. Parses `R$xml.nt_grayscale_icon_map` (`h()`), maintains `themedIconMap`. `getThemeDataForPackage(String)` → `ThemeData`. `canUseLocalIconOverrides()` via `FeatureFlags`. |
| `LauncherIconProvider` | `jadx:LauncherIconProvider.java:10-20` | Extends `d7.l`, DI entry, toggles `USE_LOCAL_ICON_OVERRIDES`. |
| `IconCache` / `BaseIconCache` | `jadx:IconCache.java:60-120`, `cache/BaseIconCache.java` | `ComponentName → BitmapInfo` with on-disk `IconDB` (`favorites` table column `iconPackage`, `mono_flag`). `getIcon(CacheLookupFlag)` path calls `IconProvider.getIcon()` then `BaseIconFactory.createIconBitmap()`. |
| `BitmapInfo` | `jadx:BitmapInfo.java` | Data holder: `Bitmap icon`, `Bitmap lowResIcon`, `ThemedBitmap themedBitmap`, `float scale`, `int color`. `Extender` interface for clock. |
| `MonoThemedBitmap` | `jadx:icons/mono/MonoThemedBitmap.java:15-80` | Implements `ThemedBitmap`. Holds `Bitmap mono (ALPHA_8)`, `monoLight`, `whiteShadowLayer`, `monoFlag`. `newDrawable()` → `ThemedIconDrawable` with `BlendModeColorFilter`. |
| `ThemedIconDrawable` | `jadx:icons/mono/ThemedIconDrawable.java:20-173` | `FastBitmapDrawable`. Draws `bgBitmap (whiteShadowLayer)` tinted `colorBg`, then `monoIcon` tinted `colorFg` (both `SRC_IN`). Colors from `m7/c` / `ThemedIconDrawable.Companion.getColors()` via `c7.b`. |
| `d7.f` (MonoGenerator) | `jadx:d7/f.java:30-280` | State machine for mono conversion. Inputs `Bitmap (iconBitmapSize x iconBitmapSize)`; method `g(Bitmap, boolean) → m7.c` does `o7.a.l` raster, `o7.k.G` grayscale/alpha extract, `d/e` contrast/threshold, `s` bounding-rect crop, `o7.a.h` ALPHA_8 crop, optional light variant via `o7.k.f()`. |
| `o7.k` / `o7.a` | `jadx:m7/c.java`, `o7/k.java`, `o7/a.java` | Low-level ops: `G(bitmap, int, int[], int[], int[][]) → (dominantGray, fillRatio)`; `F(bitmap, int, int[][])` alpha extraction; `h(bitmap, Rect, int, float, Config)` ALPHA_8 create; `f(bitmap)` light mono. |
| `k7.h` (MonoInfo) | `jadx:k7/h.java:10-90` | Bridges `Drawable + Bitmap` → decides `monoFlag {1,2}`, calls `d7.f.b(this)` → `MonoThemedBitmap` attached to `BitmapInfo`. |
| `IconNormalizer` | `jadx:IconNormalizer.java` | Computes legacy icon scale via mask path; used by `BaseIconFactory.normalizeNonAdaptiveIcon()`. |
| `ShadowGenerator` / `GraphicsUtils` | `jadx:ShadowGenerator.java` | Shadow path for adaptive icons; `getWhiteShadowLayer()` provides mono bg bitmap. |
| `DotRenderer` | `jadx:DotRenderer.java` | Notification dots overlay, not icon pipeline. |
| `ThemeManager` | `jadx:graphics/ThemeManager.java:42-115` | Singleton. Static `MONO_THEME_CONTROLLER = MonoIconThemeController`. `IconState(iconMask, folderShapeMask, IconThemeController, themeCode, float, ShapeDelegate, ShapeDelegate)` holds `IconThemeController`. `getThemeController() → MonoIconThemeController`. |

### 1.3 `d7.*` / `k7.*` / `m7.*` / `o7.*` — Nothing mono pipeline (obfuscated packages)

| Package | Evidence | Purpose |
|---|---|---|
| `d7` | `d7.f`, `d7.l`, `d7.h` (IconPackManager) | Mono generation + themed map + pack manager |
| `k7` | `k7.h` (MonoInfo), `k7.b` (IconPack entity), `k7.h` etc. | Per-package mono metadata, caching |
| `m7` | `m7.c` (Mono normal/light holder), `m7.a/b/d` | Data structures, debug flags `m7.d.a()/b()` |
| `o7` | `o7.a` (bitmap ops), `o7.k` (alpha/gray), `o7.c/d/e` | Pixel math, threshold, luminance |
| `c7.b` / `h.f16811p` | `c7.b`, `d7.h.f16811p` | Color provider (`getColors()` → `[bg, fg]`), feature gating (`y()`, `v()`, `u(pkg)`) |

### 1.4 `com.nothing.launcher.*` — Nothing launcher shell

| Package | Role | Key entries |
|---|---|---|
| `com.nothing.launcher` | Shell | `NTLauncher` (extends `SearchLauncher`), `NTLauncherApplication`, `BaseApplication`, `FallbackHome` (manifest HOME) |
| `com.nothing.launcher.setting.iconpack` | UI | `IconPackPickerActivity` / `IconPackPickerFragment` (manifest `com.nothing.launcher.icon_pack_picker`), bindings `FragmentIconPackPickerBinding` |
| `com.nothing.launcher.graphics` | Nothing theme glue | Re-uses Launcher3 `ThemeManager`; Nothing-specific `mono_nothing_*` colors |
| `com.nothing.launcher.bigicon` | Big icon | `NTBigIconCachingLogic` |
| `com.nothing.launcher.privatespace` / `widget` / `folder` / `card` | Features | Private space, big folders, cards — incidental to icon pipeline |

### 1.5 `com.android.launcher3.icons.mono` — public mono API

- `MonoIconThemeController` (`jadx:mono/MonoIconThemeController.java:27-99`): `IconThemeController` impl, `createThemedAdaptiveIcon(Context, AdaptiveIconDrawable, BitmapInfo) → AdaptiveIconDrawable(ColorDrawable(bg), monochrome)`; `decode(byte[], BitmapInfo, BaseIconFactory, SourceHint) → MonoThemedBitmap` (ALPHA_8 deserialize).
- `MonoThemedBitmap` / `ThemedIconDrawable` documented above.

## 2. Icon pack overview (base.apk 1.6 MB, 1 DEX)

| Observation | Evidence |
|---|---|
| **Zero** `com.nothing.icon.*` classes | `unzip -p base.apk classes.dex | strings | grep -oE "Lcom/[^;]+;" | sort -u` → only `com.google.android.material.*` + `androidx.*` + obfuscated `a0`, `A`, `b0` etc. No `nothing` string in dex (only `onNothingSelected` false positive from material). |
| **Zero** icon resources | `apktool` res dump → no `mipmap-*` icons beyond `ic_launcher.xml`, no `drawable/*` app icons, no `xml/appfilter.xml` (checked `res/xml/`). |
| **Zero** providers/activities beyond startup | `apkanalyzer manifest print` → only `androidx.startup.InitializationProvider`. No `HOME`, no `THEME` metadata. |
| **921 smali files** all material | `find smali -name "*.smali" | wc -l` 921; every package is `com.google.android.material.*` / `androidx.*`. |
| Split `xxhdpi` apk | `unzip -l split_config.xxhdpi.apk` → 61 KB, only `abc_*` material 9-patches. |

**Conclusion:** Icon pack is a **registration/trigger APK** with no rendering logic; all rendering lives in launcher (see `ICON_PACK_RELATIONSHIP.md`).

## 3. Cross-cutting dependencies

- `com.android.systemui.shared` (`SysUiStatsLog` used for placeholder color in `BaseIconFactory`).
- `Dagger` (`DaggerLauncherPreviewRenderer`).
- `Kotlin coroutines + Flow` (`d7.h` `x2.b`, `e1.b`, `o0` Flow).
- `Room` (`BaseIconCache.IconDB` — SQLite `favorites` table `ALTER TABLE DROP COLUMN iconPackage` seen in dex strings).

## 4. Confidence

- Launcher class map: **CONFIRMED** (jadx source + dexdump + manifest).
- Mono pipeline packages `d7/k7/m7/o7`: **CONFIRMED** (linked via `ThemedIconDrawable.monoIcon`, `MonoThemedBitmap.newDrawable`, `BaseIconFactory.mMonochromeGenerator`, `k7.h.g()` flow).
- Icon pack emptiness: **CONFIRMED** (multiple independent probes: manifest, res list, dex strings, smali list).
