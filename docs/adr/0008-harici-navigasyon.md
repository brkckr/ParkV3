# ADR-0008: Harici navigasyon

## Karar
Yol tarifi intent'leri sırayla deneniyor. İlk başarılı olanda duruluyor:
1. Google Maps uygulaması (`com.google.android.apps.maps`):
   `https://www.google.com/maps/dir/?api=1&destination=lat,lng&travelmode=driving`
2. Herhangi bir harita uygulaması: `geo:lat,lng?q=lat,lng(Ad)` (seçici ile).
3. Tarayıcı: aynı Google Maps web bağlantısı.
4. Hiçbiri yoksa uygulama çökmüyor, kullanıcıya mesaj gösteriliyor.

`ActivityNotFoundException` ve `SecurityException` yakalanıyor. Manifestte `<queries>`
bildirimleri var. Seçim mantığı (`DirectionsIntentFactory`) saf ve test edilebilir.
Koordinatı geçersiz parkta yol tarifi düğmesi devre dışı ve bunun nedeni yazıyor.

## Paylaşım
Detay ekranındaki "Paylaş" düğmesi sistem paylaşım menüsünü (`ACTION_SEND`, `text/plain`)
açıyor. Metin otoparkın adı, adresi ve
`https://www.google.com/maps/search/?api=1&query=lat,lng` bağlantısından oluşuyor. Bağlantı
uygulama, hesap veya API anahtarı gerektirmiyor. Koordinatlar yol tarifindeki gibi her
yerelde ondalık noktayla yazılıyor. Doluluk birkaç dakikada eskidiği için metne eklenmiyor.
Geçerli konumu olmayan otoparkta düğme gösterilmiyor. Paylaşacak uygulama yoksa çökme yerine
mesaj gösteriliyor.
