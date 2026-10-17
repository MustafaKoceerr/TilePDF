# Dil ve tema kütüphanelerinin entegrasyonu

## Bağlam

Kullanıcı dil ve tema işini kendi kütüphanelerine taşıdı ve TilePDF'in bunları JitPack'ten kullanmasını istiyor:
- `compose-theme-manager` 2.0.0
- `compose-language-manager` 2.1.0

Bugünkü durum ve hedef:
- Phase 6b'de yazılan dil kodu kaldırılacak: `AppLanguageRepository`, `AppCompatLanguageRepository`, `LanguageModule`, manifestteki `AppLocalesMetadataHolderService` ve kütüphane ⋮ menüsündeki dil diyaloğu.
- `TilePDFTheme` bugün her zaman sistem karanlık modunu ve dinamik rengi kullanıyor; kullanıcı seçemiyor.
- Dil ve tema, kütüphane menüsünden açılan ayrı bir **Ayarlar ekranından** yönetilecek. Bu kararı kullanıcı verdi.
- Dinamik renk bir seçenek olacak, varsayılanı kapalı. Bu da kullanıcı kararı; kütüphanenin varsayılanıyla aynı.
- Yedekleme kuralları ve Phase 7'nin geri kalanı bu işten sonra ele alınacak.

**Zorunlu ön koşul:** iki kütüphane de Kotlin 2.4.21 ve Compose BOM 2026.09.00 ile derlenmiş. TilePDF'teki Kotlin 2.2.10 bu kütüphanelerin metadata'sını okuyamaz, bu yüzden araç zinciri önce yükseltiliyor. KSP 2.3.12 iki tarafta da aynı.

Dal: `phase6c-adaptive`'den yeni bir `language-theme-libraries` dalı açılacak ve PR #12'nin üstüne yığılacak. Bu plan uygulamaya geçerken `docs/superpowers/plans/2026-10-18-language-theme-libraries.md` olarak repoya kopyalanır ve ledger ile yürütülür (executing-plans).

## Adımlar

### 1. Araç zinciri ve JitPack
- `gradle/libs.versions.toml`: `kotlin = "2.4.21"`, `composeBom = "2026.09.00"`, `appcompat = "1.8.0"`.
- Yeni girdiler:
  - `themeManager = "2.0.0"` ile `com.github.MustafaKoceerr.compose-theme-manager:theme-manager`, `theme-manager-compose`, `theme-manager-hilt` ve `theme-manager-testing`;
  - `languageManager = "2.1.0"` ile `com.github.MustafaKoceerr.compose-language-manager:language-manager`, `language-manager-hilt` ve `language-manager-testing`;
  - `androidx-core-splashscreen`.
- `settings.gradle.kts` → `dependencyResolutionManagement.repositories`: `maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.MustafaKoceerr.*") } }`. JitPack yalnızca kullanıcının kendi grubuna açılıyor; başka bir bağımlılık oradan çözülemez.
- Doğrulama: `assembleDebug testDebugUnitTest :core:model:test lintDebug` yeşil. Kotlin 2.4'ün yeni uyarılarını ve hatalarını düzeltirken davranışa dokunmuyorum. Commit: "Kotlin 2.4.21, Compose BOM 2026.09.00, JitPack for own libraries".

### 2. Dil: language-manager
- **Silinecekler:**
  - `core/data/.../preferences/AppLanguageRepository.kt`;
  - `app/.../language/AppCompatLanguageRepository.kt` ve `app/.../di/LanguageModule.kt`;
  - `app/build.gradle.kts` içindeki doğrudan `appcompat` bağımlılığı bırakılıyor, çünkü `MainActivity` bir `AppCompatActivity` ve buna ihtiyaç duyuyor;
  - manifestteki `AppLocalesMetadataHolderService` bloğu siliniyor, çünkü kütüphane kendi manifestinden bunu birleştiriyor.
- **Değişmeyenler:** `MainActivity : AppCompatActivity`, AppCompat teması, `generateLocaleConfig`, `localeFilters` ve `resources.properties`. Kütüphane README'si de bunları istiyor.
- `core/model/.../AppLanguage.kt` (System/English/Turkish ve `fromTag`) uygulamanın desteklediği dillerin listesi olarak kalıyor; README de böyle bir uygulama enum'u öneriyor.
- Eşleme yeni Ayarlar modülünde, `LanguageMapping.kt` içinde:
  - `LanguageMode.toAppLanguage()`: `SystemDefault` → `System`, `Custom(tag)` → `AppLanguage.fromTag(tag)`;
  - `AppLanguage.toLanguageMode()`: `System` → `SystemDefault`, diğerleri → `Custom(tag)`.
- `app` modülüne `language-manager-hilt` eklenir; `LanguageManager` singleton olarak gelir.

### 3. Tema: theme-manager
- `core/designsystem`: `api(theme-manager-compose)` eklenir.
  - `TilePDFTheme(settings: ThemeSettings = ThemeSettings(), content)` artık `ManagedTheme(settings, LightColorScheme, DarkColorScheme, typography = Typography)` çağırıyor.
  - Bugünkü `darkTheme`/`dynamicColor` parametreleri ve elle yazılmış dinamik renk mantığı kalkıyor.
  - Önizlemeler ve testler `TilePDFTheme { }` olarak değişmeden çalışmaya devam ediyor.
- `app` modülüne `theme-manager-hilt` ve `core-splashscreen` eklenir. Ardından:
  - Yeni `app/.../MainActivityViewModel.kt`: `@HiltViewModel`, `ThemeManager` alıyor ve `themeState: StateFlow<ThemeState>` sunuyor.
  - `MainActivity`:
    - `installSplashScreen()` çağrısı `super.onCreate`'ten önce yapılıyor;
    - `setKeepOnScreenCondition { themeState.value is ThemeState.Loading }`;
    - `setContent { TilePDFTheme(themeState.settingsOrDefault) { … } }`.
  - `themes.xml`: `Theme.TilePDF.Starting` (parent `Theme.SplashScreen`, `postSplashScreenTheme` = `@style/Theme.TilePDF`) ekleniyor ve manifestte activity teması olarak kullanılıyor.
- Okuyucunun sayfa temaları (Açık/Gece/Sepya) ayrı bir şey ve dokunulmuyor. Okuyucudaki `SystemBarsEffect`, tam ekranda çubukları gizlemeye devam ediyor; `ManagedTheme` yalnızca simge rengini ayarlıyor.

### 4. Ayarlar ekranı: yeni `feature:settings` modülü
- `settings.gradle.kts` dosyasına `:feature:settings` eklenir.
- Modül `tilepdf.android.feature` eklentisini kullanıyor. Bağımlılıkları: `theme-manager`, `language-manager`; test için `theme-manager-testing` ve `language-manager-testing`.
- `SettingsViewModel(themeManager: ThemeManager, languageManager: LanguageManager)`:
  - durumu: `uiState: StateFlow<SettingsUiState>`;
    - `Loading`: tema okunana kadar;
    - `Success(theme: ThemeSettings, language: AppLanguage, dynamicColorAvailable: Boolean)`;
  - işlevleri: `onThemeModeSelected(ThemeMode)`, `onDynamicColorChanged(Boolean)`, `onLanguageSelected(AppLanguage)`.
  - Dil, `languageManager.languageMode` akışından geliyor. Böylece sistem ayarlarından yapılan değişiklik de görünüyor; 6b'den ertelenen küçük konu da çözülüyor.
- `SettingsScreen`: `TopAppBar`, geri düğmesi ve iki bölüm.
  - **Tema:** Sistem varsayılanı, Açık ve Koyu için radyo satırları. "Dinamik renk" anahtarı yalnızca `isDynamicColorAvailable` ise görünüyor; açıklaması "Duvar kağıdından renk al".
  - **Dil:** Sistem varsayılanı, English ve Türkçe. Bugünkü `LanguageDialog` satırları buraya taşınıyor; dil adları kendi dillerinde kalıyor.
  - Satırların hepsi 48 dp, `selectable`, `Role.RadioButton` ya da `Switch`.
- Navigasyon: `navigation/SettingsNavigation.kt` içinde `@Serializable data object SettingsRoute`, `NavGraphBuilder.settingsScreen(onBack)` ve `NavController.navigateToSettings()`. Mevcut `LibraryNavigation.kt` desenini izliyor.
- Metinler `values/` ve `values-tr/` altında:
  - `settings_title`: Settings / Ayarlar;
  - `settings_theme`: Theme / Tema;
  - `settings_theme_system`: System default / Sistem varsayılanı;
  - `settings_theme_light`: Light / Açık;
  - `settings_theme_dark`: Dark / Koyu;
  - `settings_dynamic_color`: Dynamic color / Dinamik renk;
  - `settings_dynamic_color_summary`: Use colors from your wallpaper / Duvar kağıdından renk al;
  - `settings_language`: Language / Dil;
  - `language_system`: System default / Sistem varsayılanı;
  - `language_english` ve `language_turkish`: translatable=false.
- **Kütüphane tarafı:**
  - `LibraryViewModel`, `LibraryUiState`, `LibraryScreen` ve `LibraryViewModelTest` içinden dil ile ilgili her şey çıkarılıyor (`language`, `onLanguageSelected`, `LanguageDialog`, `AppLanguage.labelRes`, sahte `Languages` sınıfı ve `languageIsShownAndSet` testi).
  - ⋮ menüsündeki "Language" girdisi "Settings" / "Ayarlar" oluyor ve `LibraryActions.onOpenSettings` çağrılıyor.
  - Kullanılmayan `library_language`, `library_cancel` ve `language_*` metinleri kütüphaneden siliniyor.
- `app/.../navigation/TilePdfNavHost.kt`: `libraryScreen(onOpenDocument, onOpenSettings = navController::navigateToSettings)` ve `settingsScreen(onBack = navController::popBackStack)`.
- README: Ayarlar ekranı ve iki kütüphane anlatılıyor.

## Testler (TDD: önce başarısız JVM testi)
- `feature/settings/src/test/.../LanguageMappingTest.kt`:
  - `systemDefaultIsSystem`;
  - `customTagsMapByLanguage` (`Custom("tr-TR")` → Turkish);
  - `unknownTagIsSystem`;
  - `roundTrip`.
- `feature/settings/src/test/.../SettingsViewModelTest.kt`, `FakeThemeManager` ve `FakeLanguageManager` ile:
  - `loadingUntilThemeIsRead`;
  - `themeModeIsSaved` (`setThemeModeCalls == [DARK]`);
  - `dynamicColorIsSaved`;
  - `languageIsSet` (`setLanguageCalls == [Custom("tr")]`, durum Turkish);
  - `externalLanguageChangeIsShown` (`simulateExternalChange`).
- Çalıştırma: `./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsViewModelTest"` (ve `*LanguageMappingTest`), ardından `:feature:library:testDebugUnitTest --tests "*LibraryViewModelTest"`.
- UI testi `feature/settings/src/androidTest/.../SettingsScreenTest.kt` (kullanıcı onayıyla): `selectingDarkReports` ve `selectingTurkishReports`. Mevcut `LibraryScreenTest` ve `LibraryAccessibilityTest` de güncellenip yeniden çalıştırılıyor; dil diyaloğu testi Ayarlar ekranına taşınıyor.

## Doğrulama
1. `assembleDebug assembleRelease testDebugUnitTest :core:model:test lintDebug` yeşil; lint `MissingTranslation` da temiz.
2. Emülatörde elle kontrol (onayla):
   - Kütüphane ⋮ → Ayarlar ekranı açılıyor.
   - Koyu, Açık ve Sistem varsayılanı anında uygulanıyor ve yeniden başlatmada korunuyor; açılışta yanlış temanın bir anlık görünmesi (flaş) olmuyor.
   - Dinamik renk anahtarı API 36'da görünüyor ve çalışıyor.
   - Türkçe seçilince uygulama Türkçeye dönüyor ve yeniden başlatmada korunuyor.
   - Dil sistem ayarlarından (Uygulama dilleri) değiştirilince Ayarlar ekranı bunu gösteriyor.
   - Okuyucuda sayfa temaları ve tam ekran eskisi gibi çalışıyor; koyu temada sistem çubuğu simgeleri okunuyor.
3. Push ve PR #13 (#12'nin üstüne yığılı).

## Sonra (bu işin kapsamı dışında)
- Phase 7 planı güncellenecek:
  - Yedekleme kuralına tema DataStore'u ve dil kütüphanesinin Android 12 ve altında kullandığı AppCompat dosyası eklenecek.
  - "Hakkında" girdisi artık kütüphane menüsü yerine Ayarlar ekranına gidecek.
