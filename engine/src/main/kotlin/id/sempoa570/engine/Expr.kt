package id.sempoa570.engine

import kotlinx.serialization.Serializable

/**
 * Pohon ekspresi dua dimensi (PRD §5.5). Pohon yang sama dipakai untuk menggambar tampilan
 * dan, setelah `=`, untuk diparse dan dievaluasi.
 */
@Serializable
sealed interface Node

/** Runtun digit dan titik desimal, misal "12.5". */
@Serializable
data class NumNode(val text: String) : Node

/** Tombol tanpa slot: operator, konstanta, variabel, fungsi akhiran. */
@Serializable
data class KeyNode(val key: Key) : Node

/**
 * Fungsi atau struktur berslot, misal pecahan (2 slot) atau akar pangkat n (2 slot).
 *
 * [closed] hanya berarti bagi grup kurung: `(` menyisipkan grup yang belum ditutup dan kurung
 * tutupnya baru digambar setelah `)` ditekan, seperti unit fisik (manual hlm. 16 Contoh 1 dan 2).
 * Templat fungsi selalu `true`. Nilai bawaan menjaga berkas DataStore lama tetap terbaca.
 */
@Serializable
data class CallNode(
    val key: Key,
    val args: List<List<Node>>,
    val closed: Boolean = true,
) : Node

/** Penanda posisi kursor; hanya muncul pada pohon untuk digambar, tidak pernah untuk diparse. */
@Serializable
data object CursorNode : Node

/** Tabel dua digit: konstanta ilmiah atau perintah konversi satuan. */
@Serializable
enum class TableKind { CONST, CONV }

/**
 * Konstanta ilmiah atau perintah konversi satuan, disimpan sebagai nomornya saja
 * (manual hlm. 42 dan 44).
 *
 * Konstanta berdiri sebagai operand dan digambar sebagai simbolnya, misalnya `C₀`; perintah
 * konversi berdiri di belakang nilainya dan digambar sebagai labelnya, misalnya `cm▸in`.
 */
@Serializable
data class TableNode(val kind: TableKind, val number: Int) : Node

/** Bentuk slot tiap struktur. */
internal object Slots {

    val count: Map<Key, Int> = mapOf(
        Key.FRAC to 2,
        Key.FRAC_MIXED to 3,
        Key.OP_POW to 2,
        Key.OP_NROOT to 2,
        Key.FN_LOGBASE to 2,
        Key.FN_SQRT to 1,
        Key.FN_CBRT to 1,
        Key.FN_ABS to 1,
        Key.FN_POW10 to 1,
        Key.FN_EXP to 1,
        Key.PAREN_OPEN to 1,
        Key.FN_LOG to 1,
        Key.FN_LN to 1,
        Key.FN_SIN to 1,
        Key.FN_COS to 1,
        Key.FN_TAN to 1,
        Key.FN_ASIN to 1,
        Key.FN_ACOS to 1,
        Key.FN_ATAN to 1,
        Key.FN_RND to 1,
        Key.FN_POL to 2,
        Key.FN_REC to 2,
        Key.FN_DIST_P to 1,
        Key.FN_DIST_Q to 1,
        Key.FN_DIST_R to 1,
        Key.FN_INTEGRAL to 3,
        Key.FN_DERIV to 2,
        Key.FN_SIGMA to 3,
        Key.FN_CONJG to 1,
        Key.FN_ARG to 1,
        Key.FN_NOT to 1,
        Key.FN_NEG to 1,
        Key.FN_DET to 1,
        Key.FN_TRN to 1,
        Key.FN_SINH to 1,
        Key.FN_COSH to 1,
        Key.FN_TANH to 1,
        Key.FN_ASINH to 1,
        Key.FN_ACOSH to 1,
        Key.FN_ATANH to 1,
    )

    /** Struktur yang memungut operand di kirinya sebagai slot pertama. */
    val absorbsLeft: Set<Key> = setOf(Key.FRAC, Key.OP_POW, Key.OP_NROOT)

    /** Fungsi yang menerima argumen tambahan lewat koma, misal log(a,b). */
    val variadic: Set<Key> = setOf(Key.FN_LOG, Key.FN_INTEGRAL, Key.FN_DERIV)

    /** Struktur yang slotnya tersusun vertikal, sehingga ▲ dan ▼ berpindah antar slot. */
    /**
     * Urutan slot dari atas ke bawah untuk ▲ dan ▼. Batas integral dan sigma disimpan sebagai
     * argumen ke-1 (bawah) dan ke-2 (atas), sedangkan di layar yang atas digambar di atas, jadi
     * urutannya dibalik. Manual hlm. 32 memakai `► 0 ▲ π` untuk mengisi kedua batas.
     */
    val verticalOrder: Map<Key, List<Int>> = mapOf(
        Key.FN_INTEGRAL to listOf(2, 1),
        Key.FN_SIGMA to listOf(2, 1),
    )

    val vertical: Set<Key> =
        setOf(Key.FRAC, Key.FRAC_MIXED, Key.OP_POW, Key.OP_NROOT) + verticalOrder.keys
}

private val MATRIX_KEYS: Set<Key> = setOf(
    Key.MAT_A, Key.MAT_B, Key.MAT_C, Key.MAT_ANS,
    Key.VCT_A, Key.VCT_B, Key.VCT_C, Key.VCT_ANS,
)

private val OPERAND_END_KEYS: Set<Key> = MATRIX_KEYS + setOf(
    Key.CONST_PI, Key.CONST_E, Key.CONST_I, Key.MEM_ANS, Key.MEM_PREANS,
    Key.POST_SQUARE, Key.POST_CUBE, Key.POST_RECIP, Key.POST_FACT,
    Key.POST_PERCENT, Key.POST_DMS,
    Key.ANGLE_DEG, Key.ANGLE_RAD, Key.ANGLE_GRA,
) + Key.VARIABLES

/**
 * Penyunting ekspresi dengan kursor bebas (PRD §4.4).
 *
 * Pohon disimpan sebagai struktur yang dapat diubah; kursor adalah pasangan daftar aktif dan
 * posisi di dalamnya, ditambah tumpukan jalur untuk keluar ke struktur induk.
 */
class ExprEditor(var mode: InputMode = InputMode.MATH_IO) {

    /** Pada BASE-N heksadesimal, A sampai F diperlakukan sebagai digit, bukan variabel. */
    var hexDigits = false

    private sealed interface MNode
    private class MKey(val key: Key) : MNode

    /** Simpul yang disisipkan apa adanya, tanpa slot dan tanpa penggabungan digit. */
    private class MPlain(val node: Node) : MNode
    private class MCall(
        val key: Key,
        val args: MutableList<MutableList<MNode>>,
        var closed: Boolean = true,
    ) : MNode

    /** Satu langkah turun ke dalam struktur berslot. */
    private class Ctx(
        val parent: MutableList<MNode>,
        val callIndex: Int,
        val call: MCall,
        var argIndex: Int,
    )

    private var root = mutableListOf<MNode>()
    private val path = mutableListOf<Ctx>()
    private var pos = 0

    /** Mode sisip atau timpa; diubah oleh INS (PRD §3.2). */
    var overwrite = false
        private set

    /**
     * Sisip berbungkus (manual hlm. 18): sesudah INS, nilai di kanan kursor menjadi argumen
     * templat yang ditekan berikutnya, misalnya 7⌟6 menjadi √(7⌟6).
     */
    var wrapping = false
        private set

    private val list: MutableList<MNode>
        get() = path.lastOrNull()?.let { it.call.args[it.argIndex] } ?: root

    val isEmpty: Boolean get() = root.isEmpty()

    fun clear() {
        root = mutableListOf()
        path.clear()
        pos = 0
        wrapping = false
    }

    /** Mengisi editor dengan pohon yang sudah ada, kursor di akhir (replay dan lanjut dari Ans). */
    fun load(nodes: List<Node>) {
        clear()
        root = nodes.flatMap { toMutable(it) }.toMutableList()
        pos = root.size
    }

    /** Menyisipkan simpul jadi di posisi kursor, dipakai CONST dan CONV (manual hlm. 42). */
    fun insertNode(node: Node) {
        insert(MPlain(node))
        wrapping = false
    }

    /**
     * INS. Natural Display selalu memakai mode sisip, sehingga di sana INS menyiapkan sisip
     * berbungkus; mode timpa hanya ada pada Linear Display (manual hlm. 18).
     */
    fun toggleOverwrite() {
        if (mode == InputMode.LINE_IO) overwrite = !overwrite else wrapping = !wrapping
    }

    // ------------------------------------------------------------------ tekan tombol

    fun press(key: Key) {
        when {
            key.isDigit || key == Key.DOT -> insertDigit(key)
            key == Key.NAV_LEFT -> moveLeft()
            key == Key.NAV_RIGHT -> moveRight()
            key == Key.NAV_UP -> moveVertical(up = true)
            key == Key.NAV_DOWN -> moveVertical(up = false)
            key == Key.PAREN_CLOSE -> closeStructure()
            key == Key.SEP_COMMA -> nextArg()
            key == Key.EDIT_DEL -> delete()
            key == Key.EDIT_INS -> toggleOverwrite()
            Slots.count.containsKey(key) -> openCall(key)
            else -> {
                if (mode == InputMode.LINE_IO && key in CLOSES_FRACTION) closeOpenFractions()
                insert(MKey(key))
            }
        }
    }

    /**
     * Digit disimpan satu node per tekanan, bukan sebagai satu bilangan utuh, supaya kursor dapat
     * berdiri di tengah bilangan. Penggabungan menjadi [NumNode] dilakukan saat pohon dibangun.
     */
    private fun insertDigit(key: Key) = insert(MKey(key))

    private fun insert(node: MNode) {
        if (overwrite && pos < list.size) {
            list[pos] = node
        } else {
            list.add(pos, node)
        }
        pos++
    }

    private fun openCall(key: Key) {
        if (key == Key.FRAC && mode == InputMode.LINE_IO && promoteToMixed()) return
        val slots = Slots.count.getValue(key)
        val args = MutableList(slots) { mutableListOf<MNode>() }
        var active = 0
        if (wrapping) {
            wrapping = false
            val taken = takeOperandRight()
            if (taken.isNotEmpty()) {
                args[0].addAll(taken)
                active = if (slots > 1) 1 else 0
            }
        } else if (key in Slots.absorbsLeft) {
            val taken = takeOperandLeft()
            if (taken.isNotEmpty()) {
                args[0].addAll(taken)
                active = 1
            }
        }
        val call = MCall(key, args, closed = key != Key.PAREN_OPEN)
        list.add(pos, call)
        path.add(Ctx(list, pos, call, active))
        pos = args[active].size
    }

    /**
     * Memungut nilai di kanan kursor untuk dibungkus templat: seluruh sisa baris, berhenti
     * sebelum kurung buka atau fungsi berkurung pertama (manual hlm. 18).
     */
    private fun takeOperandRight(): List<MNode> {
        val taken = mutableListOf<MNode>()
        while (pos < list.size) {
            val node = list[pos]
            if (node is MCall && ExprRenderer.symbol(node.key).endsWith("(")) break
            if (node is MCall && node.key == Key.PAREN_OPEN) break
            taken += list.removeAt(pos)
        }
        return taken
    }

    /** Memungut operand tepat di kiri kursor; runtun digit diambil utuh, bukan satu digit. */
    private fun takeOperandLeft(): List<MNode> {
        val before = list.getOrNull(pos - 1) ?: return emptyList()
        if (before is MKey && isDigitKey(before.key)) {
            var start = pos
            while (start > 0) {
                val n = list[start - 1]
                if (n is MKey && isDigitKey(n.key)) start-- else break
            }
            val taken = list.subList(start, pos).toList()
            repeat(pos - start) { list.removeAt(start) }
            pos = start
            return taken
        }
        if (!isOperandEnd(before)) return emptyList()
        list.removeAt(pos - 1)
        pos--
        return listOf(before)
    }

    private fun isDigitKey(key: Key) =
        key.isDigit || key == Key.DOT || (hexDigits && key in HEX_LETTERS)

    /**
     * LineIO menulis pecahan campuran dengan menekan tombol pecahan dua kali: `3⌟1⌟2`.
     * Tekanan kedua di dalam penyebut mengubah pecahan menjadi pecahan campuran.
     */
    private fun promoteToMixed(): Boolean {
        val ctx = path.lastOrNull() ?: return false
        if (ctx.call.key != Key.FRAC || ctx.argIndex != 1 || ctx.call.args[1].isEmpty()) return false
        val mixed = MCall(
            Key.FRAC_MIXED,
            mutableListOf(ctx.call.args[0], ctx.call.args[1], mutableListOf()),
        )
        ctx.parent[ctx.callIndex] = mixed
        path[path.size - 1] = Ctx(ctx.parent, ctx.callIndex, mixed, 2)
        pos = 0
        return true
    }

    /**
     * LineIO menulis pecahan satu baris, jadi penyebutnya berakhir begitu operator ditekan:
     * `2⌟3+1⌟2` harus terbaca dua pecahan, bukan pecahan bertingkat.
     */
    private fun closeOpenFractions() {
        while (path.isNotEmpty() && path.last().call.key in FRACTION_KEYS) exitStructure()
    }

    // ------------------------------------------------------------------ navigasi

    private fun moveLeft() {
        if (pos > 0) {
            val before = list[pos - 1]
            if (before is MCall) {
                enter(before, pos - 1, before.args.size - 1, atEnd = true)
            } else {
                pos--
            }
            return
        }
        val ctx = path.lastOrNull() ?: return
        if (ctx.argIndex > 0) {
            ctx.argIndex--
            pos = list.size
        } else {
            exitStructure(after = false)
        }
    }

    private fun moveRight() {
        if (pos < list.size) {
            val at = list[pos]
            if (at is MCall) {
                enter(at, pos, 0, atEnd = false)
            } else {
                pos++
            }
            return
        }
        val ctx = path.lastOrNull() ?: return
        if (ctx.argIndex < ctx.call.args.size - 1) {
            ctx.argIndex++
            pos = 0
        } else {
            exitStructure(after = true)
        }
    }

    /** ▲ dan ▼ berpindah antar slot struktur vertikal, misal pembilang dan penyebut. */
    private fun moveVertical(up: Boolean) {
        val ctx = path.lastOrNull()
        if (ctx != null && ctx.call.key in Slots.vertical) {
            val order = Slots.verticalOrder[ctx.call.key] ?: ctx.call.args.indices.toList()
            val here = order.indexOf(ctx.argIndex)
            val target = if (here < 0) null else order.getOrNull(if (up) here - 1 else here + 1)
            if (target != null && target in ctx.call.args.indices) {
                ctx.argIndex = target
                pos = list.size
                return
            }
        }
        // Di luar struktur vertikal, kursor masuk ke struktur terdekat di kiri kursor.
        val before = list.getOrNull(pos - 1)
        if (before is MCall && before.key in Slots.vertical) {
            val order = Slots.verticalOrder[before.key] ?: before.args.indices.toList()
            enter(before, pos - 1, if (up) order.first() else order.last(), atEnd = true)
        }
    }

    private fun enter(call: MCall, index: Int, argIndex: Int, atEnd: Boolean) {
        val parent = list
        path.add(Ctx(parent, index, call, argIndex))
        pos = if (atEnd) call.args[argIndex].size else 0
    }

    private fun exitStructure(after: Boolean = true) {
        val ctx = path.removeAt(path.size - 1)
        pos = if (after) ctx.callIndex + 1 else ctx.callIndex
    }

    /** `)` menutup struktur terdekat dan menempatkan kursor sesudahnya. */
    private fun closeStructure() {
        if (path.isEmpty()) return
        path.last().call.closed = true
        exitStructure(after = true)
    }

    /**
     * Koma berpindah ke slot berikutnya. Untuk fungsi yang boleh menerima argumen tambahan,
     * slot baru hanya dibuat setelah semua slot bawaan terisi, misal tol pada ∫(f,a,b,tol).
     */
    private fun nextArg() {
        // Di luar argumen fungsi, koma tetap sah: sintaks {persamaan},{variabel} milik SOLVE
        // memakainya di tingkat atas (manual hlm. 39).
        val ctx = path.lastOrNull() ?: return insert(MKey(Key.SEP_COMMA))
        when {
            ctx.argIndex < ctx.call.args.size - 1 -> {
                ctx.argIndex++
                pos = 0
            }
            ctx.call.key in Slots.variadic -> {
                ctx.call.args.add(mutableListOf())
                ctx.argIndex = ctx.call.args.size - 1
                pos = 0
            }
        }
    }

    /** DEL menghapus satu karakter angka, atau seluruh struktur, di kiri kursor. */
    private fun delete() {
        if (pos > 0) {
            list.removeAt(pos - 1)
            pos--
            return
        }
        if (path.isNotEmpty()) {
            val ctx = path.removeAt(path.size - 1)
            ctx.parent.removeAt(ctx.callIndex)
            pos = ctx.callIndex
        }
    }

    // ------------------------------------------------------------------ keluaran

    /** Pohon untuk diparse; tanpa penanda kursor. */
    fun build(): List<Node> = convert(root)

    /**
     * Menaruh kursor tepat sesudah simpul ke-[index] dari [build] pada tingkat atas; dipakai ◄
     * dan ► pada layar error untuk membawa kursor ke tempat error terjadi (manual hlm. 72).
     *
     * ponytail: runtun digit dihitung sebagai satu simpul, sama seperti [convert], tetapi kasus
     * titik desimal ganda yang memaksa [convert] memecah runtun tidak ditiru. Input semacam
     * `1.2.3` memang Syntax ERROR, dan di situ kursornya bisa meleset satu simpul.
     */
    fun gotoAfterBuilt(index: Int) {
        if (index < 0) return
        path.clear()
        var built = -1
        var previousWasDigit = false
        var end = root.size
        for (k in root.indices) {
            val isDigit = root[k].let { it is MKey && isDigitKey(it.key) }
            if (!(isDigit && previousWasDigit)) built++
            previousWasDigit = isDigit
            if (built > index) break
            if (built == index) end = k + 1
        }
        pos = end.coerceIn(0, root.size)
        wrapping = false
    }

    /**
     * Untuk tiap simpul hasil [convert] dari [nodes]: indeks sumbernya di dalam [nodes] dan
     * posisi kursor tepat sesudahnya.
     *
     * Perlu dihitung tersendiri karena [convert] menggabungkan runtun digit menjadi satu
     * [NumNode] dan penanda kursor memotong runtun itu, sehingga indeks yang digambar tidak
     * sama dengan indeks di dalam daftar sumbernya.
     */
    private fun drawnMap(nodes: List<MNode>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var runStart = -1
        var sawDot = false
        fun flush(end: Int) {
            if (runStart < 0) return
            out.add(runStart to end)
            runStart = -1
            sawDot = false
        }
        for (k in nodes.indices) {
            val node = nodes[k]
            when {
                node is MKey && node.key == CURSOR_MARK -> {
                    flush(k)
                    out.add(k to k)
                }
                node is MKey && isDigitKey(node.key) -> {
                    val dot = node.key == Key.DOT
                    if (dot && sawDot) flush(k)
                    if (runStart < 0) runStart = k
                    if (dot) sawDot = true
                }
                else -> {
                    flush(k)
                    out.add(k to (k + 1))
                }
            }
        }
        flush(nodes.size)
        return out
    }

    /**
     * Menaruh kursor memakai jalur pada pohon yang digambar [buildWithCursor]: pasangan
     * (indeks simpul, indeks slot) berulang sedalam struktur yang ditembus, lalu satu bilangan
     * penutup yaitu simpul yang kursornya berada tepat sesudahnya; -1 berarti awal daftar itu.
     *
     * Dipakai ketukan layar, termasuk ketukan ke dalam pembilang pecahan, pangkat, dan batas
     * integral. Penanda kursor disisipkan dulu supaya indeksnya sejalan dengan yang digambar,
     * lalu jalurnya disusun ulang dari objek [MCall]-nya, bukan dari indeks yang bisa bergeser.
     */
    fun gotoDrawn(steps: IntArray) {
        if (steps.isEmpty() || steps.size % 2 == 0) return
        val marker = MKey(CURSOR_MARK)
        val markerList = list
        val markerIndex = pos
        markerList.add(markerIndex, marker)

        val descended = mutableListOf<Pair<MCall, Int>>()
        var current: MutableList<MNode> = root
        var landed = -1
        var failed = false
        var i = 0
        while (true) {
            val map = drawnMap(current)
            val drawn = steps[i]
            if (i == steps.size - 1) {
                landed = if (drawn < 0) 0 else map.getOrNull(drawn)?.second ?: current.size
                break
            }
            val source = map.getOrNull(drawn)?.first
            val call = source?.let { current.getOrNull(it) } as? MCall
            val arg = steps[i + 1]
            if (call == null || arg !in call.args.indices) {
                failed = true
                break
            }
            descended.add(call to arg)
            current = call.args[arg]
            i += 2
        }

        markerList.remove(marker)
        if (failed) return
        // Penanda tadi ikut terhitung; posisi sesudahnya mundur satu setelah ia dibuang.
        if (current === markerList && landed > markerIndex) landed--

        path.clear()
        var parent: MutableList<MNode> = root
        for ((call, arg) in descended) {
            val at = parent.indexOf(call)
            if (at < 0) {
                path.clear()
                pos = 0
                return
            }
            path.add(Ctx(parent, at, call, arg))
            parent = call.args[arg]
        }
        pos = landed.coerceIn(0, parent.size)
        wrapping = false
    }

    /** Pohon untuk digambar; kursor disisipkan sebagai [CursorNode]. */
    fun buildWithCursor(): List<Node> {
        val marker = MKey(CURSOR_MARK)
        list.add(pos, marker)
        val tree = convert(root)
        list.remove(marker)
        return tree
    }

    /** Menggabungkan runtun digit menjadi satu [NumNode]; kursor memutus runtun. */
    private fun convert(nodes: List<MNode>): List<Node> {
        val out = mutableListOf<Node>()
        val digits = StringBuilder()
        fun flush() {
            if (digits.isEmpty()) return
            val text = digits.toString()
            out.add(NumNode(if (text.startsWith(".")) "0$text" else text))
            digits.clear()
        }
        for (node in nodes) {
            when {
                node is MKey && node.key == CURSOR_MARK -> {
                    flush()
                    out.add(CursorNode)
                }
                node is MKey && isDigitKey(node.key) -> {
                    val ch = when {
                        node.key == Key.DOT -> "."
                        node.key in HEX_LETTERS -> ('A' + HEX_LETTERS.indexOf(node.key)).toString()
                        else -> node.key.digitValue.toString()
                    }
                    if (ch == "." && digits.contains('.')) flush()
                    digits.append(ch)
                }
                node is MPlain -> {
                    flush()
                    out.add(node.node)
                }
                node is MKey -> {
                    flush()
                    out.add(KeyNode(node.key))
                }
                node is MCall -> {
                    flush()
                    out.add(CallNode(node.key, node.args.map { convert(it) }, node.closed))
                }
            }
        }
        flush()
        return out
    }

    private fun toMutable(node: Node): List<MNode> = when (node) {
        is NumNode -> node.text.mapNotNull { ch ->
            when (ch) {
                '.' -> MKey(Key.DOT)
                in '0'..'9' -> MKey(Key.entries.first { it.name == "DIGIT_$ch" })
                else -> null
            }
        }
        is KeyNode -> listOf(MKey(node.key))
        is TableNode -> listOf(MPlain(node))
        is CallNode -> listOf(
            MCall(
                node.key,
                node.args.map { arg -> arg.flatMap { toMutable(it) }.toMutableList() }.toMutableList(),
                node.closed,
            )
        )
        CursorNode -> emptyList()
    }

    private fun isOperandEnd(node: MNode): Boolean = when (node) {
        is MCall -> true
        // Konstanta menutup operand; perintah konversi juga, sebab nilainya sudah lengkap.
        is MPlain -> true
        is MKey -> node.key in OPERAND_END_KEYS || isDigitKey(node.key)
    }

    private companion object {
        val HEX_LETTERS = listOf(Key.VAR_A, Key.VAR_B, Key.VAR_C, Key.VAR_D, Key.VAR_E, Key.VAR_F)
        val FRACTION_KEYS = setOf(Key.FRAC, Key.FRAC_MIXED)
        val CLOSES_FRACTION = setOf(Key.OP_ADD, Key.OP_SUB, Key.OP_MUL, Key.OP_DIV, Key.OP_NEG)

        /** Tombol semu yang hanya dipakai sebagai penanda kursor di dalam pohon. */
        val CURSOR_MARK = Key.POWER_ON
    }
}

/** Menggambar pohon ekspresi menjadi satu baris teks sesuai penyandian docs/golden/README.md. */
object ExprRenderer {

    fun render(nodes: List<Node>): String {
        val sb = StringBuilder()
        var dmsIndex = 0
        for (node in nodes) {
            when {
                node is KeyNode && node.key == Key.POST_DMS -> {
                    sb.append(DMS_MARKS[dmsIndex.coerceAtMost(2)])
                    dmsIndex++
                }
                node is KeyNode && node.key in RESET_DMS -> {
                    dmsIndex = 0
                    sb.append(renderOne(node))
                }
                else -> sb.append(renderOne(node))
            }
        }
        return sb.toString()
    }

    private val DMS_MARKS = listOf("°", "'", "\"")

    private val RESET_DMS = setOf(Key.OP_ADD, Key.OP_SUB, Key.OP_MUL, Key.OP_DIV)

    private fun renderOne(node: Node): String = when (node) {
        is NumNode -> node.text
        is KeyNode -> symbol(node.key)
        is CallNode -> renderCall(node)
        is TableNode -> tableLabel(node)
        CursorNode -> "|"
    }

    /** Simbol konstanta atau label konversi seperti yang dicetak manual. */
    fun tableLabel(node: TableNode): String = when (node.kind) {
        TableKind.CONST -> ScientificConstants.byNumber(node.number)?.symbol ?: ""
        TableKind.CONV -> UnitConversions.byNumber(node.number)?.label ?: ""
    }

    private fun renderCall(node: CallNode): String {
        val a = node.args.map { render(it) }
        return when (node.key) {
            Key.FRAC -> "${a[0]}⌟${a[1]}"
            Key.FRAC_MIXED -> "${a[0]}⌟${a[1]}⌟${a[2]}"
            Key.OP_POW -> "${a[0]}^(${a[1]})"
            Key.OP_NROOT -> "${a[0]}√(${a[1]})"
            Key.FN_LOGBASE -> "log${subscript(a[0])}(${a[1]})"
            Key.FN_SQRT -> "√(${a[0]})"
            Key.FN_CBRT -> "³√(${a[0]})"
            Key.FN_ABS -> "Abs(${a[0]})"
            Key.FN_POW10 -> "10^(${a[0]})"
            Key.FN_EXP -> "e^(${a[0]})"
            Key.PAREN_OPEN -> if (node.closed) "(${a[0]})" else "(${a[0]}"
            else -> symbol(node.key) + a.joinToString(",") + ")"
        }
    }

    private fun subscript(s: String): String = s.map { SUBSCRIPTS[it] ?: it }.joinToString("")

    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    )

    fun symbol(key: Key): String = when (key) {
        Key.OP_ADD -> "+"
        Key.OP_SUB -> "-"
        Key.OP_MUL -> "×"
        Key.OP_DIV -> "÷"
        Key.OP_NEG -> "-"
        Key.OP_NPR -> "P"
        Key.OP_NCR -> "C"
        Key.EXP10 -> "E"
        Key.CONST_PI -> "π"
        Key.CONST_E -> "e"
        Key.CONST_I -> "i"
        Key.SEP_COMMA -> ","
        Key.MEM_STO -> "→"
        Key.MEM_ANS -> "Ans"
        Key.MEM_PREANS -> "PreAns"
        Key.POST_SQUARE -> "²"
        Key.POST_CUBE -> "³"
        Key.POST_RECIP -> "^(-1)"
        Key.POST_FACT -> "!"
        Key.ANGLE_DEG -> "\u00b0"
        Key.ANGLE_RAD -> "\u02b3"
        Key.ANGLE_GRA -> "\u1d4d"
        Key.POST_PERCENT -> "%"
        Key.POST_DMS -> "°"
        Key.FN_LOG -> "log("
        Key.FN_LN -> "ln("
        Key.FN_SIN -> "sin("
        Key.FN_COS -> "cos("
        Key.FN_TAN -> "tan("
        Key.FN_ASIN -> "sin⁻¹("
        Key.FN_ACOS -> "cos⁻¹("
        Key.FN_ATAN -> "tan⁻¹("
        Key.FN_RND -> "Rnd("
        Key.FN_INTEGRAL -> "∫("
        Key.FN_DERIV -> "d/dx("
        Key.FN_SIGMA -> "Σ("
        Key.REL_EQ -> "="
        Key.FN_POL -> "Pol("
        Key.FN_REC -> "Rec("
        Key.FN_DIST_P -> "P("
        Key.FN_DIST_Q -> "Q("
        Key.FN_DIST_R -> "R("
        Key.FN_TO_T -> "▸t"
        Key.FN_EST_X -> "x̂"
        Key.FN_EST_X1 -> "x̂₁"
        Key.FN_EST_X2 -> "x̂₂"
        Key.FN_EST_Y -> "ŷ"
        Key.FN_CONJG -> "Conjg("
        Key.FN_ARG -> "arg("
        Key.FN_NOT -> "Not("
        Key.FN_NEG -> "Neg("
        Key.FN_SINH -> "sinh("
        Key.FN_COSH -> "cosh("
        Key.FN_TANH -> "tanh("
        Key.FN_ASINH -> "sinh\u207b\u00b9("
        Key.FN_ACOSH -> "cosh\u207b\u00b9("
        Key.FN_ATANH -> "tanh\u207b\u00b9("
        Key.FN_DET -> "det("
        Key.FN_TRN -> "Trn("
        Key.FN_DOT -> "·"
        Key.MAT_A -> "MatA"
        Key.MAT_B -> "MatB"
        Key.MAT_C -> "MatC"
        Key.MAT_ANS -> "MatAns"
        Key.VCT_A -> "VctA"
        Key.VCT_B -> "VctB"
        Key.VCT_C -> "VctC"
        Key.VCT_ANS -> "VctAns"
        Key.FN_TO_POLAR -> "▸r∠θ"
        Key.FN_TO_RECT -> "▸a+bi"
        Key.OP_ANGLE -> "∠"
        Key.FN_AND -> " and "
        Key.FN_OR -> " or "
        Key.FN_XOR -> " xor "
        Key.FN_XNOR -> " xnor "
        Key.SEP_COLON -> ":"
        else -> if (key in Key.VARIABLES) key.name.removePrefix("VAR_") else key.name
    }
}
