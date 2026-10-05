# ADR-0011: Kütüphane sürümleri

Sürümler 2026-10-04 tarihinde resmî kaynaklardan doğrulandı:
developer.android.com (AGP ve AndroidX sürüm tabloları, Compose BOM eşlemesi) ve Maven
Central metadata.

| Bileşen | Sürüm | Kaynak / not |
|---|---|---|
| AGP | 9.4.0 | developer.android.com. Gradle ≥ 9.6.0, JDK 17+, build-tools 36.0.0 gerektiriyor |
| Gradle | 9.6.0 | services.gradle.org |
| Kotlin (KGP) | 2.4.10 | Maven Central. `maps-compose` 8.6.0, stdlib 2.4.10 istiyor. AGP built-in Kotlin, buildscript classpath ile yükseltildi |
| KSP | 2.3.12 | Maven Central |
| Compose BOM | 2026.09.00 | developer.android.com BOM eşlemesi |
| Activity | 1.13.0 | AndroidX |
| AppCompat | 1.7.1 | AndroidX. Uygulama içi dil için ([ADR-0013](0013-uygulama-ici-dil.md)). Bu ortamdan Google Maven'a erişilemediği için varlığı bilinen sürüm seçildi. Çözümlenip çözümlenmediği CI derlemesinde görülüyor |
| Lifecycle | 2.11.0 | AndroidX |
| Navigation | 2.10.2 | AndroidX |
| Room | 2.8.5 | AndroidX |
| Core | 1.19.1 | AndroidX |
| Hilt (Dagger) | 2.60.1 | Maven Central |
| AndroidX Hilt | 1.4.0 | AndroidX (`hilt-lifecycle-viewmodel-compose`) |
| Retrofit | 3.0.0 | Maven Central |
| OkHttp | 5.5.0 | Maven Central |
| kotlinx.serialization | 1.11.0 | Maven Central (1.12.0 henüz RC) |
| Coroutines | 1.11.0 | Maven Central |
| maps-compose (+utils) | 8.6.0 | Maven Central. 9.0.0 üç gün önce çıktığı ve büyük sürüm olduğu için bilinçli olarak bekletildi |
| play-services-location | 21.3.0 | developers.google.com ve Google Maven bu ortamdan erişilemedi. Varlığı bilinen sürüm seçildi (21.4.0 çıktığı bildiriliyor, backlog) |
| Robolectric | 4.17 | Maven Central |
| androidx.test | core/runner/rules 1.7.0, ext.junit 1.3.0, espresso 3.7.0 | AndroidX |

compileSdk = 37 (AGP 9.4'ün desteklediği en yüksek API. Core 1.19 ve Activity 1.13 AAR'ları
yeni compileSdk isteyebiliyor), targetSdk = 36 (Android 17 davranış değişiklikleri cihazda
test edilmeden hedeflenmedi. Backlog'da), minSdk = 26 (java.time ve adaptive icon desteği,
cihazların büyük çoğunluğu).
