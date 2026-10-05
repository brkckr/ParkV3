# ADR-0012: Alan poligonlarının çizimi

## Bağlam
`ParkDetay` yanıtı otoparkın alanını WKT metni olarak veriyor (`areaPolygon`). Görev
tanımına göre poligonun koordinat sırası sayıların büyüklüğüne bakılarak tahmin edilmemeli.
v1'de metin yalnızca saklanıyordu. Sözleşme kontrolü ([API_CONTRACT.md](../API_CONTRACT.md#sözleşme-kontrolü))
ilk geniş ölçümde 20 poligonun 20'sinin de WKT standardına (X = boylam, Y = enlem) uyduğunu
gösterdi.

## Karar
- Poligon her zaman WKT standardındaki sırayla okunuyor: X boylam, Y enlem. Eksenler
  **hiçbir durumda yer değiştirilmiyor**.
- Bir alan yalnızca şu koşulların hepsi sağlanırsa çiziliyor (`AreaPolygonParser`):
  - `POLYGON` veya `MULTIPOLYGON`, iç içe parantezleri eksiksiz. Z/M boyutları yok sayılıyor.
  - Her halkada en az 3 farklı nokta var.
  - Her nokta geçerli bir İstanbul koordinatı (listedeki koordinat kuralıyla aynı kutu).
  - Her nokta otoparkın **kendi konumuna** 2 km'den yakın. Otoparkın konumu yoksa alan
    çizilmiyor.
  - Toplam nokta sayısı 2000'i geçmiyor.
- Koşullardan biri sağlanmazsa alan hiç çizilmiyor. Kısmi çizim ya da "düzeltme" yok.
  Ham metin önbellekte olduğu gibi kalıyor.
- Alan, haritada seçili otopark için çiziliyor. Kenar ve hafif dolgu tema rengiyle (seçili
  işaretin çerçevesiyle aynı renk).
- Alan detaydan geldiği için, harita görünümündeyken detayı önbellekte olmayan bir otopark
  seçilince detayı **bir kez** indiriliyor. Liste görünümündeki seçim indirme başlatmıyor.
  Hata sessiz kalıyor: seçim kartı liste verisini her durumda gösteriyor.

## Sonuçlar
- Kaynak sırayı bir gün değiştirirse noktalar İstanbul kutusunun dışına düşer veya otoparkın
  konumundan uzaklaşır. Alan çizilmez, yanlış yerde bir alan gösterilmez. Haftalık sözleşme
  kontrolü de değişikliği issue olarak bildirir.
- Detayı hiç indirilemeyen (çevrimdışı) otoparklar için alan görünmez.
