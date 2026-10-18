# Robot Arkadaşım (Gizem)

Çocuklar için konuşan, ders yardımı yapan, oyun oynayan robot arkadaş. Android uygulaması.

## Telefona kurulum
`apk/robot-arkadas.apk` dosyasını telefona indirip kurun (bir kez yeterli).

## Yeni özellikleri telefona göndermek (APK yüklemeden)
Uygulamanın ekranları, oyunları ve dükkanı `assets/` klasöründeki HTML/JS dosyalarıdır.
Uygulama her açılışta bu daldaki `assets/version.json` dosyasına bakar; `version` sayısı
telefondakinden büyükse yeni dosyaları indirir ve yeni özelliklerle açılır.

1. `assets/` içindeki dosyaları değiştirin (`index.html`, `robot.js`, `sfx.js`, `bubble.html`).
2. `assets/version.json` içindeki `"version"` sayısını 1 artırın.
3. Bu `robot` dalına push edin. Telefonlar bir sonraki açılışta günceller
   (GitHub önbelleği yüzünden birkaç dakika sürebilir).

Yeni bir dosya eklerseniz `version.json` içindeki `files` listesine de yazın.
Java tarafında (`src/`) değişiklik gerekirse APK'yı yeniden derleyip `minApp`'i yeni
APK sürüm koduna (`AndroidManifest.xml` → `versionCode`) yükseltin; eski APK'lar o
web sürümünü almaz.

## APK derleme
`./build.sh` — javac → dx → aapt2 → apksig. İmza anahtarı `release.p12` depoda **yoktur**
(güvenlik); aynı anahtarla imzalanmayan APK eski sürümün üzerine kurulamaz.
