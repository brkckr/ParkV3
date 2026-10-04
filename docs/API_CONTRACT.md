# Veri Kaynağı Sözleşmesi — İSPARK (İBB)

> **Doğrulama durumu: CANLI OLARAK DOĞRULANMADI.**
> Geliştirme konteynerinin ağ politikası `api.ibb.gov.tr` ve `data.ibb.gov.tr` alan adlarını
> engelliyor (CONNECT 403). Aşağıdaki alan listesi referans projeden (ParkV2) ve ikincil
> kaynaklardan derlendi. Canlı davranış `.github/workflows/api-probe.yml` işiyle GitHub
> Actions üzerinden ölçülecek, sonuç bu belgenin "Probe sonuçları" bölümüne işlenecek.
> Uygulama bu belirsizlik nedeniyle tüm alanları **isteğe bağlı ve tipi belirsiz** kabul
> ederek ayrıştırıyor.

## Uç noktalar

| Uç nokta | Yöntem | Parametre | Beklenen gövde |
|---|---|---|---|
| `https://api.ibb.gov.tr/ispark/Park` | GET | — | JSON dizi, her eleman bir otopark |
| `https://api.ibb.gov.tr/ispark/ParkDetay?id={parkID}` | GET | `id`: tam sayı | JSON dizi (0 veya 1 eleman). Tek nesne gelirse o da kabul edilir |

Kimlik doğrulama gerekmiyor (açık veri, CC BY 4.0). Hız sınırı belgelenmemiş.

## Alanlar

"Kaynak" sütunu: **R** = referans projenin DTO'su, **S** = ikincil kaynak (üçüncü taraf açık
kaynak projeler ve İBB veri portalı arama özetleri). Tipler canlı olarak doğrulanmadı.

### `Park` (liste)

| Alan | Beklenen tip | Kaynak | Anlam | Uygulamanın yorumu |
|---|---|---|---|---|
| `parkID` | int | R, S | Otopark kimliği | Zorunlu. Sayı ya da sayısal metin kabul edilir. Geçersizse kayıt atlanır ve sayılır |
| `parkName` | string | R, S | Ad | Boşsa "Adsız otopark (#id)" gösterilir |
| `lat`, `lng` | number | R, S | WGS84 enlem/boylam (ondalık derece) | Sayı ya da metin kabul edilir (virgüllü ondalık dahil). Sonlu değilse, (0,0) ise veya İstanbul kutusu dışındaysa (enlem 40.5–41.9, boylam 27.5–30.0) **geçersiz**: haritada gösterilmez, mesafe hesaplanmaz. Enlem ve boylam **asla yer değiştirilmez** |
| `capacity` | int | R, S | Toplam araç kapasitesi | Eksik/null → bilinmiyor. `≤ 0` → tutarsız |
| `emptyCapacity` | int | R, S | Anlık boş yer | Eksik/null → bilinmiyor. `< 0` veya `> capacity` → tutarsız (sayı gösterilmez) |
| `isOpen` | int (0/1) | R, S | Otoparkın açık olup olmadığı | `1`, `true`, `"1"`, `"true"` → açık. `0`, `false`, `"0"`, `"false"` → kapalı. Diğer tüm durumlar (eksik, null, `2`, `"x"`) → **bilinmiyor** |
| `workHours` | string | R, S | Çalışma saatleri (serbest metin, ör. "08:00-24:00", "24 Saat") | Ham metin olarak gösterilir. Açık/kapalı hesaplamasında **kullanılmaz** |
| `parkType` | string | R, S | Otopark türü (ör. "AÇIK OTOPARK", "KAPALI OTOPARK", "YOL ÜSTÜ") | Ham metin |
| `district` | string | R, S | İlçe | Aramada kullanılır. Boşsa "İlçe bilinmiyor" |
| `freeTime` | int/string | R, S | Ücretsiz süre (birimi belgelenmemiş, büyük olasılıkla dakika) | Ham değer "Ücretsiz süre (kaynak değeri)" etiketiyle gösterilir |
| `fee` | string? | R | Ücret (kapsadığı süre belgelenmemiş) | Ham değer. **"Saatlik" etiketi konmaz** |
| `monthlyFee` | string? | R | Aylık ücret | Ham değer, "Aylık ücret (kaynak değeri)" |

### `ParkDetay` (detay) — listedekilere ek olarak

| Alan | Beklenen tip | Kaynak | Anlam | Uygulamanın yorumu |
|---|---|---|---|---|
| `address` | string | R, S | Açık adres | Ham metin |
| `phone` | string | R | Telefon | Varsa gösterilir |
| `tariff` | string | R, S | Tarife (serbest metin, `;` ile ayrılmış satırlar olduğu varsayılıyor) | `;` ve satır sonlarından bölünür, satırlar olduğu gibi gösterilir. Fiyat/süre anlamı çıkarılmaz |
| `areaPolygon` | string (WKT) | R, S | Otopark alanı poligonu, ör. `POLYGON((x y, ...))` | **v1'de çizilmez**, ham metin saklanır. Koordinat sırası doğrulanmadan (WKT standardı X=boylam, Y=enlem) çizim yapılmayacak ([ADR-0003](adr/0003-toleransli-ayristirma.md)) |
| `updateDate` | string (tarih-saat) | S | Kaynağın veriyi son güncelleme zamanı | "Kaynak güncelleme zamanı" olarak gösterilir. Biçim doğrulanmadı: ISO-8601 (bölgeli/bölgesiz → Europe/Istanbul) ve `dd.MM.yyyy HH:mm[:ss]` denenir. Ayrıştırılamazsa ham metin gösterilir |

## Hata ve uç durumlar

| Durum | Uygulamanın davranışı |
|---|---|
| HTTP 2xx, geçerli dizi | Senkron politikasına göre yazılır ([ADR-0004](adr/0004-liste-senkron-politikasi.md)) |
| HTTP 2xx, boş dizi `[]` | **Hata (EmptyResponse)**. Önbellek silinmez |
| HTTP 2xx, dizi değil / JSON değil | Hata (Malformed). Önbellek değişmez |
| Kayıtların yarısından fazlası geçersiz kimlikli | Hata (Malformed). Şema değişikliği olasılığı nedeniyle yazılmaz |
| HTTP 4xx/5xx | Hata (Http, kod). Sunucu gövdesi UI'a taşınmaz |
| Zaman aşımı / bağlantı yok | Hata (Network) |
| `ParkDetay` boş dizi | Detay bulunamadı (NotFound). Önbellekte detay varsa korunur |
| Coroutine iptali | Hata olarak kaydedilmez, yukarı iletilir |

## Zaman kavramları

- **Son başarılı alım (`lastSuccessAt`)**: Cihaz saatine göre listenin en son başarıyla
  alınıp yazıldığı an. Başarısız yenileme bu değeri değiştirmez.
- **Kaynak güncelleme zamanı (`updateDate`)**: Kaynağın kendi bildirdiği zaman (yalnızca
  detayda bekleniyor). Cihaz saatiyle karıştırılmaz, ayrı etiketle gösterilir.

## Probe sonuçları

_Henüz yok._ `api-probe` iş akışı ilk kez çalıştığında alan tipleri, null sayıları, `isOpen`
değer dağılımı, kapasite tutarsızlıkları, koordinat aralıkları ve `areaPolygon`/`updateDate`
örnekleri buraya işlenecek.
