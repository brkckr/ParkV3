# ADR-0015: targetSdk 37 (Android 17)

## Bağlam
compileSdk zaten 37'ydi. targetSdk 36'da tutuluyordu, çünkü Android 17'nin yalnızca API 37'yi
hedefleyen uygulamalara uygulanan davranış değişiklikleri değerlendirilmemişti
([ADR-0011](0011-surum-secimi.md), backlog). Değişiklik listesi:
[developer.android.com/about/versions/17/behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17)
(2026-10-01 güncellemesi).

## Karar
targetSdk 37'ye çıkarıldı. Değişikliklerin ParkV3'e etkisi:

| Değişiklik | Etki ve yapılan |
|---|---|
| Sertifika şeffaflığı (CT) varsayılan olarak açık | Uygulamanın tek sunucusu `api.ibb.gov.tr`. Sertifikası CT günlüklerinden SCT taşımazsa Android 17'de tüm istekler başarısız olur. API probu her çalışmada sunucu sertifikasındaki gömülü SCT'leri sayıyor, 2'den azsa sapma bildiriyor ([API_CONTRACT.md](../API_CONTRACT.md#sözleşme-kontrolü)) |
| Encrypted Client Hello (ECH) varsayılan olarak açık | Sunucu ECH desteklemiyorsa istemci rastgele içerikli (GREASE) uzantı gönderiyor. Uygulamada yapılacak bir şey yok. Canlı API ile Android 17'de denenmedi |
| Yerel ağ izni (`ACCESS_LOCAL_NETWORK`) | Uygulama yerel ağa bağlanmıyor. Cihaz testlerinin sahte sunucusu aynı süreçte `127.0.0.1` üzerinde. Belgeler loopback'in kapsamda olup olmadığını söylemiyor; API 37 emülatöründeki test çalıştırması bunu gösteriyor |
| Kilitsiz `MessageQueue` | Uygulama kodu `MessageQueue`'ya reflection ile erişmiyor. Test altyapısı (Espresso, Compose test) API 37 emülatöründe çalıştırılarak kontrol ediliyor |
| `static final` alanlar reflection ile değiştirilemiyor | Uygulama kodunda reflection yok |
| Büyük ekranda yön, en-boy oranı ve boyutlandırma kısıtları yok sayılıyor (Android 16'daki muafiyet kalktı) | Uygulama bu kısıtları koymuyor. Tablet ve geniş ekran düzeni zaten var |
| `IntentSender` için arka plandan etkinlik başlatma kısıtları | `PendingIntent` ve `IntentSender` kullanılmıyor |
| Widget bellek sınırı, arka plan ses kısıtları, SMS OTP gizleme, rehber (CP2) kısıtları, Bluetooth RFCOMM, yerel kitaplık yükleme, `setContentCaptureEnabled`, parola gösterimi, IME erişilebilirliği | İlgili API'ler kullanılmıyor |

- CI'daki cihaz testleri artık iki emülatörde koşuyor: API 34 (zorunlu kontrol) ve API 37
  (Android 17, targetSdk 37 davranışlarıyla).
- Robolectric testleri SDK 35'te kalıyor (`robolectric.properties`). Android 17 davranışları
  JVM testlerinde görünmez, onları API 37 emülatörü kapsıyor.

## Sonuçlar
- Fiziksel bir Android 17 cihazda deneme yapılmadı. Doğrulama emülatörde ve CI'da.
- `api.ibb.gov.tr` bir gün SCT'siz bir sertifikaya geçerse uygulama Android 17'de veri
  alamaz. Haftalık prob bunu issue olarak bildirir. O durumda geçici çözüm, ağ güvenliği
  yapılandırmasında bu alan adı için CT'yi kapatmaktır; güvenliği azalttığı için ancak
  bilinçli bir kararla yapılmalı.
