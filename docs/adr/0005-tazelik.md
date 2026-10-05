# ADR-0005: Tazelik

## Karar
- İki zaman ayrı tutuluyor ve ayrı etiketle gösteriliyor:
  - **Son başarılı alım** (cihaz saati): "Son güncelleme 14:05 · 3 dk önce".
  - **Kaynak güncelleme zamanı** (`updateDate`, yalnızca detayda): "Kaynak: 14:02".
- Eşikler (`FreshnessPolicy`):
  - Liste 5 dakikadan eskiyse uygulama ön plana geldiğinde otomatik yenileniyor
    (`ListRefreshTriggers`, `ProcessLifecycleOwner` STARTED iken).
  - Liste 15 dakikadan eskiyse veya son deneme başarısız olduysa "Veriler eski olabilir"
    uyarısı gösteriliyor.
  - Detay 5 dakikadan eskiyse detay ekranı açıldığında ve ekran ön plana döndüğünde
    yenileniyor.
- Saat geri alınırsa (gelecekteki bir zaman damgası) veri eski kabul ediliyor.

## Bağlantı geri geldiğinde
- `NetworkMonitor`, varsayılan ağın internete ulaştığı Android tarafından doğrulanmış mı
  (`NET_CAPABILITY_VALIDATED`) onu izliyor. Yalnızca "ne zaman yeniden denensin" sorusunu
  yanıtlıyor. İstekler hiçbir zaman ona bakılarak engellenmiyor. Durum yanlış bile olsa
  istek gider, hata her zamanki gibi gösterilir.
- Yeniden bağlanma, izleme sırasında bağlantının önce kopup sonra gelmesi demek. İzleme
  başladığında zaten çevrimiçi olmak yeniden bağlanma sayılmıyor (o durumu ön plana gelme
  kontrolü karşılıyor).
- Liste: yeniden bağlanınca son deneme **ağ hatasıyla** bittiyse ya da liste 5 dakikadan
  eskiyse yenileniyor (`FreshnessPolicy.shouldRefreshListOnReconnect`). HTTP veya bozuk
  yanıt hataları, cihaz yeniden bağlandı diye tekrar denenmiyor. Bağlantı gidip geldikçe
  sunucuya istek yağdırmamak için.
- Detay: ekran açıkken aynı kural uygulanıyor. Son istek ağ hatasıyla bittiyse yeniden
  deneniyor, değilse yalnızca detay 5 dakikadan eskiyse yenileniyor.
- İzleme yalnızca uygulama (liste) veya ekran (detay) görünürken çalışıyor. Arka planda
  ağ isteği yapılmıyor. `ACCESS_NETWORK_STATE` kurulumda verilen normal bir izin, kullanıcıya
  sorulmuyor.
- Bazı cihazlarda `ConnectivityManager` çağrıları `SecurityException` fırlatabiliyor. Bu
  durumda izleyici "çevrimiçi" varsayıp sessiz kalıyor. Otomatik deneme bir kolaylık,
  elle yenileme ve ön plana gelme kontrolü yine çalışıyor.
