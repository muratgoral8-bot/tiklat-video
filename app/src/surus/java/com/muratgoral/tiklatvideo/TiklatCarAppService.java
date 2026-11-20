package com.muratgoral.tiklatvideo;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/** Android Auto bu servise bağlanır ve araç ekranındaki oturumu açar. */
public final class TiklatCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Uygulama Play Store dışından yüklendiği için tüm Android Auto sürümlerine izin ver.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    @SuppressWarnings("deprecation")
    public Session onCreateSession() {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new VideoEkrani(getCarContext());
            }
        };
    }
}
