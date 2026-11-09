package id.sempoa570.engine

import java.math.BigDecimal

/** Mode kalkulasi (PRD §3.1). */
@kotlinx.serialization.Serializable
enum class Mode { COMP, CMPLX, STAT, BASE, EQN, MATRIX, TABLE, VECTOR }

/** Basis bilangan pada mode BASE-N (PRD §3.1). */
@kotlinx.serialization.Serializable
enum class NumberBase(val radix: Int) { DEC(10), HEX(16), BIN(2), OCT(8) }

/** Bentuk tampilan hasil kompleks (PRD §3.2). */
@kotlinx.serialization.Serializable
enum class ComplexForm { RECTANGULAR, POLAR }

/** Bilangan kompleks: dua [CasioNumber] (PRD §5.1). */
data class Complex(val re: CasioNumber, val im: CasioNumber) {

    val isReal: Boolean get() = im.isZero

    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)

    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)

    operator fun div(o: Complex): Complex {
        val d = o.re * o.re + o.im * o.im
        if (d.isZero) mathError()
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    operator fun unaryMinus() = Complex(-re, -im)

    fun conjugate() = Complex(re, -im)

    /** |z|² sebagai nilai yang masih bisa eksak, supaya Abs(1+i) tampil sebagai √2. */
    fun squaredMagnitude(): CasioNumber = re * re + im * im

    /** Argumen dalam satuan sudut aktif, rentang −180° < θ ≤ 180° (PRD §3.2). */
    fun argument(unit: AngleUnit): CasioNumber {
        if (re.isZero && im.isZero) mathError()
        val radians = CasioMath.atan2(im.value, re.value)
        return fromRadians(radians, unit)
    }

    companion object {
        val ZERO = Complex(CasioNumber.ZERO, CasioNumber.ZERO)
        val I = Complex(CasioNumber.ZERO, CasioNumber.ONE)

        fun real(n: CasioNumber) = Complex(n, CasioNumber.ZERO)

        /** r∠θ menjadi bentuk a+bi, dengan θ dalam satuan sudut aktif. */
        fun polar(r: CasioNumber, theta: CasioNumber, unit: AngleUnit): Complex {
            val radians = toRadians(theta, unit)
            return Complex(
                snap(CasioNumber.of(CasioMath.cos(radians))) * r,
                snap(CasioNumber.of(CasioMath.sin(radians))) * r,
            )
        }

        internal fun toRadians(theta: CasioNumber, unit: AngleUnit): BigDecimal = when (unit) {
            AngleUnit.RAD -> theta.value
            AngleUnit.DEG -> theta.value.multiply(CasioMath.PI, CasioNumber.WORK)
                .divide(BigDecimal(180), CasioNumber.WORK)
            AngleUnit.GRA -> theta.value.multiply(CasioMath.PI, CasioNumber.WORK)
                .divide(BigDecimal(200), CasioNumber.WORK)
        }

        internal fun fromRadians(radians: BigDecimal, unit: AngleUnit): CasioNumber = when (unit) {
            AngleUnit.RAD -> CasioNumber.of(radians)
            AngleUnit.DEG -> CasioNumber.of(
                radians.multiply(BigDecimal(180), CasioNumber.WORK)
                    .divide(CasioMath.PI, CasioNumber.WORK)
            )
            AngleUnit.GRA -> CasioNumber.of(
                radians.multiply(BigDecimal(200), CasioNumber.WORK)
                    .divide(CasioMath.PI, CasioNumber.WORK)
            )
        }

        /** Nilai yang sangat dekat bilangan bulat dirapikan, supaya cos 90° tepat nol. */
        private fun snap(n: CasioNumber): CasioNumber {
            val rounded = n.roundSignificant(11)
            return if (rounded.isInteger) rounded else n
        }
    }
}

/**
 * Bilangan BASE-N: bilangan bulat 32 bit dengan komplemen dua (PRD §5.1).
 *
 * Seluruh operasi membungkus seperti register 32 bit, sehingga hasil di luar jangkauan
 * −2147483648..2147483647 berputar persis seperti kalkulator fisik.
 */
@JvmInline
value class BaseN(val bits: Int) {

    operator fun plus(o: BaseN) = BaseN(bits + o.bits)
    operator fun minus(o: BaseN) = BaseN(bits - o.bits)
    operator fun times(o: BaseN) = BaseN(bits * o.bits)

    /** Pembagian BASE-N memotong bagian pecahan, bukan membulatkan. */
    operator fun div(o: BaseN): BaseN {
        if (o.bits == 0) mathError()
        return BaseN(bits / o.bits)
    }

    operator fun unaryMinus() = BaseN(-bits)

    infix fun and(o: BaseN) = BaseN(bits and o.bits)
    infix fun or(o: BaseN) = BaseN(bits or o.bits)
    infix fun xor(o: BaseN) = BaseN(bits xor o.bits)
    infix fun xnor(o: BaseN) = BaseN((bits xor o.bits).inv())
    fun not() = BaseN(bits.inv())

    fun toNumber(): CasioNumber = CasioNumber.of(bits.toLong())

    /** Tulisan pada basis tertentu; basis selain desimal memakai pola bit tanpa tanda. */
    fun format(base: NumberBase): String = when (base) {
        NumberBase.DEC -> bits.toString()
        NumberBase.HEX -> bits.toUInt().toString(16).uppercase()
        NumberBase.BIN -> bits.toUInt().toString(2)
        NumberBase.OCT -> bits.toUInt().toString(8)
    }

    companion object {
        fun of(n: CasioNumber): BaseN = BaseN(n.value.toBigInteger().toInt())

        /** Membaca runtun digit pada basis tertentu; digit di luar basis adalah Syntax ERROR. */
        fun parse(text: String, base: NumberBase): BaseN {
            if (text.isEmpty()) syntaxError()
            var acc = 0L
            for (ch in text) {
                val digit = Character.digit(ch, base.radix)
                if (digit < 0) syntaxError()
                acc = acc * base.radix + digit
                if (acc > 0xFFFFFFFFL) mathError()
            }
            return BaseN(acc.toInt())
        }
    }
}
