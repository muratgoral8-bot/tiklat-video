package com.muratgoral.tiklatvideo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Telefon uygulaması ile araç ekranının paylaştığı ayarlar. */
final class Ayarlar {
    static final String VARSAYILAN_ANA = "https://www.tiklat.com.tr/autoplay/";
    static final String VARSAYILAN_IMLER =
            "Tiklat Video | https://www.tiklat.com.tr/autoplay/\n"
            + "YouTube | https://m.youtube.com/\n"
            + "YouTube Kids | https://www.youtubekids.com/\n"
            + "YouTube Müzik | https://music.youtube.com/\n"
            + "Google | https://www.google.com/";

    private final SharedPreferences p;

    Ayarlar(Context c) {
        p = c.getSharedPreferences("ayar", Context.MODE_PRIVATE);
    }

    String anaSayfa() {
        return duzelt(p.getString("ana", VARSAYILAN_ANA));
    }

    String imlerMetni() {
        return p.getString("imler", VARSAYILAN_IMLER);
    }

    void kaydet(String ana, String imler) {
        p.edit().putString("ana", duzelt(ana)).putString("imler", imler).apply();
    }

    /** Her satır: "Ad | adres". Ad yoksa adresin alan adı kullanılır. */
    List<String[]> yerImleri() {
        List<String[]> l = new ArrayList<>();
        for (String satir : imlerMetni().split("\n")) {
            satir = satir.trim();
            if (satir.isEmpty()) continue;
            String ad, adres;
            int i = satir.indexOf('|');
            if (i >= 0) {
                ad = satir.substring(0, i).trim();
                adres = satir.substring(i + 1).trim();
            } else {
                ad = "";
                adres = satir;
            }
            if (adres.isEmpty()) continue;
            adres = duzelt(adres);
            if (ad.isEmpty()) {
                String h = Uri.parse(adres).getHost();
                ad = h != null ? h : adres;
            }
            l.add(new String[] {ad, adres});
        }
        return l;
    }

    static String duzelt(String u) {
        if (u == null) return VARSAYILAN_ANA;
        u = u.trim();
        if (u.isEmpty()) return VARSAYILAN_ANA;
        if (!u.toLowerCase().startsWith("http://") && !u.toLowerCase().startsWith("https://")) u = "https://" + u;
        return u;
    }
}
