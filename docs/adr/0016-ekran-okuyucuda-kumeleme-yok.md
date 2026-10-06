# ADR-0016: Ekran okuyucu açıkken haritada kümeleme yok

## Bağlam
Harita, yakın otoparkları sayı gösteren küme balonlarında topluyor. Tek otopark işaretleri ekran
okuyucuya ad, durum ve doluluk veriyor ([MarkerText](../../app/src/main/java/com/brkckr/parkv3/ui/map/MarkerText.kt)).
Küme balonlarının ise etiketi yok: TalkBack bir balonda hangi otoparkların olduğunu
okuyamıyor.

Balonu çizen `maps-compose` sınıfı (`ComposeUiClusterRenderer`) `final` ve küme işaretine başlık
vermiyor. Etiket eklemek için çiziciyi baştan yazmak, composable'ları bitmap'e çeviren kısmı da
kopyalamak gerekirdi. Bu, gerçek harita olmadan test edilemeyecek büyük bir değişiklik.

## Karar
- Dokunarak keşif açıkken (TalkBack) kümeleme kapatılıyor: çizicinin en küçük küme boyutu
  "hiçbir zaman" yapılıyor (`minClusterSize`). Her otopark kendi işaretiyle, kendi metniyle
  görünüyor.
- TalkBack açılıp kapanınca harita yeniden kümeleniyor. Uygulamayı yeniden başlatmak gerekmiyor
  (`rememberTouchExplorationEnabled`).
- TalkBack kapalıyken her şey eskisi gibi: aynı balonlar, aynı işaretler, aynı dokunma
  davranışı. Değişen tek şey, çizicinin `Clustering` içinden alınıp `maps-compose`'un belgelenmiş
  özelleştirme yoluyla (`rememberClusterManager`, `rememberClusterRenderer`) kurulması.

## Sonuçlar
- Ekran okuyucu kullanıcısı bütün otoparkları tek tek dolaşabiliyor. Yaklaşık 250 işaret
  aynı anda çiziliyor. Görsel olarak üst üste binebiliyorlar, ama TalkBack'in sola/sağa
  kaydırmayla dolaşması bundan etkilenmiyor.
- Tam erişilebilir alternatif liste görünümü olmaya devam ediyor.
- Doğrulanan: dokunarak keşif durumunun izlenmesi (Robolectric) ve kümeleme kuralı (birim
  testi). Doğrulanmayan: haritanın kendisi. Robolectric Google haritası çizemiyor, cihaz
  testlerinde de harita anahtarı yok. TalkBack ile gerçek cihazda denenmedi.
