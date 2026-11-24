#!/usr/bin/env bash
# Pull the entire SMS inbox from a connected Android device as JSONL.
# Debug builds only (uses run-as). Raw messages stay local: tools/corpus/real
# is git-ignored.
#
# Usage:
#   tools/export_inbox.sh                 # auto-pick the USB phone
#   tools/export_inbox.sh <serial>        # explicit device
#   ANDROID_SERIAL=... tools/export_inbox.sh
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
ADB="$SDK/platform-tools/adb"
PKG="app.pocketledger"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/corpus/real"

SERIAL="${ANDROID_SERIAL:-${1:-}}"
if [[ -z "$SERIAL" ]]; then
  # Prefer a real USB phone over the emulator.
  SERIAL="$("$ADB" devices | awk '$2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"
  [[ -n "$SERIAL" ]] || SERIAL="$("$ADB" devices | awk '$2=="device" {print $1; exit}')"
fi
if [[ -z "$SERIAL" ]]; then
  echo "✗ no device/emulator connected (adb devices is empty)"; exit 1
fi

echo "▸ device: $SERIAL"
if ! "$ADB" -s "$SERIAL" shell pm path "$PKG" >/dev/null 2>&1; then
  echo "✗ $PKG is not installed on $SERIAL"; exit 1
fi

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo "▸ installing latest debug build (data preserved)"
  "$ADB" -s "$SERIAL" install -r "$APK" >/dev/null
else
  echo "! app-debug.apk missing — using the installed build"
  echo "  run ./gradlew :app:assembleDebug first if the export action is unknown"
fi

echo "▸ granting READ_SMS (debug build)"
"$ADB" -s "$SERIAL" shell pm grant "$PKG" android.permission.READ_SMS 2>/dev/null || true

echo "▸ exporting inbox on device (no balances touched)"
"$ADB" -s "$SERIAL" shell am broadcast \
  -n "$PKG/.DemoSeedReceiver" \
  -a "$PKG.DEBUG_EXPORT_INBOX" >/dev/null
sleep 3

FILE="$("$ADB" -s "$SERIAL" shell run-as "$PKG" ls -t files/corpus 2>/dev/null \
  | tr -d '\r' | grep '^inbox-' | head -1 || true)"
if [[ -z "$FILE" ]]; then
  echo "✗ export file not found. Device log:"
  "$ADB" -s "$SERIAL" logcat -d -s InboxExport | tail -5 || true
  exit 1
fi

mkdir -p "$OUT"
"$ADB" -s "$SERIAL" exec-out run-as "$PKG" cat "files/corpus/$FILE" > "$OUT/$FILE"
ROWS="$(wc -l < "$OUT/$FILE" | tr -d ' ')"
echo "✓ $ROWS messages -> tools/corpus/real/$FILE"
echo "  (git-ignored; hand the file to the ML agent)"
