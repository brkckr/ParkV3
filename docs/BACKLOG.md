# İkinci Sürüm Backlog'u (öncelik sırasıyla)

## P1 — Veri doğruluğu ve güvenilirlik
1. **`isOpen` anlamını İBB ile netleştirmek.** Ölçümde "24 Saat" çalışan en az 63 otopark
   `isOpen = 0` geldi. Bu alan "işletme kapalı" mı, "canlı veri yok" mu demek? Cevaba göre
   etiket ve "Açık" filtresi güncellenecek.
2. **targetSdk 37.** Android 17 davranış değişiklikleri cihazda test edildikten sonra.

## P2 — Ürün
3. **Adres/yer araması** (hedefi adla seçmek). Places/Geocoding API ücretli. Maliyet ve
   hesap kararı gerektiriyor.
4. **Uygulama içi dil seçimi.** Bugün sistemin uygulama başına dil ayarı destekleniyor
   (`generateLocaleConfig`).
5. **Harita erişilebilirliği.** TalkBack ile işaretlere gezinme. Bugün liste görünümü
   erişilebilir alternatif.

## P3 — Mühendislik
6. **Bağımlılık güncellemeleri.** `maps-compose` 9.x (çıkışı 2026-10-01),
    `play-services-location` 21.4.x (bu ortamdan Google Maven'a erişilemediği için
    doğrulanamadı).
7. **Baseline profile ve açılış performansı ölçümü.**
8. **Ekran görüntüsü (screenshot) testleri.** Açık/koyu tema ve büyük yazı.
9. **Release imzalama CI'ı.** İmza anahtarı GitHub secrets'ta, AAB üretimi. Mağazaya
    yükleme yok.
10. **Ekran sayısı artarsa modüllere ayırma** (`:core:data`, `:feature:map` vb.).

## Tamamlananlar
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
