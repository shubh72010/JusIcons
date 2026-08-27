---
name: Bug report
about: Visual mismatch or crash
labels: bug
---
**Package:** `com.example` (from `adb shell pm list packages` or JusIcons row subtitle)

**Screenshots:** Attach `ORIGINAL | JUSICONS` + the 8-stage strip (`01`→`08`)

**Expected (Nothing device if you have it):**

**RenderOptions used:** `forensicScale / showBackground / binary`

**Stage where it diverges:** `03_grayscale` / `04_analysis` / `05_remapped` / `06_alpha` / `07_cropped` / `08_final`
