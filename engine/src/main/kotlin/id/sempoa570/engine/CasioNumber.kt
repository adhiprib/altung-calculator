package id.sempoa570.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Pesan error kalkulator (PRD §3.3). */
enum class ErrorKind(val display: String) {
    MATH("Math ERROR"),
    SYNTAX("Syntax ERROR"),
    STACK("Stack ERROR"),
    ARGUMENT("Argument ERROR"),
    DIMENSION("Dimension ERROR"),
    VARIABLE("Variable ERROR"),
    INSUFFICIENT_MEM("Insufficient MEM"),
    CANT_SOLVE("Can't Solve"),
    TIME_OUT("Time Out"),
}

/**
 * [at] adalah indeks simpul tingkat atas tempat error terjadi, atau -1 bila tak diketahui.
 * Dipakai layar error: ◄ dan ► menaruh kursor di situ (manual hlm. 72).
 */
class CalcError(val kind: ErrorKind, val at: Int = -1) : RuntimeException(kind.display) {
    /** Melekatkan posisi bila belum ada; yang terdalam menang saat error menyembul. */
    fun locatedAt(position: Int): CalcError =
        if (at >= 0 || position < 0) this else CalcError(kind, position)
}

fun mathError(): Nothing = throw CalcError(ErrorKind.MATH)

fun syntaxError(): Nothing = throw CalcError(ErrorKind.SYNTAX)

/**
 * Nilai numerik kalkulator: desimal 15 digit signifikan (PRD §5.1).
 *
 * Perhitungan antara memakai [WORK] (25 digit) agar galat pembulatan tidak naik ke digit ke-15,
 * lalu tiap nilai yang disimpan dipangkas ke 15 digit dengan HALF_UP seperti kalkulator fisik.
 */
class CasioNumber private constructor(val value: BigDecimal) : Comparable<CasioNumber> {

    operator fun plus(o: CasioNumber) = of(value.add(o.value, WORK))
    operator fun minus(o: CasioNumber) = of(value.subtract(o.value, WORK))
    operator fun times(o: CasioNumber) = of(value.multiply(o.value, WORK))

    operator fun div(o: CasioNumber): CasioNumber {
        if (o.isZero) mathError()
        return of(value.divide(o.value, WORK))
    }

    operator fun unaryMinus() = of(value.negate())

    val isZero: Boolean get() = value.signum() == 0
    val signum: Int get() = value.signum()
    val isNegative: Boolean get() = value.signum() < 0

    /** true bila nilainya bilangan bulat persis. */
    val isInteger: Boolean get() = value.stripTrailingZeros().scale() <= 0

    fun toLongExact(): Long {
        if (!isInteger) mathError()
        return try {
            value.longValueExact()
        } catch (e: ArithmeticException) {
            mathError()
        }
    }

    fun toIntOrNull(): Int? =
        if (!isInteger) null else try {
            value.intValueExact()
        } catch (e: ArithmeticException) {
            null
        }

    fun abs() = of(value.abs())

    /** Pembulatan ke [digits] digit signifikan, HALF_UP (formatter dan Rnd). */
    fun roundSignificant(digits: Int) = of(value.round(MathContext(digits, RoundingMode.HALF_UP)))

    /** Pembulatan ke [places] angka di belakang koma, HALF_UP (Fix). */
    fun roundScale(places: Int) = of(value.setScale(places, RoundingMode.HALF_UP))

    override fun compareTo(other: CasioNumber): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is CasioNumber && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = value.toPlainString()

    companion object {
        /** Presisi kerja internal; lebih tinggi dari 15 agar pembulatan antara tidak bocor. */
        val WORK: MathContext = MathContext(25, RoundingMode.HALF_UP)

        /** Presisi nilai tersimpan (PRD §5.1). */
        val STORE: MathContext = MathContext(15, RoundingMode.HALF_UP)

        /** Batas jangkauan kalkulator: |x| < 1e100 (PRD §3.3). */
        private val LIMIT: BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(100)

        /** Batas bawah; di bawah ini kalkulator menampilkan nol (PRD §5.1). */
        private val UNDERFLOW: BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(-99)

        val ZERO = of(BigDecimal.ZERO)
        val ONE = of(BigDecimal.ONE)
        val TWO = of(BigDecimal.valueOf(2))
        val TEN = of(BigDecimal.TEN)

        fun of(v: BigDecimal): CasioNumber {
            val rounded = v.round(STORE)
            if (rounded.abs() >= LIMIT) mathError()
            if (rounded.signum() != 0 && rounded.abs() < UNDERFLOW) {
                return CasioNumber(BigDecimal.ZERO)
            }
            return CasioNumber(rounded)
        }

        fun of(v: Long): CasioNumber = of(BigDecimal.valueOf(v))

        fun parse(text: String): CasioNumber = of(BigDecimal(text))
    }
}
