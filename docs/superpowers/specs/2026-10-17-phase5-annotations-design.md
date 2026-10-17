# Aşama 5 — Açıklama: tasarım kararları

Tarih: 2026-10-17 · Durum: incelemede · Üst belge: `2026-10-16-tilepdf-v1-design.md`

Ana spec'in "Açıklama" kapsamı ve "Açıklama kayıt akışı" geçerli. Bu belge
spec'in açık bıraktığı yerleri kapatıyor ve iki yerde onu daraltıyor
(**Spec'ten farklar**).

## Bölünme

Aşama 5 üç plana ve üç PR'a ayrılıyor; her biri tek başına test edilebilir:

- **5a — Motor:** açıklama modeli, PDFium ile okuma, yazma ve artımlı kayıt.
- **5b — Veri ve kayıt:** Room taslağı, geri al/yinele, `SaveAnnotationsWorker`,
  yazma izni, "Kopya olarak kaydet".
- **5c — Arayüz:** araç çubuğu, vurgu/altı/üstü çizili, kalem, silgi, not,
  renk/kalınlık, açıklama listesi.

## Kararlar

**Kimlik.** TilePDF'in oluşturduğu her açıklamanın `/NM` değeri
`tilepdf-<uuid>`. Bunlar "kendi açıklamalarımız". Diğer uygulamaların
açıklamaları (yabancı) PDFium tarafından sayfayla birlikte çizilir. Bunlar
listede görünür ve silinebilir, ama düzenlenemez. Yabancı bir açıklamanın
anahtarı, varsa `/NM` değeri, yoksa sayfadaki sırasıdır. Sıra, taslağın
dayandığı dosya sürümünde geçerlidir; dosya değişirse bu zaten bir
çakışmadır.

**Silme (sektör beklentisi).** Acrobat, Xodo ve Google'ın düzenleyicisinde
olduğu gibi, işaretleme türündeki her açıklama (vurgu, altı/üstü çizili,
dalgalı, kalem, not, serbest metin, şekiller, damga) dokunup "Sil" ile
silinebilir ve silme geri alınabilir. Form alanları, linkler ve popup'lar
listede görünmez ve silinemez. Bir açıklama silinince ona bağlı `/Popup` da
silinir.

**Silgi.** Silgi kalem çizgilerinde çalışır (kendi ya da yabancı) ve dokunduğu
çizgiyi bütün olarak siler (Apple ve Google'daki nesne silgisi). Vurgu ve
notlara etkisi yoktur. Kısmi silgi v1'de yok.

**Değişmezlik.** Kendi açıklamalarımız değiştirilmez, yeniden yazılır: bir
notun metnini düzenlemek eskisini silip yeni kimlikle eklemek demektir.
Böylece kayıt, kimlik kümelerinin farkından ibaret olur.

**Çizim.** Okuyucu belgeyi açarken kendi açıklamalarımızı PDFium'un
bellekteki kopyasında gizli işaretler (`FPDF_ANNOT_FLAG_HIDDEN`; dosyaya
yazılmaz). Hepsini, kayıtlı olsun olmasın, Compose katmanı çizer. Kendi
açıklamalarımızı kaydetmek ya da silmek karo yeniden render etmeyi
gerektirmez; kayıttan sonra hiçbir şey kaybolmaz ya da çift görünmez.
Silinen bir yabancı açıklama da bellekte gizlenir ve yalnızca o sayfanın
karoları yeniden render edilir. Silme geri alınırsa gizleme kaldırılır.

**Taslak = tam liste.** Room'da belge başına bir satır tutulur: kendi
açıklamalarımızın tam listesi, silinen yabancı açıklamaların anahtarları,
`dirty` bayrağı, taslağın dayandığı dosyanın boyutu ve son değişiklik
zamanı, son kayıt hatası. Geri al/yinele bellekte tutulan anlık
görüntülerle yapılır. Çökmede açıklamalar kalır, geri alma geçmişi kalmaz.
Başarılı bir kayıttan sonra geçmiş temizlenir, çünkü kaydedilmiş bir
yabancı silmesi artık geri getirilemez.

**İlk açılış.** Taslak yoksa sayfalar arka planda tek tek taranıp kendi
açıklamalarımız okunur ve temiz bir taslak oluşturulur (başka cihazda
açıklanmış dosyalar için). Tarama bitene kadar düzenleme kapalıdır.

**Kayıt.** Worker dosyayı kendisi açar, dosyadaki kendi açıklama kimliklerini
taslakla karşılaştırır, fazlaları siler, eksikleri ekler; taslaktaki yabancı
silmelerini uygular (sıra anahtarları değişiklikten önce çözülür). Appearance stream
üretmek için değişen sayfaları PDFium'a bir kez render ettirir. Ardından
spec'teki akış gelir: geçici dosyaya artımlı kayıt → yeniden açıp doğrulama →
SAF `"wt"` ile orijinalin üzerine yazma. Artımlı kayıt dosyayı yalnızca
uzattığı için `"wt"` desteklenmezse `"w"` kullanılır. Son yazma iptal
edilemez.

**Şifreli PDF.** Şifre diske, Room'a ya da loglara hiç yazılmaz. Okuyucu
doğru şifreyi süreç ömrü boyunca bellekte tutar ve kayıt worker'ı onu
kullanır. Süreç öldüyse kayıt "şifre gerekli" hatasıyla bekler ve belge bir
dahaki sefere şifreyle açılıp kapandığında yapılır.

**İzin.** Kütüphane açarken okuma izninin yanında yazma iznini de kalıcı
almaya çalışır. Yazma izni yoksa ya da dosya dışarıdan değiştiyse taslak
korunur; belge bir sonraki açılışta "Kopya olarak kaydet" önerir. Kopya
kaydedilince taslak kopyaya geçer ve okuyucu kopyayı açar.

## Spec'ten farklar

0. Açıklama arayüzü ayrı bir `feature:annotation` modülünde değil,
   `feature:reader` içinde (`feature.reader.annotation` paketi). Feature
   modülleri birbirine bağımlı olamaz ve bu arayüz okuyucu ekranının parçası.

1. Spec "kaydedilmiş olanları PDFium render eder" diyor. Kendi
   açıklamalarımızı kayıttan sonra da Compose çizer; PDFium yalnızca yabancı
   açıklamaları çizer. Böylece kayıttan sonra açık okuyucuda açıklamalar
   kaybolmaz, silme anında görünür.
2. Silgi, dokunduğu kalem çizgisini bütün olarak siler; çizginin bir
   parçasını silmez (nesne silgisi).
3. Geri alma geçmişi her başarılı kayıtta (belgeden çıkış ya da uygulamanın
   arka plana geçmesi) temizlenir.
