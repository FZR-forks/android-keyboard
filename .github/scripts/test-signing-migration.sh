#!/usr/bin/env bash
set -euo pipefail

PACKAGE=org.futo.inputmethod.latin
DATA="/data/user/0/$PACKAGE"
PREFS="$DATA/shared_prefs/signing-migration-test.xml"
OLD_APK=(legacy/*.apk)
NEW_APK=(release/*.apk)
test "${#OLD_APK[@]}" -eq 1
test "${#NEW_APK[@]}" -eq 1

adb root
adb wait-for-device
adb install "${OLD_APK[0]}"
OLD_UID="$(adb shell stat -c %u "$DATA" | tr -d '\r')"
adb shell mkdir -p "$DATA/shared_prefs"
adb shell chown "$OLD_UID:$OLD_UID" "$DATA/shared_prefs"
printf '<map><string name="migration_test">settings-retained</string></map>\n' > /tmp/signing-migration-test.xml
adb push /tmp/signing-migration-test.xml "$PREFS"
adb shell chown "$OLD_UID:$OLD_UID" "$PREFS"
adb shell chmod 600 "$PREFS"
adb shell restorecon -R "$DATA"

adb install -r "${NEW_APK[0]}"
NEW_UID="$(adb shell stat -c %u "$DATA" | tr -d '\r')"
test "$OLD_UID" = "$NEW_UID"
adb shell cat "$PREFS" | grep -F settings-retained

# Exercise startup and the keyboard's private receiver registration on both APIs.
adb logcat -c
adb shell am start -W -n "$PACKAGE/.uix.settings.SettingsActivity"
adb shell ime enable "$PACKAGE/.LatinIME"
adb shell ime set "$PACKAGE/.LatinIME"
adb shell am startservice -n "$PACKAGE/.LatinIME"
sleep 5
adb shell pidof "$PACKAGE"
adb logcat -d -b crash > /tmp/keyboard-startup-crash.txt
if grep -F "Process: $PACKAGE" /tmp/keyboard-startup-crash.txt; then
  cat /tmp/keyboard-startup-crash.txt
  exit 1
fi

# Same package and new versionCode, but signed with the exposed old key.
# This must fail even though the new APK carries a migration lineage.
if adb install -r old-key-update.apk > /tmp/old-key-install.txt 2>&1; then
  cat /tmp/old-key-install.txt
  echo 'ERROR: Android accepted an APK signed with the compromised old key' >&2
  exit 1
fi
cat /tmp/old-key-install.txt
grep -F INSTALL_FAILED_UPDATE_INCOMPATIBLE /tmp/old-key-install.txt
adb shell cat "$PREFS" | grep -F settings-retained
echo "PASS: API $(adb shell getprop ro.build.version.sdk | tr -d '\r'): settings retained, old-key update rejected"
