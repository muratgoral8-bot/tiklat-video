package com.muratgoral.tiklatvideo;

import android.app.Activity;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Telefondaki ayarlar: kurulum adımları, ana sayfa ve yer imleri. */
public final class AyarlarActivity extends Activity {
    private static final int ZEMIN = 0xFF0D2A4A, KART = 0xFF143A63, VURGU = 0xFFFFC107, KIRMIZI = 0xFFE53935;

    private EditText anaKutu, imKutu;
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
        ic.addView(yazi(BuildConfig.EKRAN ? "Koltuk arkası ekrana kurulu sürüm"
                : BuildConfig.ARKA ? "Arka koltuk ekranı için" : "Android Auto ekranında video", 16, VURGU, true));
        ic.addView(bosluk(18));

        if (BuildConfig.ARKA) {
            LinearLayout arka = kart(ic);
            arka.addView(yazi("Arka koltuk onayı", 18, Color.WHITE, true));
            arka.addView(bosluk(8));
            arka.addView(yazi(
                    "Bu uygulama ön koltuk başlığının arkasına takılı, yalnızca arkada oturanların gördüğü Android Auto "
                    + "ekranı içindir ve araç giderken de açık kalacak şekilde işaretlidir. Sürücünün görebileceği "
                    + "ön ekranda kullanmayın; sürüşte ekrana bakmak hem tehlikeli hem yasaktır.",
                    15, 0xFFDDE6F0, false));
            arka.addView(bosluk(10));
            CheckBox onay = new CheckBox(this);
            onay.setText("Ekran arka koltukta, sürücü göremiyor. Yalnızca arka koltuk için kullanacağım.");
            onay.setTextColor(Color.WHITE);
            onay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            onay.setButtonTintList(android.content.res.ColorStateList.valueOf(VURGU));
            onay.setChecked(ayar.arkaOnay());
            onay.setOnCheckedChangeListener((kutuDugme, secili) -> {
                ayar.arkaOnayKaydet(secili);
                Toast.makeText(this, secili ? "Onaylandı" : "Onay kaldırıldı. Araç ekranı kapalı kalır.", Toast.LENGTH_SHORT).show();
                if (secili) setResult(RESULT_OK);
            });
            arka.addView(onay);
        }

        LinearLayout kurulum = kart(ic);
        if (BuildConfig.EKRAN) kurulum.setVisibility(View.GONE);
        kurulum.addView(yazi("Kurulum (bir kez yapılır)", 18, Color.WHITE, true));
        kurulum.addView(bosluk(10));
        kurulum.addView(yazi(
                "1. Telefonun Ayarlar uygulamasında \"Android Auto\" diye arat ve aç.\n\n"
                + "2. En alttaki \"Sürüm\" yazısına art arda 10 kez dokun, çıkan uyarıya Tamam de. Geliştirici modu açılır.\n\n"
                + "3. Sağ üstteki üç noktaya dokun → Geliştirici ayarları → \"Bilinmeyen kaynaklar\" kutusunu işaretle.\n\n"
                + (BuildConfig.ARKA
                        ? "4. Yukarıdaki onay kutusunu işaretle.\n\n5. Telefonu arka ekrana bağla. Uygulama listesinde Tiklat Arka Koltuk'u aç."
                        : "4. Telefonu araca bağla. Araç park halindeyken uygulama listesinde Tiklat Video'yu aç."),
                15, 0xFFDDE6F0, false));

        LinearLayout guvenlik = kart(ic);
        if (BuildConfig.EKRAN) guvenlik.setVisibility(View.GONE);
        guvenlik.addView(yazi("Güvenlik", 18, Color.WHITE, true));
        guvenlik.addView(bosluk(8));
        guvenlik.addView(yazi(
                BuildConfig.ARKA
                        ? "Uygulama Android Auto'ya \"sürüşte açık kalabilir\" diye bildirilir. Android Auto buna uyarsa çizgi film araç "
                        + "giderken de devam eder. Yine kapatırsa ikinci yol Tiklat Arka Sürüş uygulamasıdır.\n\n"
                        + "YouTube uygulamasında bir videoda ya da kanalda Paylaş → Tiklat Arka Koltuk dersen yer imlerine eklenir."
                        : "Araç ekranında yalnızca park halindeyken açılır. Araç hareket edince Android Auto uygulamayı kendisi kapatır "
                        + "(Google'ın kuralı). Durunca yeniden açıp \"Kaldığın yerden\" bölümünden devam edebilirsin.\n\n"
                        + "YouTube uygulamasında bir videoda ya da kanalda Paylaş → Tiklat Video dersen yer imlerine eklenir.",
                15, 0xFFDDE6F0, false));

        LinearLayout ayarlar = kart(ic);
        ayarlar.addView(yazi("Ana sayfa adresi", 16, Color.WHITE, true));
        ayarlar.addView(bosluk(6));
        ayarlar.addView(yazi("Boş bırakırsan Tiklat başlangıç sayfası açılır (giriş gerekmez).", 13, 0xFFB8C7D9, false));
        ayarlar.addView(bosluk(6));
        anaKutu = kutu(false);
        anaKutu.setHint("Boş: başlangıç sayfası");
        anaKutu.setText(ayar.anaSayfaYazisi());
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
            anaKutu.setText(ayar.anaSayfaYazisi());
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });
        ayarlar.addView(kaydet);

        ic.addView(bosluk(18));
        TextView alt = yazi("www.muratgoral.com.tr", 13, 0xFF8FA6BF, false);
        alt.setGravity(Gravity.CENTER);
        ic.addView(alt);

        setContentView(kaydir);
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
