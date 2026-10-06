# İkinci Sürüm Backlog'u (öncelik sırasıyla)

## P1 — Veri doğruluğu ve güvenilirlik
1. **Android 17'de cihaz testleri.** targetSdk 37, ama cihaz testleri yalnızca API 34'te koşuyor.
   GitHub runner'ındaki API 37 emülatörü kararlı çalışmadı ([ADR-0015](adr/0015-target-sdk-37.md)).
   Fiziksel bir cihazda ya da kararlı bir emülatör imajı çıktığında CI'da denenmeli.

## P2 — Ürün
2. **Adres/yer araması** (hedefi adla seçmek). Places/Geocoding API ücretli. Maliyet ve
   hesap kararı gerektiriyor.

## P3 — Mühendislik
3. **Kalan bağımlılık güncellemeleri.** Gradle 9.8.0 (sağlama toplamı bu ortamdan
   alınamadı) ve Google Maven'daki kütüphaneler (`play-services-location`, AndroidX, AGP).
   Google Maven'a erişebilen bir ortamda kontrol edilmeli.
4. **Baseline profile ve açılış performansı ölçümü.**
5. **Release imzalama CI'ı.** İmza anahtarı GitHub secrets'ta, AAB üretimi. Mağazaya
   yükleme yok.
6. **Ekran sayısı artarsa modüllere ayırma** (`:core:data`, `:feature:map` vb.).

## Tamamlananlar
- **Harita küme balonlarının erişilebilirliği** (eski P2). TalkBack açıkken kümeleme
  kapatılıyor, her otopark kendi etiketli işaretiyle görünüyor
  ([ADR-0016](adr/0016-ekran-okuyucuda-kumeleme-yok.md)). TalkBack ile gerçek cihazda denenmedi.
- **targetSdk 37** (eski P1). Android 17 davranış değişiklikleri tek tek değerlendirildi. API
  probu sunucu sertifikasının sertifika şeffaflığı şartını karşıladığını kontrol ediyor
  ([ADR-0015](adr/0015-target-sdk-37.md)). Android 17'de cihaz testi yapılamadı, yukarıda P1.
- **`isOpen` belirsizliği** (eski P1 #1). İBB'den açıklama beklenmeden kapatıldı: alan artık
  okunmuyor. "Açık" filtresi ve "Kapalı" etiketi kaldırıldı, uygunluk yalnızca dolulukla
  belirleniyor. Veritabanı sürüm 2, favoriler korunuyor
  ([ADR-0014](adr/0014-isopen-kullanilmiyor.md)).
- **Ekran görüntüsü testleri** (eski P3). Roborazzi ile ana liste (açık, koyu, Türkçe + büyük
  yazı), çevrimdışı durum, detay ve tablet düzeni. PR'larda son `main` ile karşılaştırma
  ([TESTING.md](TESTING.md#ekran-görüntüsü-testleri)).
- **Bağımlılık güncellemeleri, Maven Central kısmı.** Kotlin 2.4.20, maps-compose 9.0.0. Diğer
  Maven Central bağımlılıkları zaten günceldi ([ADR-0011](adr/0011-surum-secimi.md)).
- **Harita işaretlerinin ekran okuyucu metni** (eski P2 harita erişilebilirliği maddesinin
  ilk kısmı). İşaret adı (adsız otoparkta yedek ad) ve "durum · doluluk" açıklaması, listedeki
  metinlerle aynı.
- **Uygulama içi dil seçimi** (eski P2 #4). Ana ekrandaki dil düğmesiyle Sistem dili, Türkçe
  veya English. Android 8–12'de de çalışıyor ([ADR-0013](adr/0013-uygulama-ici-dil.md)).
- **Paylaşım** (eski P2 #6). Detay ekranından ad, adres ve harita bağlantısı sistem paylaşım
  menüsüyle gönderiliyor. Doluluk, birkaç dakikada eskidiği için eklenmiyor.
- **Tablet ve geniş yatay ekran düzeni** (eski P2 #4). 840 dp ve üstünde liste ve harita yan
  yana, detay içeriği okunabilir genişlikte ([ARCHITECTURE.md](ARCHITECTURE.md#ekran-durumu)).
- **Alan poligonları** (eski P1 #2). Seçili otoparkın alanı haritada çiziliyor. WKT sırası
  hiç değiştirilmiyor, alan yalnızca otoparkın kendi konumunun çevresindeyse gösteriliyor
  ([ADR-0012](adr/0012-alan-poligonlari.md)).
- **`api-probe` sözleşme kontrolü** (eski P1 #3). Alan, tip, kayıt sayısı, koordinat,
  tarih biçimi ve poligon sırası sapmalarında iş akışı kırmızıya dönüyor ve issue açıyor
  ([API_CONTRACT.md](API_CONTRACT.md#sözleşme-kontrolü)).
- **Bağlantı geri geldiğinde otomatik yeniden deneme** (eski P1 #2). Ağ hatasıyla biten
  liste ve detay istekleri, uygulama görünürken bağlantı geri gelince yeniden deneniyor
  ([ADR-0005](adr/0005-tazelik.md)).
