# TilePDF Gizlilik Politikası

*Yürürlük tarihi: 20 Ekim 2026*

TilePDF, Android için bir PDF okuyucudur. Belgeleriniz tamamen cihazınızda
açılır, okunur, işaretlenir ve kaydedilir.

## Kısaca

- **Dosyalarınız, içerikleri, dosya adları, açıklamalarınız ve yazdığınız her
  şey cihazınızda kalır.** Hiçbir yere gönderilmez.
- TilePDF, sorunların düzeltilebilmesi için **çökme raporları** gönderir.
  Bunları *Ayarlar → Gizlilik* bölümünden kapatabilirsiniz.
- TilePDF **anonim kullanım istatistiklerini yalnızca izin verirseniz**
  gönderir. Bir kez sorar; cevabınızı istediğiniz zaman *Ayarlar → Gizlilik*
  bölümünden değiştirebilirsiniz.
- TilePDF'te **reklam, reklam kimliği ve hesap yoktur**; geliştirici hiçbir
  veriyi satmaz veya paylaşmaz.

## Çökme raporları (Firebase Crashlytics)

TilePDF çöktüğünde ya da metin tanıma gibi bir özellik başarısız olduğunda,
uygulama Google Firebase Crashlytics'e bir çökme raporu gönderir. Rapor
şunları içerir:

- hatanın teknik ayrıntıları (yığın izi) ve uygulama sürümü;
- cihaz modeli, Android sürümü, boş bellek ve depolama alanı, uygulamanın ön
  planda olup olmadığı;
- Firebase'in oluşturduğu, adınıza, hesabınıza veya başka uygulamalara bağlı
  olmayan rastgele bir kurulum kimliği.

Çökme raporları belgelerinizi, metinlerini, dosya adlarını veya yollarını asla
içermez. 90 gün saklanır ve yalnızca hataları bulup düzeltmek için kullanılır.
Uygulamanın çalışır tutulabilmesi için çökme raporları varsayılan olarak
açıktır; *Ayarlar → Gizlilik* bölümünden kapattığınızda henüz gönderilmemiş
raporlar da silinir.

## Kullanım istatistikleri (Google Analytics for Firebase)

Yalnızca izin verirseniz TilePDF hangi ekranların ve özelliklerin
kullanıldığını sayar; örneğin "bir belge açıldı", "bir vurgu eklendi" ya da
"koyu sayfa teması seçildi". Olaylar yalnızca kategori taşır, örneğin sayfa
sayısı aralığı ("11–50 sayfa"); bir belgenin adını, içeriğini ya da tam
boyutunu asla taşımaz. Google Analytics ayrıca cihaz modelini, Android
sürümünü, uygulama sürümünü, ülkeyi (IP adresinden çıkarılır, IP adresi
saklanmaz) ve rastgele bir kurulum kimliğini kaydeder.

İstatistikler asla reklam için kullanılmaz: reklam özellikleri ve reklam
kimliği kapalıdır, veriler 2 ay saklanır. İstatistiklere izin vermezseniz ya da
sonradan kapatırsanız hiçbir şey toplanmaz.

## Metin tanıma (Google ML Kit)

Taranmış sayfalarda metin tanıma, cihazınızda Google ML Kit'in uygulamayla
gelen modeliyle çalışır; sayfalarınız hiçbir yere gönderilmez. ML Kit'in
kendisi, ML Kit'i geliştirmek için Google'a cihaz modeli, uygulama sürümü ve
tanımanın ne kadar sürdüğü gibi teknik kullanım bilgileri gönderebilir. Bu
bilgiler sayfalarınızı veya tanınan metni asla içermez.

## Dijital imzalar

Dijital imzaların denetimi cihazınızda, Android'in kendi güvendiği
sertifikalarla yapılır. İnternette hiçbir şey sorgulanmaz.

## Verileri kim işler?

Çökme raporları, istatistikler ve ML Kit'in kullanım bilgileri, Firebase için
Google koşulları kapsamında geliştirici adına Google LLC tarafından işlenir.
Veriler Amerika Birleşik Devletleri dahil ülkeniz dışında işlenebilir.
Ayrıntılar: <https://firebase.google.com/support/privacy>.

## Uygulama neyi, nerede saklar?

Aşağıdakilerin hepsi cihazınızda, uygulamanın özel depolama alanında kalır:

- **Açtığınız belgeler.** TilePDF bunları Android'in dosya seçicisiyle ya da
  başka bir uygulama paylaştığında okur. Bazı dosyalar sonradan yeniden
  açılabilmesi için özel depolamaya kopyalanır.
- **Açıklamalar.** Vurgular, çizimler, notlar ve tanınan metin PDF dosyasının
  içine kaydedilir. Kaydedilene kadar uygulamanın özel veritabanında bir taslak
  olarak durur.
- **Kütüphane.** Açtığınız belgelerin listesi, okuma konumları, favoriler ve
  yer imleri.
- **Tercihler.** Okuma ayarları, tema, dil ve gizlilik tercihleriniz.

Bunların hepsini TilePDF'i kaldırarak ya da *Ayarlar → Uygulamalar → TilePDF →
Depolama alanını temizle* ile silebilirsiniz. Bir belgeyi kütüphaneden
kaldırmak, onun özel kopyasını ve kaydedilmemiş açıklamalarını da siler.

## Android yedeklemesi

Android yedeklemesi açıksa Android, TilePDF'in yalnızca **tercihlerini** kendi
cihaz yedeğinize ekleyebilir. Belgeleriniz, açıklamalarınız ve kütüphaneniz
yedeğe girmez. Bu yedeği Android ve Google hesabınız yönetir; geliştirici buna
erişemez.

## İzinler

TilePDF çalışırken hiçbir izin istemez. İnternet iznini yalnızca çökme
raporlarını ve izin verilirse istatistikleri göndermek için kullanır. Diğer
kurulum izinleri, açıklamaları arka planda kaydeden Android kütüphanelerinden
gelir.

## Haklarınız

Çökme raporlarını ve istatistikleri uygulamadan istediğiniz zaman
kapatabilirsiniz. Veriler yalnızca rastgele bir kurulum kimliğine bağlı olduğu
için geliştirici hangi verinin size ait olduğunu bilemez; TilePDF'i kaldırmak
ya da depolamasını temizlemek yeni bir kimlik başlatır. Silme dahil verilerinizle
ilgili her türlü talep ve soru için aşağıdaki adresten geliştiriciye
ulaşabilirsiniz.

## Çocuklar

TilePDF çocuklara yönelik değildir ve bilerek çocuklardan veri toplamaz.

## Değişiklikler

Bu politika değişirse yeni sürümü yeni bir yürürlük tarihiyle burada
yayımlanır.

## İletişim

Bu politikayla ilgili soru ve talepler için: kocerlabs@gmail.com
