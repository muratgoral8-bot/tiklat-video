package com.muratgoral.tiklatvideo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/** Telefonda aynı sayfayı denemek için basit tarayıcı. */
public final class OnizlemeActivity extends Activity {
    private WebView web;
    private FrameLayout kok;
    private View tamEkran;
    private WebChromeClient.CustomViewCallback tamEkranGeri;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        kok = new FrameLayout(this);
        kok.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sema = r.getUrl().getScheme();
                return !("http".equalsIgnoreCase(sema) || "https".equalsIgnoreCase(sema));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                tamEkran = v;
                tamEkranGeri = cb;
                kok.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                if (tamEkran != null) kok.removeView(tamEkran);
                tamEkran = null;
                tamEkranGeri = null;
            }
        });
        kok.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(kok);
        String adres = getIntent().getStringExtra("adres");
        web.loadUrl(adres != null ? adres : new Ayarlar(this).anaSayfa());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tamEkran != null && tamEkranGeri != null) {
            tamEkranGeri.onCustomViewHidden();
            return;
        }
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
