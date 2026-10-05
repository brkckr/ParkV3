# İkinci Sürüm Backlog'u (öncelik sırasıyla)

## P1 — Veri doğruluğu ve güvenilirlik
1. **`isOpen` anlamını İBB ile netleştirmek.** Ölçümde "24 Saat" çalışan en az 63 otopark
   `isOpen = 0` geldi. Bu alan "işletme kapalı" mı, "canlı veri yok" mu demek? Cevaba göre
   etiket ve "Açık" filtresi güncellenecek.
2. **Bağlantı geri geldiğinde otomatik yenileme** (`ConnectivityManager` callback). Bugün
   yalnızca ön plan ve elle yenileme var.
3. **Poligon çizimi.** WKT sırası ölçülen örneklerde standarda (boylam, enlem) uyuyor.
   Daha geniş bir örneklemle doğrulandıktan sonra detayda alan gösterimi eklenebilir.
4. **`api-probe` sonuçlarını sözleşme testine bağlamak.** Alan tipi veya sayısı belirgin
   değiştiğinde iş akışı kırmızıya dönsün ve bir issue açsın.
5. **targetSdk 37.** Android 17 davranış değişiklikleri cihazda test edildikten sonra.

## P2 — Ürün
6. **Adres/yer araması** (hedefi adla seçmek). Places/Geocoding API ücretli. Maliyet ve
   hesap kararı gerektiriyor.
7. **Tablet ve yatay ekran için iki bölmeli düzen** (liste ve harita yan yana).
8. **Uygulama içi dil seçimi.** Bugün sistemin uygulama başına dil ayarı destekleniyor
   (`generateLocaleConfig`).
9. **Harita erişilebilirliği.** TalkBack ile işaretlere gezinme. Bugün liste görünümü
   erişilebilir alternatif.
10. **Paylaşım.** Otopark konumunu bağlantı olarak paylaşmak.

## P3 — Mühendislik
11. **Bağımlılık güncellemeleri.** `maps-compose` 9.x (çıkışı 2026-10-01),
    `play-services-location` 21.4.x (bu ortamdan Google Maven'a erişilemediği için
    doğrulanamadı).
12. **Baseline profile ve açılış performansı ölçümü.**
13. **Ekran görüntüsü (screenshot) testleri.** Açık/koyu tema ve büyük yazı.
14. **Release imzalama CI'ı.** İmza anahtarı GitHub secrets'ta, AAB üretimi. Mağazaya
    yükleme yok.
15. **Ekran sayısı artarsa modüllere ayırma** (`:core:data`, `:feature:map` vb.).
