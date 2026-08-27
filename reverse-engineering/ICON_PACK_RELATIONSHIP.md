# ICON_PACK_RELATIONSHIP.md — Phase 5: What `com.nothing.icon` Actually Does

> Hypothesis under test: `com.nothing.icon` is **not** the renderer. Prove division of responsibilities.

## 1. Summary (one-line)

`com.nothing.icon (1.0.2)` is a **registration / trigger package** (30 KB code, zero icon assets) whose presence is detected by launcher's `IconPackManager (d7.h)` via `PackageManager` queries; **all** monochrome generation (`d7.f`, `k7.h`, `m7.c`, `o7.*`) and drawable construction (`MonoThemedBitmap → ThemedIconDrawable`) lives in `com.nothing.launcher (4.0.20)`.

## 2. Evidence that icon pack contains no rendering

| Test | Command | Result |
|---|---|---|
| Manifest components | `apkanalyzer manifest print icon/base.apk` | **Only** `androidx.startup.InitializationProvider`. Zero `activity|service|receiver|provider` with `nothing` in name, zero intent filters, zero `meta-data` for theme/icon registration. |
| Resources | `apktool d icon/base.apk; ls res/drawable res/mipmap-* res/xml` | No `appfilter.xml`, no `drawable/*` app icons, no `iconpack.xml`. Only `ic_launcher.xml` + material `abc_*`. |
| DEX packages | `unzip -p classes.dex \| strings \| grep -oE "Lcom/[^;]+;" \| sort -u` | Packages are `com.google.android.material.*`, `androidx.*`, obfuscated `a0/B/C` — **no** `com/nothing/icon`. |
| Strings | `strings classes.dex \| grep -i nothing` | Only `onNothingSelected` (material false positive). No `Mono`, `Themed`, `DotMatrix`, `Grayscale`. |
| Smali count | `find smali -name "*.smali" \| wc -l` | 921 files, all material/androidx. |
| Size | `base.apk` 1.6 MB vs launcher 59 MB | 1.6 MB unpacked is appcompat+material; icon DB for 100s of apps would be >5 MB. |
| Density split | `unzip -l split_config.xxhdpi.apk` | 61 KB, only `abc_*` 9-patches, no app icons. |

## 3. Evidence that launcher owns the pipeline

### 3.1 Icon pack manager in launcher (d7.h)

- `jadx:d7/h.java:60-200` defines `IconPackManager` holding `Context`, `h7.c` (DAO), `n7.b` (observer), `i7.b`, `f0` Flow. Methods `H(String)`, `e(Context, ResolveInfo)`, `C(SuspendBool, boolean)` manipulate icon pack state.
- Strings in `classes4.dex`: `IconPackManager`, `IconPackListViewModel`, `IconPackPickerFragment`, `IconPackSettingsCache`, `IconPackageReader`, `IconPackagesCache`, `adaptedIconPackages`, `bg_icon_pack_item_add`.
- Gating: `d7.h.f16812q` static checks `ib.a.a("NTF_*")` device model; `h.f16811p.a().y()` / `v()` / `u(pkg)` feature flags (`c7.b`) control whether themed path is active.

### 3.2 Installation / removal handling

- Dex strings (`classes4.dex`): `NothingIcon was installed`, `:NothingIcon was installed, but had an incorrect signature.`, `NothingIconPackCache`, `checkNothingIconForceRenderChanged`, `com.nothing.launcher.NOTHING_ICON_FORCE_RENDER_ENABLE_CHANGED`, `QNothingIconForceRenderRefreshJob execute, unsuitable icons will be refreshed now.`
- `d7.h::c` coroutine: iterates `HashSet<String>` removed packages; if `str == "com.nothing.icon"` sets `p0.f19789a`, then `C(false, true)` + `J(context, "SYSTEM_ICONS", true)` resets icon pack to SYSTEM. Evidence: `jadx:d7/h.java:120-180` inside `class c extends l implements p`.
- `IconCache`/`BaseIconCache` strings: `NothingIconApplyStatusUpdateTask`, `NOTHING_ICON_APPLY_STATUS_URI`, `NOTHING_ICON_FORCE_RENDER_ENABLE_URI`.

### 3.3 UI for picking icon pack

- Manifest: `com.nothing.launcher.setting.iconpack.IconPackPickerActivity` exported with `com.nothing.launcher.icon_pack_picker`.
- Bindings: `FragmentIconPackPickerBinding`, `SingleIconPackItemLayoutBinding` (classes3/4).
- Resources: `bg_icon_pack_item_add`, `icon_pack_*` colors/dimens (14 color resources, 8 dimens) in `aapt2 dump resources` — all in **launcher**, not icon pack.

### 3.4 Per-package themed map (grayscale overrides)

- `d7.l:h()` parses `res/xml/nt_grayscale_icon_map` (`aapt2 dump resources` confirms presence) into `themedIconMap: Map<String, ThemeData>`; `getThemeDataForPackage(pkg)` looks up there. This is **launcher** local override, not icon-pack-provided.
- `IconProvider.getIcon()` delegates monochrome padding via `ThemeData.loadPaddedDrawable()`.

## 4. How the two APKs communicate (no direct IPC)

| Mechanism | Checked | Found |
|---|---|---|
| Explicit Intent (`startActivity` with `com.nothing.icon` class) | `grep -rn "com.nothing.icon" /tmp/jadx_launcher` | Only package-name equality checks in `d7.h.c` (removal detection), no class refs. |
| Broadcast | manifest receivers | No `RECEIVE_*` from icon pack; only `SESSION_COMMITTED`/`APPWIDGET_RESTORED` standard. |
| ContentProvider | manifest providers | No `com.nothing.icon.*` provider; launcher queries via `PackageManager.getResourcesForApplication` / `getActivityInfo` generic, not icon-specific. |
| Shared lib / reflection | `strings` `Class.forName` | No reflection targeting `com.nothing.icon`. |
| Theme registration XML | `res/xml/` | Icon pack has zero; launcher has `nt_grayscale_icon_map` locally. |

**Communication pattern is indirect:** launcher's `PackageManager.query` / `getPackageInfo("com.nothing.icon")` + `Resources` checks (standard icon-pack discovery). Signature check exists (`had an incorrect signature` log) → implies launcher verifies `com.nothing.icon` signature before trusting it.

## 5. What the icon pack *does* do (minimal)

1. **Declares `uses-feature com.nothing.feature.OS.V2_0 required=true`** → only installable on Nothing OS 2.0+ (Play Store distribution split).
2. **Provides a package identity** (`com.nothing.icon`) that launcher's `IconPackManager` treats as the "Nothing" pack (see `y.d(str, "com.nothing.icon")` guard). Without it, launcher falls back to `SYSTEM_ICONS` (`d7.h::C(..., "SYSTEM_ICONS")`).
3. Possibly triggers **force-render flag** (`NOTHING_ICON_FORCE_RENDER_ENABLE_URI`, `NothingIconForceRenderRefreshJob`) that causes launcher to re-render cached icons via `d7.f` pipeline. The job logs `unsuitable icons will be refreshed now` — i.e., icons previously not suitable for mono get regenerated after pack install.

No evidence of:
- Drawable database (A),
- Transformation resources (B),
- Launcher registration metadata broadcast (C) beyond package presence,
- Renderer library.

## 6. Classification

- **Database?** No — size + res list prove absence.
- **Renderer?** No — all `d7.f`/`k7.h`/`m7`/`o7` live in launcher.
- **Registration / trigger?** **Yes** — package presence + signature is the signal; launcher does the work.
- **Theme provider?** Partially — it *is* the theme identity (`com.nothing.icon` == Nothing pack), but theme assets (masks, colors, grayscale map) are baked into launcher's `res/xml` and `colors` (`mono_nothing_*`).
- **Compatibility layer?** No.

## 7. Confidence

| Claim | Level | Basis |
|---|---|---|
| Icon pack has no rendering code | **CONFIRMED** | Manifest + res + dex strings + smali + size converge. |
| Launcher holds full mono pipeline | **CONFIRMED** | `d7.f`, `k7.h`, `m7.c`, `o7.*` source in `/tmp/jadx_launcher`. |
| Communication is package-presence + signature check, not IPC | **STRONGLY SUPPORTED** | No intent/provider refs; `d7.h.c` equality check + signature log. |
| Icon pack triggers `ForceRender` refresh | **LIKELY** | `NothingIconForceRenderRefreshJob` + `NOTHING_ICON_FORCE_RENDER_ENABLE_CHANGED` strings; exact JobScheduler trigger not yet traced to smali entry. |
| Launcher fallback to SYSTEM_ICONS when pack removed | **CONFIRMED** | `d7.h.c: J(context, "SYSTEM_ICONS", true)`. |

## 8. Implication for JusIcons

Do **not** repackage `com.nothing.icon`'s nonexistent renderer. Implement the launcher-side pipeline (`BaseIconFactory → d7.f → MonoThemedBitmap → ThemedIconDrawable`) as a standalone `icon-engine` module. Distribution as a *package identity* (icon pack stub) is unnecessary for a library; but if you want Nothing Launcher to recognize JusIcons as a selectable pack, replicate the `com.nothing.icon` package-name/signature pattern via `PackageManager` query (documented in `PORTABILITY.md` later).
