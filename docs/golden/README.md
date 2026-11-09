# Format golden test (PRD §7.3 poin 2)

Golden test adalah rekaman perilaku kalkulator fisik: satu baris = satu skenario tekan tombol.
Berkas `*.txt` di folder ini dibaca oleh tes JVM di `:engine` (mulai Tahap 1).

## Aturan baris

- Satu kasus per baris, enam kolom dipisah `|`. Spasi di sekitar `|` diabaikan.
- Baris kosong diabaikan. Baris yang diawali `#` adalah komentar.

```
mode | setup | urutan tombol | tampilan ekspresi | tampilan hasil | sumber
COMP | default | DIGIT_2 OP_ADD DIGIT_3 EXEC_EQ | 2+3 | 5 | manual
```

### Kolom 1 — `mode`

`COMP`, `CMPLX`, `STAT`, `BASE`, `EQN`, `MATRIX`, `TABLE`, `VECTOR`.

### Kolom 2 — `setup`

`default` berarti MthIO, Deg, Norm 1, d/c, pemisah titik. Selain itu tulis daftar dipisah koma tanpa spasi,
misal `LineIO,Rad` atau `Fix3` atau `LineIO,Sci3`. Nilai yang tidak disebut memakai nilai default.

### Kolom 3 — `urutan tombol`

Token dari `docs/keymap.json`, dipisah spasi, **satu token per tekanan fisik**.
SHIFT dan ALPHA adalah tekanan tersendiri: tekan SHIFT menghasilkan `MOD_SHIFT`, lalu tombol berikutnya
menghasilkan token lapis SHIFT-nya. Jadi `x³` ditulis `MOD_SHIFT POST_CUBE`.
Angka multi-digit tetap satu token per digit: `1 2 . 5` ditulis `DIGIT_1 DIGIT_2 DOT DIGIT_5`.

### Kolom 4 dan 5 — tampilan ekspresi dan tampilan hasil

Penyandian linear satu baris dari apa yang terlihat di LCD. Ini adalah kontrak formatter untuk Tahap 1.

| Bentuk di LCD | Ditulis sebagai | Contoh |
| --- | --- | --- |
| pecahan | `a⌟b` | `7⌟6` |
| pecahan campuran | `a⌟b⌟c` | `1⌟1⌟2` |
| akar | `√(x)` | `2√(2)` |
| akar pangkat n | `n√(x)` | `5√(32)` |
| pangkat | `x^(n)` | `2^(10)` |
| ×10 pangkat | `E` | `1.23456789E11` |
| derajat-menit-detik | `d°m's"` | `3°0'0"` |
| pi | `π` | `2π` |
| tanda negatif | `-` | `-4` |
| simpan ke variabel | `→` | `3+5→A` |
| kursor/slot kosong | `▭` | `√(▭)` |

Pesan error ditulis apa adanya di kolom hasil: `Math ERROR`, `Syntax ERROR`, `Stack ERROR`,
`Dimension ERROR`, `Variable ERROR`, `Can't Solve`, `Insufficient MEM`, `Time Out`, `Argument ERROR`.

Bila satu urutan tombol menghasilkan lebih dari satu tampilan berturut-turut (misal multi-statement
atau konversi berulang), tulis tiap tampilan sebagai baris kasus tersendiri dengan urutan tombol penuh
dari AC, supaya tiap baris berdiri sendiri.

### Kolom 6 — `sumber`

| Nilai | Arti | Status |
| --- | --- | --- |
| `fisik` | direkam langsung dari unit fx-570ES PLUS | wajib lulus |
| `emu` | disalin dari emulator resmi Casio (PRD §7.4) | wajib lulus |
| `manual` | contoh perhitungan dari User's Guide, disalin hasilnya | belum diverifikasi unit fisik |
| `manual?` | rekonstruksi, bukan contoh langsung dari manual | belum diverifikasi unit fisik |

Kriteria lulus v1.0 (PRD §7.5) hanya menghitung baris `fisik` dan `emu`. Baris `manual` dan `manual?`
berfungsi sebagai target sementara dan **harus** diganti statusnya setelah dicek dengan unit fisik.

## Daftar tunggu verifikasi fisik

Kasus berikut sengaja tidak dimasukkan sebagai golden karena PRD §5.2 mewajibkan verifikasi unit fisik
lebih dulu. Mohon rekam hasilnya dari kalkulator, lalu tambahkan dengan sumber `fisik`:

1. `2 ÷ 2√2 =` — prioritas perkalian implisit terhadap ÷
2. `6 ÷ 2(1+2) =` — perkalian implisit dengan kurung
3. `1 ÷ 2π =` — perkalian implisit dengan konstanta
4. `2^3^2 =` — asosiativitas pangkat bertingkat
5. `3!² =` — fungsi akhiran berurutan
6. `-2² =` dan `(-2)² =` — pangkat pada tanda negatif unary
7. Batas tampil bentuk eksak: pecahan dengan total digit pembilang+penyebut tepat 10 dan 11
