# Aşama 6 — Kütüphane, uyarlanabilir düzen, dil, erişilebilirlik, hatalar

Tarih: 2026-10-17 · Durum: incelemede · Üst belge: `2026-10-16-tilepdf-v1-design.md`

## Bölünme

- **6a — Kütüphane:** favoriler, sıralama, arama, geri alınabilir kaldırma.
- **6b — Dil, erişilebilirlik, hata ekranları:** İngilizce varsayılan + Türkçe,
  uygulama içi dil seçimi, TalkBack etiketleri, dokunma hedefleri, kontrast,
  tipli hata ekranları.
- **6c — Uyarlanabilir düzen:** tablet ve katlanabilirde içindekiler/yer
  imleri/açıklamalar yan panelde, geniş kütüphane düzeni.

## 6a kararları (sektör beklentisi: Drive, Acrobat, Xodo)

- **Filtre:** "Tümü" ve "Favoriler" çipleri. Favori, satırdaki yıldızla açılıp
  kapanır.
- **Sıralama:** son açılan (varsayılan), ada göre, eklenme tarihine göre.
  Seçim hatırlanır.
- **Arama:** üst bardaki büyüteçle açılır, adı süzer. Büyük/küçük harf ve
  Türkçe işaretler fark etmez ("sozlesme" "Sözleşme"yi bulur).
- **Kaldırma:** satırın menüsünde. Satır hemen kaybolur, "Kaldırıldı · Geri al"
  bildirimi çıkar ve kaldırma ancak bildirim kapanınca yapılır. Kaldırma özel
  kopyayı ve kaydedilmemiş açıklamaları da sildiği için geri alınabilmesi
  gerekir.
- **v1 dışı:** kütüphanede ilk sayfa küçük resmi (her belgeyi açmak gerekir).

## 6b kararları

- **Dil:** İngilizce `values/`, Türkçe `values-tr/`. Uygulama içi seçim
  kütüphanenin ⋮ menüsünde: Sistem varsayılanı, English, Türkçe (dil adları
  kendi dillerinde). AppCompat'in uygulama başına dil API'si; Android 13+
  sistem ayarlarında da görünür.
- **Hata ekranları:** bulunamadı ve izin kaybı → "Dosya seç" + "Kütüphaneye
  dön"; bozuk → "Kütüphaneye dön"; bellek → "Tekrar dene" + "Kütüphaneye dön".
  Şifre mevcut diyalogla sorulur.
- **Erişilebilirlik:** 48 dp dokunma hedefleri, renk ve kalınlıklar için ayrı
  etiketler, TalkBack açıkken okuyucu çubukları gizlenmez; ATF denetimleri UI
  testlerinde.
- **Kapsam dışı:** render sırasındaki bellek baskısı (önbellek bütçeleri zaten
  sınırlı).

## 6c kararları

- **Yan panel:** içindekiler/yer imleri/açıklamalar, genişlik ≥ 840 dp ve
  yükseklik ≥ 480 dp olduğunda sayfaların solunda 360 dp'lik kalıcı panelde;
  aksi halde bugünkü alt sayfa. Yatay telefon alt sayfada kalır. Karar ekran
  türüne değil okuyucuya verilen alana göre (bölünmüş ekran doğru çalışır).
- **Panel davranışı:** seçim sayfaya atlar, panel açık kalır; vurgulu bölüm
  okudukça ilerler; tam ekran çubukları gizler, paneli değil; Geri önce
  paneli kapatır.
- **Kütüphane:** geniş ekranda sütunlara akan ızgara (en az 360 dp).
- **Kapsam dışı:** menteşeye göre düzen (masaüstü duruşu); dil ve tema
  (kullanıcının kendi kütüphaneleri).
