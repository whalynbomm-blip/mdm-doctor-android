# MDM Doctor (Android)

A local-first Android device diagnostics app. It reports Android-visible device and management indicators and provides safe shortcuts to relevant Android settings.

## Current features
- Device manufacturer, model, Android version, API level, build display, and security patch level.
- Active device-admin package list returned by Android.
- Enabled accessibility services and notification-listener services reported by Android secure settings.
- Checks whether MDM Doctor itself is registered as device owner or profile owner.
- Reports Android's public device-management status signals where supported.
- Checks a limited, clearly documented set of user restrictions visible to the app.
- Separately reports whether Samsung KG Client is visible among active device administrators, without treating that as proof of an active finance lock.
- Dedicated PayJoy and financing-indicators summary combining visible PayJoy package information, Samsung KG Client visibility, current network state, and Android-visible restrictions.
- Explicitly separates device-side observations from private PayJoy server, payment-account, remote-command, and Knox Guard enforcement states that an ordinary app cannot verify.
- Clear limitation notice for Knox, finance-provider, and server-side policies that ordinary apps cannot conclusively inspect.
- Re-run diagnostics and see the last-check time.
- Copy a plain-text diagnostic report to the clipboard or share it using Android's chooser.
- Open Android device-admin, accessibility, usage-access, overlay-permission, and app-information settings.

## PayJoy and financing diagnostics
The PayJoy summary groups independently observable indicators. The app checks only the exact `com.payjoy` package name currently declared in its package-visibility query; a different package name or OEM integration may not be visible. Network connectivity does not prove that PayJoy servers were contacted or that a lock/unlock command was received. Account status and authoritative lock state must be confirmed with PayJoy or the finance provider.

## Diagnostic interpretation
- Android management signals and user restrictions are observations, not a complete inventory of all policies.
- Samsung KG Client visibility does not confirm Knox Guard enrollment, finance status, lock enforcement, or the identity of a managing organisation.
- A negative result does not prove that no OEM, Knox, finance-provider, or server-side restriction exists.

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
