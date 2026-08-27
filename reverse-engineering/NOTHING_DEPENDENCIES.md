# NOTHING_DEPENDENCIES.md — Phase 6: Nothing-Specific Dependencies

> Each entry: where used → why → can replace → proposed replacement. Category: STANDARD_ANDROID / NOTHING_SPECIFIC / OPTIONAL / UNKNOWN.

## 1. OS / framework

| Dependency | Where | Category | Replacement |
|---|---|---|---|
| `com.nothing.feature.OS.V2_0` required feature (icon pack manifest) | `icon/base.apk:AndroidManifest uses-feature` | **NOTHING_SPECIFIC** | Remove gating; JusIcons targets AOSP `minSdk 24`. Gate on `BuildCompat.isAtLeastT()` instead for themed path. |
| `minSdk 36` (launcher) compile against Nothing SDK | `aapt2 dump badging: platformBuildVersion 35, minSdk 36` | **NOTHING_SPECIFIC** | Compile against AOSP SDK 35; stub missing Nothing stubs. 36 features (e.g., `AdaptiveIconDrawable.getMonochrome` available since 33) already standard. |
| Nothing system services `com.nothing.*.permission.*`, `READ_WALLPAPER_INTERNAL`, `BlurWallpaper.AtmosphereProvider` | `launcher manifest` | **NOTHING_SPECIFIC / OPTIONAL** | Strip from JusIcons app; not needed for icon rendering. |
| Device-model check `ib.a.a("NTF_*")` (`NTF_PONG/SPACEWAR/PACMAN...`) | `jadx:d7/h.java: static { f16812q = !isNothingDevice }` | **NOTHING_SPECIFIC** | Replace with `true` (treat any device as capable). No hardware tie. |
| `isSplitRequired=true`, Play `derived.apk.id`, `splits` metadata | both manifests | **STANDARD_ANDROID** (App Bundle) | Keep bundle splits via `aapt2`; no Nothing dep. |
| `ACCESS_SHORTCUTS`, `QUERY_ALL_PACKAGES`, privileged `WRITE_SECURE_SETTINGS`, `MANAGE_ACTIVITY_TASKS` etc. | launcher manifest | **STANDARD_ANDROID / NOTHING_SPECIFIC** | JusIcons only needs `QUERY_ALL_PACKAGES` (or `GET_INSTALLED_APPS` on 33+) for `PackageManager` enumeration. Others not needed. |
| `Instrumentation` / `Dagger` / `WindowExtensions` | launcher manifest/libs | **STANDARD_ANDROID** | Standard deps; keep. |

## 2. Resources / theming

| Dependency | Where | Category | Replacement |
|---|---|---|---|
| `R$xml.nt_grayscale_icon_map` | `d7.l:h()` parses to `themedIconMap` | **NOTHING_SPECIFIC** (content is Nothing's per-package override list) | Replace with JusIcons own `res/xml/jusicons_grayscale_map.xml` or empty map (ALL icons go through mono path). Behavior proven: empty map simply means no `ThemeData` per-package, but mono path still runs via `d7.f` / `k7.h`. |
| `R$color.mono_nothing_background_color`, `mono_nothing_foreground_color`, `mono_color_background/foreground` | `d7.f16798l:a/b`, `MonoThemedBitmap` color provider | **NOTHING_SPECIFIC** | Map to Material3 `colorSurface` / `colorOnSurface` or allow caller-supplied `[bg, fg]` via `RenderOptions`. Already a `RenderOptions` abstraction is proposed. |
| `R$bool.enable_forced_themed_icon` | `BaseIconFactory ctor` → `mShouldForceThemeIcon` | **NOTHING_SPECIFIC / OPTIONAL** | Boolean flag that forces themed path; ignore or expose as `options.forceThemed`. |
| `R$drawable.*` Nothing previews (`theme_preview_workspace_nothing_v*`) | `aapt2` res | **NOTHING_SPECIFIC** | Not needed for engine. |
| `R$dimen` Nothing icon pack UI | `icon_pack_*` colors/dimens | **NOTHING_SPECIFIC** | UI only; not in engine. |

## 3. Code / feature flags

| Dependency | Where | Category | Replacement |
|---|---|---|---|
| `c7.b` / `h.f16811p` (`y()`, `v()`, `u(pkg)`, `t()`, `w(float)`) singleton | Everywhere: `d7.l`, `d7.f`, `k7.h`, `BaseIconFactory` | **NOTHING_SPECIFIC** (remote config / A/B gating) | Replace with local `FeatureFlags` / `BuildConfig` booleans. `y()` = mono enabled, `v()` = Nothing colorway vs generic, `u(pkg)` = per-package allowlist, `w()` = scale. Hardcode `true` for reproduce. |
| `o7.d / o7.c / o7.k` mono math | `d7.f.d/e/r/s`, `o7.a.h`, `o7.k.f` | **UNKNOWN** (origin may be Nothing fork of AOSP mono util but no standard equivalent) | Reimplement clean-room from captured formula (`210/(61504 - gray^2)` etc.) — see `RENDERING_ALGORITHM.md`. No SDK dep. |
| `ThemeManager.MONO_THEME_CONTROLLER` / `IconThemeController` / `MonoIconThemeController` | `graphics/ThemeManager.java:42-115` | **STANDARD_ANDROID + NOTHING_SPECIFIC** wrapper | `IconThemeController` interface is clean (Launcher3 standard since T); `MonoIconThemeController` impl is Nothing's mono variant. Reimplement `IconThemeController` with JusIcons mono; no OS tie beyond `Canvas`/`BlendMode`. |
| `BaseIconFactory.getWhiteShadowLayer()` | `BaseIconFactory.java:482` → `ShadowGenerator` | **STANDARD_ANDROID** (Launcher3) | Copy `ShadowGenerator` or replace with plain white `Bitmap` (mono bg). Standard `Bitmap/Canvas` only. |
| `b7.a.a(Drawable)` helper | `k7.h ctor: drawableA = b7.a.a(appIcon)` | **UNKNOWN** (tiny wrapper, likely Insets normalization) | Replace with identity or `AdaptiveIconDrawable` check. |
| `ib.a.a(device)` | `d7.h` static init | **NOTHING_SPECIFIC** | Stub. |
| Kotlin `Flow`/`Coroutine` (`x2.b`, `e1.b`) for `IconPackManager` | `d7.h` | **STANDARD** (kotlinx-coroutines) | Replace with `Executor`/`Handler` or keep coroutines (std lib). |

## 4. Providers / caches

| Dependency | Where | Category | Replacement |
|---|---|---|---|
| `BaseIconCache.IconDB` SQLite columns `iconPackage`, `mono_flag`, `mono_icon` | dex strings `ALTER TABLE favorites... iconPackage`, `COLUMN_MONO_*` | **NOTHING_SPECIFIC** schema extension | Reuse AOSP `IconCache` schema or add own `jusicons_cache` table; ALPHA_8 serialize is just `byte[]` via `ByteBuffer`, standard. |
| `NOTHING_ICON_FORCE_RENDER_ENABLE_URI` / `NOTHING_ICON_APPLY_STATUS_URI` | dex strings + `d7.h` | **NOTHING_SPECIFIC** (Nothing Settings provider) | Replace with local `SharedPreferences` flag or ignore. |
| `NothingIconApplyStatusUpdateTask` / `NothingIconPackCache` | dex strings + `model/` | **NOTHING_SPECIFIC** | Not needed for engine; JusIcons can expose `invalidateCache(packageName)` API. |
| `LauncherProvider (com.nothing.launcher.settings)` | manifest | **STANDARD_ANDROID** | Standard Launcher3 provider; JusIcons can use own provider or no provider. |
| `GridCustomizationsProxy` shapes (icon mask) | `ThemeManager.IconState` | **STANDARD_ANDROID** (adaptive mask) | Standard `AdaptiveIconDrawable.getIconMask()`; no Nothing dep. |

## 5. IPC / package checks

| Dependency | Where | Category | Replacement |
|---|---|---|---|
| Package presence `com.nothing.icon` + signature check (`had an incorrect signature`) | `d7.h.c` removal loop, dex log strings | **NOTHING_SPECIFIC** | Remove. JusIcons engine does not need to detect an external pack; it *is* the pack. If you want Nothing Launcher interop, optionally publish a stub `com.jusdots.jusicons` with `uses-feature` not required. |
| `com.nothing.launcher.NOTHING_ICON_FORCE_RENDER_ENABLE_CHANGED` broadcast | dex strings | **NOTHING_SPECIFIC** | Replace with local `ContentObserver` or `sendBroadcast` not needed. |

## 6. Rendering-adjacent (GPU/shader)

| Dependency | Where | Category | Replacement |
|---|---|---|---|
| `triangleNoise(p * in_pixelDensity)/255` string in `classes4.dex` | `dex:strings triangleNoise` | **UNKNOWN** — may be AGSL `RuntimeShader` for dithering, not Java. If it exists, it would import `android.graphics.RuntimeShader` (Android 13+ standard). | Mark UNKNOWN until shader source located via `strings` with 200-byte context or `dexdump -d`. If found, replace with `Paint` dithering or ignore (threshold path works without dither). |
| `BlendModeColorFilter` / `BlendMode.SRC_IN` | `ThemedIconDrawable` | **STANDARD_ANDROID** (API 29+) | Standard; fallback to `PorterDuffColorFilter` for `minSdk 24` via compat. |
| `Bitmap.Config.ALPHA_8` | `o7.a.h`, `MonoIconThemeController.decode` | **STANDARD_ANDROID** (API 26+) | For `minSdk 24` use `ALPHA_8` on 26+ and `ARGB_8888` alpha-only fallback on 24-25. Provide compat branch. |

## 7. Summary counts

- **NOTHING_SPECIFIC:** ~14 items (feature/OS gating, device model, grayscale map, nothing colors, force flag, package presence/signature, settings URIs, mono-math origin).
- **STANDARD_ANDROID:** ~10 items (adaptive icons, BlendMode, ALPHA_8, IconThemeController interface, IconCache schema, providers).
- **OPTIONAL:** ~3 items (Nothing wallpapers, privileged perms).
- **UNKNOWN:** 4 items (`o7.d` luminance, `b7.a`, `triangleNoise` shader, `ib.a`).

All NOTHING_SPECIFIC items are replaceable with local flags, resources, or clean-room reimplementation; none require Nothing framework at runtime for the mono pipeline. Engine can run on AOSP API 24+ with the two compat branches noted.
