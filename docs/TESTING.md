# Testler

## Türler ve çalıştırma

| Tür | Konum | Ne test ediyor | Komut |
|---|---|---|---|
| JVM unit | `app/src/test` | Ayrıştırıcı, senkron politikası, filtre/sıralama/Türkçe arama, alan kuralları, uzak kaynak (MockWebServer + gerçek Retrofit/OkHttp), ViewModel'ler (fake repository) | `./gradlew testDebugUnitTest` |
| Robolectric | `app/src/test` | Repository + in-memory Room, yol tarifi intent zinciri, `ConnectivityManager` ile bağlantı izleyicisi, Hilt ile uçtan uca Compose akışları, erişilebilirlik ve yerelleştirme | aynı komut |
| Cihaz | `app/src/androidTest` | Room şeması ve mevcut veritabanında favorilerin korunması, cihazda ana akış | `./gradlew connectedDebugAndroidTest` |
| Lint | — | Android lint (hata olursa derleme kırılır) | `./gradlew lintDebug` |
| Ekran görüntüsü | `app/src/test/.../ui/screenshots` | Ana liste (açık, koyu, Türkçe + büyük yazı), çevrimdışı boş durum, detay, tablet düzeni | Aşağıya bakın |
| Python | `scripts/test_api_contract.py` | API sözleşme kontrolü (ağsız, sentetik yanıtlarla) | `python3 -m unittest discover -s scripts` |

Ortak test altyapısı `app/src/sharedTest` altında: sahte İSPARK sunucusu (`TestServer`),
Hilt test modülleri, `FakeLocationProvider` ve **sentetik** fixture'lar. JVM ve cihaz
testleri bunları birlikte kullanıyor. Hiçbir test canlı API'ye gitmiyor. Canlı sözleşme ayrı
`api-probe` iş akışıyla ölçülüp sözleşmeyle karşılaştırılıyor
([API_CONTRACT.md](API_CONTRACT.md#sözleşme-kontrolü)).

### Ekran görüntüsü testleri

Robolectric'in yerel grafik modunda [Roborazzi](https://github.com/takahirom/roborazzi) ile
alınıyor. Saat ve saat dilimi sabit, böylece görüntüler çalışmadan çalışmaya değişmiyor.
Görüntüler repoya eklenmiyor:

- `main`'e her push'ta `Screenshot tests` iş akışı görüntüleri kaydediyor ve önbelleğe alıyor.
- PR'larda son `main` görüntüleriyle karşılaştırıyor. Fark varsa iş kırmızı oluyor ve
  farklar (`*_compare.png`) `screenshots` artefaktına yükleniyor. Zorunlu kontrol değil:
  bilinçli bir arayüz değişikliği birleştirmeyi engellemiyor.
- Normal unit test çalışmasında bu testler ekranları yalnızca çiziyor, görüntü almıyor.

Yerelde kaydetmek için:

```bash
./gradlew testDebugUnitTest --tests 'com.brkckr.parkv3.ui.screenshots.*' -Proborazzi.test.record=true
```

Görüntüler `app/build/outputs/roborazzi/` altına yazılıyor. Karşılaştırma için aynı komut
`-Proborazzi.test.verify=true` ile çalıştırılıyor.

### Cihaz testlerini çalıştırma

1. Bir emülatör başlatın veya USB hata ayıklaması açık bir cihaz bağlayın. Önerilen ortam:
   API 34+, Google APIs imajı. `adb devices` cihazı göstermeli.
2. `./gradlew connectedDebugAndroidTest` çalıştırın.
3. Rapor: `app/build/reports/androidTests/connected/index.html`.

Testler debug APK'sını kullanıyor. Sahte sunucu `localhost` üzerinde HTTP ile çalışıyor ve
test thread'inde başlatılıyor (`TestServer.reset()`, `@Before` içinde). Çevrimdışı durum
gerçek bir bağlantı kesilmesiyle değil, Hilt ile eklenen ve `IOException` fırlatan bir
OkHttp interceptor'ıyla (`FakeConnectivity.offline`) simüle ediliyor. Aynı anahtar
uygulamanın `NetworkMonitor`'üne de yansıyor, böylece bağlantının geri gelmesi de test
edilebiliyor.
Cleartext izni yalnızca debug derlemesinde ve yalnızca `localhost` / `127.0.0.1` için
açık (`app/src/debug/res/xml/network_security_config.xml`). Testler Hilt test runner'ı
(`com.brkckr.parkv3.HiltTestRunner`) ile çalışıyor.

CI'da aynı testler `Instrumented tests` iş akışında, API 34 x86_64 emülatörde koşuyor.

## İstenen senaryolar ve karşılayan testler

| # | Senaryo | Testler |
|---|---|---|
| 1 | Önbellek yokken ağ hatası, ardından başarılı yeniden deneme | `OfflineFirstParkRepositoryTest.without cache a network failure…`, `MainViewModelTest.without cache a network failure is an offline screen…`, `MainFlowTest.firstLaunchOffline_explainsAndRetryLoadsTheList` |
| 2 | Önbellek varken ağ hatası: içerik korunur, eski olduğu belirtilir | `OfflineFirstParkRepositoryTest.with cache a failed refresh keeps content…`, `MainViewModelTest.with cache a failed refresh keeps the items…`, `MainFlowTest.cachedListStaysWhenARefreshFails` |
| 3 | Yenileme sırasında değiştirilen favori kaybolmaz | `OfflineFirstParkRepositoryTest.favorites changed while a refresh is in flight are kept` |
| 4 | Detay ilk yükleme hatasından sonra yeniden deneme çalışır | `DetailViewModelTest.retry works after the first load fails`, `OfflineFirstParkRepositoryTest.detail first failure then retry succeeds`, `MainFlowTest.detailFirstLoadFailure_thenRetrySucceeds` |
| 5 | Eksik `isOpen` ile açıkça kapalı durum ayrılır | `ParkJsonParserTest.explicit isOpen 0 is closed while missing isOpen is unknown`, `…isOpen values other than explicit true or false are unknown`, `DetailViewModelTest.missing isOpen stays unknown…`, `DomainRulesTest` |
| 6 | HTTP hata mesajını göstermek uygulamayı çökertmez | `IsparkRemoteDataSourceTest.HTTP errors keep only the status code even with hostile bodies`, `MainFlowTest.httpErrorWithHostileBody_isShownAsSafeMessage` |
| 7 | Birleştirilmiş filtreler ve Türkçe arama | `ParkListQueryTest`, `MainViewModelTest.combined filters and Turkish search…`, `MainFlowTest.searchFiltersDetailAndFavorites…` |
| 8 | Konum izni olmadan ana akış tamamlanır | `MainViewModelTest.main flow works without location permission`, `MainFlowTest` (tüm akışlar izin ve harita anahtarı olmadan çalışıyor) |
| 9 | Google Maps yokken navigasyon güvenli alternatif sunar | `DirectionsTest` (geo: ve tarayıcı yedekleri, hiçbir uygulama yokken çökmeden `false`, Türkçe yerelde ondalık nokta) |
| 10 | Veritabanı güncellemesi favorileri korur | `OfflineFirstParkRepositoryTest.a complete response hides vanished parks and keeps their favorites…`, `…purged with their details but favorites remain`, cihazda `ParkDatabaseMigrationTest` |

Ek olarak: boş/bozuk yanıt önbelleği silmez, küçülme koruması, eşzamanlı yenilemelerin tek
isteğe birleşmesi, iptalin hata olarak kaydedilmemesi, bilinmeyen id için gelen sahte
kaydın saklanmaması, yaklaşık konum, konum zaman aşımı, kalıcı izin reddi, kapalı konum
servisi, Türkçe arayüz, 2x yazı ölçeğinde dokunma hedefleri, bağlantı geri gelince
yeniden deneme (`ReconnectionsTest`, `ListRefreshTriggersTest`,
`ConnectivityNetworkMonitorTest`, `DetailViewModelTest`,
`MainFlowTest.detailLoadsByItselfWhenTheConnectionComesBack`), alan poligonu: WKT sırası,
yer değiştirmeme ve otoparkın konumuna yakınlık (`AreaPolygonParserTest`), seçili alanın
haritaya gelmesi ve haritada seçimde eksik detayın bir kez indirilmesi (`MainViewModelTest`),
telefon ve tablet düzenleri (`ResultLayoutTest`, `AdaptiveLayoutTest`: Robolectric Google
haritası çizemediği için harita yuvasına yer tutucu konuyor), paylaşım metni ve sistem
paylaşım menüsü (`ShareTest`, Türkçe yerelde ondalık nokta dahil), dil seçimi
(`AppLanguageTest`, `LanguageMenuTest`), harita işaretlerinin ekran okuyucu metni
(`MarkerTextTest`, Türkçe dahil).

## İlkeler

- Testler uygulama kodunu kopyalamıyor. Beklenen değerler fixture'lardan ve açık
  kurallardan (ör. "`emptyCapacity > capacity` tutarsızdır") geliyor.
- Mock kütüphanesi kullanılmıyor. Fake'ler (`FakeParkRepository`, `FakeLocationProvider`)
  ve gerçek bileşenler (Room in-memory, MockWebServer) tercih ediliyor.
- Fixture'lar `SENTETİK` olarak işaretli
  (`app/src/sharedTest/resources/fixtures/README.md`) ve uygulamaya paketlenmiyor.

## Sonuçlar

Güncel sonuçlar PR'daki CI kontrollerinde görülebilir. Teslim anındaki özet PR
açıklamasında yer alıyor.
