#!/usr/bin/env bash
# PocketLedger emulator E2E: install, seed, inject fake bank SMS, capture screens.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
ADB="$SDK/platform-tools/adb"
PKG="app.pocketledger"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHOTS="$ROOT/docs/screenshots"
mkdir -p "$SHOTS"

cd "$ROOT"

echo "▸ building + installing"
./gradlew :app:installDebug --console=plain

echo "▸ granting SMS permissions"
"$ADB" shell pm grant "$PKG" android.permission.READ_SMS || true
"$ADB" shell pm grant "$PKG" android.permission.READ_SMS
"$ADB" shell pm grant "$PKG" android.permission.RECEIVE_SMS

echo "▸ seeding demo accounts (debug build only)"
"$ADB" shell am broadcast -a app.pocketledger.DEBUG_SEED >/dev/null

echo "▸ launching app"
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 3
"$ADB" exec-out screencap -p > "$SHOTS/01-home.png"

echo "▸ injecting fake bank SMS (live receiver path)"

# 1. HDFC debit — should auto-record (whitelist + tail 1234 + ₹ amount)
"$ADB" emu sms send HDFCBK "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90"
sleep 1

# 2. ICICI credit — should auto-record
"$ADB" emu sms send ICICIB "Rs.25,000.00 credited to a/c XX5678 towards NEFT CR-ACME CORP Ref 045123ABC"
sleep 1

# 3. Monzo spend — foreign currency, whitelisted
"$ADB" emu sms send MONZO "You spent £12.40 at Tesco. Balance: £1,234.56"
sleep 1

# 4. OTP — must be IGNORED
"$ADB" emu sms send HDFCBK "123456 is your OTP for HDFC netbanking. Do not share."
sleep 1

# 5. Promo with a rupee amount — must be IGNORED
"$ADB" emu sms send AXISBK "Get pre-approved personal loan up to Rs.5,00,000! Apply now."
sleep 1

# 6. No account tail — should land in REVIEW (account unmapped)
"$ADB" emu sms send KOTAKB "Rs.749.00 spent on your card ending 4021"
sleep 2

echo "▸ capturing result screens"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 2
"$ADB" exec-out screencap -p > "$SHOTS/02-home-after-sms.png"

# open Review tab (tap coordinates for the pill nav, 1080x2400 default-density device)
"$ADB" shell input tap 620 2260 || true
sleep 1
"$ADB" exec-out screencap -p > "$SHOTS/03-review.png"

echo "▸ app SMS audit (via run-as sqlite is not available; use debug export in-app)"
echo "▸ checking no INTERNET permission in merged manifest"
AAPT2="$SDK/build-tools/35.0.0/aapt2"
"$AAPT2" dump badging "$ROOT/app/build/outputs/apk/debug/app-debug.apk" | grep -E "uses-permission" || true
if "$AAPT2" dump badging "$ROOT/app/build/outputs/apk/debug/app-debug.apk" | grep -q "android.permission.INTERNET"; then
  echo "✗ INTERNET permission found — FAIL"; exit 1
else
  echo "✓ no INTERNET permission"
fi

echo "✓ E2E complete — screenshots in $SHOTS"
