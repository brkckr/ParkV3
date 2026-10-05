# ADR-0013: Uygulama içi dil seçimi

## Bağlam
Arayüz Türkçe ve İngilizce. Android 13 ve üstünde sistem ayarlarında uygulama başına dil
seçilebiliyordu (`generateLocaleConfig`). Android 8–12'de (minSdk 26) bu ayar yok, dil
yalnızca cihaz diline bağlıydı.

## Karar
- Ana ekran başlığına bir dil düğmesi eklendi. Seçenekler: **Sistem dili**, **Türkçe**,
  **English**. Dil adları, arayüz hangi dilde olursa olsun kendi dillerinde yazılıyor.
- Seçim AndroidX AppCompat'in uygulama başına dil API'siyle uygulanıyor
  (`AppCompatDelegate.setApplicationLocales`):
  - Android 13+: seçim sistemin uygulama dili ayarına yazılıyor. Sistem ayarlarındaki seçim de
    uygulamada görünüyor, ikisi aynı ayar.
  - Android 8–12: AppCompat seçimi kendisi saklıyor (`AppLocalesMetadataHolderService`,
    `autoStoreLocales`) ve açılışta uyguluyor.
- Bunun için `MainActivity`, `AppCompatActivity`'den türüyor. Tema
  `Theme.AppCompat.DayNight.NoActionBar` tabanlı. Gece renkleri `values-night/colors.xml`'den
  geliyor. Arayüzün kendisi Compose ve Material 3 ile çiziliyor, AppCompat yalnızca dil ve
  pencere teması için kullanılıyor.
- Dil değişince etkinlik yeniden oluşturuluyor. Ekran durumu (`SavedStateHandle`: arama,
  filtreler, görünüm, seçim, hedef) korunuyor.
- Uygulamanın desteklemediği bir dil saklıysa "Sistem dili" seçili görünüyor.

## Sonuçlar
- Yeni bağımlılık: `androidx.appcompat:appcompat` 1.7.1.
- Dil seçimi yerel bir ayar. Ağ, hesap veya sunucu gerektirmiyor.
