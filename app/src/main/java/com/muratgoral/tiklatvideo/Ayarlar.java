package com.muratgoral.tiklatvideo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Telefon uygulaması ile araç ekranının paylaştığı ayarlar. */
final class Ayarlar {
    /** Uygulamanın içindeki, giriş gerektirmeyen başlangıç sayfası. */
    static final String BASLANGIC = "file:///android_asset/baslangic.html";
    private static final String ESKI_VARSAYILAN = "https://www.tiklat.com.tr/autoplay/";
    static final String VARSAYILAN_IMLER =
            "Tiklat Video | https://www.tiklat.com.tr/autoplay/\n"
            + "YouTube | https://m.youtube.com/\n"
            + "YouTube Kids | https://www.youtubekids.com/\n"
            + "YouTube Müzik | https://music.youtube.com/\n"
            + "Google | https://www.google.com/";

    private final SharedPreferences p;

    Ayarlar(Context c) {
        p = c.getSharedPreferences("ayar", Context.MODE_PRIVATE);
        if (p.getInt("surum", 1) < 2) {
            // 1.0'da ana sayfa site adresiydi; 1.1'de varsayılan, uygulamanın kendi başlangıç sayfası.
            SharedPreferences.Editor e = p.edit().putInt("surum", 2);
            if (ESKI_VARSAYILAN.equals(p.getString("ana", ""))) e.remove("ana");
            e.apply();
        }
    }

    /** Kullanıcının yazdığı ana sayfa; boşsa başlangıç sayfası. */
    String anaSayfa() {
        String a = p.getString("ana", "");
        return a == null || a.trim().isEmpty() ? BASLANGIC : duzelt(a);
    }

    /** Telefondaki kutuda görünecek değer (başlangıç sayfası için boş). */
    String anaSayfaYazisi() {
        String a = p.getString("ana", "");
        return a == null ? "" : a;
    }

    String imlerMetni() {
        return p.getString("imler", VARSAYILAN_IMLER);
    }

    void kaydet(String ana, String imler) {
        String a = ana == null ? "" : ana.trim();
        p.edit().putString("ana", a.isEmpty() ? "" : duzelt(a)).putString("imler", imler).apply();
    }

    String imlerJson() {
        JSONArray d = new JSONArray();
        try {
            for (String[] im : yerImleri()) d.put(new JSONObject().put("ad", im[0]).put("adres", im[1]));
        } catch (Exception ignored) {
        }
        return d.toString();
    }

    // ------------------------------------------------ son izlenenler

    /** YouTube izleme adresinden video kimliği; değilse null. */
    static String videoKimligi(String adres) {
        if (adres == null) return null;
        try {
            Uri u = Uri.parse(adres);
            String h = u.getHost();
            if (h == null || !h.contains("youtube.com")) return null;
            String yol = u.getPath() == null ? "" : u.getPath();
            if (yol.startsWith("/watch")) return u.getQueryParameter("v");
            if (yol.startsWith("/shorts/")) {
                List<String> p = u.getPathSegments();
                return p.size() >= 2 ? p.get(1) : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    synchronized void sonEkle(String id, String adres, String baslik) {
        if (id == null || id.isEmpty()) return;
        if (baslik != null) {
            baslik = baslik.replaceFirst("\\s*-\\s*YouTube\\s*$", "").trim();
            if (baslik.isEmpty() || baslik.equalsIgnoreCase("YouTube") || baslik.startsWith("http")) baslik = null;
        }
        try {
            JSONArray eski = new JSONArray(p.getString("sonlar", "[]"));
            JSONArray yeni = new JSONArray();
            String eskiBaslik = null;
            for (int i = 0; i < eski.length(); i++) {
                JSONObject o = eski.getJSONObject(i);
                if (id.equals(o.optString("id"))) eskiBaslik = o.optString("baslik", null);
            }
            yeni.put(new JSONObject().put("id", id).put("adres", adres)
                    .put("baslik", baslik != null ? baslik : (eskiBaslik != null ? eskiBaslik : "")));
            for (int i = 0; i < eski.length() && yeni.length() < 12; i++) {
                JSONObject o = eski.getJSONObject(i);
                if (!id.equals(o.optString("id"))) yeni.put(o);
            }
            p.edit().putString("sonlar", yeni.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    synchronized String sonlarJson() {
        return p.getString("sonlar", "[]");
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
        if (u == null) return BASLANGIC;
        u = u.trim();
        if (u.isEmpty()) return BASLANGIC;
        if (u.startsWith("file:///android_asset/")) return u;
        if (!u.toLowerCase().startsWith("http://") && !u.toLowerCase().startsWith("https://")) u = "https://" + u;
        return u;
    }
}
