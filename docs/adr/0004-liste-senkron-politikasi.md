# ADR-0004: Liste senkron politikası

## Karar
Bir liste yanıtı yalnızca aşağıdaki koşulların **hepsini** sağlıyorsa "tam ve başarılı"
sayılıyor:
1. HTTP 2xx ve gövde bir JSON dizisi.
2. En az bir geçerli kimlikli kayıt içeriyor (boş dizi hatadır).
3. Geçersiz kimlikli kayıtlar toplamın yarısından fazla değil.

Tam ve başarılı yanıtta (tek transaction):
- Gelen kayıtlar upsert ediliyor: `lastSeenAt = now`, `missingSince = null`.
- **Küçülme koruması**: Önbellekte en az 20 aktif kayıt varken yeni yanıt, aktif kayıtların
  %50'sinden azını içeriyorsa yanıt "kısmi" kabul ediliyor. Bu durumda kayıtlar upsert
  ediliyor ama hiçbir kayıt kayıp olarak işaretlenmiyor.
- Aksi halde yanıtta olmayan aktif kayıtlar `missingSince = now` ile **kayıp** olarak
  işaretleniyor. Kayıp kayıtlar listede ve haritada gösterilmiyor.
- 30 günden uzun süredir kayıp olan kayıtlar ve bunlara ait detay önbelleği siliniyor.
- Favoriler hiçbir koşulda silinmiyor. Kaynaktan kalkan bir favori, favoriler görünümünde
  "Kaynakta artık listelenmiyor" olarak gösteriliyor.
- `sync_state.lastSuccessAt = now`, son hata temizleniyor.

Başarısız yanıtta yalnızca `lastAttemptAt` ve son hata yazılıyor. İçerik ve
`lastSuccessAt` değişmiyor.

## Gerekçe
Hatalı veya boş yanıtın tüm önbelleği silmesi, çevrimdışı kullanım ve favori görünümü için
kabul edilemez. Kayıp işaretleme (tombstone), gerçekten kapanan otoparkların listeden
kalkmasını sağlarken geri dönüşü de mümkün kılıyor.
