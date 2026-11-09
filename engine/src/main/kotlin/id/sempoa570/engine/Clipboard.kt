package id.sempoa570.engine

/**
 * Teks papan klip menjadi urutan tombol.
 *
 * Tidak ada jalan dari teks kembali ke pohon ekspresi di mana pun: [Parser] menerima `List<Node>`,
 * bukan `String`. Cara termurah yang tetap benar adalah memainkan ulang teksnya sebagai tekanan
 * tombol lewat [Calculator.press], sehingga seluruh aturan editor — templat berslot, pecahan yang
 * memungut operand di kirinya, kurung yang belum ditutup — berlaku apa adanya tanpa ditulis ulang.
 *
 * Kosakatanya sengaja dibuat sama dengan yang dicetak [ExprRenderer], jadi teks yang disalin dari
 * aplikasi ini dapat ditempel kembali utuh, sekaligus cukup longgar untuk menerima angka dan
 * rumus sederhana dari aplikasi lain.
 *
 * ponytail: bentuk yang tidak terwakili teks sebaris — misalnya matriks atau hasil bertingkat —
 * di luar jangkauan. Naikkan ke pengurai penuh hanya bila memang dibutuhkan.
 */
object Clipboard {

    /** Awalan yang menelan lebih dari satu huruf; diperiksa sebelum pemetaan per huruf. */
    private val PREFIXES: List<Pair<String, Key>> = listOf(
        "³√(" to Key.FN_CBRT,
        "sin⁻¹(" to Key.FN_ASIN,
        "cos⁻¹(" to Key.FN_ACOS,
        "tan⁻¹(" to Key.FN_ATAN,
        "Abs(" to Key.FN_ABS,
        "sin(" to Key.FN_SIN,
        "cos(" to Key.FN_COS,
        "tan(" to Key.FN_TAN,
        "log(" to Key.FN_LOG,
        "ln(" to Key.FN_LN,
        // Templat sudah membawa slotnya sendiri, jadi kurung buka sesudahnya ikut ditelan;
        // kurung tutup pasangannya nanti yang mengeluarkan kursor dari slot itu.
        "√(" to Key.FN_SQRT,
        "^(" to Key.OP_POW,
        "√" to Key.FN_SQRT,
        "^" to Key.OP_POW,
    )

    private val SINGLES: Map<Char, Key> = mapOf(
        '+' to Key.OP_ADD,
        '×' to Key.OP_MUL,
        '*' to Key.OP_MUL,
        '÷' to Key.OP_DIV,
        '/' to Key.OP_DIV,
        '(' to Key.PAREN_OPEN,
        ')' to Key.PAREN_CLOSE,
        '⌟' to Key.FRAC,
        'π' to Key.CONST_PI,
        ',' to Key.SEP_COMMA,
        '%' to Key.POST_PERCENT,
        '!' to Key.POST_FACT,
        '.' to Key.DOT,
        'A' to Key.VAR_A,
        'B' to Key.VAR_B,
        'C' to Key.VAR_C,
        'D' to Key.VAR_D,
        'F' to Key.VAR_F,
        'M' to Key.VAR_M,
        'X' to Key.VAR_X,
        'Y' to Key.VAR_Y,
    )

    /** Sesudah tombol-tombol ini, tanda kurang berarti tanda negatif, bukan pengurangan. */
    private val EXPECTS_SIGN = setOf(
        Key.OP_ADD, Key.OP_SUB, Key.OP_MUL, Key.OP_DIV, Key.OP_NEG,
        Key.OP_POW, Key.PAREN_OPEN, Key.SEP_COMMA, Key.FRAC, Key.EXP10,
    )

    /**
     * Urutan tombol yang mewakili [text], atau null bila ada huruf yang tak dikenal.
     *
     * Sengaja menolak seluruhnya, bukan menempel sebagiannya: tempelan separuh lebih sulit
     * disadari pemakai daripada tempelan yang tidak terjadi.
     */
    fun keysFrom(text: String): List<Key>? {
        if (text.isBlank()) return null
        val keys = mutableListOf<Key>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.isWhitespace()) {
                i++
                continue
            }
            if (ch.isDigit()) {
                keys += Key.entries.first { it.name == "DIGIT_$ch" }
                i++
                continue
            }
            if (ch == '-' || ch == '−') {
                val negative = keys.isEmpty() || keys.last() in EXPECTS_SIGN
                keys += if (negative) Key.OP_NEG else Key.OP_SUB
                i++
                continue
            }
            // `E` bermakna ganda: eksponen pada 1.5E3, tetapi variabel E bila berdiri sendiri.
            if (ch == 'E') {
                keys += if (keys.lastOrNull()?.isDigit == true) Key.EXP10 else Key.VAR_E
                i++
                continue
            }
            val prefix = PREFIXES.firstOrNull { text.startsWith(it.first, i) }
            if (prefix != null) {
                keys += prefix.second
                i += prefix.first.length
                continue
            }
            val single = SINGLES[ch] ?: return null
            keys += single
            i++
        }
        return keys.ifEmpty { null }
    }
}
