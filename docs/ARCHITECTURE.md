# Mimari

Tek `:app` modülü, katman paketleri ve use case içermeyen ince bir yapı ([ADR-0001](adr/0001-tek-modul-ve-katmanlar.md)).

```
com.brkckr.parkv3
├── domain/            Saf Kotlin. Android, Room, Retrofit ve Maps tipi içermez
│   ├── model/         Park, ParkDetail, Occupancy, Availability, SyncInfo, RefreshError,
│   │                  FreshnessPolicy, Clock
│   ├── ParkListQuery  Filtre (VE), Türkçe arama, sıralama, haversine mesafe
│   └── ParkRepository Arayüz (observe* + refresh*)
├── data/
│   ├── remote/        Retrofit API (JsonElement), tip toleranslı ayrıştırıcı, uzak kaynak
│   ├── sync/          ListSyncPolicy: yanıt kabul/ret ve küçülme koruması
│   ├── local/         Room varlıkları, DAO (tek transaction'lı senkron), eşleyiciler
│   └── OfflineFirstParkRepository  Single-flight yenileme, iptal semantiği
├── location/          FusedLocationProvider (yalnızca güncel konum)
├── navigation/        Tip güvenli rotalar, harici yol tarifi zinciri
├── ui/
│   ├── main/          MainViewModel, MainUiState, liste, filtre, boş durumlar
│   ├── map/           Kümelemeli harita, harita kullanılabilirliği
│   ├── detail/        DetailViewModel, detay ekranı
│   ├── components/    Durum rozeti, biçimlendirme yardımcıları
│   └── theme/         Material 3, açık/koyu tema, durum renkleri
└── di/                Hilt modülleri (debug'a özel ağ logu src/debug altında)
```

## Veri akışı

```
           ┌──────────── refresh*() (single-flight, uygulama scope'u) ────────────┐
           │                                                                       ▼
 UI ◀── StateFlow ◀── ViewModel ◀── observe*() ◀── Room (tek doğruluk kaynağı) ◀── ListSyncPolicy ◀── ayrıştırıcı ◀── Retrofit/OkHttp ◀── İSPARK
           ▲                                                                       │
           └──────── isRefreshingList / observeRefreshingDetails() (ağ durumu) ────┘
```

- **İçerik ve ağ durumu ayrı**: Ekranlar içeriği Room'dan okuyor. Yenileme durumu ayrı bir
  akış olarak geliyor. Yenileme sürerken içerik yerinde kalıyor, yalnızca ince bir ilerleme
  çubuğu ve tazelik bandı değişiyor.
- **Hata modeli**: `RefreshError` tipli bir model. UI yalnızca yerelleştirilmiş metin ve
  HTTP kodu gösteriyor, sunucu gövdesi hiçbir zaman okunmuyor.
- **Tazelik**: `sync_state.lastSuccessAt` yalnızca başarılı senkronda ilerliyor. Liste,
  ön plana gelişte (`ProcessLifecycleOwner`) ve soğuk açılışta 5 dakikadan eskiyse
  yenileniyor. Detay, ekran her START olduğunda 5 dakikadan eskiyse yenileniyor.
- **Kayıp kayıt politikası**: Tam ve geçerli yanıtta olmayan parklar silinmiyor,
  `missingSince` ile işaretleniyor. 30 gün sonra siliniyor. Favoriler etkilenmiyor
  ([ADR-0004](adr/0004-liste-senkron-politikasi.md)).

## Ekran durumu

| Ekran | Durum | İçerik türleri |
|---|---|---|
| Ana | `MainUiState` (`SavedStateHandle` ile süreç ölümüne dayanıklı sorgu, filtre, görünüm, seçim, hedef) | `Loading`, `OfflineWithoutCache`, `SourceErrorWithoutCache`, `NoFavorites`, `NoSearchResults`, `NoFilterResults`, `Items` |
| Detay | `DetailUiState` | `Loading`, `Error` (önbellek yok), `ListDataOnly` (detay yok, liste verisi var), `Full` |

Harita ve liste aynı `items` listesini ve aynı `selectedParkId` değerini kullanıyor.
Listeden "Haritada göster" seçimi haritaya, haritadaki seçim listeye taşınıyor.

## Güvenlik ve gizlilik

- Release derlemesinde OkHttp log kütüphanesi classpath'te bile yok
  (`debugImplementation`, `src/debug/.../DebugNetworkModule.kt`).
- Cleartext izni yalnızca debug'da ve yalnızca `localhost` / `127.0.0.1` için var. Amacı
  cihaz testlerindeki sahte sunucu.
- Konum yalnızca cihazda, sıralama için kullanılıyor. Hiçbir yere gönderilmiyor ve
  saklanmıyor.
- API anahtarı ve imzalama sırları repoda yok (`secrets.properties`, `keystore.properties`
  `.gitignore` içinde).
