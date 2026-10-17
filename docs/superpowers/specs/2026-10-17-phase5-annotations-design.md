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
açıklamaları (yabancı) PDFium tarafından sayfayla birlikte çizilir, listede
salt okunur görünür ve silinemez.

**Değişmezlik.** Kendi açıklamalarımız değiştirilmez, yeniden yazılır: bir
notun metnini düzenlemek eskisini silip yeni kimlikle eklemek demektir.
Böylece kayıt, kimlik kümelerinin farkından ibaret olur.

**Çizim.** Okuyucu belgeyi açarken kendi açıklamalarımızı PDFium'un
bellekteki kopyasında gizli işaretler (`FPDF_ANNOT_FLAG_HIDDEN`; dosyaya
yazılmaz). Hepsini, kayıtlı olsun olmasın, Compose katmanı çizer. Kaydetmek
ya da silmek karo yeniden render etmeyi gerektirmez, kayıttan sonra hiçbir
şey kaybolmaz ya da çift görünmez.

**Taslak = tam liste.** Room'da belge başına bir satır tutulur: kendi
açıklamalarımızın tam listesi, `dirty` bayrağı, taslağın dayandığı dosyanın
boyutu ve son değişiklik zamanı, son kayıt hatası. Geri al/yinele bellekte
tutulan liste anlık görüntüleriyle yapılır. Çökmede açıklamalar kalır, geri
alma geçmişi kalmaz.

**İlk açılış.** Taslak yoksa sayfalar arka planda tek tek taranıp kendi
açıklamalarımız okunur ve temiz bir taslak oluşturulur (başka cihazda
açıklanmış dosyalar için). Tarama bitene kadar düzenleme kapalıdır.

**Kayıt.** Worker dosyayı kendisi açar, dosyadaki kendi açıklama kimliklerini
taslakla karşılaştırır, fazlaları siler, eksikleri ekler. Appearance stream
üretmek için değişen sayfaları PDFium'a bir kez render ettirir. Ardından
spec'teki akış gelir: geçici dosyaya artımlı kayıt → yeniden açıp doğrulama →
SAF `"wt"` ile orijinalin üzerine yazma. Artımlı kayıt dosyayı yalnızca
uzattığı için `"wt"` desteklenmezse `"w"` kullanılır. Son yazma iptal
edilemez.

**İzin.** Kütüphane açarken okuma izninin yanında yazma iznini de kalıcı
almaya çalışır. Yazma izni yoksa ya da dosya dışarıdan değiştiyse taslak
korunur; belge bir sonraki açılışta "Kopya olarak kaydet" önerir. Kopya
kaydedilince taslak kopyaya geçer ve okuyucu kopyayı açar.

## Spec'ten farklar

1. Spec "kaydedilmiş olanları PDFium render eder" diyor. Kendi
   açıklamalarımızı kayıttan sonra da Compose çizer; PDFium yalnızca yabancı
   açıklamaları çizer. Böylece kayıttan sonra açık okuyucuda açıklamalar
   kaybolmaz, silme anında görünür.
2. Silgi ve silme yalnızca kendi açıklamalarımız için geçerli. Silgi, dokunduğu
   açıklamayı bütün olarak siler; çizginin bir parçasını silmez.
