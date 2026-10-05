# Veri Kaynağı Sözleşmesi — İSPARK (İBB)

> **Doğrulama durumu:** Canlı uç noktalar 2026-10-04 21:12 UTC'de GitHub Actions
> (`api-probe` iş akışı, Azure eastus2) üzerinden ölçüldü. Geliştirme konteyneri
> `api.ibb.gov.tr` alan adına erişemediği için ölçüm CI üzerinden yapıldı.
> Ölçüm tek bir ana ait. Alanlar zamanla değişebilir. Bu yüzden uygulama tüm alanları hâlâ
> **isteğe bağlı ve tipi esnek** olarak ayrıştırıyor. `api-probe` her hafta ve elle
> tetiklendiğinde yeniden çalışıyor ve yanıtları aşağıdaki sözleşmeyle karşılaştırıyor
> ([Sözleşme kontrolü](#sözleşme-kontrolü)).

## Uç noktalar

| Uç nokta | Yöntem | Ölçülen yanıt |
|---|---|---|
| `https://api.ibb.gov.tr/ispark/Park` | GET | 200, `application/json;charset=utf-8`, ~56 KB, **246** nesneden oluşan dizi, ~1,6 sn |
| `https://api.ibb.gov.tr/ispark/ParkDetay?id={parkID}` | GET | 200, tek elemanlı dizi, ~1,1 sn |
| `ParkDetay?id=999999999` (bilinmeyen id) | GET | **200 ve sahte kayıt**: `parkID: 0`, boş metinler, `capacity: 1`, `emptyCapacity: 1` |

Kimlik doğrulama gerekmiyor (açık veri, CC BY 4.0). Hız sınırı belgelenmemiş.

## `Park` (liste) — ölçülen alanlar

246 kaydın hepsinde aşağıdaki 11 alan vardı. Null değer yoktu.

| Alan | Ölçülen tip | Örnek | Anlam | Uygulamanın yorumu |
|---|---|---|---|---|
| `parkID` | int | `3068` | Kimlik (246 farklı değer, yinelenen yok) | Zorunlu. Sayı veya sayısal metin. `≤ 0` ya da geçersizse kayıt atlanıp sayılır |
| `parkName` | string | `"19 Mayıs Açık"` | Ad | Boşsa "Adsız otopark (#id)" |
| `lat`, `lng` | **string** | `"41.0563"`, `"28.9941"` | WGS84 ondalık derece | Metin veya sayı. Sonlu değilse, (0,0) ise veya İstanbul kutusu (enlem 40.5–41.9, boylam 27.5–30.0) dışındaysa geçersiz. Ölçümde 246/246 kutu içindeydi. Yer değiştirme **yapılmaz** |
| `capacity` | int | `65` | Toplam kapasite | Eksik → bilinmiyor. `≤ 0` → tutarsız (ölçümde yok) |
| `emptyCapacity` | int | `0` | Anlık boş yer | Eksik → bilinmiyor. `< 0` veya `> capacity` → tutarsız (ölçümde yok) |
| `isOpen` | int | `0` (169), `1` (77) | Kaynağın açık/kapalı bildirimi | `1/true` → açık, `0/false` → kapalı, diğer her şey → bilinmiyor. **Not:** `workHours = "24 Saat"` olan en az 63 kayıt ölçüm anında `isOpen = 0` idi. Alanın anlamı (işletme kapalı mı, yoksa canlı veri mi yok) doğrulanamadı. UI "Kapalı (kaynağa göre)" diyor ve çalışma saatinden açıklık hesaplamıyor |
| `workHours` | string | `"24 Saat"` (140), `"08:00-23:00"` | Çalışma saatleri, serbest metin | Ham gösterim |
| `parkType` | string | `"AÇIK OTOPARK"` (110), `"KAPALI OTOPARK"` (86), `"YOL ÜSTÜ"` (50) | Tür | Ham gösterim |
| `district` | string | `"FATİH"` | İlçe, büyük harf | Aramada kullanılır |
| `freeTime` | int | `15` (112), `0` (82), `10`, `5`, `25`, `60`, `20` | Ücretsiz süre. **Birim belgelenmemiş** | Ham sayı, "birim kaynakta belirtilmiyor" notuyla |

`fee` ve `monthlyFee` listede **yok** (referans projenin DTO'sunda vardı, canlıda yok).

## `ParkDetay` (detay) — ölçülen alanlar

Ölçülen 2 kayıtta 16 alan vardı:

| Alan | Ölçülen tip | Örnek | Uygulamanın yorumu |
|---|---|---|---|
| `parkID`, `parkName`, `lat`, `lng`, `capacity`, `emptyCapacity`, `workHours`, `parkType`, `freeTime`, `district` | listedeki gibi | | Listedeki kurallar |
| `locationName` | string | `"1612 15 Temmuz Şehitler Meydanı Zeminaltı Otoparkı"` | İç kod ve ad. Kullanılmıyor |
| `address` | string | `"ÜMRANİYE 15 TEMMUZ ŞEHİTLER MEYDANI"` | Ham gösterim |
| `monthlyFee` | **float** | `3500.0`, `0.0` | `> 0` → "Aylık abonelik: 3.500,00". `0.0` ve eksik → "Belirtilmemiş". Bilinmeyen id yanıtı da `0.0` döndüğü için 0 "ücretsiz" olarak yorumlanmıyor |
| `tariff` | string | `"0-1 Saat : 110,00;1-2 Saat : 140,00;…;Tam Gün : 370,00"` | `;` ile satırlara bölünür. Her satır ilk `:` işaretinden etiket ve değer olarak ayrılıp olduğu gibi gösterilir. Para birimi kaynakta yazmadığı için eklenmiyor |
| `updateDate` | string **veya null** | `"05.10.2026 00:10:23"`, `null` | `dd.MM.yyyy HH:mm:ss`, Europe/Istanbul (ölçüm anı 21:12 UTC ile tutarlı). "Kaynak güncelleme zamanı" olarak ayrı gösterilir |
| `areaPolygon` | string (WKT) | `"POLYGON ((29.0910 41.0251, 29.0920 41.0251, …))"` | Ölçülen örnekler WKT standardındaki X=boylam, Y=enlem sırasındaydı. Probe bunu her hafta değişen 20 kayıtlık bir örneklemde, poligonun kaydın kendi konumuna hangi sırada denk geldiğine bakarak kontrol ediyor. v1'de çizilmiyor, ham saklanıyor (backlog) |

Detayda **`isOpen`, `fee` ve `phone` yok.** Detay ekranı açık/kapalı bilgisini listedeki kayıttan alıyor
ve kendi zaman damgasıyla gösteriyor.

## Hata ve uç durumlar

| Durum | Uygulamanın davranışı |
|---|---|
| HTTP 2xx, geçerli dizi | Senkron politikası ([ADR-0004](adr/0004-liste-senkron-politikasi.md)) |
| HTTP 2xx, boş dizi `[]` | Hata (EmptyResponse). Önbellek silinmez |
| HTTP 2xx, dizi değil / HTML / boş gövde | Hata (Malformed). Önbellek değişmez |
| Kayıtların yarısından fazlası geçersiz kimlikli | Hata (Malformed) |
| HTTP 4xx/5xx | Hata (Http, kod). Sunucu gövdesi okunmaz, UI'a taşınmaz |
| Zaman aşımı / bağlantı yok | Hata (Network) |
| `ParkDetay` boş dizi, `parkID` 0 ya da istenen id'den farklı | Detay bulunamadı (NotFound). Sahte kayıt **yazılmaz**. Önbellekteki detay korunur |
| Coroutine iptali | Hata olarak kaydedilmez, yukarı iletilir |

## Zaman kavramları

- **Son başarılı alım (`lastSuccessAt`)**: Cihaz saatine göre listenin en son başarıyla
  alındığı an. Başarısız yenileme bu değeri değiştirmez. Liste uç noktası kendi güncelleme
  zamanını vermiyor.
- **Detay alım zamanı (`fetchedAt`)**: Detayın cihaza indiği an.
- **Kaynak güncelleme zamanı (`updateDate`)**: Yalnızca detayda ve bazen null. Cihaz
  saatiyle karıştırılmaz.

## Sözleşme kontrolü

`scripts/api_contract.py` yukarıdaki ölçümü kod olarak tutuyor. `api-probe` her çalışmada
listeyi, park türlerine yayılan ve her hafta değişen 20 detayı ve bilinmeyen id yanıtını
bununla karşılaştırıyor.

**Sapma (iş akışı kırmızı, zamanlanmış veya elle çalışmada issue):**
- `Park` HTTP 200 dışında bir kod (5xx hariç), dizi olmayan gövde ya da 123'ten az kayıt
  (ölçülen 246'nın yarısı, uygulamanın küçülme korumasıyla aynı mantık).
- Kayıtların %5'inden fazlasında: bir alan eksik, tipi ölçülenden farklı, yeni bir alan var,
  kullanılabilir `parkID` yok, koordinat İstanbul kutusu dışında, `isOpen` 0/1 dışında.
- Detay: tek elemanlı dizi değil, istenen id'den farklı `parkID` (uygulama bunu "bulunamadı"
  sayar), uygulamanın okuyamadığı `updateDate`, enlem-boylam sırasında bir `areaPolygon`,
  WKT olmayan poligonlar.
- Bilinmeyen id için o id'ye ait bir kayıt dönmesi.

**Not (yalnızca raporda):** %5'in altındaki sapmalar, uygulamanın hâlâ okuyabildiği yeni bir
tarih biçimi, `etiket : değer` biçiminde olmayan tarife satırları, kaydın konumundan uzak
poligonlar, bilinmeyen id davranışının değişmesi (ör. 404).

**Kesinti sözleşme değişikliği sayılmıyor:** Ağ hatası veya HTTP 5xx'te kontrol atlanıyor,
iş akışı kırmızı oluyor ama issue açılmıyor. Çıkış kodları: 0 sözleşme geçerli, 1 kaynağa
ulaşılamadı, 2 sapma.

**Issue:** `api-contract` etiketli açık bir issue varsa yeni çalışma ona yorum ekliyor, yoksa
"İSPARK API sözleşmesi değişti" başlıklı yeni bir issue açılıyor. PR'lardaki çalışmalar issue
açmıyor.

**Sözleşme değişince:** Değişiklik kalıcıysa `scripts/api_contract.py` içindeki beklentiyi,
bu belgeyi ve gerekirse `ParkJsonParser`'ı aynı PR'da güncelleyin.

## Probe nasıl çalıştırılır

GitHub → Actions → "API contract probe" → Run workflow. Yerelde
`python3 scripts/api_probe.py --out probe-output` (ağ erişimi gerekir). Ham yanıtlar ve
Markdown rapor `probe-output/` klasörüne ve iş akışı artefaktına yazılır. Sahte bir sunucuya
karşı denemek için `ISPARK_PROBE_BASE=http://127.0.0.1:8765/ispark/`.

Kontrolün kendi testleri ağ gerektirmiyor: `python3 -m unittest discover -s scripts`.
