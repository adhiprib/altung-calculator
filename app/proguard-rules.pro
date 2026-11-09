# Kelas hasil kotlinx-serialization dipakai lewat refleksi serializer, jadi harus dipertahankan.
# Mencakup seluruh paket: engine (status tersimpan) dan ui (keymap.json dan manual.json).
-keepclassmembers class id.sempoa570.** {
    *** Companion;
}
-keepclasseswithmembers class id.sempoa570.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class id.sempoa570.**$$serializer { *; }

# Tanpa jaringan dan tanpa pelaporan galat, jadi tidak ada aturan lain yang diperlukan.
