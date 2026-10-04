# ADR-0003: Toleranslı ayrıştırma ve veri doğruluğu kuralları

## Bağlam
Kaynak şeması resmî olarak belgelenmemiş ve canlı olarak doğrulanamadı
([API_CONTRACT.md](../API_CONTRACT.md)). Alanlar sayı, metin veya null gelebilir.

## Karar
- Yanıt önce `JsonElement` olarak alınıyor (kotlinx.serialization). Her alan, tip
  toleranslı okuyucularla tek tek ayrıştırılıyor. Sabit tipli DTO ile gelen "tip uyuşmazlığı
  yüzünden tüm liste düştü" riskinden kaçınılıyor.
- **Eksik ≠ 0**: Tüm sayısal alanlar nullable. Varsayılan değer olarak `0` kullanılmıyor.
- **Açık/kapalı/bilinmiyor**: `OpenState { OPEN, CLOSED, UNKNOWN }`. Yalnızca açık değerler
  eşleniyor. Diğer her şey `UNKNOWN`. Önceki değere veya `workHours` metnine düşülmüyor.
- **Doluluk**: `Occupancy.Known(capacity, empty)` yalnızca `capacity > 0` ve
  `0 ≤ empty ≤ capacity` olduğunda. Alan eksikse `Missing`, değerler çelişkiliyse
  `Inconsistent`. Tutarsız sayılar UI'da gösterilmiyor; "Doluluk verisi tutarsız" yazıyor.
- **Uygunluk** (`Availability`): kapalı → `CLOSED`. Açık ve `Known` iken boş > 0 →
  `AVAILABLE`, boş = 0 → `FULL`. Açık ama doluluk bilinmiyorsa → `OPEN_OCCUPANCY_UNKNOWN`.
  Açıklık bilinmiyorsa → `UNKNOWN`. "Boş yeri olan" filtresi yalnızca `AVAILABLE` kayıtları
  geçiriyor, "Açık" filtresi yalnızca `OpenState.OPEN` kayıtlarını.
- **Koordinat**: Sonlu, (0,0) olmayan ve İstanbul kutusu içinde olan değerler geçerli.
  Geçersiz kayıt listede "Konum bilgisi yok" etiketiyle görünüyor, haritada gösterilmiyor.
  Enlem ile boylam büyüklüğe bakılarak yer değiştirilmiyor.
- **Ücret**: `fee`, `monthlyFee`, `freeTime` ve `tariff` ham metin olarak taşınıyor. Süre
  birimi doğrulanmadığı için "saatlik" etiketi kullanılmıyor.
- **Poligon**: `areaPolygon` ham olarak saklanıyor, v1'de çizilmiyor. Çizim için WKT
  standardındaki (X=boylam, Y=enlem) sıranın canlı veriyle doğrulanması gerekiyor (backlog).
- Kimliği geçersiz kayıtlar atlanıp sayılıyor. Aynı kimlik birden fazla gelirse ilk kayıt
  alınıyor.
