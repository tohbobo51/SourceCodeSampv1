# News RP Android SA-MP

Kode sumber APK Android News RP/SA-MP Mobile, dengan launcher, HUD, overlay WebView, alat host lokal, editor Pawn, downloader Data Lite/Full, dan lapisan native C/C++.

## Apa yang ada dalam proyek ini

- Launcher Android dalam Java.
- HUD dan layar in-game dalam Java/XML.
- Antarmuka WebView dalam HTML/CSS/JS.
- Source C/C++ SA-MP dalam C/C++ melalui NDK.
- Downloader Data Lite/Full melalui `update_sources.json`.
- Editor/kompiler Pawn dan alat host lokal.

## Struktur utama

```text
app/src/main/java/com/xyron/game/launcher   Launcher, tab, unduhan, dan pengaturan
app/src/main/java/com/xyron/game/main       Aktivitas game, HUD, overlay, dan penghubung Java/native
app/src/main/res                            Layout XML, ikon, tema, dan gambar Android
app/src/main/assets/interfaces              Antarmuka ponsel, inventaris, peta, dan runtime WebView
app/src/main/assets/update_sources.json     Sumber unduhan Data Lite/Full
app/src/main/jniLibs/armeabi-v7a            Library native yang digunakan APK
jni/jni                                     Source C/C++ untuk libSAMP
jni/compile.cmd                             Skrip Windows untuk mengompilasi library native
prdownloader                                Modul downloader lokal
server                                      File pendukung untuk host/editor
```

## Persyaratan

- Windows dengan Android Studio terpasang.
- Android SDK Platform 33.
- Android Build Tools terpasang melalui Android Studio.
- NDK dengan `ndk-build.cmd` terpasang. Skrip mencari NDK 27, 26, 25, atau 21.
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

## Cara mengompilasi lib native

Source C/C++ berada di `jni/jni`.

```powershell
cd jni
.\compile.cmd
```

Kemudian salin lib yang dihasilkan ke APK:

```powershell
copy jni\libs\armeabi-v7a\libSAMP.so ..\app\src\main\jniLibs\armeabi-v7a\libSAMP.so
```

## Data Lite dan Data Full

Sumber berada di:

```text
app/src/main/assets/update_sources.json
```

APK mengunduh file game ke:

```text
/sdcard/Android/data/com.xyron.game/files
```

Untuk menghindari crash native saat boot, Data Lite harus berisi file kritis seperti:

- `texdb/txd/txd.*`
- `texdb/samp/samp.*`
- `texdb/samp.img`
- `texdb/gta3.img`
- `texdb/gta_int.img`
- `SAMP/main.scm`

Jika game crash di `libGTASA.so CCustomRoadsignMgr::Initialise`, biasanya Data Lite tidak lengkap. Unduh ulang data melalui launcher atau periksa apakah `texdb/txd` dan `texdb/samp` ada.

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
- ABI utama: `armeabi-v7a`.
- Data default: Lite.
- Inventaris dengan gambar lokal yang diperbaiki di `assets/interfaces/inventario/images`.
- Pemeriksaan Data Lite diperkuat agar tidak memulai game dengan file yang tidak lengkap.
