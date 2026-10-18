package my.mrdhameri.steambattleship;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final String ONLINE = "https://mohammaddhameri-blip.github.io/stem-battleship/";
    private static final String OFFLINE = "https://appassets.androidplatform.net/assets/game/index.html";
    private WebView gameView;
    private WebViewAssetLoader assetLoader;
    private boolean usingOffline = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        gameView = new WebView(this);
        gameView.setBackgroundColor(Color.rgb(6, 16, 28));
        gameView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        gameView.setVerticalScrollBarEnabled(true);
        gameView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false); // Respect width=device-width, avoid shrinking mobile controls.
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        gameView.setWebChromeClient(new WebChromeClient());
        gameView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("mohammaddhameri-blip.github.io".equalsIgnoreCase(host) ||
                    "appassets.androidplatform.net".equalsIgnoreCase(host)) {
                    return false;
                }
                if ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !usingOffline &&
                    ONLINE.equals(view.getUrl())) {
                    useOfflineCopy();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && !usingOffline && response.getStatusCode() >= 500) {
                    useOfflineCopy();
                }
            }
        });

        setContentView(gameView);
        gameView.loadUrl(ONLINE);
    }

    private void useOfflineCopy() {
        if (usingOffline || gameView == null) return;
        usingOffline = true;
        gameView.post(() -> gameView.loadUrl(OFFLINE));
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onBackPressed() {
        if (gameView == null) {
            super.onBackPressed();
            return;
        }
        // Use the game's own screens rather than unexpectedly leaving an active match.
        String js = "(function(){var visible=function(id){var e=document.getElementById(id);return e&&!e.classList.contains('hidden');};" +
            "var click=function(id){var e=document.getElementById(id);if(e)e.click();};" +
            "if(visible('end-overlay')){click('end-menu-btn');return true;}" +
            "if(visible('battle')){click('menu-btn');return true;}" +
            "if(visible('waiting')){click('cancel-wait');return true;}" +
            "if(visible('online-setup')){click('setup-back');return true;}" +
            "return false;})()";
        gameView.evaluateJavascript(js, result -> {
            if (!"true".equals(result)) {
                runOnUiThread(() -> {
                    if (gameView.canGoBack() && !usingOffline) gameView.goBack();
                    else finish();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
        if (gameView != null) gameView.onResume();
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) {
            gameView.stopLoading();
            gameView.destroy();
            gameView = null;
        }
        super.onDestroy();
    }
}
