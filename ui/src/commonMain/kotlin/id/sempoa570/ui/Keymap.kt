package id.sempoa570.ui

import id.sempoa570.engine.Key
import id.sempoa570.engine.Mode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Satu lapis tombol dalam docs/keymap.json. */
@Serializable
data class KeyLayer(
    val token: String,
    val label: String,
    val unverified: Boolean = false,
) {
    val key: Key? get() = Key.byName(token)
}

/** Satu tombol fisik beserta ketiga lapisnya. */
@Serializable
data class KeyEntry(
    val id: String,
    val label: String,
    val group: String,
    val main: KeyLayer,
    val shift: KeyLayer? = null,
    val alpha: KeyLayer? = null,
    val baseN: KeyLayer? = null,
    val cmplx: KeyLayer? = null,
)

@Serializable
data class Keymap(val device: String, val keys: List<KeyEntry>) {

    private val byId by lazy { keys.associateBy { it.id } }

    operator fun get(id: String): KeyEntry = byId[id] ?: error("tombol tidak ada di keymap: $id")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Isi docs/keymap.json; berkasnya dibaca cangkang masing-masing platform. */
        fun parse(text: String): Keymap = json.decodeFromString(text)
    }
}

/** Lapis papan tombol yang sedang aktif. */
enum class Layer { MAIN, SHIFT, ALPHA }

/**
 * Mesin status tombol modifier (PRD §4.3). SHIFT dan ALPHA bersifat sekali pakai: aktif hanya
 * untuk satu tekanan berikutnya, dan menekan tombolnya lagi membatalkannya.
 */
class ModifierState {

    var layer: Layer = Layer.MAIN
        private set

    /** Menekan SHIFT atau ALPHA; true bila tekanan itu hanya mengubah lapis. */
    fun onPress(entry: KeyEntry): Boolean = when (entry.main.token) {
        Key.MOD_SHIFT.name -> {
            layer = if (layer == Layer.SHIFT) Layer.MAIN else Layer.SHIFT
            true
        }
        Key.MOD_ALPHA.name -> {
            layer = if (layer == Layer.ALPHA) Layer.MAIN else Layer.ALPHA
            true
        }
        else -> false
    }

    /**
     * Token yang dihasilkan tombol pada lapis aktif; lapis lalu kembali normal.
     *
     * Pada mode BASE-N, tombol x², x▭, log, dan ln langsung memilih basis tanpa modifier,
     * seperti tulisan biru di badan unit fisik (manual hlm. 56).
     */
    fun resolve(
        entry: KeyEntry,
        mode: Mode = Mode.COMP,
        /**
         * Sesudah STO atau RCL kalkulator menunggu nama variabel, sehingga tombolnya langsung
         * berlaku sebagai huruf tanpa menekan ALPHA lebih dulu (manual hlm. 25).
         */
        variableExpected: Boolean = false,
    ): Key? {
        val chosen = when (layer) {
            Layer.MAIN -> when {
                variableExpected && entry.alpha != null -> entry.alpha
                mode == Mode.BASE -> entry.baseN ?: entry.main
                else -> entry.main
            }
            Layer.SHIFT -> entry.shift ?: entry.main
            Layer.ALPHA -> entry.alpha ?: entry.main
        }
        layer = Layer.MAIN
        return chosen.key
    }
}

/**
 * Susunan papan tombol untuk layar 6,78 inci portrait (PRD §4.2, keputusan 3).
 *
 * Bukan salinan susunan fisik: blok angka dan operator dasar diperbesar di bagian bawah agar
 * terjangkau ibu jari, fungsi ilmiah di baris tengah, navigasi dan kontrol dekat display.
 * Kelompok fungsi yang berdekatan di kalkulator asli tetap berdekatan di sini.
 */
object KeypadLayout {

    /**
     * Baris tombol mengikuti susunan unit fisik fx-570ES PLUS, bukan tata letak khusus layar.
     * Pemilik produk memilih kemiripan dengan kalkulator asli agar hafalan urutan tombol tetap
     * berlaku, menggantikan keputusan 3 pada PRD §4.2.
     *
     * Baris fungsi memakai enam kolom dan `null` berarti tidak ada tombol di posisi itu;
     * blok angka memakai lima kolom, persis seperti kalkulator aslinya.
     */
    val functionRows: List<List<String?>> = listOf(
        listOf("KEY_CALC", "KEY_INTEGRAL", null, null, "KEY_RECIP", "KEY_LOGBASE"),
        listOf("KEY_FRAC", "KEY_SQRT", "KEY_SQUARE", "KEY_POWER", "KEY_LOG", "KEY_LN"),
        listOf("KEY_NEG", "KEY_DMS", "KEY_HYP", "KEY_SIN", "KEY_COS", "KEY_TAN"),
        listOf("KEY_RCL", "KEY_ENG", "KEY_LPAREN", "KEY_RPAREN", "KEY_SD", "KEY_MPLUS"),
    )

    val numberRows: List<List<String>> = listOf(
        listOf("KEY_7", "KEY_8", "KEY_9", "KEY_DEL", "KEY_AC"),
        listOf("KEY_4", "KEY_5", "KEY_6", "KEY_MUL", "KEY_DIV"),
        listOf("KEY_1", "KEY_2", "KEY_3", "KEY_ADD", "KEY_SUB"),
        listOf("KEY_0", "KEY_DOT", "KEY_EXP", "KEY_ANS", "KEY_EQ"),
    )

    /** Tombol di baris paling atas, mengapit bantalan replay. */
    const val SHIFT = "KEY_SHIFT"
    const val ALPHA = "KEY_ALPHA"
    const val MODE = "KEY_MODE"
    const val ON = "KEY_ON"
    const val UP = "KEY_UP"
    const val LEFT = "KEY_LEFT"
    const val RIGHT = "KEY_RIGHT"
    const val DOWN = "KEY_DOWN"
}
