package com.muratgoral.tiklatvideo;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

/** YouTube araması. Android Auto klavyeyi yalnızca araç dururken gösterir. */
final class AramaEkrani extends Screen {
    private final VideoEkrani ana;

    AramaEkrani(@NonNull CarContext cc, VideoEkrani ana) {
        super(cc);
        this.ana = ana;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return new SearchTemplate.Builder(new SearchTemplate.SearchCallback() {
            @Override
            public void onSearchTextChanged(@NonNull String metin) {}

            @Override
            public void onSearchSubmitted(@NonNull String metin) {
                String s = metin.trim();
                if (s.isEmpty()) return;
                ana.ara(s);
                getScreenManager().pop();
            }
        })
                .setHeaderAction(Action.BACK)
                .setSearchHint("YouTube'da ara")
                .setShowKeyboardByDefault(true)
                .setItemList(new ItemList.Builder().build())
                .build();
    }
}
