package com.muratgoral.tiklatauto;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Siteyi (PWA) tam ekran WebView'de açar. Aynı etkinlik telefonda ve — Android 15+ ile — park halindeyken
 * Android Auto araç ekranında çalışır. Araç hareket edince Android Auto uygulamayı kapatır; ayrıca uygulama
 * görünmez olduğunda sayfadaki tüm videolar durdurulur.
 */
public class MainActivity extends Activity {
    static final String DEFAULT_URL = "https://www.tiklat.com.tr/autoplay/";
    private static final int REQ_LOCATION = 10;
    private static final int REQ_FILE = 11;
    private static final int BG = 0xFF0B0D12;

    private static final String JS_PAUSE_ALL =
            "(function(){try{"
            + "document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();}catch(e){}});"
            + "document.querySelectorAll('iframe').forEach(function(f){try{f.contentWindow.postMessage("
            + "'{\"event\":\"command\",\"func\":\"pauseVideo\",\"args\":[]}','*');}catch(e){}});"
            + "}catch(e){}})();";

    private FrameLayout root;
    private WebView web;
    private LinearLayout errorBox;
    private TextView errorText;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private ValueCallback<Uri[]> fileCallback;
    private String homeUrl;
    private String baseDomain;

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setHome(prefs().getString("url", DEFAULT_URL));

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildErrorBox();
        root.addView(errorBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (Build.VERSION.SDK_INT >= 30) {
            // Android 15 kenardan kenara çizim: içerik durum/gezinme çubuklarının altında kalmasın.
            root.setOnApplyWindowInsetsListener((v, ins) -> {
                android.graphics.Insets b = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(b.left, b.top, b.right, b.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(root);
        setupWebView();

        if (state == null || web.restoreState(state) == null) {
            web.loadUrl(homeUrl);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        web.onResume();
        markDisplay();
    }

    @Override
    protected void onStop() {
        // Uygulama görünmez olunca (ör. araç hareket edince Android Auto kapattığında) videoyu durdur.
        exitFullscreen();
        web.evaluateJavascript(JS_PAUSE_ALL, null);
        web.onPause();
        CookieManager.getInstance().flush();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            root.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        markDisplay();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_LOCATION && geoCallback != null) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, ok, false);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }

    // ------------------------------------------------------------------ WebView

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // IPTV yayınları sıkça http'dir; https sayfada oynayabilmeleri için (kişisel kullanım).
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setGeolocationEnabled(true);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " MedyaAuto/1.0");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, cd, mime, len) -> openExternal(Uri.parse(url)));
    }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
            if ((scheme.equals("http") || scheme.equals("https")) && sameSite(u.getHost())) {
                return false;
            }
            if (!r.isForMainFrame()) {
                return false;
            }
            openExternal(u);
            return true;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            markDisplay();
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame()) {
                showError("Sayfa açılamadı: " + e.getDescription());
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
            // WebView işlemi çökerse uygulamayı kapatmadan yeniden kur.
            recreate();
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback cb) {
            if (customView != null) {
                cb.onCustomViewHidden();
                return;
            }
            customView = view;
            customCallback = cb;
            root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
            if (hasLocation()) {
                cb.invoke(origin, true, false);
                return;
            }
            geoOrigin = origin;
            geoCallback = cb;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }
            fileCallback = cb;
            try {
                startActivityForResult(params.createIntent(), REQ_FILE);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ yardımcılar

    private SharedPreferences prefs() {
        return getSharedPreferences("cfg", MODE_PRIVATE);
    }

    private void setHome(String url) {
        homeUrl = url;
        String host = Uri.parse(url).getHost();
        host = host == null ? "" : host.toLowerCase();
        baseDomain = host.startsWith("www.") ? host.substring(4) : host;
    }

    private boolean sameSite(String host) {
        if (host == null || baseDomain.isEmpty()) return false;
        host = host.toLowerCase();
        return host.equals(baseDomain) || host.endsWith("." + baseDomain);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Bu bağlantı açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    /** Sayfaya araç ekranında mı açıldığını bildirir: <html class="aa-car">. */
    private void markDisplay() {
        boolean car = false;
        if (Build.VERSION.SDK_INT >= 30) {
            Display d = getDisplay();
            car = d != null && d.getDisplayId() != Display.DEFAULT_DISPLAY;
        }
        web.evaluateJavascript("(function(c){var e=document.documentElement;if(e){e.classList.toggle('aa-car',c);}"
                + "window.__aaCar=c;try{window.dispatchEvent(new CustomEvent('aacar',{detail:c}));}catch(x){}})(" + car + ");", null);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        View v = customView;
        WebChromeClient.CustomViewCallback cb = customCallback;
        customView = null;
        customCallback = null;
        root.removeView(v);
        web.setVisibility(View.VISIBLE);
        if (cb != null) cb.onCustomViewHidden();
    }

    private void buildErrorBox() {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setPadding(pad, pad, pad, pad);
        errorBox.setBackgroundColor(BG);
        errorBox.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("Bağlantı kurulamadı");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        errorBox.addView(title);

        errorText = new TextView(this);
        errorText.setTextColor(0xFFB6BCCD);
        errorText.setTextSize(16);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, pad / 2, 0, pad / 2);
        errorBox.addView(errorText);

        Button retry = new Button(this);
        retry.setText("Tekrar dene");
        retry.setOnClickListener(v -> {
            errorBox.setVisibility(View.GONE);
            String cur = web.getUrl();
            if (cur == null || cur.startsWith("data:") || cur.startsWith("about:")) web.loadUrl(homeUrl);
            else web.reload();
        });
        errorBox.addView(retry);

        Button change = new Button(this);
        change.setText("Site adresini değiştir");
        change.setOnClickListener(v -> askAddress());
        errorBox.addView(change);
    }

    private void showError(String msg) {
        errorText.setText(msg + "\n\nİnternet bağlantınızı kontrol edin.\nAdres: " + homeUrl);
        errorBox.setVisibility(View.VISIBLE);
    }

    private void askAddress() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(homeUrl);
        new AlertDialog.Builder(this)
                .setTitle("Site adresi")
                .setView(input)
                .setPositiveButton("Kaydet", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (u.isEmpty()) u = DEFAULT_URL;
                    if (!u.matches("(?i)^https?://.*")) u = "https://" + u;
                    prefs().edit().putString("url", u).apply();
                    setHome(u);
                    errorBox.setVisibility(View.GONE);
                    web.loadUrl(homeUrl);
                })
                .setNeutralButton("Varsayılan", (d, w) -> {
                    prefs().edit().remove("url").apply();
                    setHome(DEFAULT_URL);
                    errorBox.setVisibility(View.GONE);
                    web.loadUrl(homeUrl);
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }
}
