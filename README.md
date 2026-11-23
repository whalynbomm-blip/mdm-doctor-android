# MDM Doctor (Android)

A local-first Android device diagnostics app. It reports basic Android-visible information and links users to app settings.

## Scope and limitations
- Read-only device model, Android version, and enabled accessibility-service checks.
- Opens Android App info for a package entered by the user.
- Does not remove, disable, evade, falsify, or interfere with financing/MDM controls, payment status, Knox enforcement, or remote lock commands.
- A normal app cannot reliably inspect every OEM/Knox/server-side policy. Absence from a permission screen is not proof that no management control exists.

## Build
Open this project in Android Studio and allow Gradle sync. Select Build > Build APK(s).

Requirements: Android Studio, Android SDK 35, JDK 17.
