# TilePDF v1 — Tasarım

Tarih: 2026-10-16 · Durum: incelemede

## Amaç

TilePDF'i Google Play'e çıkacak kalitede bir **PDF okuyucu + açıklama**
uygulamasına dönüştürmek. Başarı ölçütü: API 29 ve üstündeki her kullanıcı
aynı hızlı, stabil deneyimi yaşar; vurguladığı dosyayı başka bir uygulamada
açtığında açıklamaları orada görür.

## Kararlar

| Konu | Karar | Gerekçe |
|---|---|---|
| Konum | Okuyucu + açıklama | Düzenleme araçları (birleştir, böl, imza, form) v1 dışı |
| Açıklama saklama | PDF'in içine, standart annotation | Sektör beklentisi (Acrobat, Xodo, Drive); dosya paylaşıldığında açıklamalar taşınır |
| Kayıt | Otomatik, güvenli | Room taslağı → geçici dosya → doğrula → SAF ile değiştir |
| Gelir | Tamamen ücretsiz | Reklam/billing yok, `INTERNET` izni yok; Data Safety: veri toplanmıyor |
| Motor | Tek motor: PDFium | Aşağıya bakın |
| Mimari | NiA modülerleşme, UDF, ViewModel + `StateFlow` | `mad-architecture` |
| SDK | minSdk 29, targetSdk 37 | |

### Neden PDFium, neden tek motor

Android'in `PdfRenderer`'ı içeride PDFium kullanır. Android 15'te gelen metin,
arama, seçim ve form API'leri ve sonrasında gelen annotation API'leri PDFium
yeteneklerinin Java'ya açılmasıdır. `androidx.pdf` en erken API 31'e iner;
API 29-30 bu özellikleri hiç almaz.

API'ye göre motor ayırmak aynı PDF'in iki motorda farklı render edilmesine,
açıklama konumlarının kaymasına ve çift test yüküne yol açar, hız kazancı da
getirmez. PDFium'u doğrudan paketlemek:

- Lisans: BSD/Apache; ticari kullanım dahil serbest.
- Chrome ve Android'in motoru; en çok test edilmiş seçenek.
- Native; PdfBox'tan (Java) hızlı ve az bellek kullanır.
- Render, metin/arama/seçim, linkler, bookmark, şifre, annotation oluşturma,
  artımlı kayıt (`FPDF_SaveAsCopy` + `FPDF_INCREMENTAL`) tek API'de.

Derleme: `bblanchon/pdfium-binaries` hazır Android derlemeleri (arm64-v8a,
armeabi-v7a, x86_64, x86; 32-bit x86 emülatörler için x86 da paketlenir).
Sürüm sabitlenir (`chromium/8076`), arşivler SHA-256 ile doğrulanarak build
sırasında indirilir; repoya binary girmez. Bizim kodumuz yalnızca ince bir JNI
köprüsü.
PdfBox-Android ve `PdfRenderer` tamamen kaldırılır.

Riskler ve önlemler:

- PDFium thread-safe değil → tüm çağrılar tek bir `limitedParallelism(1)`
  dispatcher'ından geçer.
- Native çökme → her JNI sınırında null/hata kontrolü; bozuk, kesik ve
  şifreli PDF'lerle instrumented testler.
- APK boyutu (~4-5 MB/ABI) → App Bundle ile her cihaz kendi ABI'sini indirir.
- 16 KB sayfa → NDK r28+, `.so` hizalaması testte doğrulanır.

## v1 kapsamı

**Okuma**
- Kütüphane: son okunanlar, favoriler, sıralama, arama
- "Birlikte aç" / "Paylaş" intent'i
- Şifreli PDF (şifre diyaloğu)
- Tile render, pürüzsüz zoom/kaydırma (mevcut boru hattı)
- Dikey sürekli ve yatay tek sayfa modları
- Kalınan sayfayı hatırlama
- İçindekiler, sayfaya atla, küçük resim ızgarası, yer imleri
- Metin arama, seçme, kopyalama, paylaşma
- Gece ve sepya teması, ekranı açık tut, dokununca tam ekran
- Dahili linkler sayfaya, harici linkler tarayıcıya

**Açıklama**
- Seçilen metin üzerinde vurgu, altı çizili, üstü çizili
- Serbest kalem ve silgi
- Yapışkan not
- Geri al/yinele, renk ve kalınlık seçimi
- Açıklama listesi (tıklayınca sayfaya git)
- Otomatik kayıt; yazma izni yoksa "Kopya olarak kaydet"

**Kalite**
- Material 3, dinamik renk, edge-to-edge
- Tablet ve katlanabilir (TOC yan panelde)
- TR/EN
- Erişilebilirlik (TalkBack etiketleri, dokunma hedefleri, kontrast)
- Tipli hata ekranları: bozuk dosya, yanlış şifre, bellek, izin kaybı

**v1 dışı:** OCR, form doldurma, imza, birleştir/böl, bulut senkronizasyonu,
metni sesli okuma, benchmark/hız testleri.

## Modüller

```
app                  MainActivity, NavHost, Hilt kökü, intent işleme
feature:library      kütüphane ekranı
feature:reader       sayfa listesi, zoom, arama, seçim, TOC, küçük resim, yer imi
feature:annotation   araç çubuğu, araçlar, geri al/yinele, açıklama listesi
core:pdf             motor modülü: PDFium JNI köprüsü (`Pdfium`, `PdfiumDocument`); Aşama 3'e kadar PdfRenderer da burada
core:render          tile, BitmapPool, BitmapCache
core:data            repository'ler
core:database        Room: belgeler, yer imleri, açıklama taslakları
core:datastore       okuyucu tercihleri
core:domain          use case'ler
core:model           saf Kotlin modeller (PdfRect, Annotation, Bookmark…)
core:common          dispatcher qualifier'ları, ApplicationScope
core:designsystem    tema, renkler, tipografi
core:ui              paylaşılan Compose bileşenleri
core:testing         fake'ler, test PDF'leri, test kuralları
build-logic          convention plugin'leri
```

`core:ui` ve `feature:annotation` ilk kodlarını aldıklarında (Aşama 5-6)
oluşturulur; boş modül açılmaz.

Bağımlılık yönü: `feature → core:domain → core:data → core:{pdf,database,datastore}`;
feature modülleri birbirine bağımlı değildir. `core:model` her yerden kullanılabilir.

Mevcut koddan taşınanlar: `core/geometry` → `core:model`; `core/render` →
`core:render`; `core/text` (TextSearcher, LineMerger, TextSelection) →
`core:data` (TextSearcher data katmanında kullanılıyor ve TextSelection ile
`internal LineMerger`'ı paylaşıyor; domain'e koymak katman yönünü ters çevirirdi); `data/*` ve `di/*` → ilgili `core:*`; `feature/*` → feature
modülleri; `app/schemas` → `core:database` (migration testi korunur).

## Veri akışı

- ViewModel'ler `StateFlow<UiState>` yayar; UI olayları ViewModel fonksiyonlarını
  çağırır. Tek yönlü akış.
- Repository'ler PDFium'a `PdfEngine` arayüzü üzerinden erişir; testlerde fake.
- Hatalar tipli sonuç olarak döner (`OpenError.Corrupt | PasswordRequired |
  WrongPassword | OutOfMemory | PermissionLost`), UI her birini kendi ekranına
  eşler.

### Açıklama kayıt akışı

1. Her düzenleme Room'a taslak olarak yazılır (belge URI'si + annotation
   listesi + sürüm). Çökmede kaybolmaz; geri al/yinele buradan beslenir.
2. Ekrana taslak açıklamalar Compose katmanı olarak çizilir; kaydedilmiş
   olanları PDFium sayfayla birlikte render eder.
3. Belge kapanınca veya uygulama arka plana geçince `SaveAnnotationsWorker`
   (WorkManager, belge başına tekil iş) çalışır:
   PDFium ile annotation'ları ve appearance stream'leri yaz → artımlı kaydı
   önbellekteki geçici dosyaya al → geçici dosyayı PDFium ile yeniden açarak
   doğrula → SAF `openOutputStream(uri, "wt")` ile orijinalin yerine yaz.
4. Başarıda taslak temizlenir. Yazma izni yoksa (`SecurityException`, salt
   okunur sağlayıcı) taslak korunur ve kullanıcıya "Kopya olarak kaydet"
   (`ACTION_CREATE_DOCUMENT`) önerilir.
5. Dosya dışarıdan değiştiyse (boyut/son değişiklik farklı) üzerine yazılmaz;
   kopya önerilir.

## Test

- TDD: her davranış önce başarısız testle.
- JVM: domain, ViewModel, repository (fake `PdfEngine`, fake DAO'lar).
- Instrumented: `core:pdf` (PDFium) gerçek PDF'lerle (normal, şifreli, bozuk,
  kesik); kayıt akışının uçtan uca testi (yaz → yeniden aç → annotation var);
  Room migration.
- Roborazzi ekran görüntüsü testleri: reader, library, annotation.
- Benchmark/hız testi yok.

## Aşamalar

Her aşama kendi uygulama planıyla (writing-plans) ve yeşil testlerle biter.

0. Spec + benchmark temizliği
1. `build-logic` ve modül iskeleti; mevcut kodu davranış değiştirmeden taşı
2. `core:pdf` içine PDFium (okuma tarafı): açma/şifre, sayfa boyutu, render,
   metin + karakter kutuları, içindekiler, testler
3. Okuyucuyu PDFium'a geçir; PdfBox ve `PdfRenderer` kaldırılır. Performans,
   motoru değiştirerek değil kullanımıyla kazanılır (MuPDF AGPL, Poppler GPL;
   PDFium lisansı uygun en iyi C++ motor): görünür sayfalar native tarafta açık
   tutulur (karo başına parse yok), metin sayfası tek yüklenir, ekrandan çıkan
   karonun render'ı iptal edilir; etkisi gerçek cihazda Perfetto ile ölçülür
4. Eksik okuma özellikleri; PDFium link okuma burada eklenir
5. `feature:annotation` + taslak + güvenli kayıt; PDFium annotation yazma ve
   artımlı kayıt burada eklenir
6. Kütüphane, adaptive, TR/EN, erişilebilirlik, hata ekranları
7. Play hazırlığı: R8, imzalama (anahtarlar env'den), Data Safety, mağaza
   metinleri, PDFium ve diğer üçüncü taraf lisanslarının uygulama içinde gösterimi
