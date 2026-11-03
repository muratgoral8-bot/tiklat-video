package com.muratgoral.tiklatauto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Sitenin JSON API'sini çağırır. Ayrı giriş yoktur: telefonda Tiklat Video ekranında açılan oturumun
 * çerezi (WebView) her sayfa yüklenişinde kaydedilir ve burada aynen kullanılır.
 */
final class Api {
    static final String PREFS = "cfg";
    static final String KEY_URL = "url";
    static final String KEY_COOKIE = "cookie";

    private Api() {}

    /** Oturum yok/düşmüş: kullanıcı telefonda uygulamayı açıp giriş yapmalı. */
    static final class NotLoggedIn extends IOException {
        NotLoggedIn() {
            super("Oturum yok");
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Sitenin kök adresi, her zaman "/" ile biter (ör. https://www.tiklat.com.tr/autoplay/). */
    static String base(Context c) {
        String u = prefs(c).getString(KEY_URL, MainActivity.DEFAULT_URL);
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        if (!u.endsWith("/")) u = u + "/";
        return u;
    }

    static void saveCookie(Context c, String cookie) {
        if (cookie == null || cookie.isEmpty()) return;
        prefs(c).edit().putString(KEY_COOKIE, cookie).apply();
    }

    /** @param rel "api/favorites" gibi göreli yol */
    static JSONObject get(Context c, String rel) throws Exception {
        String cookie = prefs(c).getString(KEY_COOKIE, "");
        if (cookie.isEmpty()) throw new NotLoggedIn();
        HttpURLConnection conn = (HttpURLConnection) new URL(base(c) + rel).openConnection();
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "MedyaAuto/1.0 (Android Auto medya)");
            conn.setRequestProperty("Cookie", cookie);
            int code = conn.getResponseCode();
            if (code == 401 || code == 403 || code == 302 || code == 404) throw new NotLoggedIn();
            if (code != 200) throw new IOException("Sunucu hatası (HTTP " + code + ")");
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 4 * 1024 * 1024) break;
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally {
            conn.disconnect();
        }
    }
}
