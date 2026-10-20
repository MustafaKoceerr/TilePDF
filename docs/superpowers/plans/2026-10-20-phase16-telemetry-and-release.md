# Faz 16: Crashlytics + Analytics ve Play'de yayın

## Bağlam

TilePDF v1 hazır (master + açık PR #31 UI cilası). Kullanıcı yayından önce çökme raporu
(Crashlytics) ve kullanım istatistiği (Google Analytics for Firebase) istiyor, ardından
uygulamayı Play'e çıkarmak istiyor. Şu an uygulama bilerek **internetsiz**: manifest
`INTERNET`'i siliyor (`app/src/main/AndroidManifest.xml:5`), build izin allowlist'i
(`build-logic/.../ReleasePermissions.kt`) INTERNET'i reddediyor, ve mağaza metni, gizlilik
politikası, Hakkında ekranı, Data safety cevapları "veri toplamaz, internete çıkmaz" diyor.
Bu faz o vaadi bilinçli olarak değiştirir ve her yerde tutarlı hale getirir.

**Onay modeli (kullanıcı kararı: "AB'den geçiyorsa 2"):** Sektörde çoğu uygulama çökme
raporu için ayrıca izin istemez; GDPR/KVKK'da çökme verisi uygulamayı çalışır tutmak için
"meşru menfaat" kapsamında savunulur (politikada açıklanır, kapatma imkânı verilir). Analytics
ise AB'de (ePrivacy) genelde onay gerektirir. Bu yüzden 2. seçenek hem yaygın pratik hem
savunulabilir (hukuki görüş değildir):
- **Crashlytics:** release'te varsayılan açık, Ayarlar'dan kapatılabilir.
- **Analytics:** kullanıcı onay verene kadar kapalı; tek seferlik onay kartı + Ayarlar anahtarı.
- Reklam kimliği yok (`AD_ID` izni kaldırılır, adid toplama kapalı), dosya adı/yolu/metin asla gönderilmez.

**Yayın:** Yeni kişisel Play hesabı → internal test → 12 testçi / 14 gün closed test →
production başvurusu. Gizlilik politikası ayrı, herkese açık bir repoda GitHub Pages ile
(TilePDF reposu private olduğu için).

## 0. Firebase projesi (birlikte)

1. Kullanıcı: `! firebase login` (Firebase CLI şu an oturumsuz).
2. Ben (Firebase MCP): `firebase_create_project` → id `tilepdf-app` (alınmışsa ek ekli), ad "TilePDF";
   `firebase_create_app` Android, paket `com.mustafakoceerr.tilepdf`; `firebase_get_sdk_config` →
   `app/google-services.json`.
3. Kullanıcı (konsol): Google Analytics'i projeye bağla (Integrations), Crashlytics'i etkinleştir,
   Analytics'te veri saklama süresini ve "Google sinyalleri"ni kapalı bırak.
4. `google-services.json` **repoya girmez** (global kural: anahtarlar repoda değil) → `.gitignore`;
   `docs/RELEASING.md`'ye "nasıl alınır" adımı.

## 1. Build kurulumu

- `gradle/libs.versions.toml`: Firebase BoM, `firebase-analytics`, `firebase-crashlytics-ndk`;
  pluginler `com.google.gms.google-services`, `com.google.firebase.crashlytics` (sürümler
  context7/Maven'dan, AGP 9.4.1 uyumu kontrol edilerek).
- `app/build.gradle.kts`: iki plugin yalnızca `google-services.json` varsa uygulanır (temiz klonda
  ve testlerde build kırılmasın; o durumda telemetri no-op). Release'te
  `CrashlyticsExtension { nativeSymbolUploadEnabled = true; mappingFileUploadEnabled = true }`,
  `unstrippedNativeLibsDir` = birleşmiş native lib'ler (PDFium JNI kütüphaneleri `core:pdf` vb.
  modüllerde CMake ile derleniyor; libpdfium.so fork release'inden hazır geliyor — sembolü yoksa
  JNI katmanı yine okunur).
- Manifest:
  - `INTERNET`'in `tools:node="remove"` satırı ve yorumu kaldırılır.
  - `com.google.android.gms.permission.AD_ID` → `tools:node="remove"`;
    `google_analytics_adid_collection_enabled=false`, `google_analytics_default_allow_ad_personalization_signals=false`.
  - `firebase_analytics_collection_enabled=false` (onaya kadar).
  - `firebase_crashlytics_collection_enabled=${crashlyticsEnabled}` → debug'da `false`, release'te `true`
    (manifestPlaceholders).
- `ReleasePermissions.kt` allowlist'ine `INTERNET` (+ Firebase'in getirdiği ve gerekli olanlar,
  ör. install referrer — build çıktısına göre; gereksizler `tools:node="remove"`).
  `ReleasePermissionsTest` güncellenir. Allowlist yorumu "ağ yalnızca Firebase için" olur.
- `proguard-rules.pro`: Firebase kendi kurallarını getirir; R8 release'te deneme şart (geçmiş
  ML Kit dersi).

## 2. Kod: `core:analytics` modülü (Now in Android deseni)

- `AnalyticsHelper` arayüzü + `AnalyticsEvent(type, extras)`; `FirebaseAnalyticsHelper`,
  `NoOpAnalyticsHelper` (Firebase başlatılmamışsa / debug), `core:testing`'de `TestAnalyticsHelper`.
  Compose için `LocalAnalyticsHelper` + `TrackScreenViewEvent(screenName)`.
- `CrashReporter` arayüzü: `recordNonFatal(throwable)`, `log(message)`; Firebase ve no-op uygulamaları.
- Firebase yoksa (`FirebaseApp.getApps(context).isEmpty()`) Hilt modülü no-op bağlar.
- **Tercihler:** `core:data` içinde `TelemetryPreferencesRepository` (DataStore,
  `DataStoreAppPaletteRepository` deseni): `crashReportsEnabled: Flow<Boolean>` (vars. true),
  `analyticsConsent: Flow<AnalyticsConsent>` (`Unasked/Granted/Denied`, vars. Unasked).
- `TilePdfApp.onCreate`: tercihleri `applicationScope`'ta dinleyip
  `setCrashlyticsCollectionEnabled` / `setAnalyticsCollectionEnabled` + consent'e yansıtan
  `ApplyTelemetryPreferencesUseCase` (mevcut `CleanUpCopiesUseCase` çağrı deseni).
- **Olaylar** (yalnızca kategorik değerler, kişisel/dosya verisi yok):
  `screen_view` (library, reader, settings, about), `document_opened` (kaynak: picker/open_with/share;
  sayfa sayısı aralığı), `document_open_failed` (`OpenFailure` türü), `annotation_saved` (tür),
  `ocr_run` (sonuç), `form_saved`, `search_used`, `share_or_print`.
  Her biri ilgili ViewModel'de, `AnalyticsHelper` enjekte edilerek. `OpenFailure` dışı beklenmeyen
  hatalar `CrashReporter.recordNonFatal`'a.
- **ML Kit:** INTERNET açılınca ML Kit kendi kullanım metriklerini Google'a gönderebilir. Uygulamada
  kapatma yolu araştırılır; yoksa Data safety ve politikada beyan edilir.

## 3. UI

- **Ayarlar** (`feature/settings`): yeni "Gizlilik" bölümü: "Çökme raporları gönder",
  "Anonim kullanım istatistikleri" anahtarları + politika bağlantısı. `SettingsViewModel`'e
  `onCrashReportsChanged`, `onAnalyticsChanged`; `SettingsUiState.Success`'e iki alan.
- **Onay kartı** (`feature/library`): `analyticsConsent == Unasked` iken kütüphane üstünde
  kapatılabilir kart: "TilePDF'i geliştirmemize yardım et — anonim kullanım istatistikleri.
  [Hayır] [İzin ver]". Seçim kaydedilir, bir daha görünmez.
- **Hakkında:** `about_offline` metni → "Dosyaların ve notların bu cihazda kalır. Yalnızca
  çökme raporları ve (izin verirsen) anonim istatistikler gönderilir." EN + TR.
- Okuyucudaki `signatures_revocation_note` ("TilePDF çevrimdışı çalışır") → "iptal durumu çevrimiçi
  denetlenmez" diye düzeltilir.

## 4. Belgeler ve mağaza metni

- `docs/play/privacy-policy.md` + `-tr.md`: Firebase Crashlytics/Analytics bölümü (ne toplanır:
  cihaz modeli, OS, uygulama sürümü, çökme yığını, Firebase kurulum kimliği, kategorik olaylar;
  ne toplanmaz: dosyalar, içerik, adlar, konum, reklam kimliği; saklama süresi; nasıl kapatılır;
  Google'ın işleyici olarak rolü), ML Kit beyanı, yeni tarih.
- `docs/play/data-safety.md`: Toplanan: Crash logs, Diagnostics, App interactions, Device or other IDs;
  amaç Analytics/App functionality; aktarımda şifreli: Evet; paylaşım: Hayır (Google işleyici);
  Analytics isteğe bağlı. Advertising ID: kullanılmıyor. İzin tablosuna INTERNET.
- `fastlane/metadata/android/{en-US,tr-TR}`: "No internet" iddiaları kaldırılır
  (kısa: "Fast, private PDF reader with notes. No ads, no account.").
- `README.md` "offline" kısımları; `docs/RELEASING.md`'ye Firebase + Play adımları.

## 5. Yayın

1. Gizlilik politikası: `gh` ile herkese açık `MustafaKoceerr/tilepdf-privacy` reposu, EN/TR HTML
   sayfaları, GitHub Pages → URL Ayarlar'daki bağlantıya ve Play Console'a.
2. Kullanıcı: upload key (`docs/RELEASING.md` §1), dört env değişkeni.
3. `bundleRelease` → Crashlytics mapping + native sembolleri otomatik yükler.
4. Kullanıcı (Play Console): uygulama oluştur, mağaza girişi (fastlane metinleri), içerik derecelendirme,
   hedef kitle, reklam yok, Data safety (yeni cevaplar), politika URL'si, internal test'e AAB.
5. Closed test: 12 testçi, 14 gün; bu sürede Crashlytics/Analytics panelleri izlenir.
6. Production başvurusu (aşamalı yayın).

## İş akışı

- Plan dosyası `docs/superpowers/plans/2026-10-20-phase16-telemetry-and-release.md`; branch
  `phase16-telemetry` (#31'in üstüne, stacked PR).
- TDD yalnız unit testler: `TelemetryPreferencesRepository`, `ApplyTelemetryPreferencesUseCase`
  (fake SDK sınırı), `SettingsViewModel`, kütüphane ViewModel'inin onay kartı durumu, olay atan
  ViewModel'ler (`TestAnalyticsHelper` ile), `ReleasePermissions`. Firebase SDK'nın kendisi test edilmez.

## Doğrulama

- Modül başına filtreli unit testler, iş sonunda tam `testDebugUnitTest` bir kez.
- `assembleRelease -Ptilepdf.signReleaseWithDebug=true` → `checkReleasePermissions` geçer;
  merged manifest'te INTERNET var, AD_ID yok.
- Emülatörde (A52'de değil, izin almadan) R8 release: geçici bir debug menü/adb ile test çökmesi
  (Java + native) → Crashlytics panelinde sembolize yığın; `adb shell setprop debug.firebase.analytics.app
  com.mustafakoceerr.tilepdf` + Analytics DebugView'da onay öncesi olay yok, onay sonrası olaylar var;
  Ayarlar'dan kapatınca gönderim durur.
- Instrumented/UI testler yalnız kullanıcı isterse.
