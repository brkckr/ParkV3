# ADR-0003: Toleranslı ayrıştırma ve veri doğruluğu kuralları

## Bağlam
Kaynak şeması resmî olarak belgelenmemiş. 2026-10-04 tarihli canlı ölçüm
([API_CONTRACT.md](../API_CONTRACT.md)) koordinatların metin, `monthlyFee` değerinin float
geldiğini, `updateDate` alanının bazen null olduğunu ve bilinmeyen id için sahte bir kayıt
döndüğünü gösterdi. Tipler zamanla değişebilir.

## Karar
- Yanıt önce `JsonElement` olarak alınıyor (kotlinx.serialization). Her alan, tip
  toleranslı okuyucularla tek tek ayrıştırılıyor. Sabit tipli DTO ile gelen "tip uyuşmazlığı
  yüzünden tüm liste düştü" riskinden kaçınılıyor.
- **Eksik ≠ 0**: Tüm sayısal alanlar nullable. Varsayılan değer olarak `0` kullanılmıyor.
- **Açık/kapalı**: [ADR-0014](0014-isopen-kullanilmiyor.md) ile değişti. İlk sürüm `isOpen`
  değerini `OpenState { OPEN, CLOSED, UNKNOWN }` olarak okuyordu. Artık alan hiç okunmuyor.
  Açıklık `workHours` metninden de çıkarılmıyor.
- **Doluluk**: `Occupancy.Known(capacity, empty)` yalnızca `capacity > 0` ve
  `0 ≤ empty ≤ capacity` olduğunda. Alan eksikse `Missing`, değerler çelişkiliyse
  `Inconsistent`. Tutarsız sayılar UI'da gösterilmiyor; "Doluluk verisi tutarsız" yazıyor.
- **Uygunluk** (`Availability`): yalnızca doluluktan ([ADR-0014](0014-isopen-kullanilmiyor.md)).
  `Known` iken boş > 0 → `AVAILABLE`, boş = 0 → `FULL`. Doluluk eksik veya tutarsızsa →
  `UNKNOWN`. "Boş yeri olan" filtresi yalnızca `AVAILABLE` kayıtları geçiriyor.
- **Koordinat**: Sonlu, (0,0) olmayan ve İstanbul kutusu içinde olan değerler geçerli.
  Geçersiz kayıt listede "Konum bilgisi yok" etiketiyle görünüyor, haritada gösterilmiyor.
  Enlem ile boylam büyüklüğe bakılarak yer değiştirilmiyor.
- **Ücret**: Canlı kaynakta `fee` alanı yok. Fiyat bilgisi yalnızca detaydaki serbest metin
  `tariff` alanında (`"0-1 Saat : 110,00;…"`). Satırlar olduğu gibi gösteriliyor. Hiçbir
  değere "saatlik ücret" etiketi konmuyor, para birimi eklenmiyor. `monthlyFee` yalnızca
  `> 0` ise gösteriliyor. `0.0` "ücretsiz" sayılmıyor, çünkü bilinmeyen id yanıtı da `0.0`
  dönüyor. `freeTime` birimi belgelenmediği için ham sayı olarak gösteriliyor.
- **Sahte kayıt**: `ParkDetay` bilinmeyen id için `parkID: 0, capacity: 1, emptyCapacity: 1`
  içeren bir kayıt dönüyor. `parkID` istenen id'ye eşit değilse yanıt "bulunamadı" sayılıyor
  ve önbelleğe yazılmıyor.
- **Poligon**: `areaPolygon` ham olarak saklanıyor, v1'de çizilmiyor. Çizim için WKT
  standardındaki (X=boylam, Y=enlem) sıranın canlı veriyle doğrulanması gerekiyor (backlog).
- Kimliği geçersiz kayıtlar atlanıp sayılıyor. Aynı kimlik birden fazla gelirse ilk kayıt
  alınıyor.
