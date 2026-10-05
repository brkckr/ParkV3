# SENTETİK test verileri / SYNTHETIC test data

Bu klasördeki JSON dosyaları **canlı İSPARK verisi değildir**. `docs/API_CONTRACT.md`'deki
alan listesine göre elle yazılmıştır ve özellikle uç durumları (farklı biçimlerde gelen veya
hiç gelmeyen `isOpen`, tutarsız kapasite, geçersiz/yer değiştirmiş koordinat, kimliksiz ve
yinelenen kayıt) içerir. Uygulama `isOpen` alanını okumaz (ADR-0014); fixture'larda kaynaktaki
biçimiyle durur.
Uygulama bu dosyaları hiçbir koşulda kullanıcıya göstermez; yalnızca testlerde kullanılır.

The JSON files in this folder are hand-written, synthetic fixtures. They are never shipped
in the app and must not be presented as live data.
