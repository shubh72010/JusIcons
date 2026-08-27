# APK_LAYOUT.md — Phase 1: APKM Extraction

> APKM is a ZIP container (APKMirror format), not a single APK. All splits extracted with `unzip`; originals preserved.

## 1. Source APKM files (untouched, repo root)

| APKM | SHA-256 (original) | Size | `info.json` pname/version |
|---|---|---|---|
| `com.nothing.launcher_4.0.20-40020116_1arch_1dpi_24lang_aa0b38868142d711e97ef5ab37c702a3_apkmirror.com.apkm` | `7ef615cb02acffd76111fc65158b5d9273d5328dc9a77accc0597af0b131c44d` | 31 MB (63341172 uncompressed) | `com.nothing.launcher`, `40020116`, `4.0.20`, `min_api 36`, `arches [arm64-v8a]`, `dpis [480]` |
| `com.nothing.icon_1.0.2-100020000_1dpi_30lang_fee08935e489d9d029f64c7f165a96ff_apkmirror.com.apkm` | `225e27b918be86a1b1988e132315c737b37ad45b677846541f51862136c9a549` | 1.1 MB (2584816) | `com.nothing.icon`, `100020000`, `1.0.2`, `min_api 33`, `dpis [480]` |

`APKM_installer.url` (58 B) and `META-INF/` inside each APKM are APKMirror metadata, not app payload.

## 2. Extraction layout

```
reverse-engineering/
├── launcher/original/
│   ├── base.apk                         # 57 MB, contains all DEX + res
│   ├── split_config.arm64_v8a.apk       # 525 KB native lib
│   ├── split_config.xxhdpi.apk          # 118 KB drawables
│   ├── split_config.{ar,de,en,es,et,fi,fr,hi,hu,in,it,ja,ko,ms,nl,pl,pt,ru,sv,th,tr,uk,vi,zh}.apk
│   ├── info.json, icon.png, META-INF/, APKM_installer.url
│   └── .gitkeep
└── icon/original/
    ├── base.apk                         # 1.6 MB, single classes.dex 850 KB
    ├── split_config.xxhdpi.apk          # 61 KB (only xxhdpi drawables)
    ├── split_config.{ar,bn,de,en,es,et,fi,fr,gu,hi,hu,in,it,ja,kn,ko,mr,ms,nl,pl,pt,ru,sv,ta,te,th,tr,uk,vi,zh}.apk
    ├── info.json, icon.png, META-INF/
    └── .gitkeep
```

All extracted via:
```bash
unzip -o -q <name>.apkm -d reverse-engineering/<launcher|icon>/original
```
Originals never modified; hashes verified post-extract.

## 3. SHA-256 of extracted APKs

### Launcher `base.apk` + splits

| File | SHA-256 |
|---|---|
| `base.apk` | `57ebac65447e0c63a608937f4b1e790c74dd225bbefbf4685c2ec0603746fd5e` |
| `split_config.arm64_v8a.apk` | `e44690918a024e8cd17fc3d74e3d425d9fb2b709c198656128271bc079bc95b0` |
| `split_config.xxhdpi.apk` | `901faeedd32307e3c560d05a780c89b72502b0d5fe76a535b1e225f833e3a56d` |
| `split_config.en.apk` | `e32c34634201407978879f12a83deb730308c0fdaef0c77c85f96a8d5d33fe28` |
| `split_config.ar.apk` | `6adb578edd3b469c4c11dfcdf486e4457b3f2005b8701041fc3e95e17dae646d` |
| `split_config.de.apk` | `967a9b7784ce4113c066342b05b1243f253a339b691cbe0926c019cc592bbf84` |
| `split_config.es.apk` | `aae08f0f88f196dad72b937a88928fc254295505a6dd7ffb88ef35c4be0b1522` |
| `split_config.fr.apk` | `91b1d7bd92eb05edeba993bc07a77e937887e34ee924b3df40edec7ce993f53f` |
| `split_config.ja.apk` | `139d08bdada6ab034998f5426d85e4c3df22dc630dd5d8427a98b4dc0d4f7060` |
| `split_config.zh.apk` | `cf01287c8b91f71d130694cbb1cf7d24742bee54d520a981bcebe8425becbaf8` |
| *(remaining lang splits)* | see `sha256sum reverse-engineering/launcher/original/split_*.apk` |

### Icon pack `base.apk` + splits

| File | SHA-256 |
|---|---|
| `base.apk` | `86d9c5742205499cc4a7699682117c6ec701fc8ff487fcfe63aaf16de3bf1421` |
| `split_config.xxhdpi.apk` | `12404096b40ade8f67635b8cf5b4cbdf5d4fc3972409f0416d3777fe840b8644` |
| `split_config.en.apk` | `0dbb08c3eed7f018fd6b2a8bc2f5ce705d0cbe8ac4a94102a72b579bdd165ca1` |
| `split_config.de.apk` | `1247e6c1a2b45c5a3d6685e9f46f1b3506b0be6a20e826c3c70de520484e3cda` |
| *(30 lang splits total)* | `sha256sum reverse-engineering/icon/original/split_*.apk` |

## 4. APK structure (aapt2 / unzip -l)

### Launcher `base.apk` (59 MB uncompressed)

- `AndroidManifest.xml` (AAPT binary, decoded via `apkanalyzer manifest print` → `/tmp/launcher_manifest.xml` 1041 lines)
- `classes.dex` 16.7 MB, `classes2.dex` 8.5 MB, `classes3.dex` 8.0 MB, `classes4.dex` 7.9 MB, `classes5.dex` 0.3 MB
- `res/` — 2000+ entries: `anim/`, `color/`, `drawable/`, `mipmap-`, `xml/`, `values/`; Nothing-specific examples:
  - `res/drawable/icon_pack_themed_icon_nothing.xml`, `res/drawable-nodpi-v4/theme_preview_workspace_nothing_v*.png` (4 previews)
  - `res/xml/nt_nothing_workspace_4x7*.xml` (4 workspace grids), `res/xml/nt_grayscale_icon_map.xml` (grayscale override map, referenced by `d7.l:h()`)
  - Colors: `icon_pack_*`, `mono_nothing_*`, `themed_icon_*` (see `aapt2 dump resources` grep)
- `resources.arsc`, `assets/`, `lib/` not in base (in `arm64_v8a` split)
- `aapt2 dump badging`: `package com.nothing.launcher v40020116 (4.0.20)`, `compileSdk 35`, `minSdk 36`, `targetSdk 36`, `application-icon res/drawable/ic_launcher_home.xml`

**Splits:**
- `split_config.arm64_v8a.apk` — `lib/arm64-v8a/` native libs
- `split_config.xxhdpi.apk` — `118K` `drawable-xxhdpi` density
- `split_config.{lang}.apk` — `105–185K` each, `resources.arsc` + `res/values-<lang>/`

### Icon pack `base.apk` (1.6 MB)

- `AndroidManifest.xml` (4.4 KB binary)
- `classes.dex` 850 KB (921 smali files, all `androidx`/`com.google.android.material` — see Phase 2)
- `res/` — only Material/AppCompat resources: `anim/`, `color/`, `drawable/` (no `mipmap-hdpi` icons, no `xml/appfilter`), `values/`, `layout/` (generic)
- **No** `res/xml/appfilter.xml`, **no** drawable icon database, **no** `Nothing` package code
- Split `split_config.xxhdpi.apk` (61 KB) — only `abc_*` material drawables scaled for xxhdpi
- `aapt2 dump badging`: `package com.nothing.icon v100020000 (1.0.2)`, `compileSdk 35`, `minSdk 33`, `targetSdk 35`, `icon res/mipmap-anydpi-v26/ic_launcher.xml`, `uses-feature com.nothing.feature.OS.V2_0 required=true`

## 5. Manifest summary

**Launcher** (`com.nothing.launcher`, `apkanalyzer manifest print` evidence):
- Permissions: `READ_WALLPAPER_INTERNAL`, `BIND_WALLPAPER`, `QUERY_ALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, `MANAGE_ACTIVITY_TASKS`, Nothing-specific `com.nothing.weather.permission.ACCESS_WEATHER_INFO`, `com.nothing.launcher.permission.READ/WRITE_SETTINGS`, etc. (full list in `aapt2 dump badging` output)
- Activities: `com.nothing.launcher.FallbackHome` (HOME/DEFAULT, `priority -1001`), `com.android.searchlauncher.SearchLauncher` (HOME/DEFAULT/MONKEY/LAUNCHER_APP, `TouchInteractionService` QUICKSTEP), `IconPackPickerActivity` (`com.nothing.launcher.icon_pack_picker`), `CustomisationSettingsActivity`, `HomeSettingsActivity`, `AllAppSettingsActivity`, etc.
- Providers: `com.android.launcher3.LauncherProvider` (`com.nothing.launcher.settings`, READ/WRITE_SETTINGS), `GridCustomizationsProxy` (`grid_control`), `LauncherSearchIndexablesProvider`
- Services: `TouchInteractionService` (QUICKSTEP), `NotificationListener`, `SystemAlarmService`/`SystemJobService` (WorkManager)

**Icon pack** (`com.nothing.icon`):
- Manifest declares **zero** activities/services/receivers/providers beyond `androidx.startup.InitializationProvider` (EmojiCompat, ProcessLifecycle). No icon provider, no intent filter for `HOME`, no `android.intent.action.MAIN`, no metadata for `com.anddoes.launcher.THEME` / `org.adw.launcher.THEMES` classic icon pack protocols.
- Only `uses-feature com.nothing.feature.OS.V2_0` gating install to Nothing OS 2.0+

## 6. Implication

- Icon pack is **not a split**: it is a distribution APK (`requiredSplitTypes base__density`), not a config split of the launcher. Both APKM files contain independent `base.apk`s.
- The 1.6 MB icon pack cannot hold a large icon DB (no `res/drawable` icons beyond launcher adaptive icon); its role must be registration/trigger rather than renderer (proved further in `ICON_PACK_RELATIONSHIP.md` and `CLASS_MAP.md`).
- Launcher `base.apk` holds all Nothing-specific theming resources (`nt_grayscale_icon_map`, `mono_nothing_*`, `themed_icon_*`, `enable_forced_themed_icon`).
