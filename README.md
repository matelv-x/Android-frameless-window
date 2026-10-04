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
- Accepts IPv4 addresses, `.local` hostnames, website domains, and complete HTTP/HTTPS URLs.
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

## Release signing

The release keystore and all signing credentials are stored only as encrypted GitHub Actions Secrets. No private signing key or password is stored in the source code or Git history:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The `.github/workflows/release.yml` workflow restores the keystore only for the duration of the build and then builds a signed APK. When a `v*` tag such as `v1.9-final` is pushed, it also creates a GitHub Release and attaches the APK.

## Installation

Download the APK from **Releases**, copy it to the tablet, and allow Android to install apps from the file manager or browser being used. Future updates must always be signed with the same release key.
