package com.muratgoral.tiklatvideo;

import android.Manifest;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Araç ekranı. Android Auto'nun verdiği çizim yüzeyine sanal bir ekran açar,
 * üzerine WebView koyar. Araç hareket ederken görüntüyü perdeyle kapatır, ses devam eder.
 */
public final class VideoEkrani extends Screen implements SurfaceCallback {
    private static final String ETIKET = "TiklatVideo";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final HizTakip hiz;

    private VirtualDisplay vd;
    private int ew, eh, edpi;
    private Presentation sunum;
    private FrameLayout kok;
    private WebView web;
    private TextView perde;
    private View tamEkran;
    private WebChromeClient.CustomViewCallback tamEkranGeri;

    private String sonAdres;
    private boolean parkOnay;
    private boolean sonOnayGerekli;
    private boolean izinIstendi;
    private boolean bitti;

    VideoEkrani(@NonNull CarContext cc) {
        super(cc);
        sonAdres = new Ayarlar(cc).anaSayfa();
        hiz = new HizTakip(cc, this::durumDegisti);
        cc.getCarService(AppManager.class).setSurfaceCallback(this);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onCreate(@NonNull LifecycleOwner o) {
                hiz.baslat();
                izinIste();
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner o) {
                bitti = true;
                h.removeCallbacksAndMessages(null);
                hiz.durdur();
                sunumKapat();
                if (vd != null) {
                    vd.release();
                    vd = null;
                }
            }
        });
        // Başlangıçta perde metnini ilk saniyelerden sonra güncelle.
        h.postDelayed(this::durumDegisti, 8500);
    }

    // ---------------------------------------------------------------- şablon

    @NonNull
    @Override
    public Template onGetTemplate() {
        sonOnayGerekli = parkOnayGerekli();
        ActionStrip.Builder ust = new ActionStrip.Builder();
        ust.addAction(ikonEylem(R.drawable.ic_ev, this::anaSayfa));
        ust.addAction(ikonEylem(R.drawable.ic_yildiz,
                () -> getScreenManager().push(new YerImleriEkrani(getCarContext(), this))));
        if (sonOnayGerekli) {
            ust.addAction(new Action.Builder().setTitle("Parktayım").setOnClickListener(this::parkOnayla).build());
        } else {
            ust.addAction(ikonEylem(R.drawable.ic_ara,
                    () -> getScreenManager().push(new AramaEkrani(getCarContext(), this))));
        }
        ust.addAction(ikonEylem(R.drawable.ic_geri, this::webGeri));

        ActionStrip.Builder harita = new ActionStrip.Builder();
        harita.addAction(Action.PAN);
        harita.addAction(ikonEylem(R.drawable.ic_yukari, () -> kaydir(-1)));
        harita.addAction(ikonEylem(R.drawable.ic_asagi, () -> kaydir(1)));
        harita.addAction(ikonEylem(R.drawable.ic_yenile, this::yenile));

        return new NavigationTemplate.Builder()
                .setActionStrip(ust.build())
                .setMapActionStrip(harita.build())
                .build();
    }

    private Action ikonEylem(int res, OnClickListener l) {
        CarIcon ikon = new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), res)).build();
        return new Action.Builder().setIcon(ikon).setOnClickListener(l).build();
    }

    // ---------------------------------------------------------------- sürüş durumu

    private boolean gorunur() {
        int d = hiz.durum();
        return d == HizTakip.PARK || (d == HizTakip.BILINMIYOR && parkOnay);
    }

    private boolean parkOnayGerekli() {
        return hiz.durum() == HizTakip.BILINMIYOR && !parkOnay;
    }

    private void parkOnayla() {
        if (hiz.durum() == HizTakip.SURUS) return;
        parkOnay = true;
        durumDegisti();
    }

    private void durumDegisti() {
        if (bitti) return;
        if (hiz.durum() == HizTakip.SURUS) parkOnay = false;
        perdeGuncelle();
        if (parkOnayGerekli() != sonOnayGerekli) invalidate();
    }

    private void perdeGuncelle() {
        if (perde == null) return;
        if (gorunur()) {
            perde.setVisibility(View.GONE);
            return;
        }
        String metin;
        if (hiz.durum() == HizTakip.SURUS) {
            metin = "Sürüş modu\n\nAraç hareket halindeyken görüntü gizlenir, ses devam eder.\n"
                    + "Araç durunca görüntü kendiliğinden geri gelir.";
        } else if (hiz.veriBekleniyor()) {
            metin = "Araç bilgisi okunuyor…";
        } else {
            metin = "Hız bilgisi alınamadı\n\nAraç park halindeyse sağ üstteki \"Parktayım\" düğmesine dokunun.";
            if (!hiz.konumIzniVar()) metin += "\n\nTelefonda Tiklat Video'yu açıp \"İzinleri ver\"e dokunursanız bu otomatik olur.";
        }
        perde.setText(metin);
        perde.setVisibility(View.VISIBLE);
    }

    private void izinIste() {
        if (izinIstendi || hiz.konumIzniVar()) return;
        izinIstendi = true;
        List<String> izinler = new ArrayList<>(Arrays.asList(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION));
        try {
            getCarContext().requestPermissions(izinler, (verilen, reddedilen) -> {
                hiz.gpsBaslat();
                durumDegisti();
            });
        } catch (Throwable t) {
            Log.w(ETIKET, "izin istenemedi", t);
        }
    }

    // ---------------------------------------------------------------- yüzey

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
        Surface s = sc.getSurface();
        if (s == null) return;
        int w = sc.getWidth(), yk = sc.getHeight(), dpi = sc.getDpi();
        if (w <= 0 || yk <= 0) return;
        if (dpi <= 0) dpi = 160;
        try {
            if (vd == null) {
                DisplayManager dm = (DisplayManager) getCarContext().getSystemService(Context.DISPLAY_SERVICE);
                vd = dm.createVirtualDisplay("TiklatVideo", w, yk, dpi, s,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
                ew = w; eh = yk; edpi = dpi;
                sunumAc();
                return;
            }
            if (w != ew || yk != eh || dpi != edpi) {
                // Boyut değişince Presentation kendini kapatır; yeniden kur.
                sunumKapat();
                vd.resize(w, yk, dpi);
                ew = w; eh = yk; edpi = dpi;
                vd.setSurface(s);
                sunumAc();
            } else {
                vd.setSurface(s);
                if (sunum == null) sunumAc();
            }
        } catch (Throwable t) {
            Log.e(ETIKET, "yüzey kurulamadı", t);
        }
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
        // Sanal ekran yaşamaya devam etsin: ses kesilmez, yüzey gelince görüntü geri gelir.
        if (vd != null) vd.setSurface(null);
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect r) {}

    @Override
    public void onStableAreaChanged(@NonNull Rect r) {}

    @Override
    public void onClick(float x, float y) {
        if (sunum == null || !gorunur()) return;
        final long ilk = SystemClock.uptimeMillis();
        dokun(MotionEvent.ACTION_DOWN, ilk, ilk, x, y);
        h.postDelayed(() -> dokun(MotionEvent.ACTION_UP, ilk, SystemClock.uptimeMillis(), x, y), 60);
    }

    @Override
    public void onScroll(float dx, float dy) {
        if (web != null && tamEkran == null && gorunur()) web.scrollBy(Math.round(dx), Math.round(dy));
    }

    @Override
    public void onFling(float vx, float vy) {
        if (web != null && tamEkran == null && gorunur()) web.flingScroll(Math.round(-vx), Math.round(-vy));
    }

    @Override
    public void onScale(float fx, float fy, float f) {
        if (web != null && tamEkran == null && gorunur() && f > 0.01f && f < 100f) web.zoomBy(f);
    }

    private void dokun(int eylem, long ilk, long an, float x, float y) {
        if (sunum == null) return;
        MotionEvent e = MotionEvent.obtain(ilk, an, eylem, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            sunum.dispatchTouchEvent(e);
        } catch (Throwable ignored) {
        }
        e.recycle();
    }

    // ---------------------------------------------------------------- sanal ekrandaki içerik

    private void sunumAc() {
        if (vd == null) return;
        Context uyg = getCarContext().getApplicationContext();
        final Presentation p = new Presentation(uyg, vd.getDisplay(),
                android.R.style.Theme_Material_NoActionBar_Fullscreen);
        Context c = p.getContext();
        kok = new FrameLayout(c);
        kok.setBackgroundColor(Color.BLACK);
        perde = new TextView(c);
        perde.setBackgroundColor(Color.BLACK);
        perde.setTextColor(Color.WHITE);
        perde.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        perde.setGravity(Gravity.CENTER);
        int pad = Math.round(32 * c.getResources().getDisplayMetrics().density);
        perde.setPadding(pad, pad, pad, pad);
        perde.setClickable(true);
        kok.addView(perde, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webKur(c);
        p.setContentView(kok);
        p.setOnDismissListener(d -> {
            if (sunum == p) {
                webYokEt();
                sunum = null;
                kok = null;
                perde = null;
            }
        });
        sunum = p;
        try {
            p.show();
        } catch (Throwable t) {
            Log.e(ETIKET, "sunum açılamadı", t);
            webYokEt();
            sunum = null;
            kok = null;
            perde = null;
            return;
        }
        perdeGuncelle();
    }

    private void sunumKapat() {
        Presentation p = sunum;
        sunum = null;
        webYokEt();
        kok = null;
        perde = null;
        if (p != null) {
            try {
                p.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    private void webKur(Context c) {
        WebView w = new WebView(c);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setBackgroundColor(Color.BLACK);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sema = r.getUrl().getScheme();
                // intent://, market:// gibi uygulama açan adresleri araç ekranında yok say.
                return !("http".equalsIgnoreCase(sema) || "https".equalsIgnoreCase(sema));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (url != null && url.startsWith("http")) sonAdres = url;
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                h.post(VideoEkrani.this::webYenidenKur);
                return true;
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                tamEkranAc(v, cb);
            }

            @Override
            public void onHideCustomView() {
                tamEkranKaldir();
            }

            @Override
            public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                r.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String u, String m, JsResult r) {
                r.confirm();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String u, String m, String d, JsPromptResult r) {
                r.cancel();
                return true;
            }

            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
        web = w;
        // perdenin altına yerleştir
        kok.addView(w, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        w.loadUrl(sonAdres);
    }

    private void webYokEt() {
        tamEkran = null;
        tamEkranGeri = null;
        WebView w = web;
        web = null;
        if (w == null) return;
        try {
            if (kok != null) kok.removeAllViews();
            w.stopLoading();
            w.loadUrl("about:blank");
            w.destroy();
        } catch (Throwable ignored) {
        }
    }

    private void webYenidenKur() {
        if (sunum == null || kok == null) return;
        Context c = sunum.getContext();
        WebView w = web;
        web = null;
        tamEkran = null;
        tamEkranGeri = null;
        if (w != null) {
            kok.removeView(w);
            try {
                w.destroy();
            } catch (Throwable ignored) {
            }
        }
        webKur(c);
    }

    private void tamEkranAc(View v, WebChromeClient.CustomViewCallback cb) {
        if (kok == null) {
            cb.onCustomViewHidden();
            return;
        }
        tamEkranKaldir();
        tamEkran = v;
        tamEkranGeri = cb;
        v.setBackgroundColor(Color.BLACK);
        // WebView'in üstüne, perdenin altına
        kok.addView(v, kok.indexOfChild(perde), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void tamEkranKaldir() {
        if (tamEkran != null && kok != null) kok.removeView(tamEkran);
        tamEkran = null;
        tamEkranGeri = null;
    }

    // ---------------------------------------------------------------- düğmeler

    void yukle(String adres) {
        sonAdres = Ayarlar.duzelt(adres);
        if (tamEkranGeri != null) tamEkranGeri.onCustomViewHidden();
        tamEkranKaldir();
        if (web != null) web.loadUrl(sonAdres);
    }

    void ara(String sorgu) {
        yukle("https://m.youtube.com/results?search_query=" + Uri.encode(sorgu));
    }

    private void anaSayfa() {
        yukle(new Ayarlar(getCarContext()).anaSayfa());
    }

    private void webGeri() {
        if (tamEkran != null) {
            WebChromeClient.CustomViewCallback cb = tamEkranGeri;
            tamEkranKaldir();
            if (cb != null) cb.onCustomViewHidden();
            return;
        }
        if (web != null && web.canGoBack()) web.goBack();
    }

    private void yenile() {
        if (web != null) web.reload();
    }

    private void kaydir(int yon) {
        if (web == null || tamEkran != null || !gorunur()) return;
        web.evaluateJavascript("window.scrollBy({top:" + yon + "*Math.round(window.innerHeight*0.7),behavior:'smooth'})", null);
    }
}
