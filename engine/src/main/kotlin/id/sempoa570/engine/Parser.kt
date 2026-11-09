package id.sempoa570.engine

/** Pohon sintaksis hasil parser, siap dievaluasi berulang (dipakai CALC/SOLVE di Tahap 3). */
sealed interface Ast

data class Lit(val value: CasioNumber) : Ast

/** Konstanta dan variabel: π, e, Ans, PreAns, A..F, X, Y, M. */
data class Ref(val key: Key) : Ast

/** Operator awalan atau akhiran satu operand. */
data class Un(val op: Key, val x: Ast) : Ast

/**
 * Operator dua operand: + − × ÷, nPr, nCr, ×10^, perkalian implisit.
 *
 * [at] menandai simpul tingkat atas terakhir yang membentuk operand kanan, supaya kursor dapat
 * dibawa ke situ saat error (manual hlm. 72). -1 berarti tak diketahui, misalnya di dalam
 * argumen fungsi yang diurai sebagai daftar tersendiri.
 */
data class Bin(val op: Key, val l: Ast, val r: Ast, val at: Int = -1) : Ast

/** Fungsi berargumen: sin, log, √, pecahan, Abs, Rnd, dan sebagainya. [at] seperti pada [Bin]. */
data class Fn(val key: Key, val args: List<Ast>, val at: Int = -1) : Ast

/** Nilai derajat-menit-detik: bagian yang ada urut derajat, menit, detik. */
data class Dms(val parts: List<Ast>) : Ast

/**
 * Konstanta ilmiah sebagai operand, atau perintah konversi satuan atas [x] (manual hlm. 42, 44).
 * [x] null berarti konstanta.
 */
data class TableRef(val kind: TableKind, val number: Int, val x: Ast? = null) : Ast

/** Persamaan `kiri = kanan` yang dipakai SOLVE (PRD §5.3). */
data class Equation(val left: Ast, val right: Ast) : Ast

/**
 * Parser Pratt dengan tabel prioritas Casio (PRD §5.2).
 *
 * Tahap 1 memakai bagian tabel yang dipakai COMP dasar. Perkalian implisit diberi prioritas
 * lebih tinggi daripada × dan ÷ sesuai edisi ES PLUS; kasus ambigu masih menunggu verifikasi
 * unit fisik (daftar tunggu di docs/golden/README.md).
 */
object Parser {

    private const val BP_EQUATION = 10
    private const val BP_OR = 14
    private const val BP_AND = 18
    private const val BP_ANGLE = 52
    private const val BP_ADD = 30
    private const val BP_MUL = 40
    private const val BP_UNARY = 45
    private const val BP_PERM = 50
    private const val BP_IMPLICIT = 60

    /**
     * Perintah yang memungut seluruh nilai di kirinya, termasuk tanda minus di depan: konversi
     * satuan dan perintah STAT ▸t, x̂, x̂₁, x̂₂, ŷ. Manual hlm. 44 Contoh 3 menghitung
     * `-31°C▸°F` sebagai -23.8, bukan -(31°C▸°F) = -87.8, dan hlm. 55 Contoh 4 menghitung
     * `-130x̂` sebagai x̂(-130) = 4.861, bukan -x̂(130) = -5.428. Ikatannya sengaja sama dengan
     * operator awalan, jadi lebih kuat dari + dan ×. Perilaku pada ekspresi majemuk belum ada
     * contohnya di manual dan menunggu cek unit fisik.
     */
    private const val BP_COMMAND = 45

    /** Perintah STAT yang memungut nilai di kirinya (manual hlm. 53 dan 56). */
    private val VALUE_COMMANDS = setOf(
        Key.FN_TO_T, Key.FN_EST_X, Key.FN_EST_X1, Key.FN_EST_X2, Key.FN_EST_Y,
    )
    private const val BP_EXP10 = 70

    private val BINARY_OPS = setOf(
        Key.OP_ADD, Key.OP_SUB, Key.OP_MUL, Key.OP_DIV,
        Key.OP_NPR, Key.OP_NCR, Key.EXP10, Key.REL_EQ, Key.OP_ANGLE,
        Key.FN_AND, Key.FN_OR, Key.FN_XOR, Key.FN_XNOR, Key.FN_DOT,
    )

    /** Nama matriks dan vektor yang dapat berdiri sebagai operand. */
    private val CONTAINER_REFS = setOf(
        Key.MAT_A, Key.MAT_B, Key.MAT_C, Key.MAT_ANS,
        Key.VCT_A, Key.VCT_B, Key.VCT_C, Key.VCT_ANS,
    )

    fun parse(nodes: List<Node>, base: NumberBase = NumberBase.DEC): Ast = parse(nodes, base, true)

    /**
     * [located] hanya benar untuk daftar simpul tingkat atas. Argumen fungsi diurai sebagai
     * daftar tersendiri yang indeksnya tidak sebanding dengan indeks tingkat atas, jadi di sana
     * posisi tidak dicatat dan error mewarisi posisi fungsinya.
     */
    private fun parse(nodes: List<Node>, base: NumberBase, located: Boolean): Ast {
        if (nodes.isEmpty()) syntaxError()
        val p = State(nodes, base, located)
        val ast = p.expr(0)
        if (!p.atEnd) throw CalcError(ErrorKind.SYNTAX, if (located) p.i else -1)
        return ast
    }

    private class State(
        val nodes: List<Node>,
        val base: NumberBase = NumberBase.DEC,
        val located: Boolean = false,
    ) {
        var i = 0
        val atEnd: Boolean get() = i >= nodes.size

        fun peek(): Node? = nodes.getOrNull(i)
        fun next(): Node = nodes.getOrNull(i++) ?: syntaxError()

        fun expr(minBp: Int): Ast {
            var left = prefix()
            while (true) {
                val node = peek() ?: break
                val bp = leftBp(node) ?: break
                if (bp <= minBp) break
                left = infix(left, node, bp)
            }
            return left
        }

        private fun prefix(): Ast {
            val node = next()
            if (node is KeyNode && (node.key == Key.OP_NEG || (node.key == Key.OP_SUB && i == 1))) {
                return Un(Key.OP_NEG, expr(BP_UNARY))
            }
            return postfix(atom(node))
        }

        private fun atom(node: Node): Ast = when (node) {
            is NumNode -> Lit(
                if (base == NumberBase.DEC) CasioNumber.parse(node.text)
                else BaseN.parse(node.text, base).toNumber()
            )
            is KeyNode -> when (node.key) {
                Key.CONST_PI, Key.CONST_E, Key.CONST_I,
                Key.MEM_ANS, Key.MEM_PREANS -> Ref(node.key)
                in CONTAINER_REFS -> Ref(node.key)
                in Key.VARIABLES -> Ref(node.key)
                else -> syntaxError()
            }
            is CallNode -> call(node)
            // Perintah konversi wajib punya nilai di kirinya, jadi tak pernah berdiri sebagai atom.
            is TableNode ->
                if (node.kind == TableKind.CONST) TableRef(node.kind, node.number)
                else syntaxError()
            CursorNode -> syntaxError()
        }

        private fun call(node: CallNode): Ast {
            // `next()` sudah melewati simpul ini, jadi letaknya di i-1.
            val here = if (located) i - 1 else -1
            val args = node.args.filter { it.isNotEmpty() }.map { parse(it, base, false) }
            if (args.isEmpty()) throw CalcError(ErrorKind.SYNTAX, here)
            if (args.size != node.args.size && node.key !in Slots.variadic) {
                throw CalcError(ErrorKind.SYNTAX, here)
            }
            return if (node.key == Key.PAREN_OPEN) args[0] else Fn(node.key, args, here)
        }

        /** Fungsi akhiran dan urutan derajat-menit-detik. */
        private fun postfix(base: Ast): Ast {
            var x = base
            while (true) {
                val node = peek()
                if (node !is KeyNode) return x
                when (node.key) {
                    Key.POST_SQUARE, Key.POST_CUBE, Key.POST_RECIP,
                    Key.POST_FACT, Key.POST_PERCENT,
                    Key.ANGLE_DEG, Key.ANGLE_RAD, Key.ANGLE_GRA,
                    Key.FN_TO_POLAR, Key.FN_TO_RECT -> {
                        i++
                        x = Un(node.key, x)
                    }
                    Key.POST_DMS -> {
                        i++
                        x = readDms(x)
                    }
                    else -> return x
                }
            }
        }

        /** Setelah `°` pertama, kumpulkan bagian menit dan detik bila ada. */
        private fun readDms(degrees: Ast): Ast {
            val parts = mutableListOf(degrees)
            while (parts.size < 3) {
                val node = peek()
                if (node !is NumNode) break
                val after = nodes.getOrNull(i + 1)
                if (after !is KeyNode || after.key != Key.POST_DMS) break
                parts.add(Lit(CasioNumber.parse(node.text)))
                i += 2
            }
            return Dms(parts)
        }

        private fun leftBp(node: Node): Int? = when {
            node is TableNode && node.kind == TableKind.CONV -> BP_COMMAND
            node is KeyNode && node.key in VALUE_COMMANDS -> BP_COMMAND
            node is KeyNode && node.key in BINARY_OPS -> when (node.key) {
                Key.OP_ADD, Key.OP_SUB -> BP_ADD
                Key.OP_MUL, Key.OP_DIV -> BP_MUL
                Key.OP_NPR, Key.OP_NCR -> BP_PERM
                Key.REL_EQ -> BP_EQUATION
                Key.FN_OR, Key.FN_XOR, Key.FN_XNOR -> BP_OR
                Key.FN_AND -> BP_AND
                Key.OP_ANGLE -> BP_ANGLE
                Key.FN_DOT -> BP_MUL
                else -> BP_EXP10
            }
            startsOperand(node) -> BP_IMPLICIT
            else -> null
        }

        private fun infix(left: Ast, node: Node, bp: Int): Ast {
            // Hanya perintah konversi yang memungut operan kiri; konstanta di posisi ini
            // berarti perkalian implisit, misalnya ε₀μ₀.
            if (node is TableNode && node.kind == TableKind.CONV) {
                i++
                return TableRef(node.kind, node.number, left)
            }
            if (node is KeyNode && node.key in VALUE_COMMANDS) {
                i++
                return Un(node.key, left)
            }
            if (node is KeyNode && node.key == Key.REL_EQ) {
                i++
                return Equation(left, expr(bp))
            }
            if (node is KeyNode && node.key in BINARY_OPS) {
                i++
                val right = expr(bp)
                // Kursor error berdiri sesudah operand kanan, seperti contoh 14÷0×2 manual hlm. 72.
                return Bin(node.key, left, right, if (located) i - 1 else -1)
            }
            val right = expr(BP_IMPLICIT)
            return Bin(Key.OP_MUL, left, right, if (located) i - 1 else -1)
        }

        private fun startsOperand(node: Node): Boolean = when (node) {
            is NumNode -> true
            is CallNode -> true
            is TableNode -> node.kind == TableKind.CONST
            CursorNode -> false
            is KeyNode -> node.key == Key.CONST_PI || node.key == Key.CONST_E ||
                node.key == Key.CONST_I || node.key == Key.MEM_ANS ||
                node.key == Key.MEM_PREANS || node.key in Key.VARIABLES ||
                node.key in CONTAINER_REFS
        }
    }
}
