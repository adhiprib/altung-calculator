package id.sempoa570.engine

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.math.BigInteger

/** Satuan sudut (PRD §3.2). */
@Serializable
enum class AngleUnit { DEG, RAD, GRA }

/** Mode input tampilan (PRD §4.1). */
@Serializable
enum class InputMode { MATH_IO, LINE_IO }

/** Format tampilan angka (PRD §3.2). */
sealed interface DisplayFormat {
    data object Norm1 : DisplayFormat
    data object Norm2 : DisplayFormat
    data class Fix(val places: Int) : DisplayFormat
    data class Sci(val digits: Int) : DisplayFormat
}

/** Setup kalkulator yang memengaruhi hitungan dan tampilan. */
data class Setup(
    val input: InputMode = InputMode.MATH_IO,
    val angle: AngleUnit = AngleUnit.DEG,
    val format: DisplayFormat = DisplayFormat.Norm1,
    val base: NumberBase = NumberBase.DEC,
    val complexForm: ComplexForm = ComplexForm.RECTANGULAR,
    /** Kolom FREQ pada Statistics Editor; Stat Format di SETUP (manual hlm. 13). */
    val statFreq: Boolean = false,
    /**
     * Fraction Display di SETUP: benar berarti pecahan campuran `ab/c`. Bawaannya `d/c`
     * alias pecahan biasa (manual hlm. 15 daftar setelan awal).
     */
    val mixedFraction: Boolean = false,
)

/** Nilai hasil hitung: bentuk eksak bila memungkinkan, jika tidak desimal 15 digit. */
sealed interface Value {
    data class Ex(val exact: Exact) : Value
    data class Num(val value: CasioNumber) : Value
    data class Cx(val value: Complex) : Value
    data class Bn(val value: BaseN) : Value
    data class Mat(val value: Matrix) : Value
    data class Vec(val value: Vector) : Value

    val number: CasioNumber
        get() = when (this) {
            is Ex -> exact.toNumber()
            is Num -> value
            is Cx -> if (value.isReal) value.re else mathError()
            is Bn -> value.toNumber()
            is Mat, is Vec -> dimensionError()
        }

    /** Nilai sebagai bilangan kompleks; nilai nyata diangkat menjadi bagian nyata saja. */
    val complex: Complex
        get() = if (this is Cx) value else Complex.real(number)

    val isComplex: Boolean get() = this is Cx && !value.isReal
}

/** Nilai variabel, Ans, dan PreAns saat evaluasi. */
class EvalContext(
    val setup: Setup,
    val vars: MutableMap<Key, Value> = mutableMapOf(),
    val ans: Value = Value.Num(CasioNumber.ZERO),
    val preAns: Value = Value.Num(CasioNumber.ZERO),
    val mode: Mode = Mode.COMP,
    /** Data STAT, diperlukan ▸t dan nilai taksiran x̂, x̂₁, x̂₂, ŷ (manual hlm. 53 dan 56). */
    val stat: StatData? = null,
    /** Isi MatA sampai MatC, VctA sampai VctC, MatAns, dan VctAns. */
    val containers: Map<Key, Value> = emptyMap(),
)

object Evaluator {

    fun eval(ast: Ast, ctx: EvalContext): Value = when (ast) {
        is Lit -> if (ctx.mode == Mode.BASE) Value.Bn(BaseN.of(ast.value)) else literal(ast.value)
        is Ref -> ref(ast.key, ctx)
        is Un -> unary(ast, ctx)
        // Posisi dilekatkan saat error menyembul, supaya ◄/► menaruh kursor di titik error.
        is Bin -> try { binary(ast, ctx) } catch (e: CalcError) { throw e.locatedAt(ast.at) }
        is Fn -> try { function(ast, ctx) } catch (e: CalcError) { throw e.locatedAt(ast.at) }
        is Dms -> dms(ast, ctx)
        is TableRef -> table(ast, ctx)
        is Equation -> syntaxError()
    }

    /**
     * Angka yang diketik: hanya bilangan bulat menjadi bentuk eksak. Angka berkoma tetap desimal
     * supaya 4×3+2.5 menghasilkan 14.5, bukan pecahan 29⌟2 seperti kalkulator fisik.
     */
    private fun literal(n: CasioNumber): Value =
        if (n.isInteger) Value.Ex(Exact.rat(n.value.toBigInteger(), BigInteger.ONE)) else Value.Num(n)

    private fun ref(key: Key, ctx: EvalContext): Value = when (key) {
        Key.CONST_PI -> Value.Ex(Exact.PI)
        Key.CONST_E -> Value.Num(CasioMath.e())
        Key.CONST_I -> Value.Cx(Complex.I)
        Key.MEM_ANS -> ctx.ans
        Key.MEM_PREANS -> ctx.preAns
        Key.MAT_A, Key.MAT_B, Key.MAT_C, Key.MAT_ANS,
        Key.VCT_A, Key.VCT_B, Key.VCT_C, Key.VCT_ANS ->
            ctx.containers[key] ?: dimensionError()
        else -> ctx.vars[key] ?: Value.Num(CasioNumber.ZERO)
    }

    // ------------------------------------------------------------------ operator

    private fun unary(ast: Un, ctx: EvalContext): Value {
        val x = eval(ast.x, ctx)
        if (x is Value.Bn) {
            return when (ast.op) {
                Key.OP_NEG, Key.FN_NEG -> Value.Bn(-x.value)
                Key.FN_NOT -> Value.Bn(x.value.not())
                else -> syntaxError()
            }
        }
        if (ast.op == Key.FN_TO_POLAR || ast.op == Key.FN_TO_RECT) return x
        if (x is Value.Mat) {
            return when (ast.op) {
                Key.OP_NEG -> Value.Mat(-x.value)
                Key.POST_SQUARE -> Value.Mat(x.value.power(2))
                Key.POST_CUBE -> Value.Mat(x.value.power(3))
                Key.POST_RECIP -> Value.Mat(x.value.inverse())
                else -> dimensionError()
            }
        }
        if (x is Value.Vec) {
            return if (ast.op == Key.OP_NEG) Value.Vec(-x.value) else dimensionError()
        }
        if (x is Value.Cx) {
            return when (ast.op) {
                Key.OP_NEG -> Value.Cx(-x.value)
                Key.POST_SQUARE -> simplify(x.value * x.value)
                Key.POST_CUBE -> simplify(x.value * x.value * x.value)
                Key.POST_RECIP -> simplify(Complex.real(CasioNumber.ONE) / x.value)
                else -> syntaxError()
            }
        }
        return when (ast.op) {
            Key.FN_NOT -> Value.Bn(BaseN.of(x.number).not())
            Key.FN_NEG -> Value.Bn(-BaseN.of(x.number))
            Key.OP_NEG -> negate(x)
            Key.POST_SQUARE -> power(x, 2)
            Key.POST_CUBE -> power(x, 3)
            Key.POST_RECIP -> power(x, -1)
            Key.POST_FACT -> factorial(x)
            Key.POST_PERCENT -> divide(x, Value.Ex(Exact.int(100)))
            // Perintah STAT yang memungut nilai di kirinya (manual hlm. 53 dan 56).
            Key.FN_TO_T -> numeric2(statOf(ctx).standardise(x.number))
            Key.FN_EST_Y -> numeric2(statOf(ctx).estimateY(x.number))
            Key.FN_EST_X, Key.FN_EST_X1 -> numeric2(statOf(ctx).estimateX(x.number, 0))
            Key.FN_EST_X2 -> numeric2(statOf(ctx).estimateX(x.number, 1))
            Key.ANGLE_DEG -> convertAngle(x, AngleUnit.DEG, ctx.setup.angle)
            Key.ANGLE_RAD -> convertAngle(x, AngleUnit.RAD, ctx.setup.angle)
            Key.ANGLE_GRA -> convertAngle(x, AngleUnit.GRA, ctx.setup.angle)
            else -> syntaxError()
        }
    }

    private fun binary(ast: Bin, ctx: EvalContext): Value {
        val l = eval(ast.l, ctx)
        val r = eval(ast.r, ctx)
        if (l is Value.Mat || r is Value.Mat || l is Value.Vec || r is Value.Vec) {
            return containerBinary(ast.op, l, r)
        }
        if (l is Value.Bn || r is Value.Bn) return baseBinary(ast.op, l, r)
        if (l is Value.Cx || r is Value.Cx || ast.op == Key.OP_ANGLE) {
            return complexBinary(ast.op, l, r, ctx)
        }
        return when (ast.op) {
            Key.OP_ADD -> combine(l, r, ExactMath::add) { a, b -> a + b }
            Key.OP_SUB -> combine(l, r, ExactMath::sub) { a, b -> a - b }
            Key.OP_MUL -> combine(l, r, ExactMath::mul) { a, b -> a * b }
            Key.OP_DIV -> divide(l, r)
            Key.OP_NPR -> permutation(l, r, combinations = false)
            Key.OP_NCR -> permutation(l, r, combinations = true)
            Key.EXP10 -> combine(l, powerOfTen(r), ExactMath::mul) { a, b -> a * b }
            else -> syntaxError()
        }
    }

    /**
     * Aritmetika matriks dan vektor (PRD §3.1). Perkalian dengan skalar diperbolehkan di kedua
     * sisi; ukuran yang tidak cocok menghasilkan Dimension ERROR.
     */
    private fun containerBinary(op: Key, l: Value, r: Value): Value = when {
        l is Value.Mat && r is Value.Mat -> when (op) {
            Key.OP_ADD -> Value.Mat(l.value + r.value)
            Key.OP_SUB -> Value.Mat(l.value - r.value)
            Key.OP_MUL -> Value.Mat(l.value * r.value)
            else -> dimensionError()
        }
        l is Value.Vec && r is Value.Vec -> when (op) {
            Key.OP_ADD -> Value.Vec(l.value + r.value)
            Key.OP_SUB -> Value.Vec(l.value - r.value)
            Key.OP_MUL -> Value.Vec(l.value cross r.value)
            Key.FN_DOT -> Value.Num(l.value dot r.value)
            else -> dimensionError()
        }
        op != Key.OP_MUL && op != Key.OP_DIV -> dimensionError()
        l is Value.Mat -> Value.Mat(l.value * scalarOf(r, op))
        r is Value.Mat -> if (op == Key.OP_DIV) dimensionError() else Value.Mat(r.value * l.number)
        l is Value.Vec -> Value.Vec(l.value * scalarOf(r, op))
        r is Value.Vec -> if (op == Key.OP_DIV) dimensionError() else Value.Vec(r.value * l.number)
        else -> dimensionError()
    }

    /** Skalar pengali; pembagian diubah menjadi perkalian dengan kebalikannya. */
    private fun scalarOf(v: Value, op: Key): CasioNumber =
        if (op == Key.OP_DIV) CasioNumber.ONE / v.number else v.number

    /** Aritmetika dan logika 32 bit pada mode BASE-N (PRD §3.1). */
    private fun baseBinary(op: Key, l: Value, r: Value): Value {
        val a = asBase(l)
        val b = asBase(r)
        return Value.Bn(
            when (op) {
                Key.OP_ADD -> a + b
                Key.OP_SUB -> a - b
                Key.OP_MUL -> a * b
                Key.OP_DIV -> a / b
                Key.FN_AND -> a and b
                Key.FN_OR -> a or b
                Key.FN_XOR -> a xor b
                Key.FN_XNOR -> a xnor b
                else -> syntaxError()
            }
        )
    }

    private fun asBase(v: Value): BaseN = if (v is Value.Bn) v.value else BaseN.of(v.number)

    /** Aritmetika bilangan kompleks, termasuk operator ∠ bentuk kutub (PRD §3.1). */
    private fun complexBinary(op: Key, l: Value, r: Value, ctx: EvalContext): Value {
        if (op == Key.OP_ANGLE) {
            return Value.Cx(Complex.polar(l.number, r.number, ctx.setup.angle))
        }
        val a = l.complex
        val b = r.complex
        val result = when (op) {
            Key.OP_ADD -> a + b
            Key.OP_SUB -> a - b
            Key.OP_MUL -> a * b
            Key.OP_DIV -> a / b
            else -> syntaxError()
        }
        return simplify(result)
    }

    /** Hasil kompleks yang bagian khayalnya nol dikembalikan sebagai bilangan biasa. */
    private fun simplify(z: Complex): Value =
        if (z.isReal) exactOrNumber(z.re) else Value.Cx(z)

    private fun powerOfTen(r: Value): Value {
        val e = r.number.toIntOrNull() ?: mathError()
        if (Math.abs(e) > 99) mathError()
        return Value.Ex(
            if (e >= 0) Exact.rat(BigInteger.TEN.pow(e), BigInteger.ONE)
            else Exact.rat(BigInteger.ONE, BigInteger.TEN.pow(-e))
        )
    }

    private inline fun combine(
        l: Value,
        r: Value,
        exactOp: (Exact, Exact) -> Exact?,
        numOp: (CasioNumber, CasioNumber) -> CasioNumber,
    ): Value {
        if (l is Value.Ex && r is Value.Ex) {
            exactOp(l.exact, r.exact)?.let { return Value.Ex(it) }
        }
        return Value.Num(numOp(l.number, r.number))
    }

    private fun divide(l: Value, r: Value): Value {
        if (r.number.isZero) mathError()
        if (l is Value.Ex && r is Value.Ex) {
            ExactMath.div(l.exact, r.exact)?.let { return Value.Ex(it) }
        }
        return Value.Num(l.number / r.number)
    }

    private fun negate(x: Value): Value = when (x) {
        is Value.Ex -> Value.Ex(ExactMath.negate(x.exact))
        is Value.Num -> Value.Num(-x.value)
        is Value.Cx -> Value.Cx(-x.value)
        is Value.Bn -> Value.Bn(-x.value)
        is Value.Mat -> Value.Mat(-x.value)
        is Value.Vec -> Value.Vec(-x.value)
    }

    private fun power(x: Value, n: Int): Value {
        if (x is Value.Ex) {
            ExactMath.pow(x.exact, n)?.let { return Value.Ex(it) }
        }
        if (n < 0 && x.number.isZero) mathError()
        return Value.Num(CasioNumber.of(CasioMath.pow(x.number.value, BigDecimal(n))))
    }

    private fun factorial(x: Value): Value {
        val n = x.number.toIntOrNull() ?: mathError()
        if (n < 0 || n > 69) mathError()
        var acc = BigInteger.ONE
        for (k in 2..n) acc = acc.multiply(BigInteger.valueOf(k.toLong()))
        return Value.Ex(Exact.rat(acc, BigInteger.ONE))
    }

    private fun permutation(l: Value, r: Value, combinations: Boolean): Value {
        val n = l.number.toIntOrNull() ?: mathError()
        val k = r.number.toIntOrNull() ?: mathError()
        if (n < 0 || k < 0 || k > n || n > 1_000_000) mathError()
        var acc = BigInteger.ONE
        for (j in 0 until k) acc = acc.multiply(BigInteger.valueOf((n - j).toLong()))
        if (combinations) {
            var d = BigInteger.ONE
            for (j in 2..k) d = d.multiply(BigInteger.valueOf(j.toLong()))
            acc = acc.divide(d)
        }
        return Value.Ex(Exact.rat(acc, BigInteger.ONE))
    }

    // ------------------------------------------------------------------ fungsi

    private fun function(ast: Fn, ctx: EvalContext): Value {
        // ∫, d/dx, dan Σ tidak boleh mengevaluasi argumen pertama di sini: argumen itu adalah
        // fungsi yang dihitung berulang dengan X terikat oleh Calculus.
        if (ast.key in CALCULUS_KEYS) return calculus(ast, ctx)
        val a = ast.args.map { eval(it, ctx) }
        return when (ast.key) {
            Key.FRAC -> divide(a[0], a[1])
            Key.FRAC_MIXED -> {
                val frac = divide(a[1], a[2])
                if (a[0].number.isNegative) combine(a[0], frac, ExactMath::sub) { x, y -> x - y }
                else combine(a[0], frac, ExactMath::add) { x, y -> x + y }
            }
            Key.OP_POW -> powerValue(a[0], a[1])
            Key.OP_NROOT -> nroot(a[0], a[1])
            Key.FN_SQRT -> sqrt(a[0])
            Key.FN_CBRT -> numeric(CasioMath.root(BigDecimal(3), a[0].number.value))
            Key.FN_DET -> Value.Num(matrixOf(a[0]).determinant())
            Key.FN_TRN -> Value.Mat(matrixOf(a[0]).transpose())
            Key.FN_NOT -> Value.Bn(asBase(a[0]).not())
            Key.FN_NEG -> Value.Bn(-asBase(a[0]))
            Key.FN_CONJG -> Value.Cx(a[0].complex.conjugate())
            Key.FN_ARG -> Value.Num(a[0].complex.argument(ctx.setup.angle))
            Key.FN_ABS -> when (val v = a[0]) {
                is Value.Vec -> Value.Num(v.value.magnitude())
                is Value.Cx -> sqrt(exactOrNumber(v.value.squaredMagnitude()))
                // Manual hlm. 65 Contoh 7: Abs pada matriks bekerja elemen demi elemen.
                is Value.Mat -> Value.Mat(v.value.copy(cells = v.value.cells.map { it.abs() }))
                is Value.Ex -> if (v.number.isNegative) Value.Ex(ExactMath.negate(v.exact)) else v
                else -> Value.Num(v.number.abs())
            }
            Key.FN_LOG -> if (a.size == 2) logBase(a[0], a[1]) else log10(a[0])
            Key.FN_LOGBASE -> logBase(a[0], a[1])
            Key.FN_LN -> numeric(CasioMath.ln(a[0].number.value))
            Key.FN_POW10 -> powerValue(Value.Ex(Exact.int(10)), a[0])
            Key.FN_EXP -> numeric(CasioMath.exp(a[0].number.value))
            Key.FN_SIN -> trig(a[0], ctx) { CasioMath.sin(it) }
            Key.FN_COS -> trig(a[0], ctx) { CasioMath.cos(it) }
            Key.FN_TAN -> {
                if (isOddQuarterTurn(a[0], ctx.setup.angle)) mathError()
                trig(a[0], ctx) { CasioMath.tan(it) }
            }
            Key.FN_ASIN -> invTrig(a[0], ctx) { CasioMath.asin(it) }
            Key.FN_ACOS -> invTrig(a[0], ctx) { CasioMath.acos(it) }
            Key.FN_ATAN -> invTrig(a[0], ctx) { CasioMath.atan(it) }
            Key.FN_SINH -> numeric(CasioMath.sinh(a[0].number.value))
            Key.FN_COSH -> numeric(CasioMath.cosh(a[0].number.value))
            Key.FN_TANH -> numeric(CasioMath.tanh(a[0].number.value))
            Key.FN_ASINH -> numeric(CasioMath.asinh(a[0].number.value))
            Key.FN_ACOSH -> numeric(CasioMath.acosh(a[0].number.value))
            Key.FN_ATANH -> numeric(CasioMath.atanh(a[0].number.value))
            Key.FN_RND -> Value.Num(roundToDisplay(a[0].number, ctx.setup))
            // Manual hlm. 35: hasilnya ditaruh di X dan Y, dan yang dikembalikan yang pertama.
            Key.FN_POL -> coordinates(a[0].number, a[1].number, ctx, polar = true)
            Key.FN_REC -> coordinates(a[0].number, a[1].number, ctx, polar = false)
            // Distribusi normal baku (manual hlm. 56): P dari -∞, Q dari 0, R sampai +∞.
            Key.FN_DIST_P -> numeric(normalArea(a[0].number))
            Key.FN_DIST_Q -> numeric(normalArea(a[0].number).subtract(java.math.BigDecimal("0.5")))
            Key.FN_DIST_R ->
                numeric(java.math.BigDecimal.ONE.subtract(normalArea(a[0].number)))
            else -> syntaxError()
        }
    }

    /**
     * ∫, d/dx, dan Σ mengevaluasi argumen pertama berulang kali, jadi pohonnya diteruskan apa
     * adanya ke [Calculus]; hanya batas dan toleransi yang dihitung lebih dulu.
     */
    private fun calculus(ast: Fn, ctx: EvalContext): Value {
        val body = ast.args[0]
        fun arg(index: Int): BigDecimal = eval(ast.args[index], ctx).number.value
        return when (ast.key) {
            // Hasil kalkulus melewati pengenalan bentuk eksak seperti trigonometri, supaya
            // Natural Display diutamakan daripada desimal (manual hlm. 32 Contoh 3).
            Key.FN_INTEGRAL -> {
                if (ast.args.size !in 3..4) syntaxError()
                exact(Calculus.integrate(body, ctx, arg(1), arg(2), if (ast.args.size == 4) arg(3) else null))
            }
            Key.FN_DERIV -> {
                if (ast.args.size !in 2..3) syntaxError()
                exact(Calculus.derivative(body, ctx, arg(1), if (ast.args.size == 3) arg(2) else null))
            }
            else -> {
                if (ast.args.size != 3) syntaxError()
                exact(Calculus.summation(body, ctx, arg(1), arg(2)))
            }
        }
    }

    private val CALCULUS_KEYS = setOf(Key.FN_INTEGRAL, Key.FN_DERIV, Key.FN_SIGMA)

    private fun matrixOf(v: Value): Matrix = (v as? Value.Mat)?.value ?: dimensionError()

    /**
     * Konstanta ilmiah dan konversi satuan (manual hlm. 42 dan 44). Keduanya dilarang di BASE-N,
     * dan konversi juga dilarang di TABLE.
     */
    private fun table(ast: TableRef, ctx: EvalContext): Value {
        if (ctx.mode == Mode.BASE) syntaxError()
        return when (ast.kind) {
            TableKind.CONST -> {
                // Nilai konstanta tetap desimal; unit fisik tidak menampilkannya sebagai pecahan.
                val entry = ScientificConstants.byNumber(ast.number) ?: syntaxError()
                Value.Num(CasioNumber.parse(entry.value))
            }
            TableKind.CONV -> {
                if (ctx.mode == Mode.TABLE) syntaxError()
                val entry = UnitConversions.byNumber(ast.number) ?: syntaxError()
                val x = eval(ast.x ?: syntaxError(), ctx)
                Value.Num(UnitConversions.apply(entry, x.number))
            }
        }
    }

    /**
     * Pol dan Rec (manual hlm. 35). Pol memberi r dan θ, Rec memberi x dan y; keduanya
     * disimpan di variabel X dan Y, dan θ berada pada -180° < θ ≤ 180° lewat [Complex.argument].
     */
    private fun coordinates(
        first: CasioNumber,
        second: CasioNumber,
        ctx: EvalContext,
        polar: Boolean,
    ): Value {
        val pair = if (polar) {
            val z = Complex(first, second)
            CasioNumber.of(CasioMath.sqrt(z.squaredMagnitude().value)) to z.argument(ctx.setup.angle)
        } else {
            val radians = toRadians(Value.Num(second), ctx.setup.angle)
            val x = first * CasioNumber.of(CasioMath.cos(radians))
            val y = first * CasioNumber.of(CasioMath.sin(radians))
            x.roundSignificant(11) to y.roundSignificant(11)
        }
        ctx.vars[Key.VAR_X] = Value.Num(pair.first)
        ctx.vars[Key.VAR_Y] = Value.Num(pair.second)
        return Value.Num(pair.first)
    }

    private fun numeric(v: BigDecimal): Value = Value.Num(CasioNumber.of(v))

    private fun numeric2(v: CasioNumber): Value = Value.Num(v)

    private fun statOf(ctx: EvalContext): StatData = ctx.stat ?: syntaxError()

    /**
     * Luas distribusi normal baku dari −∞ sampai [t] (manual hlm. 56). P, Q, dan R adalah
     * potongan luas yang berbeda dari kurva yang sama.
     */
    private fun normalArea(t: CasioNumber): BigDecimal {
        val half = BigDecimal("0.5")
        val scaled = t.value.divide(CasioMath.sqrt(BigDecimal(2)), CasioNumber.WORK)
        return half.multiply(BigDecimal.ONE.add(CasioMath.erf(scaled)), CasioNumber.WORK)
    }

    private fun exact(v: BigDecimal): Value = exactOrNumber(CasioNumber.of(v))

    private fun powerValue(base: Value, exp: Value): Value {
        val n = exp.number.toIntOrNull()
        if (n != null && Math.abs(n) <= 64 && base is Value.Ex) {
            ExactMath.pow(base.exact, n)?.let { return Value.Ex(it) }
        }
        return numeric(CasioMath.pow(base.number.value, exp.number.value))
    }

    private fun sqrt(x: Value): Value {
        if (x.number.isNegative) mathError()
        if (x is Value.Ex) {
            ExactMath.sqrt(x.exact)?.let { if (ExactMath.isDisplayable(it)) return Value.Ex(it) }
        }
        return numeric(CasioMath.sqrt(x.number.value))
    }

    private fun nroot(n: Value, x: Value): Value =
        numeric(CasioMath.root(n.number.value, x.number.value))

    private fun log10(x: Value): Value {
        if (x.number.signum <= 0) mathError()
        return snapInteger(
            CasioNumber.of(CasioMath.ln(x.number.value).divide(CasioMath.ln(BigDecimal.TEN), CasioNumber.WORK))
        )
    }

    private fun logBase(base: Value, x: Value): Value {
        if (base.number.signum <= 0 || x.number.signum <= 0) mathError()
        if (base.number == CasioNumber.ONE) mathError()
        return snapInteger(
            CasioNumber.of(
                CasioMath.ln(x.number.value).divide(CasioMath.ln(base.number.value), CasioNumber.WORK)
            )
        )
    }

    /** Hasil yang sangat dekat bilangan bulat dirapikan, seperti kalkulator fisik. */
    private fun snapInteger(n: CasioNumber): Value {
        val rounded = n.roundSignificant(11)
        return if (rounded.isInteger) {
            Value.Ex(Exact.rat(rounded.value.toBigInteger(), BigInteger.ONE))
        } else {
            Value.Num(n)
        }
    }

    /**
     * Hasil hitung numerik yang nilainya ternyata eksak dikembalikan sebagai bentuk eksak,
     * sehingga Natural Display diutamakan daripada desimal: sin 60° menjadi √3⌟2 dan
     * ∫₀^π (sin X + cos X)² dX menjadi π (manual hlm. 32 Contoh 3).
     */
    private fun exactOrNumber(n: CasioNumber): Value =
        Exact.recognise(n)?.takeIf { ExactMath.isDisplayable(it) }?.let { Value.Ex(it) }
            ?: Value.Num(n)

    private inline fun trig(x: Value, ctx: EvalContext, f: (BigDecimal) -> BigDecimal): Value {
        val result = CasioNumber.of(f(toRadians(x, ctx.setup.angle)))
        val tidy = result.roundSignificant(11)
        return exactOrNumber(if (tidy.isInteger || tidy.isZero) tidy else result)
    }

    private inline fun invTrig(x: Value, ctx: EvalContext, f: (BigDecimal) -> BigDecimal): Value {
        val converted = fromRadians(f(x.number.value), ctx.setup.angle)
        val tidy = converted.roundSignificant(11)
        return exactOrNumber(if (tidy.isInteger) tidy else converted)
    }

    /**
     * Konversi satuan sudut (manual hlm. 28): nilai ditulis dalam satuan [from], lalu
     * dinyatakan ulang dalam satuan yang sedang dipakai. Contoh pada Deg, (pi dibagi 2)
     * diikuti penanda radian menghasilkan 90.
     */
    private fun convertAngle(x: Value, from: AngleUnit, to: AngleUnit): Value {
        if (from == to) return x
        val converted = fromRadians(toRadians(x, from), to)
        // Dirapikan seperti hasil trigonometri, supaya pi dibagi 2 radian menjadi tepat 90.
        val tidy = converted.roundSignificant(11)
        return exactOrNumber(if (tidy.isInteger) tidy else converted)
    }

    /**
     * tan tak terdefinisi pada kelipatan ganjil seperempat putaran: 90° dan 270° di Deg, 100 di
     * Gra, π/2 di Rad, sehingga Casio memberi Math ERROR. Sudutnya diperiksa langsung, bukan
     * lewat cos yang hampir nol: konversi 90° ke radian dan π 15 digit menyisakan galat
     * pembulatan, sehingga cos tidak pernah tepat nol dan tan memberi angka raksasa.
     */
    private fun isOddQuarterTurn(x: Value, unit: AngleUnit): Boolean {
        val quarter = when (unit) {
            AngleUnit.DEG -> BigDecimal(90)
            AngleUnit.GRA -> BigDecimal(100)
            AngleUnit.RAD -> CasioMath.PI.divide(BigDecimal(2), CasioNumber.WORK)
        }
        val turns = x.number.value.divide(quarter, CasioNumber.WORK)
        val nearest = turns.setScale(0, RoundingMode.HALF_EVEN)
        if (nearest.toBigInteger().testBit(0).not()) return false
        // Toleransi relatif 1e-12: cukup untuk π 15 digit yang diketik, jauh di bawah sudut
        // sah terdekat yang bisa dimasukkan, misalnya 89.9999999 derajat.
        val gap = turns.subtract(nearest).abs()
        return gap <= BigDecimal("1E-12").multiply(nearest.abs().max(BigDecimal.ONE))
    }

    private fun toRadians(x: Value, unit: AngleUnit): BigDecimal = when (unit) {
        AngleUnit.RAD -> x.number.value
        AngleUnit.DEG -> x.number.value.multiply(CasioMath.PI, CasioNumber.WORK)
            .divide(BigDecimal(180), CasioNumber.WORK)
        AngleUnit.GRA -> x.number.value.multiply(CasioMath.PI, CasioNumber.WORK)
            .divide(BigDecimal(200), CasioNumber.WORK)
    }

    private fun fromRadians(r: BigDecimal, unit: AngleUnit): CasioNumber = when (unit) {
        AngleUnit.RAD -> CasioNumber.of(r)
        AngleUnit.DEG -> CasioNumber.of(
            r.multiply(BigDecimal(180), CasioNumber.WORK).divide(CasioMath.PI, CasioNumber.WORK)
        )
        AngleUnit.GRA -> CasioNumber.of(
            r.multiply(BigDecimal(200), CasioNumber.WORK).divide(CasioMath.PI, CasioNumber.WORK)
        )
    }

    /** Rnd: bulatkan ke format tampilan aktif (PRD §5.4). */
    fun roundToDisplay(n: CasioNumber, setup: Setup): CasioNumber = when (val f = setup.format) {
        is DisplayFormat.Fix -> n.roundScale(f.places)
        is DisplayFormat.Sci -> n.roundSignificant(f.digits)
        else -> n.roundSignificant(10)
    }

    // ------------------------------------------------------------------ DMS

    private fun dms(ast: Dms, ctx: EvalContext): Value {
        val parts = ast.parts.map { eval(it, ctx).number }
        val deg = parts.getOrElse(0) { CasioNumber.ZERO }
        val min = parts.getOrElse(1) { CasioNumber.ZERO }
        val sec = parts.getOrElse(2) { CasioNumber.ZERO }
        val magnitude = deg.abs() + min / CasioNumber.of(60) + sec / CasioNumber.of(3600)
        return Value.Num(if (deg.isNegative) -magnitude else magnitude)
    }
}
