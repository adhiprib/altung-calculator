package id.sempoa570.engine

import java.math.BigDecimal

/**
 * Penyelesaian persamaan mode EQN (PRD §3.1, §5.3): sistem linear 2 dan 3 variabel dengan
 * eliminasi Gauss, serta persamaan kuadrat dan kubik dengan rumus tertutup yang juga
 * menampilkan akar kompleks.
 */
internal object EqnSolver {

    /** Jenis persamaan pada menu EQN. */
    enum class Type(val unknowns: Int, val coefficients: Int, val label: String) {
        LINEAR2(2, 6, "anX+bnY=cn"),
        LINEAR3(3, 12, "anX+bnY+cnZ=dn"),
        QUADRATIC(2, 3, "aX²+bX+c=0"),
        CUBIC(3, 4, "aX³+bX²+cX+d=0"),
    }

    private val TWO = CasioNumber.of(2)
    private val THREE = CasioNumber.of(3)
    private val FOUR = CasioNumber.of(4)

    /**
     * Sistem linear dengan eliminasi Gauss-Jordan dan pivot parsial. Koefisien diberikan baris
     * demi baris, tiap baris berisi [size] koefisien diikuti ruas kanannya.
     * Determinan nol menghasilkan Math ERROR, sama seperti kalkulator fisik.
     */
    fun linear(size: Int, coefficients: List<CasioNumber>): List<Complex> {
        val m = Array(size) { row ->
            Array(size + 1) { col -> coefficients[row * (size + 1) + col].value }
        }
        for (col in 0 until size) {
            var pivot = col
            for (row in col until size) {
                if (m[row][col].abs() > m[pivot][col].abs()) pivot = row
            }
            if (m[pivot][col].signum() == 0) mathError()
            val swap = m[col]; m[col] = m[pivot]; m[pivot] = swap
            for (row in 0 until size) {
                if (row == col) continue
                val factor = m[row][col].divide(m[col][col], CasioNumber.WORK)
                for (k in col..size) {
                    m[row][k] = m[row][k]
                        .subtract(factor.multiply(m[col][k], CasioNumber.WORK), CasioNumber.WORK)
                }
            }
        }
        return (0 until size).map { row ->
            Complex.real(CasioNumber.of(m[row][size].divide(m[row][row], CasioNumber.WORK)))
        }
    }

    /** aX² + bX + c = 0; diskriminan negatif memberi sepasang akar kompleks sekawan. */
    fun quadratic(a: CasioNumber, b: CasioNumber, c: CasioNumber): List<Complex> {
        if (a.isZero) mathError()
        val discriminant = b * b - FOUR * a * c
        val denominator = TWO * a
        if (discriminant.signum >= 0) {
            val root = CasioNumber.of(CasioMath.sqrt(discriminant.value))
            return listOf(
                Complex.real((-b + root) / denominator),
                Complex.real((-b - root) / denominator),
            )
        }
        val root = CasioNumber.of(CasioMath.sqrt(discriminant.value.negate()))
        val realPart = -b / denominator
        val imagPart = root / denominator
        return listOf(Complex(realPart, imagPart), Complex(realPart, -imagPart))
    }

    /**
     * aX³ + bX² + cX + d = 0.
     *
     * Persamaan diubah ke bentuk tertekan t³ + pt + q = 0 dengan X = t − b/(3a), lalu
     * diselesaikan secara trigonometri bila ketiga akarnya nyata, atau dengan Cardano bila
     * ada sepasang akar kompleks.
     */
    fun cubic(a: CasioNumber, b: CasioNumber, c: CasioNumber, d: CasioNumber): List<Complex> {
        if (a.isZero) mathError()
        val shift = b / (THREE * a)
        val p = (THREE * a * c - b * b) / (THREE * a * a)
        val q = (TWO * b * b * b - CasioNumber.of(9) * a * b * c + CasioNumber.of(27) * a * a * d) /
            (CasioNumber.of(27) * a * a * a)
        return depressedRoots(p, q).map { root -> tidy(Complex(root.re - shift, root.im)) }
    }

    /** Akar t³ + pt + q = 0. */
    private fun depressedRoots(p: CasioNumber, q: CasioNumber): List<Complex> {
        if (p.isZero && q.isZero) return List(3) { Complex.ZERO }
        val discriminant = (q * q) / FOUR + (p * p * p) / CasioNumber.of(27)
        if (discriminant.signum <= 0 && p.signum < 0) {
            // Tiga akar nyata: bentuk trigonometri Viète.
            val m = TWO * CasioNumber.of(CasioMath.sqrt((-p / THREE).value))
            val inner = ((THREE * q) / (p * m)).value.max(BigDecimal(-1)).min(BigDecimal.ONE)
            val theta = CasioMath.acos(inner).divide(BigDecimal(3), CasioNumber.WORK)
            return (0..2).map { k ->
                val angle = theta.subtract(
                    CasioMath.PI.multiply(BigDecimal(2L * k), CasioNumber.WORK)
                        .divide(BigDecimal(3), CasioNumber.WORK),
                    CasioNumber.WORK,
                )
                Complex.real(m * CasioNumber.of(CasioMath.cos(angle)))
            }
        }
        val sqrtDisc = CasioNumber.of(CasioMath.sqrt(discriminant.abs().value))
        val u = cubeRoot(-q / TWO + sqrtDisc)
        val v = cubeRoot(-q / TWO - sqrtDisc)
        val realRoot = u + v
        val halfSum = -realRoot / TWO
        val imagPart = CasioNumber.of(CasioMath.sqrt(BigDecimal(3))) * (u - v) / TWO
        return listOf(
            Complex.real(realRoot),
            Complex(halfSum, imagPart),
            Complex(halfSum, -imagPart),
        )
    }

    private fun cubeRoot(x: CasioNumber): CasioNumber =
        CasioNumber.of(CasioMath.root(BigDecimal(3), x.value))

    /** Sisa pembulatan pada bagian khayal dan bagian nyata dirapikan. */
    private fun tidy(z: Complex): Complex {
        val scale = z.re.abs().value.max(BigDecimal.ONE)
        val threshold = scale.multiply(BigDecimal("1E-12"))
        val im = if (z.im.abs().value < threshold) CasioNumber.ZERO else z.im
        val rounded = z.re.roundSignificant(12)
        return Complex(if (rounded.isInteger) rounded else z.re, im)
    }
}
