# Contributing to JusIcons

Thanks for helping make the Nothing look reproducible on any Android!

## Quick start

```bash
git clone git@github.com:shubh72010/JusIcons.git
cd JusIcons
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
# tap any icon → 8-stage trace in cache/debug-output/
```

## What to work on

- `app/src/main/java/com/jusdots/jusicons/engine/` — the port. Keep it under 5 files; no `com.nothing.*` imports.
- `reverse-engineering/` — every visual claim needs a `jadx:file:line` cite.
- Visual fixes: wire `WallpaperColors` to `RenderOptions bg/fg`, soften `whiteShadowLayer` blur, implement `o7.k.f` light variant.

## PR checklist

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes
- [ ] If you touch the renderer, attach `adb exec-out run-as com.jusdots.jusicons tar -c cache/debug-output | tar -x` screenshots (`01`→`08`)
- [ ] No proprietary decompiled code pasted — independent Kotlin only
- [ ] Updated `reverse-engineering/RENDERING_ALGORITHM.md` if you changed a constant

## Reporting a mismatch

Open an issue with:
1. Package name (e.g. `com.google.android.youtube`)
2. Screenshot of `ORIGINAL | JUSICONS` + the 8-stage strip
3. Expected (Nothing device screenshot if you have it)

## Commit style

`feat:`, `fix:`, `docs:`, `chore:` — keep it short. The CI checks AGP 9.3 / Kotlin 2.2 / minSdk 24.
