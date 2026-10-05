# ADR-0007: Harita ve API anahtarı

## Karar
- Google Maps SDK + `maps-compose` ve kümeleme için `maps-compose-utils`.
- API anahtarı Git dışında, kök dizindeki `secrets.properties` dosyasında (`MAPS_API_KEY`)
  veya `MAPS_API_KEY` ortam değişkeninde tutuluyor. Örnek dosya: `secrets.properties.example`.
  Anahtar manifest placeholder'ı ile enjekte ediliyor. Ayrıca `BuildConfig.HAS_MAPS_KEY`
  üretiliyor.
- Anahtar yoksa harita görünümü yerine açıklayıcı bir ekran gösteriliyor. Liste, arama,
  detay ve yol tarifi anahtarsız da çalışıyor.
- Harita işaretleri durumu renk, ikon ve metinle birlikte gösteriyor. Favori işareti,
  durum alanını örtmeyen ayrı bir köşe rozeti.

## Not
Maps SDK for Android, Google Cloud projesi ve faturalandırma hesabı gerektiriyor. Anahtar
oluşturma ve kısıtlama adımları README'de. Bu oturumda hiçbir ücretli servis
etkinleştirilmedi.
