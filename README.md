# News RP Android SA-MP

Kode sumber APK Android News RP/SA-MP Mobile, dengan launcher, HUD, overlay WebView, alat host lokal, editor Pawn, downloader CRMP Data Full, dan lapisan native C/C++.

## Apa yang ada dalam proyek ini

- Launcher Android dalam Java.
- HUD dan layar in-game dalam Java/XML.
- Antarmuka WebView dalam HTML/CSS/JS.
- Source C/C++ SA-MP dalam C/C++ melalui NDK.
- Downloader satu paket data penuh `CRMP.zip` melalui `update_sources.json`.
- Editor/kompiler Pawn dan alat host lokal.

## Struktur utama

```text
app/src/main/java/com/xyron/game/launcher   Launcher, tab, unduhan, dan pengaturan
app/src/main/java/com/xyron/game/main       Aktivitas game, HUD, overlay, dan penghubung Java/native
app/src/main/res                            Layout XML, ikon, tema, dan gambar Android
app/src/main/assets/interfaces              Antarmuka ponsel, inventaris, peta, dan runtime WebView
app/src/game/assets/update_sources.json     Sumber unduhan CRMP.zip
app/src/game/jniLibs/armeabi-v7a            Library native 32-bit
app/src/game/jniLibs/arm64-v8a              Library native 64-bit
app/src/main/cpp                            Source C/C++/CMake untuk library native APK
app/src/game/assets                        Aset game yang dikemas ke APK
prdownloader                                Modul downloader lokal
server                                      File pendukung untuk host/editor
```

## Persyaratan

- Windows dengan Android Studio terpasang.
- Android SDK Platform 35.
- Android Build Tools terpasang melalui Android Studio.
- Android Gradle Plugin 8.6.1 dan Gradle Wrapper 8.7.
- Android NDK `26.2.11394342`.
- Java 17.
- Perangkat Android dengan debug USB/Wireless ADB aktif untuk memasang dan menguji.

Jika Gradle tidak menemukan Java di terminal, atur `JAVA_HOME` atau edit secara lokal `gradle.properties` dan arahkan `org.gradle.java.home` ke JBR Android Studio.

## Cara mengompilasi APK

1. Buka folder root di Android Studio atau PowerShell.
2. Konfigurasikan SDK/NDK melalui Android Studio.
3. Kompilasi APK debug:

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug --no-daemon
```

APK dihasilkan di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Untuk memasang via ADB:

```powershell
adb devices
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
```

## Dukungan ABI dan signing release

APK dikonfigurasi untuk menyertakan `armeabi-v7a` (32-bit) dan `arm64-v8a` (64-bit). Gradle/CMake membangun library proyek untuk kedua ABI; library game prebuilt yang cocok berada di `app/src/game/jniLibs`.

Build debug:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Untuk build release bertanda tangan, simpan keystore di lokasi privat dan atur variabel environment berikut terlebih dahulu. Ambil kata sandi dari file kredensial keystore yang diberikan terpisah; jangan masukkan nilainya ke repositori atau command yang dibagikan.

```powershell
$env:SIGNING_STORE_FILE = "C:\\secure\\NewsRP-release-signing.jks"
$env:SIGNING_STORE_PASSWORD = "<store password>"
$env:SIGNING_KEY_ALIAS = "news-rp-upload"
$env:SIGNING_KEY_PASSWORD = "<key password>"
.\gradlew.bat :app:assembleRelease --no-daemon
```

Tanpa seluruh variabel signing tersebut, Gradle tidak menandatangani varian release. Jangan publikasikan APK yang belum ditandatangani atau ditandatangani dengan debug key.

## Data Lite dan Data Full

Sumber berada di:

```text
app/src/main/assets/update_sources.json
```

APK mengunduh file game ke:

```text
/sdcard/Android/data/com.xyron.game/files
```

Setelah mengunduh CRMP.zip, pastikan file game kritis berikut tersedia di folder data aplikasi:

- `texdb/txd/txd.*`
- `texdb/samp/samp.*`
- `texdb/samp.img`
- `texdb/gta3.img`
- `texdb/gta_int.img`
- `SAMP/main.scm`

Jika game crash di `libGTASA.so CCustomRoadsignMgr::Initialise`, periksa apakah instalasi CRMP.zip selesai dan folder `texdb/txd`, `texdb/samp`, serta `SAMP/main.scm` tersedia.

### Data game CRMP.zip

- Launcher hanya menyediakan **Data Full CRMP**; opsi Data Lite dan sumber unduhan lamanya dihapus. Paket tunggal berukuran 1,074,673,867 byte (sekitar 1 GB).
- Sumber resmi: [CRMP.zip — samp-game-data v1.0](https://github.com/tohbobo51/samp-game-data/releases/download/v1.0/CRMP.zip). URL, ukuran, dan SHA-256 paket tercatat di `app/src/game/assets/update_sources.json`.
- Launcher memverifikasi ukuran dan SHA-256 sebelum instalasi. Arsip memakai prefix `files/`; installer melepas prefix tersebut, lalu hanya memasang root aset yang didukung (`anim`, `audio`, `data`, `fonts`, `models`, `SAMP`, `TEXT`, `texdb`). File log/pengaturan pengguna dilewati dan file aset yang ditimpa dicadangkan.
- Mulai v0.0.10, jika sudah memiliki arsip resmi, salin `CRMP.zip` ke `Android/data/com.xyron.game/files/` atau subfolder `download/`. Launcher memeriksa dan memasangnya sebelum mencoba mengunduh. Arsip yang Anda salin tetap disimpan; hapus manual setelah data terpasang dan game berjalan jika ingin mengosongkan ruang.
- Pastikan perangkat memiliki ruang kosong yang cukup untuk arsip sekitar 1 GB dan file hasil ekstraksi.

Untuk memasang paket manual di Windows, Linux, atau Termux:

```bash
python3 tools/install_crmp_data.py CRMP.zip "/path/ke/Android/data/com.xyron.game/files" --dry-run
python3 tools/install_crmp_data.py CRMP.zip "/path/ke/Android/data/com.xyron.game/files"
```

Skrip memeriksa ukuran, SHA-256, CRC dan keamanan path arsip, meminta konfirmasi, mencadangkan file yang ditimpa, serta melewati log/pengaturan pengguna.

## Di mana mengedit

- Nama/ikon app: `app/build.gradle`, `app/src/main/res/mipmap-*`, `app/src/main/res/drawable-nodpi`.
- Layar awal/launcher: `app/src/main/res/layout/fragment_home.xml`.
- Downloader: `app/src/main/java/com/xyron/game/launcher/UpdateService.java`.
- Pemeriksa data: `app/src/main/java/com/xyron/game/launcher/util/GameDataVerifier.java`.
- Inventaris/ransel: `app/src/main/assets/interfaces/inventario/index.html`.
- Gambar item ransel: `app/src/main/assets/interfaces/inventario/images`.
- Ponsel: `app/src/main/assets/interfaces/celular/index.html`.
- Hooks/native SA-MP: `jni/jni/game`, `jni/jni/net`, `jni/jni/main.cpp`.

## Hal yang perlu diperhatikan sebelum mempublikasikan

Jangan masukkan ke ZIP publik:

- `app/build/`
- `prdownloader/build/`
- `.gradle/`
- `.idea/`
- `.vscode/`
- `local.properties`
- APK hasil build
- log, tangkapan layar, dan file sementara
- kunci `.jks` atau `.keystore`

`app/google-services.json` berisi data placeholder. Siapa pun yang akan menggunakan Firebase harus menggantinya dengan file mereka sendiri.

`app/src/main/jniLibs` dan `app/libs` dapat berisi library prebuilt yang diperlukan untuk membuat APK yang dapat dijalankan. Library ini tidak menggantikan source C/C++ di `jni/jni`. Jika ingin menerbitkan rilis yang sepenuhnya berisi source, hapus berkas biner ini dan jelaskan di README cara memulihkan dependensi lokal.

## Debugging crash

Selalu analisis dengan logcat:

```powershell
adb logcat -c
adb logcat -v time | Select-String "FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|libGTASA|libSAMP|libsamp"
```

Untuk crash native, cari:

- `Build fingerprint`
- `pid`
- `tid`
- `signal`
- `fault addr`
- `backtrace`
- nama library dan offset, misalnya `libGTASA.so pc 005a576a`.

Jangan menebak offset. Gunakan logcat/tombstone dan bandingkan dengan lib yang benar.

## Status saat ini

- Nama app: News RP.
- Paket Android: `com.xyron.game`.
- ABI: `armeabi-v7a` dan `arm64-v8a`.
- Data default: Lite.
- Inventaris dengan gambar lokal yang diperbaiki di `assets/interfaces/inventario/images`.
- Pemeriksaan Data Lite diperkuat agar tidak memulai game dengan file yang tidak lengkap.

## Login Google native

Tombol **Main** memakai Android Credential Manager untuk mengambil Google ID token dan nonce acak, mengirimkannya melalui HTTPS ke API, lalu meluncurkan game hanya setelah API memverifikasi identitas dan menerbitkan tiket satu kali. Gamemode menukarkan tiket lewat MySQL; ID token Google tidak dikirim ke server game dan tidak dicatat ke Logcat.

Untuk membangun APK yang dapat login, atur variabel berikut di environment build:

```powershell
$env:GOOGLE_WEB_CLIENT_ID = "<Web application OAuth Client ID>"
$env:AUTH_API_BASE_URL = "https://openmp-gm.vercel.app"
```

`GOOGLE_WEB_CLIENT_ID` harus sama dengan `GOOGLE_CLIENT_ID` di environment API Vercel; gunakan **Web application client ID** dari Google Cloud, bukan client ID Android. OAuth Android client juga harus mendaftarkan package `com.xyron.game` dan SHA-1 sertifikat release. Client ID bukan client secret; jangan pernah menanamkan client secret ke APK. Gradle menolak build release jika Web Client ID kosong.

Server API dan gamemode memakai tabel `auth_login_tickets` (lihat dokumentasi dan skema pada repository `openmp-gm`). Tiket berlaku 90 detik dan hanya dapat diklaim sekali. Saat ini login hanya untuk akun yang sudah ada di `ucp_accounts` dan sudah memiliki karakter di `characters`; pendaftaran akun/karakter baru melalui launcher belum diaktifkan.
