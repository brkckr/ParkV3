# ADR-0002: Room tek doğruluk kaynağı

## Karar
- Ekranlar kalıcı veriyi **yalnızca Room'dan** (`Flow`) okuyor. Ağ yanıtı doğrudan UI'a
  gitmiyor; önce doğrulanıyor, sonra Room'a yazılıyor.
- Tablolar:
  - `parks`: liste anlık görüntüsü. `lastSeenAt` ve `missingSince` kolonları senkron
    politikası için tutuluyor ([ADR-0004](0004-liste-senkron-politikasi.md)).
  - `park_details`: detay önbelleği (`fetchedAt` ile birlikte).
  - `favorites`: `parkId`, ad ve ilçe anlık görüntüsü, ekleme zamanı. Ağ kayıtlarından
    **bağımsız**. Yenileme ve temizleme işlemleri bu tabloya hiç yazmıyor.
  - `sync_state`: tek satır. `lastSuccessAt`, `lastAttemptAt`, son hata türü ve kodu.
- **Ağ yenileme durumu** (`Idle` / `Refreshing` / son hata) repository'de ayrı bir
  `StateFlow` olarak tutuluyor ve içerikle karıştırılmıyor. UI, içerik varken yenileme
  sırasında yalnızca ince bir ilerleme çubuğu gösteriyor.
- Şema değişiklikleri migration ile yapılacak. `fallbackToDestructiveMigration`
  **kullanılmıyor**, çünkü favorileri siler. Şema JSON'ları `app/schemas` altında
  versiyonlanıyor.

## Gerekçe
Favorilerin park satırında kolon olarak tutulması, yenilemede `REPLACE` sırasında favorinin
kaybolma riskini doğurur. Ayrı tablo bu riski yapısal olarak ortadan kaldırıyor.
