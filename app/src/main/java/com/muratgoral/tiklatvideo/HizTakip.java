package com.muratgoral.tiklatvideo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.hardware.CarHardwareManager;
import androidx.car.app.hardware.common.CarValue;
import androidx.car.app.hardware.common.OnCarDataAvailableListener;
import androidx.car.app.hardware.info.CarInfo;
import androidx.car.app.hardware.info.Speed;
import androidx.core.content.ContextCompat;

/**
 * Aracın hareket edip etmediğini izler.
 * Kaynaklar: Android Auto'nun verdiği araç hızı ve telefonun GPS hızı.
 * İkisinden biri hareket gösterirse sürüş kabul edilir.
 */
final class HizTakip {
    static final int BILINMIYOR = 0, PARK = 1, SURUS = 2;

    interface Dinleyici {
        void durumDegisti();
    }

    private static final float HAREKET_ESIK = 2.8f; // ≈10 km/s
    private static final float DURMA_ESIK = 1.0f;   // ≈3.6 km/s

    private final CarContext cc;
    private final Dinleyici dinleyici;
    private final Handler h = new Handler(Looper.getMainLooper());

    private float aracHiz = -1;
    private float gpsHiz = -1;
    private long gpsZaman;
    private long baslangic;
    private int hareketSayac, durmaSayac;
    private int durum = BILINMIYOR;

    private CarInfo carInfo;
    private OnCarDataAvailableListener<Speed> aracDinleyici;
    private LocationManager lm;
    private LocationListener ll;
    private boolean calisiyor;

    private final Runnable tik = new Runnable() {
        @Override
        public void run() {
            degerlendir();
            if (calisiyor) h.postDelayed(this, 1000);
        }
    };

    HizTakip(CarContext cc, Dinleyici d) {
        this.cc = cc;
        this.dinleyici = d;
    }

    int durum() {
        return durum;
    }

    /** İlk saniyelerde henüz hiçbir kaynaktan veri gelmediyse true. */
    boolean veriBekleniyor() {
        return durum == BILINMIYOR && aracHiz < 0 && gpsHiz < 0
                && SystemClock.elapsedRealtime() - baslangic < 8000;
    }

    boolean konumIzniVar() {
        return ContextCompat.checkSelfPermission(cc, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    void baslat() {
        if (calisiyor) return;
        calisiyor = true;
        baslangic = SystemClock.elapsedRealtime();
        try {
            CarHardwareManager hw = cc.getCarService(CarHardwareManager.class);
            carInfo = hw.getCarInfo();
            aracDinleyici = speed -> {
                Float v = deger(speed.getRawSpeedMetersPerSecond());
                if (v == null) v = deger(speed.getDisplaySpeedMetersPerSecond());
                if (v != null) aracHiz = Math.abs(v);
            };
            carInfo.addSpeedListener(ContextCompat.getMainExecutor(cc), aracDinleyici);
        } catch (Throwable t) {
            carInfo = null;
            aracDinleyici = null;
        }
        gpsBaslat();
        h.post(tik);
    }

    private static Float deger(CarValue<Float> c) {
        if (c == null || c.getStatus() != CarValue.STATUS_SUCCESS) return null;
        return c.getValue();
    }

    void gpsBaslat() {
        if (lm != null || !konumIzniVar()) return;
        try {
            lm = (LocationManager) cc.getSystemService(Context.LOCATION_SERVICE);
            ll = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location l) {
                    if (l.hasSpeed()) {
                        gpsHiz = l.getSpeed();
                        gpsZaman = SystemClock.elapsedRealtime();
                    }
                }

                @Override
                public void onProviderEnabled(@NonNull String p) {}

                @Override
                public void onProviderDisabled(@NonNull String p) {}

                @Override
                @SuppressWarnings("deprecation")
                public void onStatusChanged(String p, int s, Bundle b) {}
            };
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ll, Looper.getMainLooper());
        } catch (Throwable t) {
            lm = null;
            ll = null;
        }
    }

    private void degerlendir() {
        long an = SystemClock.elapsedRealtime();
        boolean gpsTaze = gpsHiz >= 0 && an - gpsZaman < 6000;
        float v = -1;
        // Araç verisi yalnızca değişince gelir, son değer geçerlidir. GPS her saniye gelir.
        if (aracHiz >= 0) v = aracHiz;
        if (gpsTaze) v = Math.max(v, gpsHiz);
        int onceki = durum;
        if (v < 0) {
            // Veri kesildi: sürüşteysek sürüşte kal (güvenli taraf).
            if (durum == PARK && aracHiz < 0 && gpsHiz >= 0 && an - gpsZaman > 20000) durum = BILINMIYOR;
        } else if (v > HAREKET_ESIK) {
            durmaSayac = 0;
            if (++hareketSayac >= 2) durum = SURUS;
        } else if (v < DURMA_ESIK) {
            hareketSayac = 0;
            if (++durmaSayac >= 3) durum = PARK;
        }
        if (durum != onceki) dinleyici.durumDegisti();
    }

    void durdur() {
        calisiyor = false;
        h.removeCallbacks(tik);
        try {
            if (carInfo != null && aracDinleyici != null) carInfo.removeSpeedListener(aracDinleyici);
        } catch (Throwable ignored) {
        }
        try {
            if (lm != null && ll != null) lm.removeUpdates(ll);
        } catch (Throwable ignored) {
        }
        carInfo = null;
        aracDinleyici = null;
        lm = null;
        ll = null;
    }
}
