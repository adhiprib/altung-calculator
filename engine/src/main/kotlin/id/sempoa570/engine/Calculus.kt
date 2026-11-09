package id.sempoa570.engine

import java.math.BigDecimal

/**
 * Kalkulus numerik COMP (PRD §5.3): integral Gauss-Kronrod, turunan beda tengah, penjumlahan Σ,
 * dan SOLVE Newton-Raphson.
 *
 * Semua fungsi menerima pohon sintaksis yang sudah diparse dan mengevaluasinya berulang dengan
 * nilai variabel yang diganti, sehingga tidak ada penguraian ulang di dalam gelang.
 */
internal object Calculus {

    /** Ambang waktu komputasi sebelum Time Out (PRD §7.1). */
    private const val TIME_LIMIT_MS = 5_000L

    /** Toleransi bawaan integral bila pengguna tidak mengisi tol. */
    private val DEFAULT_INTEGRAL_TOL = BigDecimal("1E-5")

    /** Selisih bawaan turunan bila pengguna tidak mengisi tol. */
    private val DEFAULT_DERIVATIVE_STEP = BigDecimal("1E-10")

    /** Batas rentang Σ. */
    private const val SUM_SPAN_LIMIT = 1_000_000L

    private const val SOLVE_ITERATIONS = 80
    private const val MAX_DEPTH = 14

    private val CONVERGENCE = BigDecimal("1E-12")

    private class Deadline(val until: Long = System.nanoTime() + TIME_LIMIT_MS * 1_000_000) {
        fun check() {
            if (System.nanoTime() > until) throw CalcError(ErrorKind.TIME_OUT)
        }
    }

    /** Mengevaluasi [body] dengan variabel [variable] diikat sementara ke [value]. */
    private fun evalAt(body: Ast, ctx: EvalContext, variable: Key, value: BigDecimal): BigDecimal {
        val previous = ctx.vars[variable]
        ctx.vars[variable] = Value.Num(CasioNumber.of(value))
        try {
            return Evaluator.eval(body, ctx).number.value
        } finally {
            if (previous == null) ctx.vars.remove(variable) else ctx.vars[variable] = previous
        }
    }

    // ---------------------------------------------------------------- integral

    /**
     * ∫ f(X) dX dari a ke b dengan Gauss-Kronrod G7K15 adaptif.
     *
     * Tiap selang dihitung dua kali: aturan Kronrod 15 titik sebagai nilai, dan aturan Gauss
     * 7 titik sebagai pembanding. Selisih keduanya menjadi taksiran galat; selang yang belum
     * cukup teliti dibelah dua.
     */
    fun integrate(
        body: Ast,
        ctx: EvalContext,
        a: BigDecimal,
        b: BigDecimal,
        tol: BigDecimal?,
    ): BigDecimal {
        if (a.compareTo(b) == 0) return BigDecimal.ZERO
        val tolerance = (tol ?: DEFAULT_INTEGRAL_TOL).abs()
        if (tolerance.signum() == 0) mathError()
        val deadline = Deadline()
        val ascending = a < b
        val lo = if (ascending) a else b
        val hi = if (ascending) b else a
        val value = adaptive(body, ctx, lo, hi, tolerance, 0, deadline)
        return if (ascending) value else value.negate()
    }

    private fun adaptive(
        body: Ast,
        ctx: EvalContext,
        lo: BigDecimal,
        hi: BigDecimal,
        tol: BigDecimal,
        depth: Int,
        deadline: Deadline,
    ): BigDecimal {
        deadline.check()
        val estimate = kronrod15(body, ctx, lo, hi)
        if (estimate.second <= tol || depth >= MAX_DEPTH) return estimate.first
        val mid = lo.add(hi, CasioNumber.WORK).divide(BigDecimal(2), CasioNumber.WORK)
        val half = tol.divide(BigDecimal(2), CasioNumber.WORK)
        return adaptive(body, ctx, lo, mid, half, depth + 1, deadline)
            .add(adaptive(body, ctx, mid, hi, half, depth + 1, deadline), CasioNumber.WORK)
    }

    /** Pasangan (nilai Kronrod 15 titik, taksiran galat terhadap Gauss 7 titik). */
    private fun kronrod15(
        body: Ast,
        ctx: EvalContext,
        lo: BigDecimal,
        hi: BigDecimal,
    ): Pair<BigDecimal, BigDecimal> {
        val center = lo.add(hi, CasioNumber.WORK).divide(BigDecimal(2), CasioNumber.WORK)
        val halfWidth = hi.subtract(lo, CasioNumber.WORK).divide(BigDecimal(2), CasioNumber.WORK)
        var kronrod = BigDecimal.ZERO
        var gauss = BigDecimal.ZERO
        for (i in NODES.indices) {
            val node = NODES[i]
            val sum = if (node.signum() == 0) {
                evalAt(body, ctx, Key.VAR_X, center)
            } else {
                val offset = halfWidth.multiply(node, CasioNumber.WORK)
                evalAt(body, ctx, Key.VAR_X, center.add(offset, CasioNumber.WORK)).add(
                    evalAt(body, ctx, Key.VAR_X, center.subtract(offset, CasioNumber.WORK)),
                    CasioNumber.WORK,
                )
            }
            kronrod = kronrod.add(sum.multiply(KRONROD_WEIGHTS[i], CasioNumber.WORK), CasioNumber.WORK)
            GAUSS_WEIGHTS[i]?.let { w ->
                gauss = gauss.add(sum.multiply(w, CasioNumber.WORK), CasioNumber.WORK)
            }
        }
        val scaledKronrod = kronrod.multiply(halfWidth, CasioNumber.WORK)
        val scaledGauss = gauss.multiply(halfWidth, CasioNumber.WORK)
        return scaledKronrod to scaledKronrod.subtract(scaledGauss, CasioNumber.WORK).abs()
    }

    /** Titik Kronrod 15 tak negatif; titik bukan nol dipakai simetris di kedua sisi pusat. */
    private val NODES = listOf(
        BigDecimal("0.991455371120812639206854697526329"),
        BigDecimal("0.949107912342758524526189684047851"),
        BigDecimal("0.864864423359769072789712788640926"),
        BigDecimal("0.741531185599394439863864773280788"),
        BigDecimal("0.586087235467691130294144838258730"),
        BigDecimal("0.405845151377397166906606412076961"),
        BigDecimal("0.207784955007898467600689403773245"),
        BigDecimal("0"),
    )

    private val KRONROD_WEIGHTS = listOf(
        BigDecimal("0.022935322010529224963732008058970"),
        BigDecimal("0.063092092629978553290700663189204"),
        BigDecimal("0.104790010322250183839876322541518"),
        BigDecimal("0.140653259715525918745189590510238"),
        BigDecimal("0.169004726639267902826583426598550"),
        BigDecimal("0.190350578064785409913256402421014"),
        BigDecimal("0.204432940075298892414161999234649"),
        BigDecimal("0.209482141084727828012999174891714"),
    )

    /** Bobot Gauss 7 hanya ada pada titik yang juga milik aturan Gauss. */
    private val GAUSS_WEIGHTS = listOf(
        null,
        BigDecimal("0.129484966168869693270611432679082"),
        null,
        BigDecimal("0.279705391489276667901467771423780"),
        null,
        BigDecimal("0.381830050505118944950369775488975"),
        null,
        BigDecimal("0.417959183673469387755102040816327"),
    )

    // ---------------------------------------------------------------- turunan

    /**
     * d/dX f(X) di titik a dengan beda tengah orde dua.
     *
     * Langkah diskalakan terhadap besar a supaya tetap berarti untuk argumen besar. Bila beda
     * satu sisi menyimpang jauh dari beda tengah, titik itu dianggap tidak dapat diturunkan
     * dan menghasilkan Math ERROR (PRD §5.3).
     */
    fun derivative(body: Ast, ctx: EvalContext, a: BigDecimal, step: BigDecimal?): BigDecimal {
        val base = (step ?: DEFAULT_DERIVATIVE_STEP).abs()
        if (base.signum() == 0) mathError()
        val h = base.multiply(a.abs().max(BigDecimal.ONE), CasioNumber.WORK)
        val forward = evalAt(body, ctx, Key.VAR_X, a.add(h, CasioNumber.WORK))
        val backward = evalAt(body, ctx, Key.VAR_X, a.subtract(h, CasioNumber.WORK))
        val central = forward.subtract(backward, CasioNumber.WORK)
            .divide(h.multiply(BigDecimal(2), CasioNumber.WORK), CasioNumber.WORK)
        val here = evalAt(body, ctx, Key.VAR_X, a)
        val oneSided = forward.subtract(here, CasioNumber.WORK).divide(h, CasioNumber.WORK)
        val gap = oneSided.subtract(central, CasioNumber.WORK).abs()
        val limit = central.abs().max(BigDecimal.ONE).multiply(BigDecimal("1E6"), CasioNumber.WORK)
        if (gap > limit) mathError()
        return central
    }

    // ---------------------------------------------------------------- sigma

    /** Σ f(X) untuk X bulat dari a sampai b. */
    fun summation(body: Ast, ctx: EvalContext, a: BigDecimal, b: BigDecimal): BigDecimal {
        if (a.stripTrailingZeros().scale() > 0 || b.stripTrailingZeros().scale() > 0) mathError()
        val from = a.toBigInteger().toLong()
        val to = b.toBigInteger().toLong()
        if (to < from || to - from > SUM_SPAN_LIMIT) mathError()
        val deadline = Deadline()
        var total = BigDecimal.ZERO
        var x = from
        while (x <= to) {
            deadline.check()
            total = total.add(evalAt(body, ctx, Key.VAR_X, BigDecimal(x)), CasioNumber.WORK)
            x++
        }
        return total
    }

    // ---------------------------------------------------------------- SOLVE

    /** Hasil SOLVE: akar yang ditemukan dan sisa ruas kiri dikurangi ruas kanan. */
    /**
     * [converged] palsu berarti Newton habis iterasi tanpa bertemu akar; [root] adalah tebakan
     * terakhir, dan kalkulator menawarkan layar `Continue:[=]` untuk meneruskan (manual hlm. 41).
     */
    data class SolveResult(
        val root: CasioNumber,
        val leftMinusRight: CasioNumber,
        val converged: Boolean = true,
    )

    /**
     * Newton-Raphson dengan turunan numerik dari tebakan awal [guess] (PRD §5.3).
     * Gagal konvergen menghasilkan Can't Solve.
     */
    /** Sisa yang jauh lebih kecil dari presisi tampilan dilaporkan sebagai nol, seperti L−R=0. */
    private fun snapResidual(v: BigDecimal): CasioNumber =
        if (v.abs() < RESIDUAL_FLOOR) CasioNumber.ZERO else CasioNumber.of(v)

    private val RESIDUAL_FLOOR = BigDecimal("1E-10")

    fun solve(body: Ast, ctx: EvalContext, variable: Key, guess: BigDecimal): SolveResult {
        val deadline = Deadline()
        var x = guess
        val h = BigDecimal("1E-8")
        repeat(SOLVE_ITERATIONS) {
            deadline.check()
            val fx = evalAt(body, ctx, variable, x)
            if (fx.abs() < CONVERGENCE) return SolveResult(CasioNumber.of(x), snapResidual(fx))
            val step = h.multiply(x.abs().max(BigDecimal.ONE), CasioNumber.WORK)
            val slope = evalAt(body, ctx, variable, x.add(step, CasioNumber.WORK))
                .subtract(evalAt(body, ctx, variable, x.subtract(step, CasioNumber.WORK)), CasioNumber.WORK)
                .divide(step.multiply(BigDecimal(2), CasioNumber.WORK), CasioNumber.WORK)
            if (slope.signum() == 0) {
                // Titik datar, misal tebakan 0 pada X²-4. Geser sedikit lalu ulangi.
                x = x.add(x.abs().max(BigDecimal.ONE).divide(BigDecimal(4), CasioNumber.WORK), CasioNumber.WORK)
                return@repeat
            }
            val next = x.subtract(fx.divide(slope, CasioNumber.WORK), CasioNumber.WORK)
            if (next.subtract(x, CasioNumber.WORK).abs() < CONVERGENCE) {
                return SolveResult(CasioNumber.of(next), snapResidual(evalAt(body, ctx, variable, next)))
            }
            x = next
        }
        // Iterasi habis: kalkulator fisik menawarkan `Continue:[=]`, bukan langsung Can't Solve.
        return SolveResult(CasioNumber.of(x), snapResidual(evalAt(body, ctx, variable, x)), false)
    }
}
