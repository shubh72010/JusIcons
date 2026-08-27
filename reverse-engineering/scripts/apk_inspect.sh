#!/usr/bin/env bash
set -euo pipefail
AAPT2="${ANDROID_SDK:-$HOME/Android/Sdk}/build-tools/37.0.0/aapt2"
APKANALYZER="${ANDROID_SDK:-$HOME/Android/Sdk}/cmdline-tools/latest/bin/apkanalyzer"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LB="$ROOT/reverse-engineering/launcher/original/base.apk"
IB="$ROOT/reverse-engineering/icon/original/base.apk"
echo "=== launcher badging ==="; "$AAPT2" dump badging "$LB" | head -60
echo "=== icon badging ==="; "$AAPT2" dump badging "$IB" | head -30
echo "=== launcher manifest (head) ==="; "$APKANALYZER" manifest print "$LB" | head -80
