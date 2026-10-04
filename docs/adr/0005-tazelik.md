# ADR-0005: Tazelik

## Karar
- İki zaman ayrı tutuluyor ve ayrı etiketle gösteriliyor:
  - **Son başarılı alım** (cihaz saati): "Son güncelleme 14:05 · 3 dk önce".
  - **Kaynak güncelleme zamanı** (`updateDate`, yalnızca detayda): "Kaynak: 14:02".
- Eşikler (`FreshnessPolicy`):
  - Liste 5 dakikadan eskiyse uygulama ön plana geldiğinde otomatik yenileniyor
    (`ProcessLifecycleOwner` ON_START).
  - Liste 15 dakikadan eskiyse veya son deneme başarısız olduysa "Veriler eski olabilir"
    uyarısı gösteriliyor.
  - Detay 5 dakikadan eskiyse detay ekranı açıldığında ve ekran ön plana döndüğünde
    yenileniyor.
- Saat geri alınırsa (gelecekteki bir zaman damgası) veri eski kabul ediliyor.
