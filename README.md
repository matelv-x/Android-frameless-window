# Android Frameless Window

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="240" alt="Stargate WebView icon">
</p>

A native Android application that displays the Stargate/FAN113 interface in a full-screen, frameless `WebView`. The project is designed primarily for tablets used in landscape orientation.

## Key features

- Full-screen display without an app bar.
- Support for the DHD interface, audio, gestures, and screen saver behavior.
- Page scaling remains under the control of FAN113 without an additional Android WebView `scale-to-fit` pass.
- The Android blue touch-highlight effect is disabled for page controls.
- The app can run as a regular launcher app or as the tablet's Home app.
- A single universal APK supports tablets from different manufacturers.

## Requirements

- Android 6.0 or newer (`minSdk 23`).
- A network connection for content loaded by the WebView.
- Landscape orientation.

## Local build

```powershell
.\gradlew.bat clean lintRelease assembleRelease
```

The generated APK is located at:

```text
app/build/outputs/apk/release/app-release.apk
```

Without the signing environment variables, a local build uses the Android debug key. Official GitHub builds are signed with the permanent release key through GitHub Actions.



## Installation

Download the APK from **Releases**, copy it to the tablet, and allow Android to install apps from the file manager or browser being used. Future updates must always be signed with the same release key.
