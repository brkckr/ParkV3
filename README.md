# ParkV3

İstanbul'daki İSPARK otoparklarını keşfetmek ve karşılaştırmak için native Android
uygulaması (Kotlin, Jetpack Compose).

- Konumunuza veya haritada seçtiğiniz bir hedefe göre **kuş uçuşu** yakınlık sıralaması
- Harita (kümelenmiş) ve liste görünümü, ortak filtre ve seçim. Tablette ve geniş yatay
  ekranda (840 dp ve üstü) ikisi yan yana. Seçili otoparkın alanı, kaynakta varsa ve
  doğrulamadan geçerse haritada çiziliyor
- Otopark adı ve ilçe araması. Türkçe karakter ve büyük/küçük harf duyarsız
  (`kadikoy` → `KADIKÖY`)
- Birlikte çalışan filtreler: **Boş yeri olan**, **Favoriler**
- Detay: adres, çalışma saatleri, kapasite, kaynakta yayımlanan tarife, aylık abonelik
- Yerel favoriler. Ağ verisinden bağımsız saklanıyor, çevrimdışı da görünüyor
- Harici harita uygulamasında yol tarifi (Google Maps → diğer harita uygulamaları → tarayıcı)
- Otoparkı paylaşma: ad, adres ve harita bağlantısı (anahtarsız Google Maps web bağlantısı)
- Verinin tazeliği: son başarılı indirme ve kaynağın güncelleme zamanı ayrı gösteriliyor.
  Elle yenileme, ön plana gelişte eskime kontrolü ve bağlantı geri gelince otomatik
  yeniden deneme var
- Çevrimdışı: önceden indirilen liste ve detaylar
- Türkçe ve İngilizce arayüz, uygulama içinden dil seçimi (Sistem dili / Türkçe / English),
  açık/koyu tema, erişilebilir etiketler, büyük yazı desteği

Veri: [İBB Açık Veri Portalı](https://data.ibb.gov.tr/) (CC BY 4.0), `https://api.ibb.gov.tr/ispark/`.
Uygulama veriyi olduğu gibi gösteriyor ve eksik, tutarsız veya doğrulanmamış değerleri
açıkça işaretliyor ([veri sözleşmesi](docs/API_CONTRACT.md)).

## Gereksinimler

| Araç | Sürüm |
|---|---|
| JDK | 17+ (CI: Temurin 21) |
| Android Gradle Plugin | 9.4.0 (wrapper ile Gradle 9.6.0 otomatik iner) |
| Android SDK | Platform 37 (compileSdk), build-tools 36.0.0. Eksikse AGP lisans kabul edilmişse otomatik indirir |
| Android Studio | AGP 9.4'ü destekleyen bir sürüm |
| Cihaz | Android 8.0+ (minSdk 26). Harita için Google Play hizmetleri |

## Kurulum

```bash
git clone https://github.com/brkckr/ParkV3.git
cd ParkV3
cp secrets.properties.example secrets.properties   # anahtarınızı ekleyin (isteğe bağlı)
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Kurmak için: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
Her PR'da CI aynı APK'yı `parkv3-debug-apk` artefaktı olarak üretiyor.

Android Studio `local.properties` dosyasını (`sdk.dir`) kendisi oluşturur. Komut satırında
`ANDROID_HOME` ortam değişkenini ayarlayın.

### Google Maps API anahtarı

Anahtar **olmadan** da uygulama çalışır: liste, arama, filtreler, detay ve yol tarifi
kullanılabilir, harita görünümünün yerinde açıklayıcı bir ekran çıkar.

1. [Google Cloud Console](https://console.cloud.google.com/)'da bir proje oluşturun. Google
   bunun için faturalandırma hesabı istiyor. Güncel fiyatlandırmayı kendiniz kontrol edin.
   Bu repo hiçbir ücretli servisi etkinleştirmez.
2. **APIs & Services → Library → Maps SDK for Android** API'sini etkinleştirin.
3. **Credentials → Create credentials → API key** ile bir anahtar oluşturun.
4. Anahtarı kısıtlayın:
   - *Application restrictions*: Android apps → paket `com.brkckr.parkv3` + SHA-1. Debug
     SHA-1 için:
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
   - *API restrictions*: yalnızca Maps SDK for Android.
5. Anahtarı Git dışındaki `secrets.properties` dosyasına yazın:
   ```properties
   MAPS_API_KEY=AIza...
   ```
   Alternatif olarak `MAPS_API_KEY` ortam değişkenini kullanabilirsiniz. CI'da isteğe bağlı
   `MAPS_API_KEY` repo secret'ı okunur.

`secrets.properties`, `local.properties`, `*.jks` ve `keystore.properties` `.gitignore`
içinde. Repoya anahtar veya imzalama sırrı yazılmaz.

### Release derlemesi

`./gradlew assembleRelease` imzasız bir APK üretir. Kod küçültme (R8) açık, HTTP gövde logu
yok. İmzalamak için kendi keystore'unuzu yerelde yapılandırın. Mağazaya yükleme bu repo
kapsamında yapılmaz.

## Testler

```bash
./gradlew testDebugUnitTest          # JVM + Robolectric (ViewModel, repository, Room, Compose akışları)
./gradlew lintDebug                  # Android lint
./gradlew connectedDebugAndroidTest  # cihaz/emülatör gerektirir
```

Ayrıntılar, istenen 10 senaryonun hangi testlerle karşılandığı ve cihaz testlerinin nasıl
çalıştırılacağı için: [docs/TESTING.md](docs/TESTING.md).

GitHub Actions iş akışları:
- **Android CI**: unit testler, lint, debug APK (her PR'da)
- **Instrumented tests**: API 34 ve API 37 (Android 17) emülatörlerinde cihaz testleri
- **Screenshot tests**: ana ekranların görüntülerini kaydeder (main) ve PR'larda karşılaştırır
- **API contract probe**: canlı İSPARK uç noktalarını ölçer ve sözleşmeyle karşılaştırır.
  Haftalık ve elle çalışır, sapma bulursa issue açar

## Belgeler

- [Uygulama planı](docs/PLAN.md)
- [Mimari](docs/ARCHITECTURE.md)
- [Veri sözleşmesi](docs/API_CONTRACT.md)
- [Teknik karar kayıtları](docs/adr/README.md)
- [Testler](docs/TESTING.md)
- [İkinci sürüm backlog'u](docs/BACKLOG.md)

## Bilinen sınırlamalar

- Açık/kapalı bilgisi gösterilmiyor. Kaynağın `isOpen` alanı belgelenmemiş ve yayımlanan
  çalışma saatleriyle çelişiyor, bu yüzden okunmuyor
  ([ADR-0014](docs/adr/0014-isopen-kullanilmiyor.md)). "Boş yer var" yalnızca doluluk
  verisine dayanıyor. Çalışma saatleri detayda kaynaktaki metin olarak görünüyor.
- Ücret alanlarının birimi belgelenmemiş. Tarife metni olduğu gibi gösteriliyor, "saatlik"
  gibi etiketler eklenmiyor. `freeTime` birimsiz ham sayı olarak gösteriliyor.
- Alan yalnızca detayı indirilmiş otoparklar için çiziliyor. Haritada seçim, eksik detayı
  bir kez indiriyor. Doğrulamadan geçmeyen alan hiç çizilmiyor.
- Harita işaretleri ekran okuyucuya ad, durum ve doluluk bilgisini veriyor, ancak bunun
  TalkBack ile okunuşu gerçek cihazda denenmedi. Küme balonlarının etiketi yok. Tam
  erişilebilir alternatif liste görünümü.
- Adres araması yok (ücretli servis kararı gerekiyor). Hedef haritadan seçiliyor.
