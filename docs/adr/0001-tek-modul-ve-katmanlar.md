# ADR-0001: Tek app modülü, katman paketleri, use case yok

## Bağlam
Uygulamada iki ekran ve tek veri kaynağı var. Ekip küçük. İstenen şey, gereksiz modül,
use case ve generic base class üretmemek.

## Karar
- Tek `:app` modülü. Paketler: `domain` (saf Kotlin modelleri, saf fonksiyonlar ve repository
  arayüzü), `data` (remote, local, repository), `ui` (ekranlar ve tema), `location` ile
  `navigation` (platform entegrasyonları), `di`.
- `domain` paketinde Android, Room, Retrofit veya Google Maps tipi yok. Harita tarafında
  `LatLng` dönüşümü yalnızca `ui` katmanında yapılıyor.
- Use case sınıfı yok. ViewModel'ler repository'yi ve `domain` içindeki saf fonksiyonları
  (filtre, sıralama, mesafe) doğrudan kullanıyor.
- Ekran durumu her ekranda tek bir `UiState` ile modelleniyor. Kullanıcı eylemleri ViewModel
  metotlarına gidiyor.

## Sonuçlar
- Derleme süresi ve yapılandırma basit kalıyor. Modüllere bölme, ekran sayısı arttığında
  backlog'da yeniden değerlendirilecek.
- `domain` ile `data/remote/parse` paketleri Android'siz derlenebiliyor. Bu sayede
  geliştirme konteynerinde de JVM testleri çalışabiliyor ([ADR-0010](0010-test-ve-ci.md)).
