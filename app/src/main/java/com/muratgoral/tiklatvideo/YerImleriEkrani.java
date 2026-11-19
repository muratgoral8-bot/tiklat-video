package com.muratgoral.tiklatvideo;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import java.util.List;

/** Telefonda kaydedilen yer imlerinin araç ekranındaki listesi. */
final class YerImleriEkrani extends Screen {
    private final VideoEkrani ana;

    YerImleriEkrani(@NonNull CarContext cc, VideoEkrani ana) {
        super(cc);
        this.ana = ana;
    }

    @NonNull
    @Override
    @SuppressWarnings("deprecation")
    public Template onGetTemplate() {
        ItemList.Builder liste = new ItemList.Builder();
        List<String[]> imler = new Ayarlar(getCarContext()).yerImleri();
        for (String[] im : imler) {
            final String adres = im[1];
            liste.addItem(new Row.Builder()
                    .setTitle(im[0])
                    .addText(kisa(adres))
                    .setOnClickListener(() -> {
                        ana.yukle(adres);
                        getScreenManager().pop();
                    })
                    .build());
        }
        if (imler.isEmpty()) liste.setNoItemsMessage("Telefondaki Tiklat Video uygulamasından yer imi ekleyin.");
        return new ListTemplate.Builder()
                .setTitle("Yer imleri")
                .setHeaderAction(Action.BACK)
                .setSingleList(liste.build())
                .build();
    }

    private static String kisa(String adres) {
        String a = adres.replaceFirst("(?i)^https?://", "").replaceFirst("^www\\.", "");
        if (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        return a.length() > 48 ? a.substring(0, 47) + "…" : a;
    }
}
