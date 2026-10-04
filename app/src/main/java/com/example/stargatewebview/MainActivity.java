package com.example.stargatewebview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String TAG = "StargateWebView";
    private static final String WEB_CONSOLE_TAG = "StargateWebViewConsole";

    private static final String PREFS_NAME = "stargate_prefs";
    private static final String KEY_ADDRESS = "saved_address";
    private static final String KEY_SCREENSAVER_TIMEOUT_MINUTES = "screensaver_timeout_minutes";

    private static final int DEFAULT_SCREENSAVER_TIMEOUT_MINUTES = 10;
    private static final int MIN_SCREENSAVER_TIMEOUT_MINUTES = 1;
    private static final int MAX_SCREENSAVER_TIMEOUT_MINUTES = 120;

    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MIN_VELOCITY = 120;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final float SCREENSAVER_DIM_BRIGHTNESS = 0.01f;

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );

    private FrameLayout rootLayout;
    private WebView webView;
    private LinearLayout errorLayout;
    private View screensaverOverlay;
    private TextView errorText;
    private Button retryButton;
    private ProgressBar progressBar;
    private GestureDetector gestureDetector;
    private final Handler screensaverHandler = new Handler(Looper.getMainLooper());
    private final Runnable showScreensaverRunnable = this::showScreensaverOverlay;

    private View fullscreenView = null;
    private WebChromeClient.CustomViewCallback fullscreenCallback = null;
    private ValueCallback<Uri[]> filePathCallback = null;
    private int originalSystemUiVisibility;
    private boolean webFullscreenActive = false;

    private String pendingRawAddress = null;
    private String pendingUrl = null;
    private String lastUrl = null;
    private boolean mainFrameFailed = false;
    private boolean gateActive = false;
    private boolean gateStateKnown = false;
    private boolean activityResumed = false;
    private int screensaverTimeoutMinutes = -1;
    private Float savedWindowBrightness = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        hideSystemBars();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        progressBar = new ProgressBar(this);
        errorLayout = createErrorLayout();
        screensaverOverlay = createScreensaverOverlay();

        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        rootLayout.addView(progressBar, progressParams);

        rootLayout.addView(errorLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        rootLayout.addView(screensaverOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootLayout);

        setupWebView();
        setupGestures();
        setupRetryButton();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            progressBar.setVisibility(View.GONE);
        } else {
            String saved = getSavedAddress();
            if (saved == null) {
                showAddressDialog(true);
            } else {
                loadSavedAddress();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        applyScreenWakeState();
        resetScreensaverTimer();
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        cancelScreensaverTimer();
        hideScreensaverOverlay(false);
        applyScreenWakeState();
        super.onPause();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (screensaverOverlay != null && screensaverOverlay.getVisibility() == View.VISIBLE) {
                hideScreensaverOverlay(true);
                return true;
            }
            resetScreensaverTimer();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        if (screensaverOverlay != null && screensaverOverlay.getVisibility() == View.VISIBLE) {
            hideScreensaverOverlay(true);
        } else if (fullscreenView != null) {
            hideHtmlFullscreen();
        } else if (webFullscreenActive) {
            exitWebFullscreenMode();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            ValueCallback<Uri[]> callback = filePathCallback;
            filePathCallback = null;

            if (callback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        results = new Uri[]{uri};
                    }
                }
                callback.onReceiveValue(results);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void enterWebFullscreenMode() {
        webFullscreenActive = true;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }

        if (webView != null) {
            webView.setFitsSystemWindows(false);
            webView.requestLayout();
        }
    }

    private void exitWebFullscreenMode() {
        webFullscreenActive = false;

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemBars();

        if (webView != null) {
            webView.requestLayout();
            webView.evaluateJavascript(
                    "(function(){try{if(window.__androidFullscreenExit){window.__androidFullscreenExit();}}catch(e){}})();",
                    null
            );
        }
    }

    private class AndroidFullscreenBridge {
        @JavascriptInterface
        public void enterFullscreen() {
            runOnUiThread(() -> enterWebFullscreenMode());
        }

        @JavascriptInterface
        public void exitFullscreen() {
            runOnUiThread(() -> exitWebFullscreenMode());
        }
    }

    private class AndroidScreenWakeBridge {
        @JavascriptInterface
        public void setGateActive(boolean active, String source) {
            runOnUiThread(() -> setGateActiveState(active, true));
        }

        @JavascriptInterface
        public void setGateStatus(String status, String source) {
            runOnUiThread(() -> {
                String normalized = status == null ? "" : status.trim().toLowerCase(Locale.US);
                if ("active".equals(normalized)) {
                    setGateActiveState(true, true);
                } else if ("inactive".equals(normalized)) {
                    setGateActiveState(false, true);
                } else {
                    setGateActiveState(false, false);
                }
            });
        }
    }

    private void setGateActiveState(boolean active, boolean known) {
        boolean changed = gateActive != active || gateStateKnown != known;
        if (changed) {
            gateActive = active;
            gateStateKnown = known;
            Log.d(TAG, "Gate state updated: active=" + active + ", known=" + known);
            applyScreenWakeState();
        }

        if (gateActive) {
            Log.d(TAG, "Gate active, suppressing screensaver timer");
            hideScreensaverOverlay(false);
            cancelScreensaverTimer();
        } else {
            Log.d(TAG, "Gate inactive or unknown, scheduling screensaver timer");
            resetScreensaverTimer();
        }
    }

    private void applyScreenWakeState() {
        boolean keepAwake = activityResumed;

        if (rootLayout != null) {
            rootLayout.setKeepScreenOn(keepAwake);
        }
        if (webView != null) {
            webView.setKeepScreenOn(keepAwake);
        }

        if (keepAwake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private View createScreensaverOverlay() {
        View overlay = new View(this);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setOnClickListener(v -> hideScreensaverOverlay(true));
        return overlay;
    }

    private void resetScreensaverTimer() {
        cancelScreensaverTimer();
        if (!activityResumed || gateActive) return;
        long timeoutMs = getScreensaverTimeoutMs();
        Log.d(TAG, "Scheduling screensaver timer for " + timeoutMs + " ms");
        screensaverHandler.postDelayed(showScreensaverRunnable, timeoutMs);
    }

    private void cancelScreensaverTimer() {
        screensaverHandler.removeCallbacks(showScreensaverRunnable);
    }

    private void showScreensaverOverlay() {
        if (!activityResumed || gateActive || screensaverOverlay == null) return;
        Log.d(TAG, "Showing screensaver overlay");
        saveCurrentWindowBrightnessIfNeeded();
        setWindowBrightness(SCREENSAVER_DIM_BRIGHTNESS);
        screensaverOverlay.setVisibility(View.VISIBLE);
        screensaverOverlay.bringToFront();
        hideSystemBars();
    }

    private void hideScreensaverOverlay(boolean restartTimer) {
        if (screensaverOverlay != null) {
            screensaverOverlay.setVisibility(View.GONE);
        }
        Log.d(TAG, "Hiding screensaver overlay, restartTimer=" + restartTimer);
        restoreWindowBrightness();
        hideSystemBars();
        if (restartTimer) {
            resetScreensaverTimer();
        }
    }

    private void saveCurrentWindowBrightnessIfNeeded() {
        if (savedWindowBrightness != null) {
            return;
        }

        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        float current = attributes != null ? attributes.screenBrightness : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if (current >= 0f) {
            savedWindowBrightness = current;
            Log.d(TAG, "Saved window brightness=" + current);
        } else {
            savedWindowBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            Log.d(TAG, "Saved window brightness as system default");
        }
    }

    private void setWindowBrightness(float brightness) {
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        getWindow().setAttributes(attributes);
        Log.d(TAG, "Set window brightness=" + brightness);
    }

    private void restoreWindowBrightness() {
        if (savedWindowBrightness == null) {
            return;
        }

        float brightness = savedWindowBrightness;
        if (brightness < 0f) {
            brightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }

        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        getWindow().setAttributes(attributes);
        Log.d(TAG, brightness < 0f
                ? "Restored window brightness to system default"
                : "Restored window brightness=" + brightness);
        savedWindowBrightness = null;
    }

    private void injectFullscreenBridge(WebView view) {
        if (view == null) return;

        String js =
                "(function(){\n" +
                "  if (window.__stargateAndroidPatchInstalled) return;\n" +
                "  window.__stargateAndroidPatchInstalled = true;\n" +
                "\n" +
                "  function clearActive(){\n" +
                "    try { if (document.activeElement && document.activeElement.blur) document.activeElement.blur(); } catch(e) {}\n" +
                "    try {\n" +
                "      var btns = document.querySelectorAll('.a-fullscreen, .fullscreen, .ui-state-active, .ui-state-focus, .active');\n" +
                "      for (var i = 0; i < btns.length; i++) {\n" +
                "        try { btns[i].blur && btns[i].blur(); } catch(e) {}\n" +
                "        try { btns[i].classList.remove('ui-state-active','ui-state-focus','pressed','selected','focus'); } catch(e) {}\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "  }\n" +
                "\n" +
                "  function androidEnter(){\n" +
                "    try { AndroidFullscreen.enterFullscreen(); } catch(e) {}\n" +
                "    try { window.dispatchEvent(new Event('resize')); } catch(e) {}\n" +
                "    clearActive();\n" +
                "  }\n" +
                "\n" +
                "  function androidExit(){\n" +
                "    try { AndroidFullscreen.exitFullscreen(); } catch(e) {}\n" +
                "    try { window.dispatchEvent(new Event('resize')); } catch(e) {}\n" +
                "    clearActive();\n" +
                "  }\n" +
                "\n" +
                "  function syncAndroidWithFillScreen(){\n" +
                "    setTimeout(function(){\n" +
                "      var isOn = false;\n" +
                "      try { isOn = localStorage.getItem('FILL_SCREEN') === 'true'; } catch(e) {}\n" +
                "      if (isOn) androidEnter(); else androidExit();\n" +
                "      try { window.dispatchEvent(new Event('resize')); } catch(e) {}\n" +
                "    }, 80);\n" +
                "  }\n" +
                "\n" +
                "  function patchToggle(){\n" +
                "    if (typeof window.toggleFillScreen !== 'function') return false;\n" +
                "    if (window.toggleFillScreen.__androidWrapped) return true;\n" +
                "    var originalToggleFillScreen = window.toggleFillScreen;\n" +
                "    window.toggleFillScreen = function(){\n" +
                "      var result;\n" +
                "      try { result = originalToggleFillScreen.apply(this, arguments); }\n" +
                "      finally { syncAndroidWithFillScreen(); }\n" +
                "      return result;\n" +
                "    };\n" +
                "    window.toggleFillScreen.__androidWrapped = true;\n" +
                "    return true;\n" +
                "  }\n" +
                "\n" +
                "  if (!patchToggle()) {\n" +
                "    var tries = 0;\n" +
                "    var timer = setInterval(function(){\n" +
                "      tries++;\n" +
                "      if (patchToggle() || tries > 40) clearInterval(timer);\n" +
                "    }, 100);\n" +
                "  }\n" +
                "\n" +
                "  try {\n" +
                "    var current = localStorage.getItem('FILL_SCREEN') === 'true';\n" +
                "    if (current) androidEnter();\n" +
                "  } catch(e) {}\n" +
                "})();";

        view.evaluateJavascript(js, null);
    }

    private void injectScreenWakeBridge(WebView view) {
        if (view == null) return;

        String js =
                "(function(){\n" +
                "  if (window.__stargateAndroidWakeInstalled) return;\n" +
                "  window.__stargateAndroidWakeInstalled = true;\n" +
                "\n" +
                "  var activeWords = /\\b(INCOMING|DIALING|CONNECTED|WORMHOLE)\\b/i;\n" +
                "  var inactiveWords = /\\b(IDLE|OFFLINE|DISCONNECTED|CLOSED|STANDBY)\\b/i;\n" +
                "  var lastState = null;\n" +
                "\n" +
                "  function readStatusText(){\n" +
                "    var parts = [];\n" +
                "    try { parts.push(document.title || ''); } catch(e) {}\n" +
                "    try { parts.push(document.body ? (document.body.innerText || '') : ''); } catch(e) {}\n" +
                "    try {\n" +
                "      var nodes = document.querySelectorAll('[data-status],[data-state],[aria-label],.status,#status,.state,#state');\n" +
                "      for (var i = 0; i < nodes.length; i++) {\n" +
                "        var n = nodes[i];\n" +
                "        parts.push(n.getAttribute('data-status') || '');\n" +
                "        parts.push(n.getAttribute('data-state') || '');\n" +
                "        parts.push(n.getAttribute('aria-label') || '');\n" +
                "        parts.push(n.textContent || '');\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "    return parts.join('\\n').toUpperCase();\n" +
                "  }\n" +
                "\n" +
                "  function publish(state, source){\n" +
                "    if (state === lastState) return;\n" +
                "    lastState = state;\n" +
                "    try { AndroidScreenWake.setGateStatus(state, source || 'page-scan'); } catch(e) {}\n" +
                "  }\n" +
                "\n" +
                "  function scan(){\n" +
                "    var text = readStatusText();\n" +
                "    if (activeWords.test(text)) {\n" +
                "      publish('active', 'active-text');\n" +
                "      return;\n" +
                "    }\n" +
                "    if (inactiveWords.test(text)) {\n" +
                "      publish('inactive', 'inactive-text');\n" +
                "      return;\n" +
                "    }\n" +
                "    publish('unknown', 'no-status-text');\n" +
                "  }\n" +
                "\n" +
                "  try {\n" +
                "    window.StargateAndroidScreenWake = {\n" +
                "      setActive: function(active){ publish(active ? 'active' : 'inactive', 'page-api'); }\n" +
                "    };\n" +
                "  } catch(e) {}\n" +
                "\n" +
                "  scan();\n" +
                "  try { new MutationObserver(scan).observe(document.documentElement, {subtree:true, childList:true, characterData:true, attributes:true}); } catch(e) {}\n" +
                "  setInterval(scan, 2000);\n" +
                "  window.addEventListener('beforeunload', function(){ publish('unknown', 'beforeunload'); });\n" +
                "})();";

        view.evaluateJavascript(js, null);
    }

    private void injectTouchHighlightFix(WebView view) {
        if (view == null) return;

        String js = "(function(){try{"
                + "var id='stargate-android-touch-highlight-fix';"
                + "if(document.getElementById(id))return;"
                + "var style=document.createElement('style');"
                + "style.id=id;"
                + "style.textContent='*,*::before,*::after{-webkit-tap-highlight-color:transparent!important;}';"
                + "(document.head||document.documentElement).appendChild(style);"
                + "}catch(e){console.warn('Android touch highlight fix failed',e);}})();";

        view.evaluateJavascript(js, null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility", "AddJavascriptInterface"})
    private void setupWebView() {
        webView.setBackgroundColor(Color.BLACK);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.addJavascriptInterface(new AndroidFullscreenBridge(), "AndroidFullscreen");
        webView.addJavascriptInterface(new AndroidScreenWakeBridge(), "AndroidScreenWake");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // FAN113 already owns responsive scaling through its viewport and scene
        // layout. Android overview mode would add a second scale-to-fit pass.
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        Log.d(TAG, "WebView media settings: javaScriptEnabled=" + settings.getJavaScriptEnabled()
                + ", domStorageEnabled=" + settings.getDomStorageEnabled()
                + ", databaseEnabled=" + settings.getDatabaseEnabled()
                + ", mediaPlaybackRequiresUserGesture=" + settings.getMediaPlaybackRequiresUserGesture()
                + ", mixedContentMode=" + settings.getMixedContentMode());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameFailed = false;
                webFullscreenActive = false;
                setGateActiveState(false, false);
                showLoading();
                lastUrl = url;
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished: " + url);

                injectFullscreenBridge(view);
                injectScreenWakeBridge(view);
                injectTouchHighlightFix(view);
                progressBar.setVisibility(View.GONE);

                if (!mainFrameFailed) {
                    if (pendingRawAddress != null) {
                        saveAddress(pendingRawAddress);
                        pendingRawAddress = null;
                        pendingUrl = null;
                        Toast.makeText(MainActivity.this, "Address saved", Toast.LENGTH_SHORT).show();
                    }

                    lastUrl = url;
                    errorLayout.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                }

                hideSystemBars();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request != null && request.isForMainFrame()) {
                    mainFrameFailed = true;
                    pendingRawAddress = null;
                    pendingUrl = null;
                    showErrorScreen("Connection failed.");
                }
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    mainFrameFailed = true;
                    pendingRawAddress = null;
                    pendingUrl = null;
                    showErrorScreen("Connection failed.");
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.isForMainFrame()) {
                    int code = errorResponse != null ? errorResponse.getStatusCode() : 0;
                    if (code >= 400) {
                        mainFrameFailed = true;
                        pendingRawAddress = null;
                        pendingUrl = null;
                        showErrorScreen("HTTP error: " + code);
                    }
                }
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                mainFrameFailed = true;
                pendingRawAddress = null;
                pendingUrl = null;
                if (handler != null) {
                    handler.cancel();
                }
                showErrorScreen("SSL error.");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    Log.d(WEB_CONSOLE_TAG,
                            "level=" + consoleMessage.messageLevel()
                                    + ", sourceId=" + consoleMessage.sourceId()
                                    + ", lineNumber=" + consoleMessage.lineNumber()
                                    + ", message=" + consoleMessage.message());
                }
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    filePathCallback.onReceiveValue(null);
                    return false;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                enterWebFullscreenMode();

                fullscreenView = view;
                fullscreenCallback = callback;
                originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();

                rootLayout.addView(fullscreenView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

                webView.setVisibility(View.GONE);
                errorLayout.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);

                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenView == null) {
                    return;
                }

                rootLayout.removeView(fullscreenView);
                fullscreenView = null;

                if (fullscreenCallback != null) {
                    fullscreenCallback.onCustomViewHidden();
                    fullscreenCallback = null;
                }

                webView.setVisibility(View.VISIBLE);
                getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
                exitWebFullscreenMode();
            }
        });
    }

    private void setupRetryButton() {
        retryButton.setOnClickListener(v -> {
            if (pendingUrl != null) {
                loadPendingAddress();
                return;
            }

            String saved = getSavedAddress();
            if (saved != null) {
                loadAddress(saved, false);
            } else {
                showAddressDialog(true);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                showAddressDialog(false);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_MIN_DISTANCE
                        && Math.abs(velocityX) > SWIPE_MIN_VELOCITY) {

                    if (diffX > 0) {
                        goBackInWebView();
                    } else {
                        goForwardInWebView();
                    }
                    return true;
                }

                if (Math.abs(diffY) > Math.abs(diffX)
                        && diffY > SWIPE_MIN_DISTANCE
                        && Math.abs(velocityY) > SWIPE_MIN_VELOCITY) {

                    refreshWebView();
                    return true;
                }

                return false;
            }
        });

        View.OnTouchListener touchListener = (v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        };

        rootLayout.setOnTouchListener(touchListener);
        webView.setOnTouchListener(touchListener);
        errorLayout.setOnTouchListener(touchListener);
    }

    private void goBackInWebView() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            Toast.makeText(this, "No previous page", Toast.LENGTH_SHORT).show();
        }
    }

    private void goForwardInWebView() {
        if (webView.canGoForward()) {
            webView.goForward();
        } else {
            Toast.makeText(this, "No next page", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshWebView() {
        if (webView != null) {
            progressBar.setVisibility(View.VISIBLE);
            webView.reload();
            Toast.makeText(this, "Refreshing", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideHtmlFullscreen() {
        if (fullscreenView != null && fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
        } else if (fullscreenView != null) {
            rootLayout.removeView(fullscreenView);
            fullscreenView = null;
            webView.setVisibility(View.VISIBLE);
            exitWebFullscreenMode();
        } else {
            exitWebFullscreenMode();
        }
    }

    private void hideKeyboard(EditText input) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        input.clearFocus();
    }

    private void showAddressDialog(boolean firstStart) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint("IP, .local, or website URL");
        input.setText(getSavedAddress() != null ? getSavedAddress() : "");
        input.setSelectAllOnFocus(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);

        EditText timeoutInput = new EditText(this);
        timeoutInput.setSingleLine(true);
        timeoutInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        timeoutInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        timeoutInput.setHint("1-120");
        timeoutInput.setText(String.valueOf(getScreensaverTimeoutMinutes()));
        timeoutInput.setSelectAllOnFocus(true);
        timeoutInput.setFocusable(true);
        timeoutInput.setFocusableInTouchMode(true);

        ScrollView scrollView = new ScrollView(this);

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 40, 40, 40);

        TextView info = new TextView(this);
        info.setText(
                "Enter Stargate address\n\n" +
                        "Examples:\n" +
                        "xxx.xxx.xxx.xxx\n" +
                        "xxx.xxx.xxx.xxx/retro/dial.html\n" +
                        "stargate.local\n" +
                        "stargate.local/retro/dial.html\n" +
                        "example.com\n" +
                        "https://example.com/path"
        );
        info.setTextColor(Color.BLACK);
        info.setPadding(0, 0, 0, 30);

        TextView timeoutLabel = new TextView(this);
        timeoutLabel.setText("Screensaver timeout (minutes)");
        timeoutLabel.setTextColor(Color.BLACK);
        timeoutLabel.setPadding(0, 30, 0, 12);

        dialogLayout.addView(info);
        dialogLayout.addView(input);
        dialogLayout.addView(timeoutLabel);
        dialogLayout.addView(timeoutInput);
        scrollView.addView(dialogLayout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(firstStart ? "Enter Stargate settings" : "Change Stargate settings")
                .setView(scrollView)
                .setCancelable(!firstStart)
                .setPositiveButton("Connect", null)
                .setNegativeButton(firstStart ? "Exit" : "Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            positive.setOnClickListener(v -> {
                String raw = input.getText().toString().trim();
                String rawTimeout = timeoutInput.getText().toString().trim();

                if (startCandidateTest(raw, rawTimeout)) {
                    hideKeyboard(input);
                    hideKeyboard(timeoutInput);
                    dialog.dismiss();
                }
            });

            negative.setOnClickListener(v -> {
                hideKeyboard(input);
                hideKeyboard(timeoutInput);
                dialog.dismiss();
                if (firstStart && getSavedAddress() == null) {
                    finish();
                }
            });

            input.requestFocus();
            input.selectAll();

            input.postDelayed(() -> {
                input.requestFocus();
                input.selectAll();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 300);
        });

        dialog.setOnDismissListener(d -> hideSystemBars());

        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;

            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive != null) positive.performClick();
                return true;
            }
            return false;
        });

        timeoutInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;

            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive != null) positive.performClick();
                return true;
            }
            return false;
        });

        dialog.show();

        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            );
        }
    }

    private boolean startCandidateTest(String rawAddress, String rawTimeoutMinutes) {
        Candidate candidate = validateAndNormalize(rawAddress);
        if (candidate == null) return false;

        Integer timeoutMinutes = validateScreensaverTimeoutMinutes(rawTimeoutMinutes);
        if (timeoutMinutes == null) return false;

        saveScreensaverTimeoutMinutes(timeoutMinutes);
        pendingRawAddress = candidate.rawToSave;
        pendingUrl = candidate.urlToLoad;
        loadPendingAddress();
        return true;
    }

    private void loadPendingAddress() {
        if (pendingUrl == null) {
            showAddressDialog(getSavedAddress() == null);
            return;
        }
        lastUrl = pendingUrl;
        mainFrameFailed = false;
        showLoading();
        webView.loadUrl(pendingUrl);
    }

    private void loadSavedAddress() {
        String saved = getSavedAddress();
        if (saved == null) {
            showAddressDialog(true);
            return;
        }
        loadAddress(saved, false);
    }

    private void loadAddress(String rawAddress, boolean saveAfterSuccess) {
        Candidate candidate = validateAndNormalize(rawAddress);
        if (candidate == null) {
            showAddressDialog(getSavedAddress() == null);
            return;
        }

        if (saveAfterSuccess) {
            pendingRawAddress = candidate.rawToSave;
            pendingUrl = candidate.urlToLoad;
        } else {
            pendingRawAddress = null;
            pendingUrl = null;
        }

        lastUrl = candidate.urlToLoad;
        mainFrameFailed = false;
        showLoading();
        webView.loadUrl(candidate.urlToLoad);
    }

    private Candidate validateAndNormalize(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.length() == 0) {
            Toast.makeText(this, "Address cannot be empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (trimmed.matches(".*\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\.local\\b.*")) {
            Toast.makeText(this, "Invalid address. Do not add .local to an IP address.", Toast.LENGTH_LONG).show();
            return null;
        }

        String url = toLoadableUrl(trimmed);
        Uri uri = Uri.parse(url);
        String host = uri.getHost();

        if (host == null || host.trim().length() == 0) {
            Toast.makeText(this, "Invalid address", Toast.LENGTH_SHORT).show();
            return null;
        }

        String normalizedHost = host.toLowerCase(Locale.US);
        boolean isIpv4 = IPV4_PATTERN.matcher(normalizedHost).matches();
        boolean isLocalHost = normalizedHost.endsWith(".local") && isValidHostName(normalizedHost);
        boolean isWebsiteDomain = !isLocalHost && isValidHostName(normalizedHost);

        if (!isIpv4 && !isLocalHost && !isWebsiteDomain) {
            Toast.makeText(this, "Use IPv4, .local, or a valid website address", Toast.LENGTH_LONG).show();
            return null;
        }

        return new Candidate(trimmed, url);
    }

    private String toLoadableUrl(String raw) {
        String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return trimmed;
        }
        return "http://" + trimmed;
    }

    private boolean isValidHostName(String host) {
        final String asciiHost;
        try {
            asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
        } catch (IllegalArgumentException error) {
            return false;
        }

        if (asciiHost.length() == 0 || asciiHost.length() > 253 || !asciiHost.contains(".")) {
            return false;
        }

        String[] labels = asciiHost.split("\\.", -1);
        for (String label : labels) {
            if (label.length() == 0 || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")
                    || !label.matches("[a-z0-9-]+")) {
                return false;
            }
        }

        String topLevelDomain = labels[labels.length - 1];
        return "local".equals(topLevelDomain)
                || topLevelDomain.matches("[a-z]{2,63}")
                || topLevelDomain.matches("xn--[a-z0-9-]{2,59}");
    }

    private Integer validateScreensaverTimeoutMinutes(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.length() == 0) {
            Toast.makeText(this, "Screensaver timeout is required.", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            int minutes = Integer.parseInt(trimmed);
            if (minutes < MIN_SCREENSAVER_TIMEOUT_MINUTES || minutes > MAX_SCREENSAVER_TIMEOUT_MINUTES) {
                Toast.makeText(
                        this,
                        "Screensaver timeout must be between 1 and 120 minutes.",
                        Toast.LENGTH_LONG
                ).show();
                return null;
            }
            return minutes;
        } catch (NumberFormatException e) {
            Toast.makeText(
                    this,
                    "Screensaver timeout must be a whole number of minutes.",
                    Toast.LENGTH_LONG
            ).show();
            return null;
        }
    }

    private void showLoading() {
        errorLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        hideSystemBars();
    }

    private void showErrorScreen(String title) {
        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);

        String target = pendingRawAddress != null
                ? pendingRawAddress
                : (getSavedAddress() != null ? getSavedAddress() : "No saved address");

        errorText.setText(
                title + "\n\n" +
                        "Target:\n" + target + "\n\n" +
                        "Tap Retry to try again.\n\n" +
                        "Long press anywhere to change address."
        );

        hideSystemBars();
    }

    private LinearLayout createErrorLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(48, 48, 48, 48);
        layout.setBackgroundColor(Color.BLACK);
        layout.setVisibility(View.GONE);

        errorText = new TextView(this);
        errorText.setTextColor(Color.WHITE);
        errorText.setTextSize(18);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

        retryButton = new Button(this);
        retryButton.setText("Retry");

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.setMargins(0, 0, 0, 32);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        layout.addView(errorText, textParams);
        layout.addView(retryButton, buttonParams);
        return layout;
    }

    private void saveAddress(String rawAddress) {
        getPrefs().edit().putString(KEY_ADDRESS, rawAddress.trim()).apply();
    }

    private String getSavedAddress() {
        String value = getPrefs().getString(KEY_ADDRESS, null);
        if (value == null || value.trim().length() == 0) return null;
        return value.trim();
    }

    private void saveScreensaverTimeoutMinutes(int minutes) {
        screensaverTimeoutMinutes = minutes;
        getPrefs().edit().putInt(KEY_SCREENSAVER_TIMEOUT_MINUTES, minutes).apply();
        Log.d(TAG, "Saved screensaver timeout minutes=" + minutes);
        resetScreensaverTimer();
    }

    private int getScreensaverTimeoutMinutes() {
        if (screensaverTimeoutMinutes < MIN_SCREENSAVER_TIMEOUT_MINUTES
                || screensaverTimeoutMinutes > MAX_SCREENSAVER_TIMEOUT_MINUTES) {
            int stored = getPrefs().getInt(KEY_SCREENSAVER_TIMEOUT_MINUTES, DEFAULT_SCREENSAVER_TIMEOUT_MINUTES);
            if (stored < MIN_SCREENSAVER_TIMEOUT_MINUTES || stored > MAX_SCREENSAVER_TIMEOUT_MINUTES) {
                stored = DEFAULT_SCREENSAVER_TIMEOUT_MINUTES;
            }
            screensaverTimeoutMinutes = stored;
            Log.d(TAG, "Loaded screensaver timeout minutes=" + screensaverTimeoutMinutes);
        }
        return screensaverTimeoutMinutes;
    }

    private long getScreensaverTimeoutMs() {
        return getScreensaverTimeoutMinutes() * 60_000L;
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static class Candidate {
        final String rawToSave;
        final String urlToLoad;

        Candidate(String rawToSave, String urlToLoad) {
            this.rawToSave = rawToSave;
            this.urlToLoad = urlToLoad;
        }
    }
}
