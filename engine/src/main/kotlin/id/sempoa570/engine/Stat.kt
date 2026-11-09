package id.sempoa570.engine

/** Jenis perhitungan statistik (PRD §3.1). */
@kotlinx.serialization.Serializable
enum class StatType(val label: String, val paired: Boolean) {
    ONE_VAR("1-VAR", false),
    LINEAR("A+BX", true),
    QUADRATIC("_+CX²", true),
    LOGARITHMIC("ln X", true),
    EXPONENTIAL_E("e^X", true),
    EXPONENTIAL_AB("A·B^X", true),
    POWER("A·X^B", true),
    INVERSE("1/X", true),
}

/** Besaran statistik yang dapat ditanyakan lewat menu STAT. */
enum class StatQuery(val label: String) {
    N("n"), SUM_X("Σx"), SUM_X2("Σx²"), MEAN_X("x̄"), SIGMA_X("σx"), SAMPLE_X("sx"),
    SUM_Y("Σy"), SUM_Y2("Σy²"), SUM_XY("Σxy"), MEAN_Y("ȳ"), SIGMA_Y("σy"), SAMPLE_Y("sy"),
    // Dipakai regresi kuadrat; ada di submenu Sum berpasangan (manual hlm. 52).
    SUM_X3("Σx³"), SUM_X2Y("Σx²y"), SUM_X4("Σx⁴"),
    REG_A("A"), REG_B("B"), REG_C("C"), CORRELATION("r"),
    MIN_X("minX"), MAX_X("maxX"), MIN_Y("minY"), MAX_Y("maxY"),
}

/**
 * Tabel data statistik: kolom x, kolom y untuk jenis berpasangan, dan kolom frekuensi.
 *
 * Tiap model regresi dikerjakan sebagai kuadrat terkecil pada data yang sudah ditransformasi
 * (PRD §5.3), sehingga hanya ada satu rumus inti untuk semua model selain kuadrat.
 */
class StatData(var type: StatType = StatType.ONE_VAR) {

    val x = mutableListOf<CasioNumber>()
    val y = mutableListOf<CasioNumber>()
    val freq = mutableListOf<CasioNumber>()

    val size: Int get() = x.size

    fun clear() {
        x.clear(); y.clear(); freq.clear()
    }

    fun add(
        xValue: CasioNumber,
        yValue: CasioNumber = CasioNumber.ZERO,
        f: CasioNumber = CasioNumber.ONE,
    ) {
        x.add(xValue); y.add(yValue); freq.add(f)
    }

    /** Isi satu sel; kolom 0 adalah X, 1 adalah Y, dan 2 adalah FREQ. */
    fun get(row: Int, col: Int): CasioNumber = when (col) {
        0 -> x[row]
        1 -> y.getOrElse(row) { CasioNumber.ZERO }
        else -> freq.getOrElse(row) { CasioNumber.ONE }
    }

    /** Mengubah satu sel dari Statistics Editor. */
    fun set(row: Int, col: Int, value: CasioNumber) {
        when (col) {
            0 -> x[row] = value
            1 -> y[row] = value
            else -> freq[row] = value
        }
    }

    /** Menyisipkan baris kosong di atas kursor (menu Edit, Ins). */
    fun insert(row: Int) {
        val at = row.coerceIn(0, x.size)
        x.add(at, CasioNumber.ZERO)
        y.add(at, CasioNumber.ZERO)
        freq.add(at, CasioNumber.ONE)
    }

    /** Menghapus satu baris data (tombol DEL di dalam editor). */
    fun removeAt(row: Int) {
        if (row !in x.indices) return
        x.removeAt(row)
        y.removeAt(row)
        freq.removeAt(row)
    }

    /** Baris (x, y, frekuensi) setelah transformasi sesuai model regresi. */
    private fun rows(): List<Triple<CasioNumber, CasioNumber, CasioNumber>> =
        x.indices.map { i ->
            Triple(
                transformX(x[i]),
                transformY(y.getOrElse(i) { CasioNumber.ZERO }),
                freq.getOrElse(i) { CasioNumber.ONE },
            )
        }

    private fun transformX(v: CasioNumber): CasioNumber = when (type) {
        StatType.LOGARITHMIC, StatType.POWER -> CasioNumber.of(CasioMath.ln(positive(v).value))
        StatType.INVERSE -> CasioNumber.ONE / v
        else -> v
    }

    private fun transformY(v: CasioNumber): CasioNumber = when (type) {
        StatType.EXPONENTIAL_E, StatType.EXPONENTIAL_AB, StatType.POWER ->
            CasioNumber.of(CasioMath.ln(positive(v).value))
        else -> v
    }

    /**
     * ŷ: nilai y yang ditaksir model regresi untuk x tertentu (manual hlm. 53).
     *
     * [regression] sudah mengembalikan A dan B dalam bentuk modelnya, bukan bentuk
     * tertransformasi, jadi rumusnya ditulis langsung per model.
     */
    fun estimateY(xValue: CasioNumber): CasioNumber {
        val c = regression()
        val a = c[0]
        val b = c[1]
        return when (type) {
            StatType.ONE_VAR -> mathError()
            StatType.LINEAR -> a + b * xValue
            StatType.QUADRATIC -> a + b * xValue + c[2] * xValue * xValue
            StatType.LOGARITHMIC -> a + b * CasioNumber.of(CasioMath.ln(positive(xValue).value))
            StatType.EXPONENTIAL_E ->
                a * CasioNumber.of(CasioMath.exp((b * xValue).value))
            StatType.EXPONENTIAL_AB ->
                a * CasioNumber.of(CasioMath.pow(b.value, xValue.value))
            StatType.POWER -> a * CasioNumber.of(CasioMath.pow(xValue.value, b.value))
            StatType.INVERSE -> a + b / xValue
        }
    }

    /**
     * x̂: kebalikan [estimateY]. Model kuadrat memberi dua nilai, dipilih lewat [root]
     * (x̂₁ dan x̂₂ pada manual hlm. 53).
     */
    fun estimateX(yValue: CasioNumber, root: Int = 0): CasioNumber {
        val c = regression()
        val a = c[0]
        val b = c[1]
        if (type == StatType.QUADRATIC) {
            val roots = EqnSolver.quadratic(c[2], b, a - yValue)
            val picked = roots.getOrNull(root) ?: mathError()
            if (!picked.isReal) mathError()
            return picked.re
        }
        if (b.isZero) mathError()
        return when (type) {
            StatType.ONE_VAR, StatType.QUADRATIC -> mathError()
            StatType.LINEAR -> (yValue - a) / b
            StatType.LOGARITHMIC ->
                CasioNumber.of(CasioMath.exp(((yValue - a) / b).value))
            StatType.EXPONENTIAL_E ->
                CasioNumber.of(CasioMath.ln(positive(yValue / a).value)) / b
            StatType.EXPONENTIAL_AB ->
                CasioNumber.of(CasioMath.ln(positive(yValue / a).value)) /
                    CasioNumber.of(CasioMath.ln(positive(b).value))
            StatType.POWER ->
                CasioNumber.of(
                    CasioMath.exp(
                        (CasioNumber.of(CasioMath.ln(positive(yValue / a).value)) / b).value
                    )
                )
            StatType.INVERSE -> b / (yValue - a)
        }
    }

    /** ▸t: nilai baku (x − x̄)/σx untuk distribusi normal (manual hlm. 56). */
    fun standardise(value: CasioNumber): CasioNumber {
        val sigma = query(StatQuery.SIGMA_X)
        if (sigma.isZero) mathError()
        return (value - query(StatQuery.MEAN_X)) / sigma
    }

    private fun positive(v: CasioNumber): CasioNumber {
        if (v.signum <= 0) mathError()
        return v
    }

    fun query(q: StatQuery): CasioNumber {
        if (size == 0) mathError()
        return when (q) {
            StatQuery.N -> total()
            StatQuery.SUM_X -> weighted { it.first }
            StatQuery.SUM_X2 -> weighted { it.first * it.first }
            StatQuery.MEAN_X -> weighted { it.first } / total()
            StatQuery.SIGMA_X -> deviation(population = true) { it.first }
            StatQuery.SAMPLE_X -> deviation(population = false) { it.first }
            StatQuery.SUM_Y -> weighted { it.second }
            StatQuery.SUM_Y2 -> weighted { it.second * it.second }
            StatQuery.SUM_XY -> weighted { it.first * it.second }
            StatQuery.SUM_X3 -> weighted { it.first * it.first * it.first }
            StatQuery.SUM_X2Y -> weighted { it.first * it.first * it.second }
            StatQuery.SUM_X4 -> weighted { it.first * it.first * it.first * it.first }
            StatQuery.MEAN_Y -> weighted { it.second } / total()
            StatQuery.SIGMA_Y -> deviation(population = true) { it.second }
            StatQuery.SAMPLE_Y -> deviation(population = false) { it.second }
            StatQuery.REG_A -> regression()[0]
            StatQuery.REG_B -> regression()[1]
            StatQuery.REG_C -> regression().getOrNull(2) ?: mathError()
            StatQuery.CORRELATION -> correlation()
            StatQuery.MIN_X -> smallest(x)
            StatQuery.MAX_X -> largest(x)
            StatQuery.MIN_Y -> smallest(y)
            StatQuery.MAX_Y -> largest(y)
        }
    }

    private fun total(): CasioNumber = freq.fold(CasioNumber.ZERO) { acc, f -> acc + f }

    private inline fun weighted(
        selector: (Triple<CasioNumber, CasioNumber, CasioNumber>) -> CasioNumber,
    ): CasioNumber = rows().fold(CasioNumber.ZERO) { acc, row -> acc + selector(row) * row.third }

    private inline fun deviation(
        population: Boolean,
        selector: (Triple<CasioNumber, CasioNumber, CasioNumber>) -> CasioNumber,
    ): CasioNumber {
        val n = total()
        val mean = weighted(selector) / n
        val squares = rows().fold(CasioNumber.ZERO) { acc, row ->
            val d = selector(row) - mean
            acc + d * d * row.third
        }
        val divisor = if (population) n else n - CasioNumber.ONE
        if (divisor.signum <= 0) mathError()
        return CasioNumber.of(CasioMath.sqrt((squares / divisor).value))
    }

    /**
     * Koefisien regresi. Model linear dan model tertransformasi memberi (A, B); model kuadrat
     * memberi (A, B, C) lewat persamaan normal 3×3. Koefisien model eksponensial dan pangkat
     * dikembalikan ke skala semula dengan eksponensial.
     */
    fun regression(): List<CasioNumber> {
        if (type == StatType.ONE_VAR) mathError()
        val data = rows()
        if (type == StatType.QUADRATIC) return quadraticRegression(data)
        val n = total()
        val sx = data.fold(CasioNumber.ZERO) { a, r -> a + r.first * r.third }
        val sy = data.fold(CasioNumber.ZERO) { a, r -> a + r.second * r.third }
        val sxx = data.fold(CasioNumber.ZERO) { a, r -> a + r.first * r.first * r.third }
        val sxy = data.fold(CasioNumber.ZERO) { a, r -> a + r.first * r.second * r.third }
        val denominator = n * sxx - sx * sx
        if (denominator.isZero) mathError()
        val b = (n * sxy - sx * sy) / denominator
        val a = (sy - b * sx) / n
        val restoredA = when (type) {
            StatType.EXPONENTIAL_E, StatType.EXPONENTIAL_AB, StatType.POWER ->
                CasioNumber.of(CasioMath.exp(a.value))
            else -> a
        }
        val restoredB =
            if (type == StatType.EXPONENTIAL_AB) CasioNumber.of(CasioMath.exp(b.value)) else b
        return listOf(restoredA, restoredB)
    }

    /** Regresi kuadrat y = A + Bx + Cx² lewat persamaan normal. */
    private fun quadraticRegression(
        data: List<Triple<CasioNumber, CasioNumber, CasioNumber>>,
    ): List<CasioNumber> {
        fun moment(px: Int, py: Int): CasioNumber = data.fold(CasioNumber.ZERO) { acc, r ->
            var term = r.third
            repeat(px) { term *= r.first }
            repeat(py) { term *= r.second }
            acc + term
        }
        val normal = listOf(
            moment(0, 0), moment(1, 0), moment(2, 0), moment(0, 1),
            moment(1, 0), moment(2, 0), moment(3, 0), moment(1, 1),
            moment(2, 0), moment(3, 0), moment(4, 0), moment(2, 1),
        )
        return EqnSolver.linear(3, normal).map { it.re }
    }

    /** Koefisien korelasi pada data hasil transformasi. */
    fun correlation(): CasioNumber {
        if (type == StatType.ONE_VAR) mathError()
        val data = rows()
        val n = total()
        val sx = data.fold(CasioNumber.ZERO) { a, r -> a + r.first * r.third }
        val sy = data.fold(CasioNumber.ZERO) { a, r -> a + r.second * r.third }
        val sxx = data.fold(CasioNumber.ZERO) { a, r -> a + r.first * r.first * r.third }
        val syy = data.fold(CasioNumber.ZERO) { a, r -> a + r.second * r.second * r.third }
        val sxy = data.fold(CasioNumber.ZERO) { a, r -> a + r.first * r.second * r.third }
        val left = n * sxx - sx * sx
        val right = n * syy - sy * sy
        val denominator = CasioNumber.of(CasioMath.sqrt((left * right).value))
        if (denominator.isZero) mathError()
        return (n * sxy - sx * sy) / denominator
    }

    private fun smallest(values: List<CasioNumber>): CasioNumber =
        if (values.isEmpty()) mathError() else values.reduce { a, b -> if (a < b) a else b }

    private fun largest(values: List<CasioNumber>): CasioNumber =
        if (values.isEmpty()) mathError() else values.reduce { a, b -> if (a > b) a else b }
}

/** Satu baris tabel fungsi mode TABLE. */
data class TableRow(val x: CasioNumber, val value: CasioNumber)

internal object TableBuilder {

    /** Jumlah baris maksimum tabel (PRD §3.1). */
    const val MAX_ROWS = 30

    /**
     * Menghitung f(X) dari Start sampai End dengan langkah Step, maksimal [MAX_ROWS] baris.
     * Langkah nol atau arah langkah yang tidak mungkin mencapai End adalah Math ERROR.
     */
    fun build(
        body: Ast,
        ctx: EvalContext,
        start: CasioNumber,
        end: CasioNumber,
        step: CasioNumber,
    ): List<TableRow> {
        if (step.isZero) mathError()
        if (step.signum > 0 && start > end) mathError()
        if (step.signum < 0 && start < end) mathError()
        val rows = mutableListOf<TableRow>()
        var x = start
        while (true) {
            if (step.signum > 0 && x > end) break
            if (step.signum < 0 && x < end) break
            // Manual hlm. 67: gabungan Start, End, dan Step yang melebihi 30 nilai X adalah
            // error, bukan tabel yang dipotong diam-diam.
            if (rows.size == MAX_ROWS) throw CalcError(ErrorKind.INSUFFICIENT_MEM)
            ctx.vars[Key.VAR_X] = Value.Num(x)
            rows.add(TableRow(x, Evaluator.eval(body, ctx).number))
            x += step
        }
        if (rows.isEmpty()) mathError()
        return rows
    }
}
