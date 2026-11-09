package id.sempoa570.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Bentuk hasil eksak untuk MthIO (PRD §5.1): pecahan rasional, kelipatan akar, kelipatan π,
 * dan jumlah dua suku berakar.
 *
 * Batasnya diambil dari "√ Form Calculation Range" manual hlm. 17: hasil berakar paling banyak
 * dua suku (suku bulat ikut dihitung), berbentuk ± a√b/c ± d√e/f.
 */
sealed class Exact {

    abstract fun toNumber(): CasioNumber

    /** p/q dengan q > 0 dan sudah disederhanakan. */
    data class Rat(val p: BigInteger, val q: BigInteger) : Exact() {
        override fun toNumber(): CasioNumber =
            CasioNumber.of(BigDecimal(p).divide(BigDecimal(q), CasioNumber.WORK))

        val isInteger: Boolean get() = q == BigInteger.ONE
    }

    /** (p/q)·√r dengan r bebas kuadrat dan r > 1. */
    data class Surd(val p: BigInteger, val q: BigInteger, val r: BigInteger) : Exact() {
        override fun toNumber(): CasioNumber {
            val root = CasioMath.sqrt(BigDecimal(r))
            return CasioNumber.of(
                BigDecimal(p).multiply(root, CasioNumber.WORK)
                    .divide(BigDecimal(q), CasioNumber.WORK)
            )
        }
    }

    /**
     * Dua suku berakar, misal `√6+2√3` (manual hlm. 20 Contoh 2).
     *
     * Invarian yang dijaga [Exact.sum]: tiap suku [Rat] atau [Surd], radikannya berbeda, dan
     * tak satu pun nol. Suku rasional ditulis lebih dulu (bentuk `±d ± a√b` manual hlm. 17);
     * bila keduanya berakar, radikan yang lebih besar dulu.
     *
     * ponytail: urutan suku disimpulkan dari dua contoh manual (`45√3+10√2` hlm. 18 dan
     * `√6+2√3` hlm. 20), tidak dinyatakan manual. Betulkan bila unit fisik berbeda.
     */
    data class Sum(val first: Exact, val second: Exact) : Exact() {
        override fun toNumber(): CasioNumber = first.toNumber() + second.toNumber()
    }

    /** (p/q)·π. */
    data class PiMul(val p: BigInteger, val q: BigInteger) : Exact() {
        override fun toNumber(): CasioNumber = CasioNumber.of(
            BigDecimal(p).multiply(CasioMath.PI, CasioNumber.WORK)
                .divide(BigDecimal(q), CasioNumber.WORK)
        )
    }

    companion object {
        val ZERO: Exact = Rat(BigInteger.ZERO, BigInteger.ONE)
        val ONE: Exact = Rat(BigInteger.ONE, BigInteger.ONE)
        val PI: Exact = PiMul(BigInteger.ONE, BigInteger.ONE)

        fun rat(p: BigInteger, q: BigInteger): Exact {
            if (q.signum() == 0) mathError()
            var np = p
            var nq = q
            if (nq.signum() < 0) {
                np = np.negate(); nq = nq.negate()
            }
            val g = np.gcd(nq)
            if (g > BigInteger.ONE) {
                np /= g; nq /= g
            }
            return Rat(np, nq)
        }

        fun int(v: Long): Exact = Rat(BigInteger.valueOf(v), BigInteger.ONE)

        /** Bentuk eksak dari nilai desimal berhingga, bila pecahannya cukup pendek. */
        fun fromDecimal(n: CasioNumber): Exact? {
            val bd = n.value.stripTrailingZeros()
            if (bd.scale() > 12) return null
            return if (bd.scale() <= 0) {
                rat(bd.toBigIntegerExact(), BigInteger.ONE)
            } else {
                rat(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
            }
        }

        /**
         * Menebak bentuk eksak dari nilai desimal: p/q, (p/q)√r, atau (p/q)π.
         *
         * Perlu karena sebagian hasil hanya berupa angka, tanpa jejak eksak yang bisa dibawa
         * dari operandnya. Manual hlm. 32 Contoh 3 menampilkan hasil ∫ sebagai π, padahal
         * integrasi numerik cuma menghasilkan 3,14159265358979. Pencocokan dibatasi
         * [TOLERANCE], seketat presisi kerja 15 digit, sehingga desimal biasa seperti sin 1
         * tidak ikut tertebak: hampiran terbaik berpenyebut ≤ [MAX_DENOMINATOR] untuk bilangan
         * tak rasional meleset jauh di atas ambang itu.
         */
        fun recognise(n: CasioNumber): Exact? {
            fromDecimal(n)?.let { return it }
            val x = n.value
            if (x.signum() == 0) return ZERO
            ratioOf(x)?.let { (p, q) -> return rat(p, q) }
            ratioOf(x.divide(CasioMath.PI, CasioNumber.WORK))?.let { (p, q) -> return piMul(p, q) }
            // ponytail: radikan dibatasi 100, cukup untuk √2 sampai √99 yang muncul di praktik;
            // naikkan bila unit fisik ternyata menampilkan akar yang lebih besar.
            for (r in 2..MAX_RADICAND) {
                val radicand = BigInteger.valueOf(r.toLong())
                if (radicand.sqrt().pow(2) == radicand) continue
                val root = CasioMath.sqrt(BigDecimal(r))
                ratioOf(x.divide(root, CasioNumber.WORK))
                    ?.let { (p, q) -> return surd(p, q, radicand) }
            }
            return null
        }

        private const val MAX_RADICAND = 100
        private val MAX_DENOMINATOR = BigInteger.valueOf(100_000L)
        private val TOLERANCE = BigDecimal("1E-13")

        /**
         * Pecahan terdekat dengan [x] lewat pecahan lanjut; null bila tak ada yang cukup dekat
         * sebelum penyebutnya melewati [MAX_DENOMINATOR].
         */
        private fun ratioOf(x: BigDecimal): Pair<BigInteger, BigInteger>? {
            val negative = x.signum() < 0
            val target = x.abs()
            val limit = TOLERANCE.multiply(target.max(BigDecimal.ONE))
            var previousP = BigInteger.ZERO
            var currentP = BigInteger.ONE
            var previousQ = BigInteger.ONE
            var currentQ = BigInteger.ZERO
            var rest = target
            repeat(24) {
                val whole = rest.setScale(0, RoundingMode.FLOOR).toBigInteger()
                val p = whole * currentP + previousP
                val q = whole * currentQ + previousQ
                if (q.signum() == 0 || q > MAX_DENOMINATOR) return null
                val approximation = BigDecimal(p).divide(BigDecimal(q), CasioNumber.WORK)
                if (approximation.subtract(target).abs() <= limit) {
                    return (if (negative) p.negate() else p) to q
                }
                val fraction = rest.subtract(BigDecimal(whole))
                if (fraction.signum() == 0) return null
                rest = BigDecimal.ONE.divide(fraction, CasioNumber.WORK)
                previousP = currentP; currentP = p
                previousQ = currentQ; currentQ = q
            }
            return null
        }

        /** (p/q)·√r dinormalkan: faktor kuadrat ditarik keluar, r = 1 menjadi [Rat]. */
        fun surd(p: BigInteger, q: BigInteger, r: BigInteger): Exact {
            if (r.signum() < 0) mathError()
            if (q.signum() == 0) mathError()
            if (r.signum() == 0 || p.signum() == 0) return ZERO
            var coef = p
            var rad = r
            var f = BigInteger.TWO
            while (f * f <= rad) {
                val sq = f * f
                while (rad % sq == BigInteger.ZERO) {
                    rad /= sq
                    coef *= f
                }
                f = f.add(BigInteger.ONE)
            }
            if (rad == BigInteger.ONE) return rat(coef, q)
            var nq = q
            if (nq.signum() < 0) {
                coef = coef.negate(); nq = nq.negate()
            }
            val g = coef.gcd(nq)
            return Surd(coef / g, nq / g, rad)
        }

        /**
         * Menjumlahkan dua suku [Rat] atau [Surd]; null bila salah satunya bukan suku berakar.
         * Radikan yang sama digabung, suku nol dibuang, sisanya disusun sesuai [Sum].
         */
        fun sum(a: Exact, b: Exact): Exact? {
            if (a is PiMul || b is PiMul || a is Sum || b is Sum) return null
            if (ExactMath.isZero(a)) return b
            if (ExactMath.isZero(b)) return a
            if (radicandOf(a) == radicandOf(b)) return mergeTerms(a, b)
            val rationalFirst = a is Rat || (b !is Rat && radicandOf(a) > radicandOf(b))
            return if (rationalFirst) Sum(a, b) else Sum(b, a)
        }

        fun piMul(p: BigInteger, q: BigInteger): Exact {
            if (q.signum() == 0) mathError()
            if (p.signum() == 0) return ZERO
            var np = p
            var nq = q
            if (nq.signum() < 0) {
                np = np.negate(); nq = nq.negate()
            }
            val g = np.gcd(nq)
            return PiMul(np / g, nq / g)
        }
    }
}

/** Aritmetika eksak; null berarti hasilnya tidak dapat ditulis dalam bentuk eksak yang didukung. */
internal object ExactMath {

    fun add(a: Exact, b: Exact): Exact? = when {
        isZero(a) -> b
        isZero(b) -> a
        a is Exact.PiMul && b is Exact.PiMul -> Exact.piMul(a.p * b.q + b.p * a.q, a.q * b.q)
        a is Exact.PiMul || b is Exact.PiMul -> null
        else -> sumOf(terms(a) + terms(b))
    }

    /** Suku-suku penyusun: [Exact.Sum] terpecah dua, bentuk lain berdiri sendiri. */
    private fun terms(a: Exact): List<Exact> =
        if (a is Exact.Sum) listOf(a.first, a.second) else listOf(a)

    /**
     * Menjumlahkan sederet suku: radikan yang sama digabung, suku nol dibuang. Lebih dari dua
     * suku tersisa berarti di luar bentuk akar manual hlm. 17, jadi null dan hasilnya desimal.
     */
    private fun sumOf(parts: List<Exact>): Exact? {
        val byRadicand = LinkedHashMap<BigInteger, Exact>()
        for (part in parts) {
            if (isZero(part)) continue
            val key = radicandOf(part)
            val merged = byRadicand[key]?.let { mergeTerms(it, part) } ?: part
            if (isZero(merged)) byRadicand.remove(key) else byRadicand[key] = merged
        }
        val kept = byRadicand.values.toList()
        return when (kept.size) {
            0 -> Exact.ZERO
            1 -> kept[0]
            2 -> Exact.sum(kept[0], kept[1])
            else -> null
        }
    }

    fun sub(a: Exact, b: Exact): Exact? = add(a, negate(b))

    fun negate(a: Exact): Exact = when (a) {
        is Exact.Rat -> Exact.rat(a.p.negate(), a.q)
        is Exact.Surd -> Exact.Surd(a.p.negate(), a.q, a.r)
        is Exact.PiMul -> Exact.piMul(a.p.negate(), a.q)
        // Urutan suku tetap: tandanya berubah, radikannya tidak.
        is Exact.Sum -> Exact.Sum(negate(a.first), negate(a.second))
    }

    /** Hasil kali dua suku tunggal; null bila bentuknya tak didukung. */
    private fun mulTerm(a: Exact, b: Exact): Exact? = when {
        a is Exact.Rat && b is Exact.Rat -> Exact.rat(a.p * b.p, a.q * b.q)
        a is Exact.Rat && b is Exact.Surd -> Exact.surd(a.p * b.p, a.q * b.q, b.r)
        a is Exact.Surd && b is Exact.Rat -> Exact.surd(a.p * b.p, a.q * b.q, a.r)
        a is Exact.Rat && b is Exact.PiMul -> Exact.piMul(a.p * b.p, a.q * b.q)
        a is Exact.PiMul && b is Exact.Rat -> Exact.piMul(a.p * b.p, a.q * b.q)
        a is Exact.Surd && b is Exact.Surd -> Exact.surd(a.p * b.p, a.q * b.q, a.r * b.r)
        else -> null
    }

    fun mul(a: Exact, b: Exact): Exact? {
        if (a !is Exact.Sum && b !is Exact.Sum) return mulTerm(a, b)
        val products = mutableListOf<Exact>()
        for (x in terms(a)) for (y in terms(b)) products += mulTerm(x, y) ?: return null
        return sumOf(products)
    }

    /** Hasil bagi dua suku tunggal; pembagi berakar dirasionalkan lewat akar kali akar. */
    private fun divTerm(a: Exact, b: Exact): Exact? = when {
        a is Exact.Rat && b is Exact.Rat -> Exact.rat(a.p * b.q, a.q * b.p)
        a is Exact.Surd && b is Exact.Rat -> Exact.surd(a.p * b.q, a.q * b.p, a.r)
        a is Exact.PiMul && b is Exact.Rat -> Exact.piMul(a.p * b.q, a.q * b.p)
        a is Exact.Rat && b is Exact.Surd -> Exact.surd(a.p * b.q, a.q * b.p * b.r, b.r)
        a is Exact.Surd && b is Exact.Surd -> Exact.surd(a.p * b.q, a.q * b.p * b.r, a.r * b.r)
        a is Exact.PiMul && b is Exact.PiMul -> Exact.rat(a.p * b.q, a.q * b.p)
        else -> null
    }

    /**
     * Pembagian. Pembagi dua suku dikalikan sekawannya lebih dulu; selisih kuadrat kedua sukunya
     * selalu rasional, jadi cara ini selalu berhasil. Dibutuhkan manual hlm. 17, yang menampilkan
     * (2+akar2) dibagi (1+akar2) sebagai akar2.
     */
    fun div(a: Exact, b: Exact): Exact? {
        if (isZero(b)) mathError()
        if (b is Exact.Sum) {
            val conjugate = Exact.sum(b.first, negate(b.second)) ?: return null
            val denominator = mul(b, conjugate) ?: return null
            val numerator = mul(a, conjugate) ?: return null
            return div(numerator, denominator)
        }
        if (a is Exact.Sum) return sumOf(terms(a).map { divTerm(it, b) ?: return null })
        return divTerm(a, b)
    }

    /** √x eksak bila x rasional: √(p/q) = √(pq)/q. */
    fun sqrt(a: Exact): Exact? {
        if (a !is Exact.Rat) return null
        if (a.p.signum() < 0) mathError()
        return Exact.surd(BigInteger.ONE, a.q, a.p * a.q)
    }

    /** Pangkat bilangan bulat; bentuk berakar dihitung dengan perkalian berulang. */
    fun pow(a: Exact, n: Int): Exact? {
        if (a is Exact.Surd || a is Exact.Sum) {
            if (n == 0) return Exact.ONE
            if (Math.abs(n) > 4) return null
            var acc = a
            repeat(Math.abs(n) - 1) { acc = mul(acc, a) ?: return null }
            return if (n > 0) acc else div(Exact.ONE, acc)
        }
        if (a !is Exact.Rat) return null
        if (Math.abs(n) > 64) return null
        if (n == 0) {
            if (a.p.signum() == 0) mathError()
            return Exact.ONE
        }
        val k = Math.abs(n)
        val p = a.p.pow(k)
        val q = a.q.pow(k)
        if (n < 0 && p.signum() == 0) mathError()
        return if (n > 0) Exact.rat(p, q) else Exact.rat(q, p)
    }

    fun isZero(a: Exact): Boolean = when (a) {
        is Exact.Rat -> a.p.signum() == 0
        is Exact.Surd -> a.p.signum() == 0
        is Exact.PiMul -> a.p.signum() == 0
        // Dua suku beradikan berbeda tak pernah saling meniadakan.
        is Exact.Sum -> false
    }

    /**
     * Syarat tampil eksak. Pecahan dan kelipatan pi memakai aturan PRD 3.3: total digit
     * pembilang dan penyebut maksimal 10.
     *
     * Bentuk berakar memakai batas koefisien manual hlm. 17 sesudah kedua sukunya disamakan
     * penyebutnya: koefisien 1..99, radikan sampai 999, penyebut bersama 1..99. Karena itu
     * 99 akar 999 tampil desimal 3129.089165, bukan 297 akar 111 (contoh manual hlm. 18).
     */
    fun isDisplayable(a: Exact): Boolean {
        fun len(v: BigInteger) = v.abs().toString().length
        if (a is Exact.Surd || a is Exact.Sum) {
            val parts = surdParts(a) ?: return false
            if (parts.denominator > MAX_COEFFICIENT) return false
            return parts.terms.all { (coefficient, radicand) ->
                coefficient.abs() <= MAX_COEFFICIENT && radicand <= MAX_RADICAND
            }
        }
        val digits = when (a) {
            is Exact.Rat ->
                len(a.p) + if (a.q == BigInteger.ONE) 0 else len(a.q)
            is Exact.PiMul ->
                (if (a.p.abs() == BigInteger.ONE) 0 else len(a.p)) +
                    if (a.q == BigInteger.ONE) 0 else len(a.q)
        }
        return digits <= 10
    }

    private val MAX_COEFFICIENT = BigInteger.valueOf(99L)
    private val MAX_RADICAND = BigInteger.valueOf(999L)
}

// ---------------------------------------------------------------- suku berakar

/** Radikan suku: 1 untuk suku rasional. Hanya sah untuk [Exact.Rat] dan [Exact.Surd]. */
private fun radicandOf(e: Exact): BigInteger = when (e) {
    is Exact.Surd -> e.r
    else -> BigInteger.ONE
}

private fun numeratorOf(e: Exact): BigInteger = when (e) {
    is Exact.Rat -> e.p
    is Exact.Surd -> e.p
    else -> BigInteger.ZERO
}

private fun denominatorOf(e: Exact): BigInteger = when (e) {
    is Exact.Rat -> e.q
    is Exact.Surd -> e.q
    else -> BigInteger.ONE
}

/** Menjumlahkan dua suku beradikan sama: (p₁/q₁ + p₂/q₂)·√r. */
private fun mergeTerms(a: Exact, b: Exact): Exact {
    val p = numeratorOf(a) * denominatorOf(b) + numeratorOf(b) * denominatorOf(a)
    return Exact.surd(p, denominatorOf(a) * denominatorOf(b), radicandOf(a))
}

private fun lcm(a: BigInteger, b: BigInteger): BigInteger = a / a.gcd(b) * b

/** Satu atau dua suku di atas satu penyebut, sesuai bentuk (±a'√b ± d'√e)/c' manual hlm. 17. */
internal class SurdParts(
    /** Pasangan (koefisien bertanda, radikan); radikan 1 berarti suku rasional. */
    val terms: List<Pair<BigInteger, BigInteger>>,
    val denominator: BigInteger,
)

/** [SurdParts] dari [Exact.Surd] atau [Exact.Sum]; null untuk bentuk lain. */
internal fun surdParts(e: Exact): SurdParts? {
    val parts = when (e) {
        is Exact.Surd -> listOf(e)
        is Exact.Sum -> listOf(e.first, e.second)
        else -> return null
    }
    val common = parts.fold(BigInteger.ONE) { acc, t -> lcm(acc, denominatorOf(t)) }
    return SurdParts(
        parts.map { (numeratorOf(it) * (common / denominatorOf(it))) to radicandOf(it) },
        common,
    )
}
