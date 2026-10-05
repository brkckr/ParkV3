# ADR-0010: Test stratejisi ve CI

## Karar
- **JVM unit testleri** (`app/src/test`): ayrıştırıcı, senkron politikası, filtre/sıralama,
  Türkçe arama, ViewModel'ler, navigasyon intent seçimi. Mock yerine fake'ler kullanılıyor.
  Ağ, MockWebServer üzerinden gerçek Retrofit/OkHttp ile test ediliyor.
- **Robolectric** (`app/src/test`): Room ile repository entegrasyonu (in-memory DB) ve ana
  akış için Compose UI testleri. Bu testler cihaz gerektirmediği için CI'da her PR'da
  çalışıyor.
- **Cihaz testleri** (`app/src/androidTest`): Room şema/migration doğrulaması ve ana akış
  smoke testi. GitHub Actions emülatör işinde ve yerel cihazda çalışıyor
  ([TESTING.md](../TESTING.md)).
- Testler uygulama kodunu kopyalamıyor. Beklenen değerler fixture'lardan ve açık
  kurallardan geliyor, aynı algoritma testte yeniden yazılmıyor.
- Tüm fixture'lar `app/src/test/resources/fixtures/` altında ve "SENTETİK" olarak
  işaretli. Canlı veri gibi sunulmuyor.
- CI işleri: `unit-test`, `lint`, `assemble-debug` (APK artefaktı), `instrumented`
  (emülatör) ve ayrı `api-probe` (canlı uç nokta sözleşme kontrolü). `api-probe` Android
  derlemesini engellemiyor: kaynak bu reponun kontrolünde değil. Sapmayı haftalık çalışmada
  issue olarak bildiriyor.

## Ortam notu
Geliştirme konteyneri Google Maven ve Android SDK'ya erişemediği için Android derlemesi
yalnızca CI'da doğrulanıyor. Konteynerde Android'siz paketler (`domain`,
`data/remote/parse`, `data/sync`) ayrı bir JVM düzeneğiyle test ediliyor. Bu düzenek
repoya eklenmiyor.
