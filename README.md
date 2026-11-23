# MDM Doctor (Android)

A local-first Android device diagnostics app. It reports Android-visible device and management indicators and provides safe shortcuts to relevant Android settings.

## Current features
- Device manufacturer, model, Android version, API level, build display, and security patch level.
- Active device-admin package list returned by Android.
- Enabled accessibility services and notification-listener services reported by Android secure settings.
- Checks whether MDM Doctor itself is registered as device owner or profile owner.
- Clear limitation notice for Knox, finance-provider, and server-side policies that ordinary apps cannot conclusively inspect.
- Re-run diagnostics and see the last-check time.
- Copy a plain-text diagnostic report to the clipboard or share it using Android's chooser.
- Open Android device-admin, accessibility, usage-access, overlay-permission, and app-information settings.

## Privacy
- No diagnostic report is uploaded automatically.
- Copying or sharing a report happens only after the user taps the relevant control.
- The report is informational and may contain device details. Review it before sharing.

## Scope and limitations
- Read-only inspection. MDM Doctor does not change device-management settings.
- It does not remove, disable, evade, falsify, or interfere with financing/MDM controls, payment status, Knox enforcement, or remote lock commands.
- A normal app cannot reliably inspect every OEM/Knox/server-side policy. Absence from a permission screen is not proof that no management control exists.

## Build
Open this project in Android Studio and allow Gradle sync. Select Build > Build APK(s).

Requirements: Android Studio, Android SDK 35, JDK 17.

GitHub Actions also builds a debug APK on relevant pushes to `main`. Successful builds publish an artifact named `mdm-doctor-debug-apk` for 14 days.
