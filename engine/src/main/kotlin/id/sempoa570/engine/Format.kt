package id.sempoa570.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Cara hasil ditampilkan saat ini; diubah oleh S⇔D, a b/c ⇔ d/c, dan ENG. */
data class ResultView(
    val decimal: Boolean = false,
    val mixed: Boolean = false,
    val engActive: Boolean = false,
    /** Pergeseran eksponen ENG dalam kelipatan tiga. */
    val engShift: Int = 0,
    /** Ekspresi yang menghasilkan nilai ini memakai tombol pecahan. */
    val fromFractionInput: Boolean = true,
    /**
     * Bentuk bilangan kompleks yang dipaksakan perintah `▸r∠θ` atau `▸a+bi` di ujung ekspresi;
     * null berarti mengikuti Setup. Perintah itu menimpa setelan (manual hlm. 47).
     */
    val complexForm: ComplexForm? = null,
)

/**
 * Formatter tampilan (PRD §5.4): nilai internal 15 digit dibulatkan ke 10 digit untuk tampilan,
 * sedangkan Fix/Sci/Norm hanya mengatur bentuk tampilan, bukan nilai yang disimpan.
 */
object ResultFormatter {

    /** Jumlah digit tampilan kalkulator (PRD §3.3). */
    private const val DISPLAY_DIGITS = 10

    fun format(value: Value, setup: Setup, view: ResultView = ResultView()): String {
        if (value is Value.Mat) return value.value.toString()
        if (value is Value.Vec) return value.value.toString()
        if (value is Value.Bn) return value.value.format(setup.base)
        if (value is Value.Cx) return complexText(value.value, setup, view)
        if (view.engActive) return engineering(value.number, setup, view.engShift)
        if (!view.decimal) {
            exactText(value, setup, view)?.let { return it }
        }
        return decimal(value.number, setup)
    }

    /**
     * Hasil sebagai pohon simpul, supaya baris hasil digambar dengan tata letak dua dimensi
     * yang sama dengan baris ekspresi: pecahan bertingkat bergaris, akar dengan tanda akar.
     */
    fun tree(value: Value, setup: Setup, view: ResultView = ResultView()): List<Node> {
        if (value is Value.Cx && !view.decimal && formOf(setup, view) == ComplexForm.POLAR) {
            // Bentuk kutub pun digambar dua dimensi, supaya 2√2∠45 memakai tanda akar sungguhan.
            val z = value.value
            val plain = setup.copy(complexForm = ComplexForm.RECTANGULAR)
            val magnitude = scalar(CasioNumber.of(CasioMath.sqrt(z.squaredMagnitude().value)))
            return tree(magnitude, plain, view) + KeyNode(Key.OP_ANGLE) +
                expNodes(decimal(z.argument(setup.angle), setup))
        }
        if (value is Value.Cx && !view.decimal && formOf(setup, view) == ComplexForm.RECTANGULAR) {
            val z = value.value
            if (!z.isReal) {
                val plain = setup.copy(complexForm = ComplexForm.RECTANGULAR)
                val magnitude = scalar(z.im.abs())
                // Koefisien 1 tidak ditulis: manual hlm. 46 menampilkan 3-i, bukan 3-1i.
                val body =
                    if (format(magnitude, plain, view) == "1") emptyList()
                    else tree(magnitude, setup, view)
                val imaginary = body + KeyNode(Key.CONST_I)
                if (z.re.isZero) {
                    val sign = if (z.im.isNegative) listOf(KeyNode(Key.OP_NEG)) else emptyList()
                    return sign + imaginary
                }
                val sign = KeyNode(if (z.im.isNegative) Key.OP_SUB else Key.OP_ADD)
                return tree(scalar(z.re), setup, view) + sign + imaginary
            }
        }
        exactTree(value, setup, view)?.let { return it }
        return expNodes(format(value, setup, view))
    }

    /**
     * Memecah teks bereksponen `1.234E3` menjadi simpul, supaya LCD menggambarnya `1.234×10³`
     * seperti unit fisik. Teks tanpa eksponen dikembalikan sebagai satu simpul.
     */
    private fun expNodes(text: String): List<Node> {
        val at = text.indexOf('E')
        if (at <= 0 || at == text.lastIndex) return listOf(NumNode(text))
        val exponent = text.substring(at + 1)
        val digits = exponent.removePrefix("-")
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return listOf(NumNode(text))
        val sign = if (exponent.startsWith("-")) listOf(KeyNode(Key.OP_NEG)) else emptyList()
        return listOf(NumNode(text.substring(0, at)), KeyNode(Key.EXP10)) + sign + NumNode(digits)
    }

    /**
     * Satu nilai skalar siap format, dengan bentuk eksaknya dikenali dari desimal karena
     * [Complex] dan akar EQN hanya menyimpan angka: manual hlm. 47 menampilkan `2∠45▸a+bi`
     * sebagai √2+√2i, dan hlm. 62 Contoh 4 menampilkan akar kembar sebagai √2.
     */
    fun scalar(n: CasioNumber): Value =
        Exact.recognise(n)?.takeIf { ExactMath.isDisplayable(it) }?.let { Value.Ex(it) }
            ?: Value.Num(n)

    /** Bentuk kompleks yang berlaku: perintah di ujung ekspresi menimpa Setup (manual hlm. 47). */
    private fun formOf(setup: Setup, view: ResultView): ComplexForm =
        view.complexForm ?: setup.complexForm

    /**
     * Bentuk eksak yang mewakili [value] di layar.
     *
     * Hanya nilai yang memang eksak, tidak menebak dari desimal: `4×3+2.5` harus tetap `14.5`,
     * bukan `29⌟2` (golden comp-basic baris 15, dari manual). Keeksakan dibawa dari operannya
     * sejak dievaluasi, dan hasil hitung numerik seperti ∫ dikenali di [Evaluator], bukan di sini.
     */
    private fun exactOf(value: Value): Exact? = (value as? Value.Ex)?.exact

    /** Bentuk eksak sebagai pohon; null bila nilainya memang tampil sebagai satu teks. */
    private fun exactTree(value: Value, setup: Setup, view: ResultView): List<Node>? {
        if (view.decimal || view.engActive) return null
        val e = exactOf(value) ?: return null
        if (setup.format is DisplayFormat.Fix || setup.format is DisplayFormat.Sci) return null
        if (!ExactMath.isDisplayable(e)) return null
        val lineIo = setup.input == InputMode.LINE_IO
        return when (e) {
            is Exact.Rat -> when {
                e.isInteger -> null
                lineIo && !view.fromFractionInput -> null
                view.mixed -> null
                else -> listOf(fractionNode(e.p.toString(), e.q.toString()))
            }
            is Exact.Surd, is Exact.Sum -> {
                if (lineIo) return null
                val parts = surdParts(e) ?: return null
                val (leadCoefficient, leadRadicand) = parts.terms[0]
                var body = surdTermNodes(leadCoefficient, leadRadicand)
                if (parts.terms.size > 1) {
                    val (coefficient, radicand) = parts.terms[1]
                    val sign = if (coefficient.signum() < 0) Key.OP_SUB else Key.OP_ADD
                    body = body + KeyNode(sign) + surdTermNodes(coefficient.abs(), radicand)
                }
                if (parts.denominator == BigInteger.ONE) body
                else listOf(
                    CallNode(
                        Key.FRAC,
                        listOf(body, listOf(NumNode(parts.denominator.toString()))),
                    )
                )
            }
            is Exact.PiMul -> {
                if (lineIo) return null
                val numerator = when (e.p) {
                    BigInteger.ONE -> listOf(KeyNode(Key.CONST_PI))
                    BigInteger.ONE.negate() -> listOf(KeyNode(Key.OP_NEG), KeyNode(Key.CONST_PI))
                    else -> listOf(NumNode(e.p.toString()), KeyNode(Key.CONST_PI))
                }
                if (e.q == BigInteger.ONE) numerator
                else listOf(CallNode(Key.FRAC, listOf(numerator, listOf(NumNode(e.q.toString())))))
            }
        }
    }

    /**
     * Satu suku bentuk akar sebagai simpul: koefisien lalu tanda akar. Radikan 1 berarti suku
     * bulat, jadi hanya angkanya yang ditulis (bentuk `±d ± a√b` manual hlm. 17).
     */
    private fun surdTermNodes(coefficient: BigInteger, radicand: BigInteger): List<Node> {
        if (radicand == BigInteger.ONE) return listOf(NumNode(coefficient.toString()))
        val root = CallNode(Key.FN_SQRT, listOf(listOf(NumNode(radicand.toString()))))
        return when (coefficient) {
            BigInteger.ONE -> listOf(root)
            BigInteger.ONE.negate() -> listOf(KeyNode(Key.OP_NEG), root)
            else -> listOf(NumNode(coefficient.toString()), root)
        }
    }

    private fun fractionNode(numerator: String, denominator: String): Node =
        CallNode(Key.FRAC, listOf(listOf(NumNode(numerator)), listOf(NumNode(denominator))))

    /**
     * Teks bentuk eksak, atau null bila nilainya harus tampil desimal.
     *
     * Bentuk √ dan π hanya muncul di MthIO; pecahan muncul di kedua mode input.
     * Fix dan Sci selalu memaksa tampilan desimal, seperti kalkulator fisik.
     */
    private fun exactText(value: Value, setup: Setup, view: ResultView): String? {
        val e = exactOf(value) ?: return null
        if (setup.format is DisplayFormat.Fix || setup.format is DisplayFormat.Sci) return null
        if (!ExactMath.isDisplayable(e)) return null
        val lineIo = setup.input == InputMode.LINE_IO
        return when (e) {
            is Exact.Rat -> when {
                e.isInteger -> null
                // LineIO hanya menampilkan pecahan bila ekspresinya memang memakai tombol pecahan.
                lineIo && !view.fromFractionInput -> null
                else -> ratText(e, view.mixed)
            }
            is Exact.Surd, is Exact.Sum -> if (lineIo) null else surdText(e)
            is Exact.PiMul -> if (lineIo) null else piText(e)
        }
    }

    /** a+bi atau r∠θ sesuai Setup, dengan bagian nol disembunyikan seperti kalkulator fisik. */
    private fun complexText(z: Complex, setup: Setup, view: ResultView): String {
        if (formOf(setup, view) == ComplexForm.POLAR) {
            val magnitude = format(
                scalar(CasioNumber.of(CasioMath.sqrt(z.squaredMagnitude().value))),
                setup.copy(complexForm = ComplexForm.RECTANGULAR),
                view,
            )
            val angle = decimal(z.argument(setup.angle), setup)
            return "$magnitude∠$angle"
        }
        val plain = setup.copy(complexForm = ComplexForm.RECTANGULAR)
        val realText = format(scalar(z.re), plain, view)
        if (z.isReal) return realText
        val imagValue = format(scalar(z.im.abs()), plain, view)
        val imagText = if (imagValue == "1") "i" else imagValue + "i"
        val sign = if (z.im.isNegative) "-" else "+"
        if (z.re.isZero) return if (z.im.isNegative) "-$imagText" else imagText
        return "$realText$sign$imagText"
    }

    private fun ratText(e: Exact.Rat, mixed: Boolean): String {
        if (e.isInteger) return e.p.toString()
        if (!mixed) return "${e.p}⌟${e.q}"
        val whole = e.p.abs() / e.q
        if (whole.signum() == 0) return "${e.p}⌟${e.q}"
        val rem = e.p.abs() % e.q
        val sign = if (e.p.signum() < 0) "-" else ""
        return "$sign$whole⌟$rem⌟${e.q}"
    }

    /** Satu atau dua suku berakar; dua suku berpenyebut ditulis `(√(6)+2√(3))⌟5`. */
    private fun surdText(e: Exact): String {
        val parts = surdParts(e) ?: return ""
        val (leadCoefficient, leadRadicand) = parts.terms[0]
        var body = surdTermText(leadCoefficient, leadRadicand)
        if (parts.terms.size > 1) {
            val (coefficient, radicand) = parts.terms[1]
            val sign = if (coefficient.signum() < 0) "-" else "+"
            body += sign + surdTermText(coefficient.abs(), radicand)
        }
        if (parts.denominator == BigInteger.ONE) return body
        return if (parts.terms.size > 1) "($body)⌟${parts.denominator}"
        else "$body⌟${parts.denominator}"
    }

    private fun surdTermText(coefficient: BigInteger, radicand: BigInteger): String {
        if (radicand == BigInteger.ONE) return coefficient.toString()
        val coef = when (coefficient) {
            BigInteger.ONE -> ""
            BigInteger.ONE.negate() -> "-"
            else -> coefficient.toString()
        }
        return "$coef√($radicand)"
    }

    private fun piText(e: Exact.PiMul): String {
        if (e.q != BigInteger.ONE) return "${e.p}⌟${e.q}π"
        val coef = when (e.p) {
            BigInteger.ONE -> ""
            BigInteger.ONE.negate() -> "-"
            else -> e.p.toString()
        }
        return "${coef}π"
    }

    // ------------------------------------------------------------------ desimal

    fun decimal(n: CasioNumber, setup: Setup): String = when (val f = setup.format) {
        is DisplayFormat.Fix -> n.value.setScale(f.places, RoundingMode.HALF_UP).toPlainString()
        is DisplayFormat.Sci -> scientific(n, f.digits)
        DisplayFormat.Norm1 -> norm(n, lowerExponent = -2)
        DisplayFormat.Norm2 -> norm(n, lowerExponent = -9)
    }

    private fun scientific(n: CasioNumber, digits: Int): String {
        if (n.isZero) return BigDecimal.ZERO.setScale(digits - 1).toPlainString() + "E0"
        val rounded = n.roundSignificant(digits).value
        val exponent = rounded.precision() - rounded.scale() - 1
        val mantissa = rounded.movePointLeft(exponent)
        return mantissa.setScale(digits - 1, RoundingMode.HALF_UP).toPlainString() + "E" + exponent
    }

    /**
     * Norm 1 dan Norm 2: desimal biasa selama eksponennya dalam jangkauan, selain itu notasi ilmiah.
     * Norm 1 beralih ke notasi ilmiah di bawah 10⁻², Norm 2 di bawah 10⁻⁹ (PRD §3.3).
     */
    private fun norm(n: CasioNumber, lowerExponent: Int): String {
        if (n.isZero) return "0"
        val rounded = n.roundSignificant(DISPLAY_DIGITS).value.stripTrailingZeros()
        val exponent = rounded.precision() - rounded.scale() - 1
        if (exponent >= DISPLAY_DIGITS || exponent < lowerExponent) {
            val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros()
            return mantissa.toPlainString() + "E" + exponent
        }
        return rounded.toPlainString()
    }

    /** Notasi teknik: eksponen kelipatan tiga, digeser oleh ENG dan ◄ENG. */
    private fun engineering(n: CasioNumber, setup: Setup, shift: Int): String {
        if (n.isZero) return "0E0"
        val rounded = n.roundSignificant(DISPLAY_DIGITS).value.stripTrailingZeros()
        val exponent = rounded.precision() - rounded.scale() - 1
        val target = Math.floorDiv(exponent, 3) * 3 + shift * 3
        val mantissa = rounded.movePointLeft(target).stripTrailingZeros()
        val f = setup.format
        val text = if (f is DisplayFormat.Fix) {
            mantissa.setScale(f.places, RoundingMode.HALF_UP).toPlainString()
        } else {
            mantissa.toPlainString()
        }
        return text + "E" + target
    }
}
