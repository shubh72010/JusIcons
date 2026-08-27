# RE.md — Repository Inventory (Phase 0)

> Initial state before any extraction or implementation. Do not overwrite existing work.

## 1. Directory layout (2026-08-27)

```
JusIcons/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/jusdots/jusicons/
│   │   │   │   ├── MainActivity.kt          # hello-world Compose
│   │   │   │   └── ui/theme/{Color,Theme,Type}.kt
│   │   │   ├── res/{drawable,mipmap-*,values,xml}
│   │   │   └── keepRules/rules.keep
│   │   ├── androidTest/java/.../ExampleInstrumentedTest.kt
│   │   └── test/java/.../ExampleUnitTest.kt
│   ├── .gitignore
│   └── (no icon-engine / icon-model yet)
├── build.gradle.kts                         # top-level, alias plugins only
├── settings.gradle.kts                      # include(":app"), foojay resolver
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.{jar,properties}
├── gradle.properties
├── local.properties                         # sdk.dir=/home/flakesofsmth/Android/Sdk
├── .idea/ .gradle/                          # IDE + cache
├── .gitignore                               # + RE blob ignores (patched 2026-08-27)
├── com.nothing.launcher_4.0.20-...apkm      # 31 MB, tracked? ignored via reverse-eng rules only
└── com.nothing.icon_1.0.2-...apkm           # 1.1 MB
```

No `reverse-engineering/` existed prior to this phase; created by Phase 0.

## 2. Build toolchain

| Field | Value | Source |
|---|---|---|
| Gradle | 9.5.0 (`distributionUrl` bin.zip, sha256 `553c78f...6b746`) | `gradle-wrapper.properties` |
| AGP | 9.3.2 | `gradle/libs.versions.toml:2` |
| Kotlin | 2.2.10 | `libs.versions.toml:9` |
| Compose BOM | 2026.02.01 | `libs.versions.toml:10` |
| compileSdk | 37 (`release(37)`) | `app/build.gradle.kts:8` |
| minSdk | 24 | `app/build.gradle.kts:13` |
| targetSdk | 37 | `app/build.gradle.kts:14` |
| namespace / appId | `com.jusdots.jusicons` | `app/build.gradle.kts:7,12` |
| Java | 11 (source/target) | `app/build.gradle.kts:29` |
| JVM args | `-Xmx2048m -Dfile.encoding=UTF-8` | `gradle.properties:9` |
| SDK | `/home/flakesofsmth/Android/Sdk` | `local.properties:10` |
| Build-tools present | 35.0.0, 36.0.0, 37.0.0 | `ls $SDK/build-tools` |
| aapt2 / apkanalyzer | `$SDK/build-tools/37.0.0/aapt2`, `$SDK/cmdline-tools/latest/bin/apkanalyzer` | probe |

Top-level `build.gradle.kts:3-4` applies `android.application` + `kotlin.compose` only via `apply false`.

## 3. APKMs (originals, untouched)

| File | SHA-256 | Size | Type |
|---|---|---|---|
| `com.nothing.launcher_4.0.20-40020116_1arch_1dpi_24lang_aa0b38868142d711e97ef5ab37c702a3_apkmirror.com.apkm` | `7ef615cb02acffd76111fc65158b5d9273d5328dc9a77accc0597af0b131c44d` | 31 MB | APKM (zip) |
| `com.nothing.icon_1.0.2-100020000_1dpi_30lang_fee08935e489d9d029f64c7f165a96ff_apkmirror.com.apkm` | `225e27b918be86a1b1988e132315c737b37ad45b677846541f51862136c9a549` | 1.1 MB | APKM (zip) |

`info.json` inside each (see `APK_LAYOUT.md`): launcher `versionCode 40020116`, `min_api 36`, `arches [arm64-v8a]`, `dpis [480]`; icon `versionCode 100020000`, `min_api 33`, `dpis [480]`, no native arch.

## 4. Existing code

- No custom icon logic, no `IconRenderer`, no RE scripts before Phase 0.
- `MainActivity.kt:1-40` — stock Compose template, `enableEdgeToEdge()`, `Scaffold` + `Greeting`.
- Tests: default `ExampleUnitTest` / `ExampleInstrumentedTest` only.

## 5. Git

- Working directory is **not** a git repo (`git status` → `fatal: not a git repo`). `.gitignore` exists but no `.git/` yet.
- Patched `.gitignore` per approved plan to track reports/scripts and ignore proprietary blobs:
  ```
  reverse-engineering/**/original/
  reverse-engineering/**/apktool_out/
  reverse-engineering/**/jadx_out/
  reverse-engineering/**/*.dex
  reverse-engineering/**/*.apk
  reverse-engineering/**/*.apkm
  !reverse-engineering/**/original/.gitkeep
  ```

## 6. Constraints / next

- Do not overwrite `app/` until `ICON_PIPELINE.md` + `RENDERING_ALGORITHM.md` are evidence-backed.
- Keep originals read-only; all extraction under `reverse-engineering/{launcher,icon}/original/`.
- Phase 1 will create `reverse-engineering/APK_LAYOUT.md` and `reverse-engineering/scripts/`.
