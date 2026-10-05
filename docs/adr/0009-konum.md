# ADR-0009: Konum

## Karar
- Konum izni isteğe bağlı. İzin istenmeden de liste, arama, filtre, detay ve yol tarifi
  çalışıyor. Sıralama ada göre veya seçilen hedefe göre yapılıyor.
- İzin yalnızca kullanıcı "Konumum" düğmesine bastığında isteniyor.
- Durum modeli: `Idle`, `Locating`, `Available(point, approximate)`, `PermissionDenied`,
  `PermissionPermanentlyDenied` (Ayarlar'a yönlendirme), `ServicesDisabled` (konum
  ayarlarına yönlendirme), `Unavailable` (zaman aşımı veya sonuç yok).
- Yaklaşık konum (yalnızca `ACCESS_COARSE_LOCATION`) kabul ediliyor. Mesafelerin yaklaşık
  olduğu belirtiliyor.
- Düğme her basışta **güncel** konum istiyor (`getCurrentLocation`). Önbellekteki son konum
  kullanılmıyor.
- Mesafe haversine ile hesaplanıyor ve "kuş uçuşu" olarak etiketleniyor.
- Referans noktası önceliği: seçilmiş hedef → kullanıcı konumu → yok (ada göre sıralama).
