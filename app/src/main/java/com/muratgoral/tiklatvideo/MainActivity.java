package com.muratgoral.tiklatvideo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Telefon ekranı: kurulum adımları, izinler, ana sayfa ve yer imleri. */
public final class MainActivity extends Activity {
    private static final int ZEMIN = 0xFF0D2A4A, KART = 0xFF143A63, VURGU = 0xFFFFC107, KIRMIZI = 0xFFE53935;

    private EditText anaKutu, imKutu;
    private TextView izinDurum;
    private Ayarlar ayar;
    private float yo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        yo = getResources().getDisplayMetrics().density;
        ayar = new Ayarlar(this);
        getWindow().setStatusBarColor(ZEMIN);
        getWindow().setNavigationBarColor(ZEMIN);

        ScrollView kaydir = new ScrollView(this);
        kaydir.setBackgroundColor(ZEMIN);
        kaydir.setFillViewport(true);
        LinearLayout ic = new LinearLayout(this);
        ic.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        ic.setPadding(p, dp(28), p, dp(40));
        kaydir.addView(ic, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView baslik = yazi("Tiklat Video", 30, Color.WHITE, true);
        ic.addView(baslik);
        ic.addView(bosluk(4));
        ic.addView(yazi("Android Auto ekranında video", 16, VURGU, true));
        ic.addView(bosluk(18));

        LinearLayout kurulum = kart(ic);
        kurulum.addView(yazi("Kurulum (bir kez yapılır)", 18, Color.WHITE, true));
        kurulum.addView(bosluk(10));
        kurulum.addView(yazi(
                "1. Telefonun Ayarlar uygulamasında \"Android Auto\" diye arat ve aç.\n\n"
                + "2. En alttaki \"Sürüm\" yazısına art arda 10 kez dokun, çıkan uyarıya Tamam de. Geliştirici modu açılır.\n\n"
                + "3. Sağ üstteki üç noktaya dokun → Geliştirici ayarları → \"Bilinmeyen kaynaklar\" kutusunu işaretle.\n\n"
                + "4. Aşağıdaki \"İzinleri ver\" düğmesine dokun.\n\n"
                + "5. Telefonu araca bağla. Araç ekranındaki uygulama listesinde Tiklat Video'yu aç.",
                15, 0xFFDDE6F0, false));
        kurulum.addView(bosluk(14));
        Button izin = dugme("İzinleri ver", VURGU, 0xFF1A1A1A);
        izin.setOnClickListener(v -> izinIste());
        kurulum.addView(izin);
        kurulum.addView(bosluk(8));
        izinDurum = yazi("", 14, Color.WHITE, false);
        kurulum.addView(izinDurum);

        LinearLayout guvenlik = kart(ic);
        guvenlik.addView(yazi("Güvenlik", 18, Color.WHITE, true));
        guvenlik.addView(bosluk(8));
        guvenlik.addView(yazi(
                "Araç hareket edince görüntü kapanır, ses devam eder. Araç durunca görüntü kendiliğinden geri gelir. "
                + "Bunun için konum izni gerekir. Hız bilgisi alınamazsa araç ekranında \"Parktayım\" düğmesi çıkar.",
                15, 0xFFDDE6F0, false));

        LinearLayout ayarlar = kart(ic);
        ayarlar.addView(yazi("Ana sayfa adresi", 16, Color.WHITE, true));
        ayarlar.addView(bosluk(6));
        anaKutu = kutu(false);
        anaKutu.setText(ayar.anaSayfa());
        ayarlar.addView(anaKutu);
        ayarlar.addView(bosluk(14));
        ayarlar.addView(yazi("Yer imleri", 16, Color.WHITE, true));
        ayarlar.addView(bosluk(2));
        ayarlar.addView(yazi("Her satıra bir tane: Ad | adres\nÖrnek: Kanalım | https://m.youtube.com/@kanaladi", 13, 0xFFB8C7D9, false));
        ayarlar.addView(bosluk(6));
        imKutu = kutu(true);
        imKutu.setText(ayar.imlerMetni());
        ayarlar.addView(imKutu);
        ayarlar.addView(bosluk(14));
        Button kaydet = dugme("Kaydet", VURGU, 0xFF1A1A1A);
        kaydet.setOnClickListener(v -> {
            ayar.kaydet(anaKutu.getText().toString(), imKutu.getText().toString());
            anaKutu.setText(ayar.anaSayfa());
            Toast.makeText(this, "Kaydedildi. Araç ekranında Yer imleri'nden açabilirsin.", Toast.LENGTH_LONG).show();
        });
        ayarlar.addView(kaydet);
        ayarlar.addView(bosluk(10));
        Button dene = dugme("Telefonda dene", KIRMIZI, Color.WHITE);
        dene.setOnClickListener(v -> {
            ayar.kaydet(anaKutu.getText().toString(), imKutu.getText().toString());
            startActivity(new Intent(this, OnizlemeActivity.class).putExtra("adres", ayar.anaSayfa()));
        });
        ayarlar.addView(dene);

        ic.addView(bosluk(18));
        TextView alt = yazi("www.muratgoral.com.tr", 13, 0xFF8FA6BF, false);
        alt.setGravity(Gravity.CENTER);
        ic.addView(alt);

        setContentView(kaydir);
    }

    @Override
    protected void onResume() {
        super.onResume();
        izinDurumGuncelle();
    }

    private void izinIste() {
        if (konumVar()) {
            Toast.makeText(this, "İzinler zaten verilmiş.", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "com.google.android.gms.permission.CAR_SPEED"}, 7);
    }

    @Override
    public void onRequestPermissionsResult(int kod, String[] izinler, int[] sonuc) {
        super.onRequestPermissionsResult(kod, izinler, sonuc);
        izinDurumGuncelle();
    }

    private boolean konumVar() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void izinDurumGuncelle() {
        if (izinDurum == null) return;
        if (konumVar()) {
            izinDurum.setText("Konum izni: verildi ✓");
            izinDurum.setTextColor(0xFF7CE38B);
        } else {
            izinDurum.setText("Konum izni: verilmedi. Hız algılanamaz, araçta \"Parktayım\" düğmesi kullanılır.");
            izinDurum.setTextColor(0xFFFFB4A8);
        }
    }

    // ---------------------------------------------------------------- görünüm yardımcıları

    private int dp(float v) {
        return Math.round(v * yo);
    }

    private TextView yazi(String s, float sp, int renk, boolean kalin) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(renk);
        t.setLineSpacing(0, 1.12f);
        if (kalin) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View bosluk(float yukseklik) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(yukseklik)));
        return v;
    }

    private LinearLayout kart(LinearLayout ust) {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        k.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(KART);
        g.setCornerRadius(dp(16));
        k.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        ust.addView(k, lp);
        return k;
    }

    private Button dugme(String s, int zemin, int yaziRengi) {
        Button d = new Button(this);
        d.setText(s);
        d.setAllCaps(false);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        d.setTypeface(Typeface.DEFAULT_BOLD);
        d.setTextColor(yaziRengi);
        GradientDrawable g = new GradientDrawable();
        g.setColor(zemin);
        g.setCornerRadius(dp(14));
        d.setBackground(g);
        d.setStateListAnimator(null);
        d.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return d;
    }

    private EditText kutu(boolean cokSatir) {
        EditText e = new EditText(this);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTextColor(0xFF1A1A1A);
        e.setHintTextColor(0xFF8A8A8A);
        int p = dp(12);
        e.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(10));
        e.setBackground(g);
        if (cokSatir) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
            e.setMinLines(6);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setHorizontallyScrolling(false);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            e.setSingleLine(true);
        }
        return e;
    }
}
