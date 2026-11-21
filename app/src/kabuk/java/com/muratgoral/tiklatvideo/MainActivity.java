package com.muratgoral.tiklatvideo;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tek ekran: telefonda ve (Android Auto park uygulaması olarak) araç ekranında aynı WebView.
 * Başlangıç sayfası uygulamanın içindedir, giriş gerektirmez.
 * Arka koltuk sürümünde (BuildConfig.ARKA) telefonda "ekran arka koltukta" onayı verilmeden içerik açılmaz.
 */
public final class MainActivity extends Activity {
    private static final int ZEMIN = 0xFF0B1626;
    private static final int AYARLAR = 21;
    private static final String DURDUR_JS =
            "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();}catch(e){}});}catch(e){}})();";

    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout kok;
    private WebView web;
    private LinearLayout araclar;
    private TextView onayPerde;
    private boolean ayarAcildi;
    private View tamEkran;
    private WebChromeClient.CustomViewCallback tamEkranGeri;
    private Ayarlar ayar;
    private volatile boolean yerelSayfa;
    private volatile boolean aracta;

    @Override
    protected void onCreate(Bundle durum) {
        super.onCreate(durum);
        ayar = new Ayarlar(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(ZEMIN);
        getWindow().setNavigationBarColor(ZEMIN);

        kok = new FrameLayout(this);
        kok.setBackgroundColor(ZEMIN);
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 15 kenardan kenara çizim: içerik sistem çubuklarının altında kalmasın.
            kok.setOnApplyWindowInsetsListener((v, ins) -> {
                android.graphics.Insets b = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(b.left, b.top, b.right, b.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        web = new WebView(this);
        web.setBackgroundColor(ZEMIN);
        kok.addView(web, tam());
        araclarKur();
        onayPerdeKur();
        setContentView(kok);
        webKur();
        aracta = aractaMi();

        if (BuildConfig.EKRAN) tamEkranModu();
        if (onayEksik()) {
            onayIste();
            return;
        }
        if (!paylasimiIsle(getIntent())) {
            if (durum == null || web.restoreState(durum) == null) web.loadUrl(ayar.anaSayfa());
        }
    }

    // ---------------------------------------------------------------- arka ekran (doğrudan koltuk ekranına kurulu)

    /** Koltuk arkası ekranda durum ve gezinme çubuklarını gizle; kenardan kaydırınca geri gelir. */
    @SuppressWarnings("deprecation")
    private void tamEkranModu() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean odak) {
        super.onWindowFocusChanged(odak);
        if (odak && BuildConfig.EKRAN) tamEkranModu();
    }

    // ---------------------------------------------------------------- arka koltuk onayı

    private boolean onayEksik() {
        return BuildConfig.ARKA && !ayar.arkaOnay();
    }

    private void onayPerdeKur() {
        onayPerde = new TextView(this);
        onayPerde.setBackgroundColor(ZEMIN);
        onayPerde.setTextColor(Color.WHITE);
        onayPerde.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        onayPerde.setGravity(Gravity.CENTER);
        int p = dp(32);
        onayPerde.setPadding(p, p, p, p);
        onayPerde.setClickable(true);
        onayPerde.setText("Tiklat Arka Koltuk\n\nBu uygulama sürücünün göremediği arka koltuk ekranı içindir.\n"
                + "Telefonda Tiklat Arka Koltuk'u açıp onay kutusunu işaretleyin.");
        onayPerde.setVisibility(View.GONE);
        kok.addView(onayPerde, tam());
    }

    private void onayIste() {
        onayPerde.setVisibility(View.VISIBLE);
        if (!aracta && !ayarAcildi) {
            ayarAcildi = true;
            startActivityForResult(new Intent(this, AyarlarActivity.class), AYARLAR);
        }
    }

    private void onayDenetle() {
        if (!BuildConfig.ARKA) return;
        if (onayEksik()) {
            onayIste();
        } else if (onayPerde.getVisibility() == View.VISIBLE) {
            onayPerde.setVisibility(View.GONE);
            web.loadUrl(ayar.anaSayfa());
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        paylasimiIsle(i);
    }

    @Override
    protected void onStart() {
        super.onStart();
        web.onResume();
        ekranDegistiMi();
        onayDenetle();
    }

    @Override
    protected void onStop() {
        // Görünmez olunca (araç hareket edince Android Auto kapattığında da) videoyu durdur.
        tamEkranKapat();
        web.evaluateJavascript(DURDUR_JS, null);
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
        h.removeCallbacksAndMessages(null);
        if (web != null) {
            kok.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        ekranDegistiMi();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tamEkran != null) {
            tamEkranKapat();
        } else if (web.canGoBack()) {
            web.goBack();
        } else if (!yerelSayfa) {
            web.loadUrl(ayar.anaSayfa());
        } else {
            super.onBackPressed();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int kod, int sonuc, Intent veri) {
        super.onActivityResult(kod, sonuc, veri);
        if (kod == AYARLAR && sonuc == RESULT_OK && !onayEksik()) web.loadUrl(ayar.anaSayfa());
    }

    // ---------------------------------------------------------------- araç ekranı

    /** Android Auto etkinliği araç ekranında (telefonun ana ekranı dışında) açar. */
    private boolean aractaMi() {
        if (BuildConfig.EKRAN) return true; // koltuk ekranının kendisi: büyük klavye ve araç görünümü
        if (Build.VERSION.SDK_INT >= 30) {
            Display d = getDisplay();
            return d != null && d.getDisplayId() != Display.DEFAULT_DISPLAY;
        }
        return false;
    }

    private void ekranDegistiMi() {
        boolean simdi = aractaMi();
        if (simdi != aracta) {
            aracta = simdi;
            if (yerelSayfa) web.reload();
        }
    }

    // ---------------------------------------------------------------- WebView

    private void webKur() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Kopru(), "Tiklat");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sema = r.getUrl().getScheme();
                // intent://, market:// gibi başka uygulama açan adresler açılmaz; sayfa uygulamada kalır.
                return !("http".equalsIgnoreCase(sema) || "https".equalsIgnoreCase(sema));
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap ikon) {
                yerelSayfa = yerel(url);
                araclarGuncelle();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
                gecmiseYaz(url, v.getTitle());
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean yenileme) {
                // YouTube sayfayı yenilemeden video değiştirir; adres buradan yakalanır.
                yerelSayfa = yerel(url);
                araclarGuncelle();
                gecmiseYaz(url, null);
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                recreate();
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                if (tamEkran != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                tamEkran = v;
                tamEkranGeri = cb;
                v.setBackgroundColor(Color.BLACK);
                kok.addView(v, kok.indexOfChild(araclar), tam());
                web.setVisibility(View.GONE);
                araclarGuncelle();
            }

            @Override
            public void onHideCustomView() {
                tamEkranKapat();
            }

            @Override
            public void onReceivedTitle(WebView v, String baslik) {
                gecmiseYaz(v.getUrl(), baslik);
            }

            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
    }

    private static boolean yerel(String url) {
        return url != null && url.startsWith("file:///android_asset/");
    }

    private void gecmiseYaz(String adres, String baslik) {
        String id = Ayarlar.videoKimligi(adres);
        if (id != null) ayar.sonEkle(id, adres, baslik);
    }

    private void tamEkranKapat() {
        if (tamEkran == null) return;
        View v = tamEkran;
        WebChromeClient.CustomViewCallback cb = tamEkranGeri;
        tamEkran = null;
        tamEkranGeri = null;
        kok.removeView(v);
        web.setVisibility(View.VISIBLE);
        if (cb != null) cb.onCustomViewHidden();
        araclarGuncelle();
    }

    // ---------------------------------------------------------------- Ana Sayfa / Geri düğmeleri

    private void araclarKur() {
        araclar = new LinearLayout(this);
        araclar.setOrientation(LinearLayout.HORIZONTAL);
        araclar.addView(dugme(R.drawable.ic_ev, "Ana sayfa", v -> {
            tamEkranKapat();
            web.loadUrl(ayar.anaSayfa());
        }));
        araclar.addView(dugme(R.drawable.ic_geri, "Geri", v -> {
            if (tamEkran != null) tamEkranKapat();
            else if (web.canGoBack()) web.goBack();
            else web.loadUrl(ayar.anaSayfa());
        }));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        int m = dp(12);
        lp.setMargins(m, m, m, m);
        araclar.setVisibility(View.GONE);
        kok.addView(araclar, lp);
    }

    private ImageButton dugme(int ikon, String ad, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(ikon);
        b.setContentDescription(ad);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xCC14243A);
        g.setStroke(dp(1), 0x55FFFFFF);
        b.setBackground(g);
        int p = dp(12);
        b.setPadding(p, p, p, p);
        b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
        lp.setMarginEnd(dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    private void araclarGuncelle() {
        h.post(() -> {
            if (araclar != null) araclar.setVisibility(yerelSayfa || tamEkran != null ? View.GONE : View.VISIBLE);
        });
    }

    // ---------------------------------------------------------------- "Paylaş → Tiklat Video"

    private static final Pattern BAGLANTI = Pattern.compile("https?://\\S+");

    private boolean paylasimiIsle(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return false;
        String metin = i.getStringExtra(Intent.EXTRA_TEXT);
        String konu = i.getStringExtra(Intent.EXTRA_SUBJECT);
        i.setAction(Intent.ACTION_MAIN);
        if (metin == null) return false;
        Matcher m = BAGLANTI.matcher(metin);
        if (!m.find()) {
            Toast.makeText(this, "Paylaşılan metinde bağlantı yok", Toast.LENGTH_SHORT).show();
            return false;
        }
        String adres = m.group().replaceAll("[)\\],.]+$", "");
        String ad = konu;
        if (ad == null || ad.trim().isEmpty()) ad = metin.substring(0, m.start()).trim();
        if (ad.isEmpty()) ad = "YouTube";
        boolean yeni = ayar.imEkle(ad, adres);
        Toast.makeText(this, yeni ? "Yer imlerine eklendi" : "Zaten yer imlerinde", Toast.LENGTH_SHORT).show();
        web.loadUrl(ayar.anaSayfa());
        return true;
    }

    // ---------------------------------------------------------------- başlangıç sayfası köprüsü

    /** Yalnızca uygulamanın kendi sayfasına veri verir. */
    private final class Kopru {
        @JavascriptInterface
        public boolean arac() {
            return aracta;
        }

        @JavascriptInterface
        public String mod() {
            return BuildConfig.EKRAN ? "ekran" : BuildConfig.ARKA ? "arka" : "";
        }

        @JavascriptInterface
        public String sonlar() {
            return yerelSayfa ? ayar.sonlarJson() : "[]";
        }

        @JavascriptInterface
        public String imlerJson() {
            return yerelSayfa ? ayar.imlerJson() : "[]";
        }

        @JavascriptInterface
        public void ayarlar() {
            h.post(() -> {
                if (!yerelSayfa) return;
                try {
                    startActivityForResult(new Intent(MainActivity.this, AyarlarActivity.class), AYARLAR);
                } catch (ActivityNotFoundException ignored) {
                }
            });
        }
    }

    // ---------------------------------------------------------------- yardımcılar

    private static FrameLayout.LayoutParams tam() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
