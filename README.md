# Altung

Altung adalah kalkulator saintifik untuk Android dan Windows 11 yang bekerja seperti
**Casio fx-570ES PLUS**: urutan tombol, hasil, format tampilan, dan pesan errornya dibuat sama
dengan kalkulator fisiknya. Siapa pun yang sudah hafal fx-570ES PLUS bisa langsung memakainya
tanpa belajar ulang.

Versi terbaru: **1.2**. Dibuat oleh N-Blu Studios, bebas dipakai dan dipelajari di bawah
lisensi [GPL-3.0](LICENSE).

> Altung tidak berafiliasi dengan, tidak disponsori, dan tidak disetujui oleh CASIO Computer
> Co., Ltd. "CASIO" dan "fx-570ES PLUS" adalah merek dagang milik pemiliknya. Seluruh mesin
> hitung Altung ditulis ulang dari nol; tidak ada ROM atau kode Casio di dalamnya.

## Isi

- [Fitur](#fitur)
- [Memasang di Android](#memasang-di-android)
- [Memasang di Windows 11](#memasang-di-windows-11)
- [Memakai keyboard dan mouse di Windows](#memakai-keyboard-dan-mouse-di-windows)
- [Membangun dari kode sumber](#membangun-dari-kode-sumber)
- [Susunan proyek](#susunan-proyek)
- [Lisensi dan tanpa jaminan](#lisensi-dan-tanpa-jaminan)

## Fitur

- **Delapan mode** seperti kalkulator aslinya: COMP, CMPLX, STAT, BASE-N, EQN, MATRIX, TABLE,
  dan VECTOR.
- **Natural Display**: pecahan bertingkat, akar, pangkat, integral, dan Σ tampil seperti di buku,
  baik saat diketik maupun pada hasilnya. Linear Display juga tersedia lewat SETUP.
- **Presisi 15 digit** dengan tampilan 10 digit, memakai aritmetika desimal, bukan bilangan
  biner. Karena itu 0,1 + 0,2 tepat 0,3, seperti kalkulator fisik.
- **Kalkulus dan pemecah persamaan**: ∫, d/dx, Σ, CALC, SOLVE, persamaan kuadrat dan kubik,
  serta sistem linear dua dan tiga variabel.
- **Statistik dan regresi**: tujuh model regresi, editor data berkisi, dan distribusi normal.
- **40 konstanta ilmiah dan 40 konversi satuan** (CONST dan CONV).
- **Susunan tombol sama dengan fx-570ES PLUS**, lengkap dengan fungsi lapis SHIFT dan ALPHA
  yang tercetak di atas tiap tombol, dalam tiga tema warna khas Altung: Malam, Kertas, dan Hutan.
- **Tambahan di luar Casio**: layar bisa disentuh untuk memilih menu dan memindah kursor, baris
  ekspresi panjang bisa digeser, salin-tempel hasil, dan **panduan berbahasa Indonesia** (19 bab)
  yang dibuka dari tombol OPTION.
- **Sepenuhnya offline**: tanpa internet, tanpa iklan, dan tanpa pengumpulan data. Di Android,
  satu-satunya izin yang diminta adalah getaran tombol.
- **Status selalu tersimpan**: isi layar, memori, variabel, dan setelan kembali seperti semula
  setiap aplikasi dibuka lagi.

## Memasang di Android

**Syarat:** Android 14 atau lebih baru.

1. Unduh `Altung-1.2.apk` (±2 MB) dari halaman
   [Releases](../../releases) repo ini.
2. Buka berkas APK itu di HP. Bila Android menolak, izinkan pemasangan dari sumber ini: ketuk
   **Setelan**, lalu nyalakan **Izinkan dari sumber ini** untuk aplikasi yang membuka APK
   (misalnya browser atau pengelola berkas).
3. Ketuk **Pasang**, lalu buka **Altung** dari laci aplikasi.

**Memperbarui:** pasang APK versi baru di atas versi lama. Isi kalkulator dan setelan tetap ada.
Jangan mencopot versi lama lebih dulu, karena mencopot aplikasi menghapus data tersimpannya.

**Getaran tombol** mati secara bawaan. Nyalakan lewat **OPTION → 2:Getar → On**.

## Memasang di Windows 11

**Syarat:** Windows 11 64-bit. Java tidak perlu dipasang, karena sudah terbawa di dalam paket.

1. Unduh `Altung-1.2-win64.zip` (±54 MB) dari halaman [Releases](../../releases) repo ini.
2. Klik kanan berkas zip, pilih **Extract All**, lalu ekstrak ke folder tetap, misalnya
   `C:\Users\<nama Anda>\AppData\Local\Programs`. Hasilnya folder `Altung` berisi `Altung.exe`.
3. Buka `Altung.exe`. Pada pembukaan pertama, Windows SmartScreen akan menampilkan
   *"Windows protected your PC"* karena aplikasi ini tidak bertanda tangan digital. Klik
   **More info**, lalu **Run anyway**.
4. Agar mudah dibuka lagi, klik kanan `Altung.exe` lalu pilih **Pin to Start**, atau
   *Show more options → Send to → Desktop (create shortcut)*.

Folder `Altung` harus tetap utuh: `Altung.exe` membutuhkan folder `app` dan `runtime` di
sebelahnya, jadi jangan memindahkan exe-nya saja.

**Memperbarui:** tutup Altung, hapus folder `Altung` yang lama, lalu ekstrak zip versi baru ke
tempat yang sama. Isi kalkulator dan setelan tidak ikut terhapus, karena tersimpan terpisah di
`%LOCALAPPDATA%\Altung`.

**Mencopot:** hapus folder `Altung`. Untuk menghapus juga isi kalkulator yang tersimpan, hapus
folder `%LOCALAPPDATA%\Altung`.

## Memakai keyboard dan mouse di Windows

Semua tombol di layar bisa diklik dengan mouse, persis seperti menyentuh layar HP. Keyboard PC
menjadi jalan pintas:

| Tombol PC | Fungsi di Altung |
| --- | --- |
| `0`–`9`, `.`, `+`, `-`, `*`, `/`, `(`, `)`, `^`, `,`, `%`, `!` | tombol yang sama di kalkulator |
| `Enter` atau `=` | `=` |
| `Backspace` atau `Delete` | DEL |
| `Esc` | AC; juga menutup panduan |
| `←` `↑` `→` `↓` | tombol arah |
| `F1` `F2` `F3` `F4` | SHIFT, ALPHA, MODE, OPTION |
| `Ctrl+C` / `Ctrl+V` | salin hasil / tempel |

Beberapa hal yang perlu diingat:

- `/` adalah pembagian biasa (÷). Untuk **pecahan bertingkat**, klik tombol pecahan di layar.
- `-` adalah pengurangan. Untuk bilangan negatif, pakai tombol **(−)** di layar.
- Tombol **pecahan sesudah angka** hanya mengambil angka terakhir sebagai pembilang, sama seperti
  kalkulator fisik. Untuk pembilang berisi `5+2`, klik tombol pecahan **lebih dulu**, ketik `5+2`,
  tekan `↓`, lalu ketik penyebutnya.
- Baris ekspresi yang panjang bisa digeser dengan menyeret mouse ke kiri atau kanan.

## Membangun dari kode sumber

Bagian ini untuk yang ingin mempelajari atau mengubah kodenya.

**Kebutuhan:** JDK 17 dan Android SDK platform 36. Untuk Android SDK, setel variabel lingkungan
`ANDROID_HOME`, atau buat berkas `local.properties` di akar proyek berisi
`sdk.dir=<lokasi Android SDK>`.

Perintah di bawah memakai `./gradlew` (Linux dan macOS). Di Windows, ganti dengan `gradlew.bat`.

```bash
# Seluruh tes mesin hitung, termasuk golden test dan contoh panduan
./gradlew :engine:test

# APK debug untuk Android: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Menjalankan versi desktop langsung
./gradlew :desktop:run

# Tes versi desktop
./gradlew :desktop:test

# Paket Windows: desktop/build/compose/binaries/main/app/Altung (jalankan di Windows)
./gradlew :desktop:createDistributable
```

Catatan:

- APK debug bertanda tangan kunci debug. APK ini **tidak bisa dipasang menimpa** Altung dari
  halaman Releases, karena tanda tangannya berbeda. Copot dulu versi rilisnya, dengan akibat isi
  kalkulator yang tersimpan ikut terhapus.
- Paket Windows hanya bisa dibuat di Windows, karena jpackage membuat paket untuk sistem operasi
  tempat ia dijalankan.

## Susunan proyek

Mesin hitung dipisahkan dari tampilan, sehingga seluruh perhitungan bisa diuji tanpa HP maupun
emulator.

| Modul | Isi |
| --- | --- |
| `engine` | Mesin hitung murni Kotlin: angka desimal 15 digit, bentuk eksak (pecahan, √, π), bilangan kompleks, matriks, vektor, parser ekspresi, kalkulus, statistik, serta tabel konstanta dan konversi |
| `ui` | Tampilan bersama Android dan Windows (Compose Multiplatform): papan tombol, layar LCD, menu, dan panduan |
| `app` | Aplikasi Android |
| `desktop` | Aplikasi Windows: jendela, penyimpanan status, papan klip, dan keyboard PC |
| `docs` | Pemetaan tombol (`keymap.json`), isi panduan (`manual.json`), dan golden test |

**Golden test** di `docs/golden/` adalah daftar urutan tombol beserta tampilan dan hasil yang
diharapkan, kebanyakan diambil dari contoh di buku petunjuk fx-570ES PLUS. Setiap perubahan pada
mesin hitung harus tetap meluluskan seluruh golden test ini.

## Lisensi dan tanpa jaminan

Kode Altung dirilis di bawah [GNU General Public License v3.0](LICENSE). Anda bebas memakai,
mempelajari, mengubah, dan menyebarkan ulang Altung. Versi turunan yang disebarkan wajib tetap
memakai lisensi yang sama dan menyertakan kode sumbernya.

Altung disediakan **apa adanya, tanpa jaminan apa pun**, termasuk jaminan ketelitian hasil
hitung. Kecocokan dengan kalkulator fisik diuji lewat ratusan kasus dari buku petunjuknya, tetapi
belum semuanya dicocokkan langsung dengan unit fisik. Untuk keperluan penting seperti ujian,
tugas resmi, atau perhitungan rekayasa, periksa ulang hasilnya dengan alat lain dan ikuti aturan
penggunaan kalkulator yang berlaku di tempat Anda.
