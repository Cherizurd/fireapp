package io.elevenreader.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://elevenreader.io/";

    // Desktop Chrome UA: gives the full site layout on a TV and avoids "wv" WebView blocking on sign-in pages.
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private static final long CURSOR_HIDE_DELAY_MS = 6000;

    private FrameLayout root;
    private WebView mainWebView;
    private final List<WebView> popups = new ArrayList<>();
    private CursorView cursor;
    private boolean cursorMode = true;
    private long lastBackPress;
    private float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideCursor = new Runnable() {
        @Override
        public void run() {
            cursor.setVisibility(View.INVISIBLE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        density = getResources().getDisplayMetrics().density;

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        mainWebView = createWebView();
        root.addView(mainWebView, matchParent());

        cursor = new CursorView(this);
        root.addView(cursor, matchParent());
        setContentView(root);

        root.post(new Runnable() {
            @Override
            public void run() {
                cursor.setPosition(root.getWidth() / 2f, root.getHeight() / 2f);
                showCursor();
            }
        });

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(mainWebView, true);

        if (savedInstanceState != null) {
            mainWebView.restoreState(savedInstanceState);
        } else {
            mainWebView.loadUrl(HOME_URL);
        }
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.BLACK);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(USER_AGENT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setBuiltInZoomControls(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showErrorPage(view, String.valueOf(error.getDescription()));
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // Sign-in flows (Google/Apple) open popups; show them as a full-screen layer above the page.
                WebView popup = createWebView();
                popups.add(popup);
                root.addView(popup, root.indexOfChild(cursor), matchParent());
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                closePopup(window);
            }
        });
        return wv;
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            return false;
        }
        try {
            Intent intent = "intent".equals(scheme)
                    ? Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception ignored) {
            // No app on the Fire TV can handle this link; just stay on the page.
        }
        return true;
    }

    private void showErrorPage(WebView view, String description) {
        String html = "<html><body style='background:#111;color:#eee;font-family:sans-serif;"
                + "display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh'>"
                + "<h2>Can't reach ElevenReader</h2><p>" + description + "</p>"
                + "<p>Check the Fire TV's Wi-Fi, then press the center button to retry.</p>"
                + "<button style='font-size:24px;padding:12px 32px' onclick=\"location.href='" + HOME_URL + "'\">"
                + "Retry</button></body></html>";
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private void closePopup(WebView popup) {
        if (popups.remove(popup)) {
            root.removeView(popup);
            popup.destroy();
        }
    }

    private WebView activeWebView() {
        return popups.isEmpty() ? mainWebView : popups.get(popups.size() - 1);
    }

    // ---- Remote control handling ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                if (down && event.getRepeatCount() == 0) {
                    onBackPressedCompat();
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (down && event.getRepeatCount() == 0) {
                    cursorMode = !cursorMode;
                    if (cursorMode) {
                        showCursor();
                    } else {
                        cursor.setVisibility(View.INVISIBLE);
                    }
                    Toast.makeText(this, cursorMode
                            ? "Pointer mode (Menu button toggles)"
                            : "D-pad mode: arrows go to the page", Toast.LENGTH_SHORT).show();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && event.getRepeatCount() == 0) {
                    runMediaScript("m.paused ? m.play() : m.pause();");
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) {
                    runMediaScript("m.currentTime = Math.min(m.duration || 1e9, m.currentTime + 15);");
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) {
                    runMediaScript("m.currentTime = Math.max(0, m.currentTime - 15);");
                }
                return true;
            default:
                break;
        }

        if (!cursorMode) {
            return super.dispatchKeyEvent(event);
        }

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    moveCursor(code, event.getRepeatCount());
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && event.getRepeatCount() == 0) {
                    sendTouch(MotionEvent.ACTION_DOWN);
                } else if (!down) {
                    sendTouch(MotionEvent.ACTION_UP);
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void onBackPressedCompat() {
        if (!popups.isEmpty()) {
            WebView popup = activeWebView();
            if (popup.canGoBack()) {
                popup.goBack();
            } else {
                closePopup(popup);
            }
            return;
        }
        if (mainWebView.canGoBack()) {
            mainWebView.goBack();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastBackPress < 2000) {
            finish();
        } else {
            lastBackPress = now;
            Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    private void moveCursor(int code, int repeat) {
        // Accelerate while the button is held down.
        float step = Math.min(10 + repeat * 4, 60) * density;
        float dx = 0;
        float dy = 0;
        if (code == KeyEvent.KEYCODE_DPAD_UP) dy = -step;
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) dy = step;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) dx = -step;
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dx = step;

        float w = root.getWidth();
        float h = root.getHeight();
        float edge = 4 * density;
        float x = cursor.getCursorX() + dx;
        float y = cursor.getCursorY() + dy;

        // Pushing against an edge scrolls the content under the pointer instead.
        float scrollX = 0;
        float scrollY = 0;
        if (x < edge) { scrollX = x - edge; x = edge; }
        if (x > w - edge) { scrollX = x - (w - edge); x = w - edge; }
        if (y < edge) { scrollY = y - edge; y = edge; }
        if (y > h - edge) { scrollY = y - (h - edge); y = h - edge; }

        cursor.setPosition(x, y);
        showCursor();
        sendHover(x, y);
        if (scrollX != 0 || scrollY != 0) {
            scrollAtCursor(scrollX * 3, scrollY * 3);
        }
    }

    private void showCursor() {
        cursor.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideCursor);
        handler.postDelayed(hideCursor, CURSOR_HIDE_DELAY_MS);
    }

    private long touchDownTime;

    private void sendTouch(int action) {
        WebView wv = activeWebView();
        float x = cursor.getCursorX();
        float y = cursor.getCursorY();
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownTime = now;
            cursor.setClicking(true);
            showCursor();
            if (!wv.hasFocus()) {
                wv.requestFocus();
            }
        } else {
            cursor.setClicking(false);
        }
        MotionEvent ev = MotionEvent.obtain(touchDownTime, now, action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        wv.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private void sendHover(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        ev.setSource(InputDevice.SOURCE_MOUSE);
        activeWebView().dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    @SuppressWarnings("deprecation")
    private void scrollAtCursor(float dxPx, float dyPx) {
        WebView wv = activeWebView();
        float scale = wv.getScale();
        if (scale <= 0) scale = density;
        int cx = Math.round(cursor.getCursorX() / scale);
        int cy = Math.round(cursor.getCursorY() / scale);
        int dx = Math.round(dxPx / scale);
        int dy = Math.round(dyPx / scale);
        // Scroll the nearest scrollable element under the pointer (many apps scroll inner panels, not the page).
        String js = "(function(x,y,dx,dy){"
                + "var el=document.elementFromPoint(x,y);"
                + "while(el&&el!==document.body&&el!==document.documentElement){"
                + "var s=getComputedStyle(el);"
                + "var canY=dy&&el.scrollHeight>el.clientHeight&&/(auto|scroll|overlay)/.test(s.overflowY);"
                + "var canX=dx&&el.scrollWidth>el.clientWidth&&/(auto|scroll|overlay)/.test(s.overflowX);"
                + "if(canY||canX){el.scrollBy(dx,dy);return;}"
                + "el=el.parentElement;}"
                + "window.scrollBy(dx,dy);"
                + "})(" + cx + "," + cy + "," + dx + "," + dy + ");";
        wv.evaluateJavascript(js, null);
    }

    private void runMediaScript(String action) {
        String js = "(function(){var all=Array.prototype.slice.call(document.querySelectorAll('audio,video'));"
                + "if(!all.length)return;"
                + "var m=all.filter(function(e){return !e.paused;})[0]||all[0];"
                + action + "})();";
        activeWebView().evaluateJavascript(js, null);
    }

    // ---- Lifecycle ----

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mainWebView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // WebView is intentionally not paused so audiobooks keep playing behind the screensaver.
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (WebView popup : new ArrayList<>(popups)) {
            closePopup(popup);
        }
        mainWebView.destroy();
        super.onDestroy();
    }
}
