# Teknik Karar Kayıtları (ADR)

| # | Karar | Durum |
|---|---|---|
| [0001](0001-tek-modul-ve-katmanlar.md) | Tek app modülü, katman paketleri, use case yok | Kabul |
| [0002](0002-room-tek-dogruluk-kaynagi.md) | Room tek doğruluk kaynağı. İçerik ve yenileme durumu ayrı, favoriler ayrı tablo | Kabul |
| [0003](0003-toleransli-ayristirma.md) | Toleranslı ayrıştırma ve veri doğruluğu kuralları | Kabul. Açık/kapalı kuralı 0014 ile değişti |
| [0004](0004-liste-senkron-politikasi.md) | Liste senkronu: kaybolan kayıtlar, boş ve hatalı yanıtlar | Kabul |
| [0005](0005-tazelik.md) | Tazelik: zaman damgaları ve eskime eşikleri | Kabul |
| [0006](0006-eszamanli-yenileme-ve-iptal.md) | Eşzamanlı yenilemeleri birleştirme ve iptal semantiği | Kabul |
| [0007](0007-harita-ve-api-anahtari.md) | Google Maps Compose, kümeleme ve API anahtarı yönetimi | Kabul |
| [0008](0008-harici-navigasyon.md) | Harici navigasyon intent zinciri | Kabul |
| [0009](0009-konum.md) | İsteğe bağlı konum ve durum modeli | Kabul |
| [0010](0010-test-ve-ci.md) | Test stratejisi ve CI | Kabul |
| [0011](0011-surum-secimi.md) | Kütüphane sürümlerinin seçimi | Kabul |
| [0012](0012-alan-poligonlari.md) | Alan poligonları: WKT sırası, doğrulama ve çizim | Kabul |
| [0013](0013-uygulama-ici-dil.md) | Uygulama içi dil seçimi (AppCompat uygulama başına dil) | Kabul |
| [0014](0014-isopen-kullanilmiyor.md) | Kaynağın `isOpen` alanı kullanılmıyor, uygunluk yalnızca doluluktan | Kabul |
