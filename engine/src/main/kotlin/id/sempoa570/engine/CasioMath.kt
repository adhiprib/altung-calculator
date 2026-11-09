package id.sempoa570.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Fungsi transendental dalam BigDecimal (aturan kerja 4: tanpa Double untuk nilai hitung).
 *
 * Semua deret dihitung pada presisi kerja [P] lalu hasilnya dibungkus [CasioNumber]
 * yang otomatis dipangkas ke 15 digit.
 */
internal object CasioMath {

    /** Presisi kerja deret; lebih tinggi dari WORK agar reduksi argumen tidak menggerus digit. */
    private val P = MathContext(34, RoundingMode.HALF_EVEN)

    /** π dengan 40 digit signifikan. */
    val PI: BigDecimal = BigDecimal("3.141592653589793238462643383279502884197")

    /** e dengan 40 digit signifikan. */
    val E: BigDecimal = BigDecimal("2.718281828459045235360287471352662497757")

    private val TWO = BigDecimal(2)
    private val HALF = BigDecimal("0.5")

    /** Ambang henti deret: dua digit di bawah presisi kerja. */
    private val EPS: BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(-(P.precision - 2))

    fun pi(): CasioNumber = CasioNumber.of(PI)
    fun e(): CasioNumber = CasioNumber.of(E)

    // ---------------------------------------------------------------- exp / ln

    /** exp(x) lewat reduksi x = k·ln2 + r lalu deret Taylor pada |r| ≤ ln2/2. */
    fun exp(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ONE
        val ln2 = ln2()
        val k = x.divide(ln2, MathContext(P.precision, RoundingMode.HALF_EVEN))
            .setScale(0, RoundingMode.HALF_EVEN)
        val r = x.subtract(k.multiply(ln2, P), P)
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        var n = 1
        while (n <= 400) {
            term = term.multiply(r, P).divide(BigDecimal(n), P)
            if (term.abs() < EPS) break
            sum = sum.add(term, P)
            n++
        }
        val shift = try {
            k.intValueExact()
        } catch (e: ArithmeticException) {
            mathError()
        }
        val pow2 = TWO.pow(Math.abs(shift), P)
        return if (shift >= 0) sum.multiply(pow2, P) else sum.divide(pow2, P)
    }

    private var ln2Cache: BigDecimal? = null

    private fun ln2(): BigDecimal = ln2Cache ?: lnSeries(TWO).also { ln2Cache = it }

    /** ln(x) untuk x > 0; skala ke [0.5, 2) dengan pangkat 2 lalu deret atanh. */
    fun ln(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) mathError()
        var m = x
        var k = 0
        while (m >= TWO) {
            m = m.divide(TWO, P); k++
        }
        while (m < HALF) {
            m = m.multiply(TWO, P); k--
        }
        val base = lnSeries(m)
        return if (k == 0) base else base.add(BigDecimal(k).multiply(ln2(), P), P)
    }

    /** ln(m) lewat 2·atanh((m-1)/(m+1)); konvergen cepat untuk m dekat 1. */
    private fun lnSeries(m: BigDecimal): BigDecimal {
        if (m.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO
        val z = m.subtract(BigDecimal.ONE, P).divide(m.add(BigDecimal.ONE, P), P)
        val z2 = z.multiply(z, P)
        var term = z
        var sum = z
        var n = 1
        while (n <= 2000) {
            term = term.multiply(z2, P)
            val add = term.divide(BigDecimal(2 * n + 1), P)
            if (add.abs() < EPS) break
            sum = sum.add(add, P)
            n++
        }
        return sum.multiply(TWO, P)
    }

    // ---------------------------------------------------------------- pangkat & akar

    /**
     * Fungsi galat, dipakai distribusi normal baku pada mode STAT (manual hlm. 56).
     *
     * Memakai deret bersuku positif erf(z) = (2z/√π)·e^(−z²)·Σ (2z²)ⁿ/(2n+1)!!, bukan deret
     * Taylor berselang-seling yang saling menghapus pada z besar. Di atas |z| = 6,5 nilainya
     * sudah ±1 pada 15 digit.
     */
    fun erf(z: BigDecimal): BigDecimal {
        if (z.signum() == 0) return BigDecimal.ZERO
        if (z.abs() > BigDecimal("6.5")) return if (z.signum() > 0) BigDecimal.ONE else BigDecimal.ONE.negate()
        val zz = z.multiply(z, P)
        val twoZZ = zz.multiply(BigDecimal(2), P)
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        var n = 1
        while (n < 400) {
            // suku(n) = suku(n-1) · 2z² / (2n+1)
            term = term.multiply(twoZZ, P).divide(BigDecimal(2L * n + 1), P)
            sum = sum.add(term, P)
            if (term.abs() < sum.abs().multiply(BigDecimal("1E-36"), P)) break
            n++
        }
        val front = BigDecimal(2).multiply(z, P).divide(sqrt(PI), P)
        return front.multiply(exp(zz.negate()), P).multiply(sum, P)
    }

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) mathError()
        if (x.signum() == 0) return BigDecimal.ZERO
        return x.sqrt(P)
    }

    /** x^y umum. Eksponen bulat memakai pangkat eksak; selain itu exp(y·ln x). */
    fun pow(x: BigDecimal, y: BigDecimal): BigDecimal {
        if (y.stripTrailingZeros().scale() <= 0) {
            val n = try {
                y.intValueExact()
            } catch (e: ArithmeticException) {
                mathError()
            }
            if (x.signum() == 0) {
                if (n <= 0) mathError()
                return BigDecimal.ZERO
            }
            if (Math.abs(n) <= 9999) {
                val p = x.abs().pow(Math.abs(n), P)
                val r = if (n >= 0) p else BigDecimal.ONE.divide(p, P)
                return if (x.signum() < 0 && n % 2 != 0) r.negate() else r
            }
        }
        if (x.signum() < 0) mathError()
        if (x.signum() == 0) {
            if (y.signum() <= 0) mathError()
            return BigDecimal.ZERO
        }
        return exp(y.multiply(ln(x), P))
    }

    /** Akar pangkat n; n ganjil boleh berargumen negatif (perilaku Casio untuk ³√). */
    fun root(n: BigDecimal, x: BigDecimal): BigDecimal {
        val ni = try {
            n.intValueExact()
        } catch (e: ArithmeticException) {
            mathError()
        }
        if (ni == 0) mathError()
        if (x.signum() < 0) {
            if (ni % 2 == 0) mathError()
            return root(n, x.negate()).negate()
        }
        if (x.signum() == 0) return BigDecimal.ZERO
        if (ni == 2) return sqrt(x)
        return exp(ln(x).divide(BigDecimal(ni), P))
    }

    // ---------------------------------------------------------------- trigonometri

    /** sin(x) dalam radian; reduksi ke (-π, π] lalu deret Taylor. */
    fun sin(x: BigDecimal): BigDecimal = sinReduced(reduce(x))

    /** cos(x) dalam radian, lewat sin(π/2 - x). */
    fun cos(x: BigDecimal): BigDecimal =
        sinReduced(reduce(PI.divide(TWO, P).subtract(reduce(x), P)))

    fun tan(x: BigDecimal): BigDecimal {
        val c = cos(x)
        if (c.abs() < EPS) mathError()
        return sin(x).divide(c, P)
    }

    /** Membawa x ke (-π, π]. */
    private fun reduce(x: BigDecimal): BigDecimal {
        val twoPi = PI.multiply(TWO, P)
        var r = x.remainder(twoPi, P)
        if (r > PI) r = r.subtract(twoPi, P)
        if (r <= PI.negate()) r = r.add(twoPi, P)
        return r
    }

    private fun sinReduced(r: BigDecimal): BigDecimal {
        var term = r
        var sum = r
        val r2 = r.multiply(r, P)
        var n = 1
        while (n <= 400) {
            term = term.multiply(r2, P)
                .divide(BigDecimal((2 * n) * (2 * n + 1)), P)
                .negate()
            if (term.abs() < EPS) break
            sum = sum.add(term, P)
            n++
        }
        return sum
    }

    /**
     * arctan. Argumen diturunkan tiga kali dengan x → x/(1+√(1+x²)) supaya deret pangkat
     * konvergen cepat, lalu hasilnya dikalikan 2³.
     */
    fun atan(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) return atan(x.negate()).negate()
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x > BigDecimal.ONE) {
            return PI.divide(TWO, P).subtract(atan(BigDecimal.ONE.divide(x, P)), P)
        }
        var v = x
        val halvings = 3
        repeat(halvings) {
            v = v.divide(BigDecimal.ONE.add(sqrt(BigDecimal.ONE.add(v.multiply(v, P), P)), P), P)
        }
        var term = v
        var sum = v
        val v2 = v.multiply(v, P)
        var n = 1
        while (n <= 2000) {
            term = term.multiply(v2, P).negate()
            val add = term.divide(BigDecimal(2 * n + 1), P)
            if (add.abs() < EPS) break
            sum = sum.add(add, P)
            n++
        }
        return sum.multiply(TWO.pow(halvings), P)
    }

    /** arctan dua argumen, hasil dalam (−π, π]; dipakai untuk argumen bilangan kompleks. */
    fun atan2(y: BigDecimal, x: BigDecimal): BigDecimal {
        val half = PI.divide(TWO, P)
        return when {
            x.signum() > 0 -> atan(y.divide(x, P))
            x.signum() < 0 && y.signum() >= 0 -> atan(y.divide(x, P)).add(PI, P)
            x.signum() < 0 -> atan(y.divide(x, P)).subtract(PI, P)
            y.signum() > 0 -> half
            y.signum() < 0 -> half.negate()
            else -> BigDecimal.ZERO
        }
    }

    /** sinh(x) = (e^x - e^-x) / 2. */
    fun sinh(x: BigDecimal): BigDecimal {
        val ex = exp(x)
        return ex.subtract(BigDecimal.ONE.divide(ex, P), P).divide(TWO, P)
    }

    /** cosh(x) = (e^x + e^-x) / 2. */
    fun cosh(x: BigDecimal): BigDecimal {
        val ex = exp(x)
        return ex.add(BigDecimal.ONE.divide(ex, P), P).divide(TWO, P)
    }

    fun tanh(x: BigDecimal): BigDecimal = sinh(x).divide(cosh(x), P)

    /** arsinh(x) = ln(x + akar(x kuadrat + 1)). */
    fun asinh(x: BigDecimal): BigDecimal =
        ln(x.add(sqrt(x.multiply(x, P).add(BigDecimal.ONE, P)), P))

    /** arcosh(x) = ln(x + akar(x kuadrat - 1)), hanya untuk x >= 1. */
    fun acosh(x: BigDecimal): BigDecimal {
        if (x < BigDecimal.ONE) mathError()
        return ln(x.add(sqrt(x.multiply(x, P).subtract(BigDecimal.ONE, P)), P))
    }

    /** artanh(x) = ln((1+x)/(1-x)) / 2, hanya untuk |x| < 1. */
    fun atanh(x: BigDecimal): BigDecimal {
        if (x.abs() >= BigDecimal.ONE) mathError()
        return ln(
            BigDecimal.ONE.add(x, P).divide(BigDecimal.ONE.subtract(x, P), P)
        ).divide(TWO, P)
    }

    fun asin(x: BigDecimal): BigDecimal {
        if (x.abs() > BigDecimal.ONE) mathError()
        if (x.abs().compareTo(BigDecimal.ONE) == 0) {
            val h = PI.divide(TWO, P)
            return if (x.signum() > 0) h else h.negate()
        }
        val d = sqrt(BigDecimal.ONE.subtract(x.multiply(x, P), P))
        return atan(x.divide(d, P))
    }

    fun acos(x: BigDecimal): BigDecimal = PI.divide(TWO, P).subtract(asin(x), P)
}
