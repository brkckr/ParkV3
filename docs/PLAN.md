# Uygulama Planı — ParkV3

## Amaç

Kullanıcı, mevcut konumuna veya haritada seçtiği bir hedefe yakın İSPARK otoparklarını
karşılaştırabilir. Seçim yaparken verinin ne kadar güncel olduğunu görür ve yol tarifini
harici bir harita uygulamasında açar.

## Referans proje incelemesi (brkckr/ParkV2)

Referans projeden yalnızca işlevleri ve veri kaynağını öğrenmek için yararlanıldı. Kod ve
mimari kopyalanmadı. ParkV2'de tespit edilen ve ParkV3'te bilinçli olarak farklı ele alınan
konular:

| Konu | ParkV2 davranışı | ParkV3 kararı |
|---|---|---|
| Eksik alanlar | DTO varsayılanları `0` / `""`. Eksik değer ile 0 ayırt edilemiyor | Alanlar nullable. Eksik, geçersiz ve 0 ayrı modelleniyor ([ADR-0003](adr/0003-toleransli-ayristirma.md)) |
| `isOpen` | `isOpen == 0` olduğunda önbellekteki değere düşüyor | `1/true` → açık, `0/false` → kapalı, diğer durumlar → **bilinmiyor** |
| Ücret | `fee` "Saatlik ücret" olarak etiketleniyor | Anlamı doğrulanmadığı için yalnızca "Ücret (kaynak değeri)" deniyor |
| Poligon | Enlem/boylam sırası sayısal büyüklüğe göre tahmin ediliyor | v1'de poligon çizilmiyor, ham metin saklanıyor ([ADR-0003](adr/0003-toleransli-ayristirma.md)) |
| Favoriler | Park tablosunda kolon olarak tutuluyor | Ayrı `favorites` tablosu; yenileme bu tabloya hiç dokunmuyor |
| Filtre | Tek filtre seçilebiliyor | Açık + boş yer + favori filtreleri birlikte kullanılabiliyor |
| Ağ logu | `BODY` seviyesi her derlemede açık | Gövde logu yalnızca debug derlemede açık |
| Hata metni | Sunucu gövdesi format argümanı olarak UI'a taşınıyor | Tipli hata modeli. UI yalnızca yerelleştirilmiş metin ve HTTP kodunu gösteriyor |

## Ortam kısıtları (bu geliştirme oturumu)

- Bulut konteynerinin ağ politikası `api.ibb.gov.tr`, `data.ibb.gov.tr` ve `dl.google.com`
  adreslerini engelliyor. Bu nedenle canlı API burada doğrulanamıyor, Android SDK ve Google
  Maven dosyaları da indirilemiyor.
- Bu yüzden derleme, test, lint ve canlı API sözleşme kontrolü **GitHub Actions** üzerinde
  çalışıyor. Konteynerde yalnızca Android'den bağımsız saf Kotlin mantığı (ayrıştırma,
  senkron politikası, filtre/sıralama) JVM üzerinde test ediliyor.
- KVM olmadığı için emülatör konteynerde çalışmıyor. Cihaz testleri GitHub Actions emülatör
  işinde ve [TESTING.md](TESTING.md) adımlarıyla yerel cihazda çalıştırılabiliyor.

## Aşamalar

Her aşama kendi içinde derlenebilir ve çalışan bir akış teslim eder.

| # | Aşama | Çalışan akış | Doğrulama |
|---|---|---|---|
| 0 | İskelet, belgeler, CI, API probu | Uygulama açılır, boş ana ekranı gösterir | CI derlemesi; probe raporu |
| 1 | Veri katmanı | Liste API'den alınıp Room'a yazılır, yenileme durumu ayrı izlenir | Ayrıştırıcı, senkron politikası, repository ve Room testleri |
| 2 | Liste, arama, filtre, favori, tazelik | Liste görünümünde arama, birleşik filtreler, favoriler, son güncelleme bilgisi, boş durum ekranları | ViewModel ve Compose UI testleri |
| 3 | Detay ve navigasyon | Detay ekranı (önbellekli), tekrar deneme, harici yol tarifi | Detay VM testleri, intent zinciri testleri |
| 4 | Harita ve konum | Kümelenmiş harita, ortak seçim, hedef seçimi, isteğe bağlı konum | Konum durumu testleri, izin olmadan ana akış testi |
| 5 | Sağlamlaştırma ve teslim | Erişilebilirlik, büyük yazı, belgeler, backlog | Tüm CI işleri ve teslim raporu |

## Kapsam dışı

Üyelik, backend, ödeme, rezervasyon, yorumlar, bildirimler, doluluk tahmini, uygulama içi
rota hesaplama ve adres arama servisi (maliyet ve erişim kararı verilmeden) bu sürüme dahil
değil.

## Belgeler

- [API_CONTRACT.md](API_CONTRACT.md) — veri kaynağı sözleşmesi ve doğrulama durumu
- [ARCHITECTURE.md](ARCHITECTURE.md) — katmanlar ve veri akışı
- [TESTING.md](TESTING.md) — test türleri ve çalıştırma
- [adr/](adr/) — teknik karar kayıtları
- [BACKLOG.md](BACKLOG.md) — ikinci sürüm için önceliklendirilmiş işler
