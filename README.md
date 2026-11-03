# Tiklat Video — Android Auto'da kendi video siten (kişisel kullanım)

`https://www.tiklat.com.tr/autoplay/` adresindeki Medya Merkezi sitesini (PWA) **telefonda** ve **Android Auto araç ekranında**
tam ekran açan ince bir Android uygulaması. Site aynen PWA olarak kalır; bu uygulama sadece onu araç ekranına taşır.

## Nasıl çalışır

- Android **15 ve üstü** telefonlarda Android Auto, "park halinde çalışan uygulama" (CAR_LAUNCHER) olarak işaretli
  uygulamaların ekranını araç ekranında gösterir. AA Browser da bu yöntemi kullanır.
- **Araç hareket edince Android Auto uygulamayı kendisi kapatır** (Google'ın resmî davranışı). Uygulama görünmez olunca
  sayfadaki videolar da durdurulur. Sitenin kendi "Araç modu" bu durumda GPS beklemez, çünkü güvenceyi Android Auto verir.
- Play Store dışı kurulduğu için Android Auto'da **Bilinmeyen kaynaklar** açık olmalıdır.
- Giriş bilgileri telefonda ve araçta ortaktır: telefonda bir kez giriş yapmanız yeterlidir.

## APK'yı telefondan derletme (bilgisayarsız, ücretsiz GitHub hesabı)

1. github.com → **New repository** → ad: `tiklat-video` → **Private** → **Create repository**.
2. **Add file → Create new file** → dosya adına tam olarak `.github/workflows/build.yml` yazın →
   `tiklat-build.yml` dosyasının içeriğini yapıştırın → **Commit changes**.
3. **Add file → Upload files** → `tiklat-video.zip` dosyasını **açmadan** yükleyin → **Commit changes**. Derleme başlar.
4. **Actions** sekmesi: "APK derle" 3–6 dk sürer. Yeşil tik olunca depo ana sayfasında **Releases** → `app-debug.apk` indirin.
   Kırmızı olursa "Derle" adımının son ~40 satırını kopyalayıp gönderin, düzeltilir.

## Kurulum ve ilk açılış

1. İndirilen `app-debug.apk`'yı açıp kurun ("Bu kaynaktan yüklemeye izin ver"; Play Protect uyarısında "Yine de yükle").
2. Telefonda **Tiklat Video**'yu açın, sitede kullanıcı adı/parola ile giriş yapın.
3. Android Auto ayarları → en alta inip **Sürüm**'e 10 kez dokunun → ⋮ → **Geliştirici ayarları** → **Bilinmeyen kaynaklar** ✔.
4. Telefonu araca bağlayın. Araç **park halindeyken** Android Auto uygulama listesinde **Tiklat Video**'ya dokunun.
5. Açılmazsa: önce Waze gibi Google Haritalar dışı bir navigasyon uygulamasını açıp sonra tekrar deneyin
   (AA Browser geliştiricisinin önerisi), Android Auto'yu güncelleyin, telefonu çıkarıp yeniden takın.

Site adresi değişirse: bağlantı hatası ekranındaki **Site adresini değiştir** düğmesi.

## Durum (dürüst)

- İlk sürüm (v1) `muratgoral8-bot/tiklat-video` deposunda GitHub Actions ile hatasız derlendi; imza ve manifest doğrulandı.
  **Bir telefonda ve Android Auto'da hiç denenmedi.**
- Android 14 ve altında uygulama telefonda çalışır ama araç ekranında açılmaz (Android Auto şartı).
- Sabit imza anahtarı (`app/tiklat.keystore`, parola `tiklat123`) projededir: yeni sürümler eskisinin üzerine kurulur.
  Depoyu **Private** tutun.
