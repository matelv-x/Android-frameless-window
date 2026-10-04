STARGATE WEBVIEW ANDROID - CLEAN VERSION

This is a clean Java Android app project.
It intentionally has:
- no Kotlin
- no CMake
- no .cxx native build dependency
- no data binding
- no auto reload timer

Features:
- fullscreen immersive WebView
- first-start address entry
- accepts exactly what you type:
  192.168.1.200
  192.168.1.200/retro/dial.html
  stargate.local
  stargate.local/retro/dial.html
  example.com
  https://example.com/path
- automatically adds only http:// if missing
- saves address only after successful connection
- long press anywhere = change address
- swipe right = WebView back
- swipe left = WebView forward
- system Back button = WebView back, then exit
- Retry button reloads only when clicked after error

Build in Android Studio:
1. Extract this folder to C:\Android\StargateWebViewAndroid_CLEAN
2. Open Android Studio
3. File > Open > select the extracted folder
4. Wait for Gradle Sync
5. Build > Clean Project
6. Build > Rebuild Project
7. Build > Build APK(s)

Debug APK location:
app\build\outputs\apk\debug\app-debug.apk
