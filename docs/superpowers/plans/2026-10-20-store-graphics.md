# Play Store görselleri ve yeni ikon (yalnızca İngilizce)

## Bağlam

Faz 16 bitti (telemetri, politika, public repo). Geriye mağaza görselleri kaldı. Şu anki
`fastlane/metadata/android/*/images` görselleri test PDF'leriyle ("Page 1" yazılı, siyah
çubuklu sayfalar) çekilmiş, düz indigo ikon uygulamanın mor temasına uymuyor ve dosyalar
RGBA. Play, ekran görüntüsü ve kapak görselinde alfa kanalı kabul etmiyor. Kullanıcı
Play'e yakışır, kullanıcıyı çeken ama **yalan içermeyen** görseller istiyor. Kararlar:
- İkon yeniden tasarlanacak (sayfa + karo fikri korunacak, modernleşecek). Launcher ikonu da değişecek.
- Ekranlar "başlık + telefon çerçevesi" stilinde olacak.
- Örnek belgeler kamu malı eserlerden olacak.
- **Sadece İngilizce.** `tr-TR/images` silinecek; Play eksik yerelleştirmede varsayılan dilin görsellerini kullanır.

Doğruluk kuralları: Her ekran gerçek uygulamanın değiştirilmemiş R8 release build'inden
alınacak (sadece çerçeveye konacak). Başlıklar yalnızca var olan özellikleri anlatacak;
"en hızlı", "#1" gibi kanıtlanamayan iddialar ve olmayan özellikler kullanılmayacak.
Durum çubuğu Android demo modunda temiz olacak (12:00, tam pil).

## 1. Örnek belgeler (kamu malı, Project Gutenberg kaynaklı)

`fastlane/design/samples/` içinde, Chrome headless `--print-to-pdf --generate-pdf-document-outline`
ile HTML'den üretilecek. Gutenberg başlığı ve marka adı çıkarılacak; metinler ABD'de kamu malı.
- **alice.pdf**: *Alice's Adventures in Wonderland*, birkaç bölüm ve John Tenniel'in kamu malı illüstrasyonları. Güzel kapak, bölüm başlıkları (içindekiler paneli için outline), okunaklı serif dizgi. Okuma, vurgu, arama, tema ve tablette çift sayfa görünümü için kullanılacak.
- Kütüphane listesi için kısa kitaplar: *Pride and Prejudice*, *Frankenstein*, *The Art of War*, *Moby-Dick* (ilk bölümler + kapak sayfası). Küçük resimler ilk sayfadan gelecek.
- **scanned-page.pdf**: Kamu malı bir sayfanın görüntüsü (hafif eğim, kâğıt dokusu), metin katmanı yok. OCR ekranı için.
- **membership-form.pdf**: Kendi yazdığımız basit bir AcroForm "Library Membership Application" (metin alanları, onay kutusu, imza alanı). `reportlab` ile üretilecek; doldurma ve imza ekranı için.
- Üretim betiği: `fastlane/design/make_samples.py`. İndirilen dosyalar `fastlane/design/.cache/`'e gidecek (gitignore); üretilen PDF'ler commit'lenecek ki görseller tekrar üretilebilsin.

## 2. Yeni ikon

- Fikir aynı kalacak: kıvrık köşeli beyaz sayfa, alt yarısında 2×2 karo (PDF'in döşeme gibi çizilmesi). Değişenler: uygulamanın moruna (#6650A4) uyumlu mordan indigoya degrade zemin, karolarda iki ton mor/lavanta, sayfada yumuşak gölge. Sayfa adaptive ikonun 66 dp güvenli alanının içinde kalacak.
- Güncellenecek dosyalar: `app/src/main/res/drawable/ic_launcher_background.xml` (degrade, `aapt:attr`; minSdk 29), `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (themed icon).
- Önce SVG kaynağı yazılacak (`fastlane/design/icon/icon.svg`); 512×512 Play ikonu (32-bit PNG, ≤1 MB, tam kare, maskeyi Play uygular) aynı şekillerden üretilecek ki launcher ikonuyla birebir aynı olsun.
- Splash ekranı ve launcher'da emülatörde kontrol edilecek (yuvarlak, squircle ve themed ikon).

## 3. Ekran görüntüleri (gerçek uygulama, API35_Phone ve Pixel_Tablet emülatörü)

Hazırlık: R8 release'i debug imzayla kur, örnek PDF'leri `/sdcard/Download/TilePDF-store/`'a it,
demo modunu aç (`sysui_demo_allowed` + `am broadcast ... demo`). Uygulamayı `ui.py` ile (uiautomator
dump/tap) sür, `adb exec-out screencap` ile çek. Bu iş için Analytics onayı "No thanks" olacak, çünkü
kart ekranda görünmemeli; telemetri kapalı kalacak.

Telefon (8 adet, 1080×1920 çerçeveli, 24-bit PNG). Başlıklar kısa ve doğru:
1. "Read PDFs smoothly": Alice'in illüstrasyonlu sayfası
2. "Highlight, underline, add notes": seçili metin, vurgu ve not
3. "Fill in forms and sign": doldurulmuş form ve parmakla atılmış imza
4. "Search the whole document": "Rabbit" araması, sonuç sayacı
5. "Make scans searchable, on your phone": taranmış sayfada metin tanıma sonucu ya da seçim
6. "Easy on the eyes": sepya/gece sayfa teması
7. "Pick up where you left off": küçük resimli kütüphane, "Page x of y"
8. "No ads. No account.": "Your files never leave your phone" alt yazısı (doğru: belgeler cihazda kalıyor). Ekranda Ayarlar → Privacy görünecek.

Tablet (4 adet, 2560×1600, aynı dosyalar hem 7" hem 10" yuvasına):
içindekiler yan paneli, çift sayfa (kitap gibi), ızgara kütüphane, işaretleme.

## 4. Kapak görseli (1024×500, 24-bit PNG)

Mor degrade, yeni ikon, "TilePDF" ve "Read, mark up and fill in PDFs". Sağda hafif eğik bir
telefon çerçevesinde gerçek Alice ekranı. Play metni kenarlarda kırpabildiği için önemli öğeler
ortadaki güvenli alanda kalacak.

## 5. Üretim hattı (tekrar üretilebilir)

- `fastlane/design/`: `templates/` (HTML/CSS çerçeve: `phone.html`, `tablet.html`, `feature.html`),
  `fonts/` (Google Fonts'tan **Plus Jakarta Sans**, OFL; lisans dosyasıyla birlikte), `raw/`
  (ham ekran görüntüleri), `captions.json`, `render.py`. Akış: Chrome headless `--screenshot
  --window-size=W,H --force-device-scale-factor=1`, ardından Pillow ile alfa kaldırılıp RGB PNG'ye çevrilecek.
- Çıktılar: `fastlane/metadata/android/en-US/images/{icon.png, featureGraphic.png,
  phoneScreenshots/1..8.png, sevenInchScreenshots/1..4.png, tenInchScreenshots/1..4.png}`.
  Eski görseller ve `tr-TR/images/` silinecek.
- Gerekirse çerçeve başlıklarında Material Symbols (Apache 2.0) ikonları kullanılacak.

## 6. Test (TDD, unit)

`app/src/test/.../StoreListingTest.kt`'ye görsel kuralları eklenecek (önce kırmızı: mevcut RGBA görseller kalır):
ikon 512×512 PNG ≤1 MB; kapak 1024×500, alfasız; telefonda 2–8, tablette ≤8 ekran; her
kenar 320–3840 px; uzun kenar ≤ 2 × kısa kenar; alfa yok; dosya ≤8 MB. `javax.imageio` ile, kütüphane gerekmez.

## İş akışı

Branch `store-graphics` (master'dan), plan `docs/superpowers/plans/2026-10-20-store-graphics.md`, tek PR.
Emülatörler sırayla açılacak (aynı anda tek emülatör). Kullanıcının A52'sine dokunulmayacak.

## Doğrulama

- `:app:testDebugUnitTest --tests "*StoreListingTest"` yeşil.
- Tüm görseller bir kontak sayfasında gözle kontrol edilecek (okunaklılık, kırpılma, yazım, iddiaların doğruluğu).
- Yeni ikon emülatör launcher'ında (squircle + themed) ve splash'te kontrol edilecek, ekran görüntüsüyle kullanıcıya gösterilecek.
- `assembleDebug` ve `assembleRelease` vektör değişikliğinden sonra derlenecek.
