# ADR-0014: Kaynağın `isOpen` alanı kullanılmıyor

## Bağlam
- Liste yanıtındaki `isOpen` alanı belgelenmemiş. 2026-10-04 ölçümünde 246 kaydın 169'unda
  `0`, 77'sinde `1` geliyordu ([API_CONTRACT.md](../API_CONTRACT.md)).
- `workHours = "24 Saat"` olan en az 63 otopark aynı anda `isOpen = 0` idi. Alan,
  yayımlanan çalışma saatiyle çelişiyor. "İşletme kapalı" mı, "canlı veri yok" mu, başka bir
  şey mi demek olduğu bilinmiyor.
- v1 değeri olduğu gibi gösteriyordu: "Kapalı (kaynağa göre)" etiketi ve "Açık" filtresi. 24
  saat açık otoparkların çoğu bu yüzden kapalı görünüyordu.
- İBB'den bir açıklama beklenmiyor (2026-10-05 ürün kararı).

## Karar
- `isOpen` hiç okunmuyor. Ayrıştırıcı alanı atlıyor. Değer ne modelde ne veritabanında var.
- Uygunluk (`Availability`) yalnızca dolulukla belirleniyor:
  - `Known` ve boş > 0 → `AVAILABLE` ("Boş yer var")
  - `Known` ve boş = 0 → `FULL` ("Dolu")
  - `Missing` veya `Inconsistent` → `UNKNOWN` ("Doluluk bilinmiyor"). Bu durum hiçbir zaman
    boş ya da dolu gösterilmiyor.
- "Açık" filtresi kaldırıldı. Filtreler: "Boş yeri olan" ve "Favoriler".
- Açıklık çalışma saatinden de hesaplanmıyor. `workHours` detayda kaynaktaki metin olarak
  gösterilmeye devam ediyor.
- Veritabanı sürüm 2: `parks.openState` sütunu Room otomatik geçişiyle (`@DeleteColumn`)
  siliniyor. Favoriler ve önbellekteki diğer alanlar korunuyor. Geçiş cihaz testinde
  (`ParkDatabaseMigrationTest`) doğrulanıyor.
- Sözleşme kontrolü `isOpen` alanını denetlemiyor (`IGNORED_FIELDS`). Alanın kaybolması,
  tipinin ya da değerlerinin değişmesi sapma sayılmıyor. Prob özeti dağılımı yalnızca bilgi
  olarak yazıyor.

## Sonuçlar
- "Boş yer var" görünen bir otopark, kaynağın o an kapalı saydığı bir otopark olabilir. Bunu
  güvenilir biçimde ayırt edecek veri yok. Kullanıcı çalışma saatini detayda görüyor.
- Kaynak alanın anlamını bir gün belgelerse karar yeniden değerlendirilecek. Bu yeni bir ADR
  ve yeni bir veritabanı sürümü gerektirir.
