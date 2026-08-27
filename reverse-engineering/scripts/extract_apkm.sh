#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LAUNCHER_APKM="$ROOT/com.nothing.launcher_4.0.20-40020116_1arch_1dpi_24lang_aa0b38868142d711e97ef5ab37c702a3_apkmirror.com.apkm"
ICON_APKM="$ROOT/com.nothing.icon_1.0.2-100020000_1dpi_30lang_fee08935e489d9d029f64c7f165a96ff_apkmirror.com.apkm"
mkdir -p "$ROOT/reverse-engineering/launcher/original" "$ROOT/reverse-engineering/icon/original"
unzip -o -q "$LAUNCHER_APKM" -d "$ROOT/reverse-engineering/launcher/original"
unzip -o -q "$ICON_APKM" -d "$ROOT/reverse-engineering/icon/original"
touch "$ROOT/reverse-engineering/launcher/original/.gitkeep" "$ROOT/reverse-engineering/icon/original/.gitkeep"
echo "hashes launcher:"
sha256sum "$ROOT/reverse-engineering/launcher/original"/*.apk | head -40
echo "hashes icon:"
sha256sum "$ROOT/reverse-engineering/icon/original"/*.apk | head -40
