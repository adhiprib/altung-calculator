package id.sempoa570.engine

/**
 * Memainkan ulang urutan token tombol pada kalkulator baru.
 *
 * Dipakai bersama oleh golden test dan panduan bawaan: panduan menggambar contohnya dengan
 * menjalankan tombolnya sungguhan, bukan dengan menyalin teks hasil, sehingga yang tampil di
 * panduan mustahil berbeda dari yang tampil di kalkulator.
 *
 * Kosakata [mode] dan [setup] sama dengan dua kolom pertama berkas golden di folder docs/golden.
 */
object Replay {

    /** Menerjemahkan kolom setup menjadi [Setup]; `default` berarti seluruhnya bawaan. */
    fun setupOf(spec: String): Setup {
        var setup = Setup()
        if (spec.isBlank() || spec == "default") return setup
        for (part in spec.split(",").map { it.trim() }) {
            setup = when {
                part == "MthIO" -> setup.copy(input = InputMode.MATH_IO)
                part == "LineIO" -> setup.copy(input = InputMode.LINE_IO)
                part == "Deg" -> setup.copy(angle = AngleUnit.DEG)
                part == "Rad" -> setup.copy(angle = AngleUnit.RAD)
                part == "Gra" -> setup.copy(angle = AngleUnit.GRA)
                part == "Norm1" -> setup.copy(format = DisplayFormat.Norm1)
                part == "Norm2" -> setup.copy(format = DisplayFormat.Norm2)
                part.startsWith("Fix") ->
                    setup.copy(format = DisplayFormat.Fix(part.removePrefix("Fix").toInt()))
                part.startsWith("Sci") ->
                    setup.copy(format = DisplayFormat.Sci(part.removePrefix("Sci").toInt()))
                else -> error("setup tak dikenal: $part")
            }
        }
        return setup
    }

    fun modeOf(name: String): Mode = when (name) {
        "", "COMP" -> Mode.COMP
        "CMPLX" -> Mode.CMPLX
        "BASE" -> Mode.BASE
        "EQN" -> Mode.EQN
        "STAT" -> Mode.STAT
        "MATRIX" -> Mode.MATRIX
        "TABLE" -> Mode.TABLE
        "VECTOR" -> Mode.VECTOR
        else -> error("mode tak dikenal: $name")
    }

    /** Kalkulator sesudah seluruh [keys] ditekan. */
    fun run(mode: String, setup: String, keys: List<Key>): Calculator {
        val calc = Calculator(setupOf(setup))
        val target = modeOf(mode)
        if (target != Mode.COMP) {
            // Pindah mode mengubah basis BASE-N, tetapi tidak boleh membatalkan kolom setup.
            val keep = calc.setup
            calc.selectMode(target)
            calc.setup = keep.copy(base = calc.setup.base)
        }
        calc.pressAll(keys)
        return calc
    }

    /** Seperti [run], tetapi dari nama token dipisah spasi; null bila ada token tak dikenal. */
    fun runTokens(mode: String, setup: String, tokens: String): Calculator? {
        val keys = tokens.split(" ").filter { it.isNotEmpty() }
            .map { Key.byName(it) ?: return null }
        if (keys.isEmpty()) return null
        return try {
            run(mode, setup, keys)
        } catch (e: IllegalStateException) {
            null
        }
    }
}
