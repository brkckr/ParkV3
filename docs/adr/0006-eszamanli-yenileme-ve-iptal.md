# ADR-0006: Eşzamanlı yenilemeleri birleştirme ve iptal

## Karar
- Liste yenilemesi "single-flight" çalışıyor. Devam eden bir yenileme varken gelen istek
  yeni bir ağ çağrısı başlatmıyor, mevcut işin sonucunu bekliyor. Detay yenilemesi de park
  kimliği başına aynı şekilde birleştiriliyor.
- Paylaşılan iş uygulama ömürlü bir `CoroutineScope` içinde çalışıyor. Çağıranın iptali
  (ör. ViewModel temizlenmesi) yalnızca onun beklemesini iptal ediyor, diğer bekleyenleri
  etkilemiyor.
- `CancellationException` hiçbir yerde ağ hatasına dönüştürülmüyor. Tüm `catch` blokları
  önce iptali yeniden fırlatıyor, `sync_state`'e hata yazılmıyor.
