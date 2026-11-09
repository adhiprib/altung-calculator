package id.sempoa570.engine

import java.math.BigDecimal
import java.math.RoundingMode

/** Menu bernomor yang dirender sebagai daftar, bukan dialog Android (PRD §4.4). */
enum class CalculatorMenu(
    val title: String,
    val items: List<String>,
    /** Jumlah kolom saat digambar; menu berbutir pendek dua kolom seperti unit fisik. */
    val columns: Int = 1,
    /** Butir per halaman; sisanya dicapai dengan ▼ (PRD §4.4). */
    val perPage: Int = 8,
    /** Baris kecil di bawah daftar; kosong berarti tidak digambar. */
    val footer: String = "",
) {
    MODE("MODE", listOf("COMP", "CMPLX", "STAT", "BASE-N", "EQN", "MATRIX", "TABLE", "VECTOR"), 2),
    // Manual hlm. 60 mencetak keempat tipe satu per baris, dengan persamaannya utuh.
    EQN_TYPE("EQN", EqnSolver.Type.entries.map { it.label }, 1, 4),
    STAT_TYPE("STAT", StatType.entries.map { it.label }, 2),

    /**
     * Menu STAT dan submenunya berubah isi menurut jenis perhitungan dan menurut layar yang
     * sedang tampil, jadi butirnya dihitung `statItems`, bukan ditulis di sini. Daftar di bawah
     * hanya bentuk terpanjangnya, dipakai saat isi dinamisnya tidak berlaku.
     */
    STAT_MAIN("STAT", listOf("Type", "Data", "Sum", "Var", "Reg", "MinMax"), 2),
    STAT_SUM("Sum", listOf("Σx²", "Σx", "Σy²", "Σy", "Σxy", "Σx³", "Σx²y", "Σx⁴"), 2),
    STAT_VAR("Var", listOf("n", "x̄", "σx", "sx", "ȳ", "σy", "sy"), 2),
    STAT_REG("Reg", listOf("A", "B", "C", "x̂₁", "x̂₂", "ŷ"), 2),
    STAT_MINMAX("MinMax", listOf("minX", "maxX", "minY", "maxY"), 2),

    /** Distribusi normal baku (manual hlm. 51 dan 56); masuk lingkup atas permintaan pemilik. */
    STAT_DISTR("Distr", listOf("P(", "Q(", "R(", "▸t"), 2),

    // Di dalam Statistics Editor butir ketiga menjadi Edit (manual hlm. 48 dan 49).
    STAT_MAIN_EDIT("STAT", listOf("Type", "Data", "Edit", "Var", "Reg", "MinMax"), 2),
    STAT_EDIT("Edit", listOf("Ins", "Del-A"), 2),

    // Menu MATRIX dan VECTOR; urutan butir milik saya, belum diverifikasi ke unit fisik.
    MATRIX_MAIN("MATRIX", listOf("Dim", "Data", "MatA", "MatB", "MatC", "MatAns", "det", "Trn")),
    // Judulnya `Matrix?` seperti layar yang muncul saat masuk mode (manual hlm. 63).
    MATRIX_NAME("Matrix?", listOf("MatA", "MatB", "MatC"), 2),
    MATRIX_EDIT("Data", listOf("MatA", "MatB", "MatC"), 2),

    // Ukuran dipilih dari daftar, bukan ditanya m lalu n (manual hlm. 63).
    MATRIX_DIM(
        "m×n?",
        listOf("3×3", "3×2", "3×1", "2×3", "2×2", "2×1", "1×3", "1×2", "1×1"),
        2,
        6,
    ),
    VECTOR_MAIN("VECTOR", listOf("Dim", "Data", "VctA", "VctB", "VctC", "VctAns", "Dot")),
    // Judulnya `Vector?` seperti layar yang muncul saat masuk mode (manual hlm. 68).
    VECTOR_NAME("Vector?", listOf("VctA", "VctB", "VctC"), 2),
    VECTOR_EDIT("Data", listOf("VctA", "VctB", "VctC"), 2),
    VECTOR_DIM("m?", listOf("3", "2"), 2),

    // Menu SETUP ringkas; Fix dan Sci yang memerlukan argumen angka menyusul.
    // Urutan butir dan halamannya dari manual hlm. 12–15. Halaman kedua dicapai dengan ▼.
    // CONT (kontras LCD) ditiadakan karena tak berlaku di ponsel, seperti tombol ON.
    SETUP(
        "SETUP",
        listOf(
            "MthIO", "LineIO", "Deg", "Rad", "Gra", "Fix", "Sci", "Norm",
            "ab/c", "d/c", "CMPLX", "STAT",
        ),
        2,
    ),
    SETUP_NORM("Norm", listOf("Norm1", "Norm2"), 2),
    SETUP_CMPLX("CMPLX", listOf("a+bi", "r∠θ"), 2),

    /**
     * Menu tombol OPTION, pengganti tombol ON yang tidak ada gunanya di ponsel: memilih
     * warna badan kalkulator sesuai tiga varian resmi fx-570ES PLUS edisi kedua.
     */
    OPTION(
        "OPTION",
        listOf("Tema", "Getar", "Manual", "Salin", "Tempel", "Reset"),
        2,
        footer = "Altung Natural Display by N-Blu Studios",
    ),

    /** Warna badan, meniru tiga varian resmi edisi kedua. */
    OPTION_SKIN("Tema", listOf("Malam", "Kertas", "Hutan"), 3),

    /** Getaran tipis tiap tekan tombol; bawaannya mati. */
    OPTION_HAPTIC("Getar", listOf("Off", "On"), 2),

    /** Reset menghapus seluruh isi kalkulator, jadi selalu ditanya dulu. */
    OPTION_RESET("Reset semua?", listOf("Ya", "Tidak"), 2),

    /** Tekan lama baris ekspresi atau baris hasil; bukan tiruan tombol fisik. */
    CLIPBOARD("Papan klip", listOf("Salin", "Tempel"), 2),

    /** SHIFT 9, manual hlm. 15, 26, dan 4: tiga tingkat penghapusan. */
    CLR("CLR", listOf("Setup", "Memory", "All"), 1),

    /** Layar pembenaran CLR; manual menulis `=(Yes)`, jadi dibenarkan dengan `=`, bukan angka. */
    CLR_CONFIRM("", emptyList(), 1),
    SETUP_STAT("STAT", listOf("ON", "OFF"), 2),

    // Urutan butir dibaca dari manual resmi fx-570ES PLUS.
    BASE_LOGIC("BASE", listOf("and", "or", "xor", "xnor", "Not", "Neg")),

    /** Konversi satuan sudut (manual hlm. 28); penanda ditulis sesudah nilainya. */
    DRG("DRG", listOf("\u00b0", "\u02b3", "\u1d4d"), 3),

    /** Urutan butir dibaca dari manual resmi. */
    HYP("hyp", listOf("sinh", "cosh", "tanh", "sinh\u207b\u00b9", "cosh\u207b\u00b9", "tanh\u207b\u00b9")),

    // Nomor butir dibaca dari manual hlm. 47: 1 arg, 2 Conjg, 3 \u25b8r\u2220\u03b8, 4 \u25b8a+bi.
    CMPLX_MAIN("CMPLX", listOf("arg", "Conjg", "\u25b8r\u2220\u03b8", "\u25b8a+bi"), 2),

    /**
     * Fix dan Sci meminta satu tekanan angka 0~9, bukan daftar bernomor (manual hlm. 12).
     * Sci 0 berarti sepuluh digit berarti, sesuai contoh manual.
     */
    SETUP_FIX("Fix 0~9?", emptyList()),
    SETUP_SCI("Sci 0~9?", emptyList()),
}

/** Satu halaman menu yang siap digambar LCD. */
data class MenuView(
    val title: String,
    val items: List<String>,
    val columns: Int,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val footer: String = "",
)

/**
 * Kisi yang sedang tampil: Statistics Editor, Matrix atau Vector Editor, dan layar MatAns
 * serta VctAns. Sel terpilih digambar terbalik warnanya, seperti unit fisik.
 */
data class GridView(
    val title: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val cursorRow: Int,
    val cursorCol: Int,
    /** Angka yang sedang diketik pada sel terpilih, berikut kursornya. */
    val typed: List<Node>,
    /** Nomor baris di kiri, seperti Statistics Editor. */
    val numbered: Boolean,
    /** Kurung siku mengapit kisi, seperti layar matriks dan vektor. */
    val bracketed: Boolean,
)

/** Indikator kecil di tepi atas LCD (PRD §4.4). */
data class Indicators(
    val memory: Boolean = false,
    val store: Boolean = false,
    val recall: Boolean = false,
    val angle: AngleUnit = AngleUnit.DEG,
    val fix: Boolean = false,
    val sci: Boolean = false,
    val math: Boolean = true,
    val insert: Boolean = false,
    val replayUp: Boolean = false,
    val replayDown: Boolean = false,
    /** Mode kalkulasi aktif; ditulis di tepi atas LCD seperti unit fisik. */
    val mode: Mode = Mode.COMP,
    val base: NumberBase = NumberBase.DEC,
)

/**
 * Status kalkulator COMP dan reducer tekan tombol (PRD §6.2).
 *
 * Tombol SHIFT dan ALPHA diselesaikan di lapisan UI memakai `docs/keymap.json`, sehingga engine
 * hanya menerima token lapis yang sudah tepat, sama seperti berkas golden.
 */
class Calculator(var setup: Setup = Setup()) {

    private val editor = ExprEditor(setup.input)
    private var view = ResultView()
    private val vars = mutableMapOf<Key, Value>()

    /** Replay memory: ekspresi lama, terbaru di akhir (PRD §4.4). */
    private val history = mutableListOf<List<Node>>()
    private var historyIndex = -1

    private var result: Value? = null
    private var error: ErrorKind? = null
        // Posisi error lama tidak boleh menempel pada error berikutnya.
        set(value) {
            field = value
            errorAt = -1
        }

    /** Indeks simpul tingkat atas tempat error terjadi; -1 bila tak diketahui. */
    private var errorAt = -1
    private var ans: Value = ZERO
    private var preAns: Value = ZERO

    private var pending: Key? = null
    private var justEvaluated = false
    private var resultIsDms = false
    private var usedFraction = false

    /** Sedang menelusuri replay memory; ▲ dan ▼ berpindah riwayat, bukan menggerakkan kursor. */
    private var replaying = false

    /**
     * Warna badan yang dipilih lewat tombol OPTION: 0 hitam, 1 biru keabuan, 2 merah muda.
     * Disimpan di sini supaya ikut terbawa persistensi bersama setelan lain.
     */
    var skin: Int = 0
        private set

    /** Getaran tipis tiap tekan tombol; dipilih lewat menu OPTION, bawaannya mati (PRD §2). */
    var hapticEnabled: Boolean = false
        private set

    /**
     * Panduan bawaan sedang terbuka. Sengaja tidak ikut persistensi: membuka aplikasi selalu
     * mendarat di kalkulator, bukan di panduan.
     */
    var manualOpen: Boolean = false

    /** Menutup panduan bawaan; dipanggil tombol tutup dan tombol kembali Android. */
    fun closeManual() {
        manualOpen = false
    }

    /**
     * Teks yang menunggu disalin ke papan klip, atau null. Engine tidak boleh menyentuh Android,
     * jadi ia hanya menyiapkan teksnya dan lapisan UI yang menaruhnya lalu mengosongkan ini.
     */
    var clipboardOut: String? = null
        private set

    /** Pemakai meminta tempel; lapisan UI membaca papan klip lalu memanggil [paste]. */
    var pasteRequested: Boolean = false

    /** Baris mana yang ditekan lama, supaya butir Salin tahu apa yang harus disalin. */
    private var clipboardFromResult = false

    /** Butir CLR yang dipilih, menunggu dibenarkan dengan `=`. */
    private var clrChoice = 0

    /**
     * Membuka menu papan klip dari tekan lama pada layar. Sengaja bermenu, bukan langsung
     * menyalin, supaya Salin dan Tempel sama-sama terjangkau dari gerakan yang sama.
     */
    fun openClipboardMenu(fromResult: Boolean) {
        clipboardFromResult = fromResult
        menu = CalculatorMenu.CLIPBOARD
    }

    /** Salin dari menu OPTION, tanpa baris yang ditunjuk: hasil dulu, selain itu ekspresi. */
    fun requestCopy() {
        copyResult()
        if (clipboardOut == null) copyExpression()
    }

    /** Salin dari tekan lama: baris yang ditekan, dengan cadangan baris satunya bila kosong. */
    fun requestCopy(fromResult: Boolean) {
        if (fromResult) copyResult() else copyExpression()
        if (clipboardOut != null) return
        if (fromResult) copyExpression() else copyResult()
    }

    /** Menyalin baris hasil; tidak melakukan apa-apa bila layar belum memberi hasil. */
    fun copyResult() {
        clipboardOut = resultText().ifBlank { null }
    }

    /** Menyalin baris ekspresi apa adanya, dalam penulisan satu baris yang sama dengan golden. */
    fun copyExpression() {
        clipboardOut = exprText().ifBlank { null }
    }

    /** Dipanggil lapisan UI sesudah teksnya benar-benar masuk papan klip. */
    fun clipboardTaken() {
        clipboardOut = null
    }

    /**
     * Tiga tingkat CLR (manual hlm. 15, 26, dan 4). Setup mengembalikan mode dan seluruh Setup
     * ke bawaan; Memory mengosongkan Ans, memori M, dan variabel; All mengerjakan keduanya
     * sekaligus berikut matriks, vektor, dan data STAT.
     *
     * Tema dan getaran adalah setelan aplikasi, bukan setelan kalkulator, jadi tidak ikut
     * tersentuh CLR — untuk itu ada Reset di menu OPTION.
     */
    private fun applyClear(choice: Int) {
        when (choice) {
            0 -> {
                val keep = save()
                restore(CalculatorSnapshot(skin = keep.skin, hapticEnabled = keep.hapticEnabled))
                // Isi memori tidak ikut terhapus oleh CLR Setup (manual hlm. 15).
                restoreMemories(keep)
            }
            1 -> clearMemories()
            else -> {
                val keep = save()
                restore(CalculatorSnapshot(skin = keep.skin, hapticEnabled = keep.hapticEnabled))
            }
        }
    }

    /** Mengosongkan Ans, memori M, dan seluruh variabel (manual hlm. 26). */
    private fun clearMemories() {
        vars.clear()
        ans = Value.Num(CasioNumber.ZERO)
        preAns = Value.Num(CasioNumber.ZERO)
    }

    /** Mengembalikan isi memori sesudah CLR Setup, yang hanya membidik setelan. */
    private fun restoreMemories(keep: CalculatorSnapshot) {
        keep.variables.forEach { (name, text) ->
            Key.byName(name)?.let { vars[it] = Value.Num(CasioNumber.parse(text)) }
        }
        ans = Value.Num(CasioNumber.parse(keep.ans))
        preAns = Value.Num(CasioNumber.parse(keep.preAns))
        keep.matrices.forEach { (name, m) ->
            Key.byName(name)?.let {
                containers[it] = Value.Mat(Matrix(m.rows, m.cols, m.cells.map(CasioNumber::parse)))
            }
        }
        keep.vectors.forEach { (name, components) ->
            Key.byName(name)?.let {
                containers[it] = Value.Vec(Vector(components.map(CasioNumber::parse)))
            }
        }
        statData.type = keep.stat.type
        keep.stat.x.indices.forEach { i ->
            statData.add(
                CasioNumber.parse(keep.stat.x[i]),
                keep.stat.y.getOrNull(i)?.let(CasioNumber::parse) ?: CasioNumber.ZERO,
                keep.stat.freq.getOrNull(i)?.let(CasioNumber::parse) ?: CasioNumber.ONE,
            )
        }
    }

    /**
     * Kembali ke setelan pabrik: mode, Setup, tema, getaran, variabel, memori, matriks, vektor,
     * data STAT, riwayat, dan baris ekspresi semuanya kosong lagi.
     *
     * Dikerjakan dengan memulihkan snapshot bawaan, bukan mengosongkan medan satu per satu,
     * supaya medan baru yang ditambahkan kelak ikut ter-reset tanpa diingat-ingat.
     */
    fun factoryReset() {
        restore(CalculatorSnapshot())
    }

    /**
     * Menempel teks papan klip sebagai tekanan tombol. False bila teksnya tak terbaca; dalam hal
     * itu baris ekspresi tidak disentuh sama sekali.
     */
    fun paste(text: String): Boolean {
        pasteRequested = false
        val keys = Clipboard.keysFrom(text) ?: return false
        // Tempel menggantikan layar hasil, sama seperti mengetik angka baru sesudah `=`.
        if (menu != null) menu = null
        pressAll(keys)
        return true
    }

    /** Mode kalkulasi aktif (PRD §3.1). */
    var mode: Mode = Mode.COMP
        private set

    /** Baris kedua hasil, dipakai SOLVE untuk menampilkan L−R. */
    private var aux = ""

    /** Menu yang terbuka; tiap kali berganti, halamannya kembali ke awal. */
    private var menu: CalculatorMenu? = null
        set(value) {
            field = value
            menuPage = 0
        }

    private var menuPage = 0

    /**
     * Variabel tujuan STO yang baru saja diisi. Selama masih tampil, baris ekspresi ditutup
     * tanda panah dan nama variabelnya, misalnya 3+5→A, seperti unit fisik (manual hlm. 25).
     */
    private var storedTo: Key? = null

    /** Pilihan MthIO/LineIO milik pengguna; dipulihkan saat keluar dari mode Linear Display. */
    private var preferredInput: InputMode = setup.input

    private val statData = StatData()

    /** Statistics Editor: kisi berkolom dengan kursor bebas (manual hlm. 47). */
    private var statEditing = false
    private var statRow = 0
    private var statCol = 0

    private var tableRows: List<TableRow> = emptyList()
    private var tableIndex = 0

    /** Kolom tersorot pada layar tabel: 0 untuk X, 1 untuk F(X). */
    private var tableCol = 0
    private var tablePrompt = -1
    private val tableInputs = mutableListOf<CasioNumber>()
    private var tableBody: List<Node> = emptyList()

    /** Isi MatA sampai MatC dan VctA sampai VctC, ditambah MatAns dan VctAns. */
    private val containers = mutableMapOf<Key, Value>()

    /** Matrix dan Vector Editor: sasaran, ukuran, isi, dan letak kursor (manual hlm. 63). */
    private var editTarget: Key? = null
    private var editRows = 0
    private var editCols = 0
    private val editCells = mutableListOf<CasioNumber>()
    private var editRow = 0
    private var editCol = 0

    /** Matriks atau vektor yang sedang ditanya ukurannya lewat menu dimensi. */
    private var dimTarget: Key? = null

    private val editing: Boolean get() = editTarget != null

    /** Kursor pada layar MatAns dan VctAns. */
    private var ansRow = 0
    private var ansCol = 0

    private var eqnType: EqnSolver.Type? = null
    /** Isi Coefficient Editor, urut baris; panjangnya selalu sebanyak koefisien tipe ini. */
    private val eqnCells = mutableListOf<CasioNumber>()

    /** Sel yang sedang disorot pada Coefficient Editor, sebagai indeks datar. */
    private var eqnIndex = 0
    private var eqnRoots: List<Complex> = emptyList()
    private var rootIndex = 0

    /** Antrean prompt CALC: variabel yang nilainya ditanyakan satu per satu. */
    private var promptVars: List<Key> = emptyList()
    private var promptIndex = -1
    private val promptEditor = ExprEditor()

    /**
     * Sesi CALC/SOLVE hidup sejak tombolnya ditekan sampai AC (manual hlm. 38 dan 40). Selama
     * hidup, `=` menanyakan ulang nilai variabel dari awal alih-alih menghitung ulang biasa.
     */
    private var calcSession = false

    /** Variabel yang dicari SOLVE; null berarti sesi ini CALC, bukan SOLVE. */
    private var solveTarget: Key? = null

    /** Persamaannya saja, tanpa ekor `,{variabel}` yang menunjuk variabel solusi. */
    private var solveEquation: List<Node> = emptyList()

    /** Label baris hasil pada layar solusi SOLVE, misalnya `X=` (manual hlm. 40). */
    private var solveLabel = ""

    /** Layar `Continue:[=]` saat Newton belum bertemu akar (manual hlm. 41). */
    private var solveContinue = false

    /** Label pasangan hasil Pol dan Rec, misalnya `r=` dan `θ=`; null bila bukan keduanya. */
    private var coordLabels: Pair<String, String>? = null

    /** Baris ekspresi sebagai teks satu baris (dipakai golden test). */
    fun exprText(): String = ExprRenderer.render(exprNodes())

    /** Isi baris ekspresi tanpa kursor, berikut penutup →A bila STO baru saja dipakai. */
    fun exprNodes(): List<Node> {
        val body = exprBody()
        val target = storedTo ?: return body
        return body + KeyNode(Key.MEM_STO) + KeyNode(target)
    }

    /** Benar bila yang sedang diketik adalah isian prompt, bukan ekspresi utama. */
    private val usesPromptEditor: Boolean
        get() = prompting || eqnEntering || statEditing || tablePrompt >= 0 || editing

    /**
     * Judul di depan isian prompt: `A?` untuk CALC, `Solve for X` untuk tebakan awal SOLVE,
     * `Start?` dan kawannya untuk TABLE, nama koefisien untuk EQN. Editor kisi tidak berjudul.
     */
    private fun promptPrefix(): List<Node> = when {
        editing || statEditing -> emptyList()
        tablePrompt >= 0 -> listOf(NumNode(TABLE_PROMPTS[tablePrompt]))
        prompting -> listOf(NumNode(promptLabel()))
        else -> emptyList()
    }

    private fun exprBody(): List<Node> =
        promptPrefix() + if (usesPromptEditor) promptEditor.build() else editor.build()

    /** Pohon ekspresi berikut penanda kursor, untuk digambar oleh LCD. */
    fun exprTree(): List<Node> {
        storedTo?.let { return exprNodes() }
        // Judulnya ikut digambar; tanpa ini layar CALC/SOLVE/EQN/TABLE tampak kosong.
        return promptPrefix() +
            if (usesPromptEditor) promptEditor.buildWithCursor() else editor.buildWithCursor()
    }

    /**
     * Ketukan pada baris ekspresi. [steps] adalah jalur pada pohon [exprTree]: pasangan
     * (indeks simpul, indeks slot) berulang sedalam struktur yang ditembus, lalu satu bilangan
     * penutup yaitu simpul yang kursornya berada tepat sesudahnya.
     *
     * Ketukan di dalam pembilang pecahan, pangkat, batas integral, dan slot lain ikut terbaca,
     * sebab penggambar mencatat letak tiap anaknya.
     */
    fun tapExpr(steps: IntArray) {
        if (storedTo != null || steps.isEmpty()) return
        val active = if (usesPromptEditor) promptEditor else editor
        // Judul prompt seperti `A?` ikut digambar di depan, jadi indeks teratas digeser.
        val prefix = promptPrefix().size
        val first = steps[0] - prefix
        if (first < 0 && steps.size > 1) return
        val adjusted = steps.copyOf()
        adjusted[0] = if (first < 0) -1 else first
        justEvaluated = false
        replaying = false
        active.gotoDrawn(adjusted)
    }

    /**
     * Ketukan pada sel kisi. Angka yang sedang diketik disimpan lebih dulu lewat jalur yang sama
     * dengan tombol navigasi, sehingga sel tidak berpindah bila isinya salah tulis.
     */
    fun tapCell(row: Int, col: Int) {
        if (row < 0 || col < 0) return
        when {
            statEditing -> {
                val lastColumn =
                    (if (statData.type.paired) 1 else 0) + (if (setup.statFreq) 1 else 0)
                if (row >= statRows || col > lastColumn) return
                if (commitStatCell()) {
                    statRow = row
                    statCol = col
                }
            }
            editing -> {
                if (row >= editRows || col >= editCols) return
                if (commitCell()) {
                    editRow = row
                    editCol = col
                }
            }
            eqnEntering -> {
                val columns = eqnColumns(eqnType ?: return)
                val index = row * columns + col
                if (col >= columns || index !in eqnCells.indices) return
                if (storeCoefficient()) eqnIndex = index
            }
            error == null && tableRows.isNotEmpty() -> {
                if (row !in tableRows.indices || col > 1) return
                tableIndex = row
                tableCol = col
            }
            else -> {
                val grid = answerGrid() ?: return
                if (row !in grid.rows.indices || col !in grid.rows[row].indices) return
                ansRow = row
                ansCol = col
            }
        }
    }

    /**
     * Kisi yang sedang tampil: Statistics Editor, Matrix atau Vector Editor, atau layar
     * MatAns dan VctAns. Null bila layar sedang menampilkan ekspresi biasa.
     */
    fun gridView(): GridView? {
        if (statEditing) return statGrid()
        if (editing) return editorGrid()
        if (eqnEntering) return eqnGrid()
        if (error == null && tableRows.isNotEmpty()) return tableGrid()
        return answerGrid()
    }

    /**
     * Coefficient Editor (manual hlm. 60): kisi berkolom a, b, c, (d) dengan kurung siku.
     * Sistem linear bernomor baris karena punya lebih dari satu persamaan; kuadrat dan kubik
     * hanya satu baris sehingga nomornya tak berguna.
     */
    private fun eqnGrid(): GridView {
        val type = eqnType ?: return answerGrid()!!
        val columns = eqnColumns(type)
        val rows = eqnCells.chunked(columns).map { row -> row.map { cellText(it) } }
        return GridView(
            title = "",
            columns = List(columns) { ('a' + it).toString() },
            rows = rows,
            cursorRow = eqnIndex / columns,
            cursorCol = eqnIndex % columns,
            typed = promptEditor.buildWithCursor(),
            numbered = rows.size > 1,
            bracketed = true,
        )
    }

    private fun eqnColumns(type: EqnSolver.Type): Int = type.unknowns + 1

    private fun statGrid(): GridView {
        val columns = buildList {
            add("X")
            if (statData.type.paired) add("Y")
            if (setup.statFreq) add("FREQ")
        }
        val rows = (0 until statData.size).map { r ->
            columns.indices.map { c -> cellText(statData.get(r, statColumn(c))) }
        }
        return GridView(
            title = "",
            columns = columns,
            rows = rows + listOf(columns.map { "" }),
            cursorRow = statRow,
            cursorCol = statCol,
            typed = promptEditor.buildWithCursor(),
            numbered = true,
            bracketed = false,
        )
    }

    /** Kolom yang tampil bergantung jenis data; indeks layar dipetakan ke kolom sebenarnya. */
    private fun statColumn(shown: Int): Int = when {
        statData.type.paired -> shown
        shown == 0 -> 0
        else -> 2
    }

    private fun editorGrid(): GridView {
        val target = editTarget ?: return answerGrid()!!
        val rows = (0 until editRows).map { r ->
            (0 until editCols).map { c -> cellText(editCells[r * editCols + c]) }
        }
        return GridView(
            title = ExprRenderer.symbol(target).removePrefix("Mat").removePrefix("Vct"),
            columns = List(editCols) { "" },
            rows = rows,
            cursorRow = editRow,
            cursorCol = editCol,
            typed = promptEditor.buildWithCursor(),
            numbered = false,
            bracketed = true,
        )
    }

    /** Layar MatAns dan VctAns: hasil matriks atau vektor dengan kursor (manual hlm. 63). */
    private fun answerGrid(): GridView? {
        if (mode != Mode.MATRIX && mode != Mode.VECTOR) return null
        if (error != null) return null
        val value = result ?: return null
        val rows = when (value) {
            is Value.Mat -> (0 until value.value.rows).map { r ->
                (0 until value.value.cols).map { c -> cellText(value.value[r, c]) }
            }
            is Value.Vec -> listOf(value.value.components.map { cellText(it) })
            else -> return null
        }
        return GridView(
            title = "Ans",
            columns = List(rows[0].size) { "" },
            rows = rows,
            cursorRow = ansRow.coerceIn(0, rows.lastIndex),
            cursorCol = ansCol.coerceIn(0, rows[0].lastIndex),
            typed = emptyList(),
            numbered = false,
            bracketed = true,
        )
    }

    private fun cellText(n: CasioNumber): String = ResultFormatter.decimal(n, setup)

    /** Baris tabel yang sedang ditampilkan pada mode TABLE. */
    fun tableRow(): TableRow? = tableRows.getOrNull(tableIndex)

    /** Menu bernomor yang sedang terbuka, atau null (PRD §4.4). */
    fun menu(): CalculatorMenu? = menu

    /** Halaman menu yang sedang tampil, sudah dipotong sesuai [CalculatorMenu.perPage]. */
    fun menuView(): MenuView? {
        val open = menu ?: return null
        val shown = statItems(open) ?: open.items
        val from = menuPage * open.perPage
        val title = when (open) {
            CalculatorMenu.MATRIX_DIM ->
                ExprRenderer.symbol(dimTarget ?: Key.MAT_A) + "(m×n)  " + open.title
            CalculatorMenu.VECTOR_DIM ->
                ExprRenderer.symbol(dimTarget ?: Key.VCT_A) + "(m)  " + open.title
            CalculatorMenu.CLR_CONFIRM ->
                CalculatorMenu.CLR.items[clrChoice] + "   [=]:Yes  [AC]:No"
            else -> open.title
        }
        return MenuView(
            title = title,
            items = shown.drop(from).take(open.perPage),
            columns = open.columns,
            hasPrev = menuPage > 0,
            hasNext = from + open.perPage < shown.size,
            footer = open.footer,
        )
    }

    /**
     * Butir menu STAT yang berlaku sekarang, atau null untuk menu yang isinya tetap.
     *
     * Manual hlm. 49–53: butir kelima adalah Distr pada 1-VAR dan Reg pada jenis berpasangan;
     * di dalam Statistics Editor butir ketiga menjadi Edit; dan submenu Sum, Var, Reg, serta
     * MinMax memanjang bila jenisnya berpasangan.
     */
    private fun statItems(open: CalculatorMenu): List<String>? {
        val paired = statData.type.paired
        return when (open) {
            CalculatorMenu.STAT_MAIN, CalculatorMenu.STAT_MAIN_EDIT -> listOf(
                "Type",
                "Data",
                if (open == CalculatorMenu.STAT_MAIN_EDIT) "Edit" else "Sum",
                "Var",
                if (paired) "Reg" else "Distr",
                "MinMax",
            )
            CalculatorMenu.STAT_SUM ->
                if (paired) open.items else listOf("Σx²", "Σx")
            CalculatorMenu.STAT_VAR ->
                if (paired) open.items else open.items.take(4)
            CalculatorMenu.STAT_REG ->
                if (statData.type == StatType.QUADRATIC) open.items
                else listOf("A", "B", "r", "x̂", "ŷ")
            CalculatorMenu.STAT_MINMAX ->
                if (paired) open.items else open.items.take(2)
            else -> null
        }
    }

    private val eqnEntering: Boolean get() = eqnType != null && eqnRoots.isEmpty()

    /**
     * Baris hasil sebagai pohon simpul untuk digambar Natural Display. Hasil yang bukan angka
     * tunggal, misalnya pesan error dan baris tabel, tetap berupa satu teks.
     */
    fun resultTree(): List<Node> {
        val text = resultText()
        if (text.isEmpty()) return emptyList()
        // Akar EQN digambar dua dimensi juga: manual hlm. 62 Contoh 3 menumpuk pecahan dan
        // memakai tanda akar sungguhan. Labelnya X1= digambar terpisah rata kiri.
        if (error == null && eqnRoots.isNotEmpty()) {
            return ResultFormatter.tree(eqnRootValue(eqnRoots[rootIndex]), setup, view)
        }
        val value = result
        if (error != null || value == null || resultIsDms || coordLabels != null ||
            tableRows.isNotEmpty() || solveLabel.isNotEmpty() || solveContinue
        ) {
            // Labelnya digambar terpisah rata kiri, jadi pohon hasil hanya berisi angkanya.
            return listOf(NumNode(text.removePrefix(solveLabel)))
        }
        return ResultFormatter.tree(value, setup, view)
    }

    /**
     * Layar tanya nomor dua digit CONST dan CONV (manual hlm. 42 dan 44): judul, baris
     * `Number 01~40?`, lalu kotak isian. Null bila layar itu tidak sedang terbuka.
     */
    fun twoDigitPrompt(): List<String>? {
        val kind = twoDigitKind ?: return null
        val title = if (kind == TwoDigit.CONST) "CONSTANT" else "CONVERSION"
        return listOf(title, "Number 01~40?", "[" + twoDigitBuffer.toString().padEnd(2, '_') + "]")
    }

    /** Baris tambahan di bawah hasil; kosong kecuali setelah SOLVE. */
    fun auxText(): String {
        // Linear Display menaruh nilai keduanya di baris bawah (manual hlm. 35).
        coordLabels?.let { (_, second) ->
            if (setup.input == InputMode.LINE_IO) {
                return second + ResultFormatter.format(coordValue(Key.VAR_Y), setup, view)
            }
        }
        return aux
    }

    private fun coordValue(key: Key): Value =
        ResultFormatter.scalar((vars[key] ?: ZERO).number)

    /** `Pol(` memberi label r dan θ, `Rec(` memberi X dan Y (manual hlm. 35). */
    private fun coordinateLabels(ast: Ast): Pair<String, String>? = when {
        ast is Fn && ast.key == Key.FN_POL -> "r=" to "θ="
        ast is Fn && ast.key == Key.FN_REC -> "X=" to "Y="
        else -> null
    }

    /**
     * Bagian kiri baris tambahan, misalnya `L-R=`. Unit fisik menulis labelnya rata kiri dan
     * angkanya rata kanan pada baris yang sama (manual hlm. 40); kosong berarti tanpa label.
     */
    fun auxLabelText(): String = if (aux.startsWith(L_MINUS_R)) L_MINUS_R else ""

    /** Label baris hasil, misalnya `X=` pada layar solusi SOLVE; kosong berarti tanpa label. */
    fun resultLabelText(): String =
        if (error == null && eqnRoots.isNotEmpty()) rootLabel() else solveLabel

    /** Pesan error yang sedang tampil, kosong bila tidak ada (manual hlm. 72). */
    fun errorText(): String = error?.display ?: ""

    private val prompting: Boolean get() = promptIndex >= 0

    private fun promptLabel(): String {
        val variable = promptVars[promptIndex]
        // Prompt terakhir SOLVE adalah tebakan awal, dan judulnya `Solve for X` (manual hlm. 40).
        if (variable == solveTarget) return "Solve for " + ExprRenderer.symbol(variable)
        return ExprRenderer.symbol(variable) + "?"
    }

    /** Baris hasil pada LCD; string kosong bila belum ada hasil. */
    fun resultText(): String {
        error?.let { return it.display }
        // Pol dan Rec memberi dua nilai sekaligus (manual hlm. 35): satu baris pada Natural
        // Display, dua baris pada Linear Display.
        coordLabels?.let { (first, second) ->
            val head = first + ResultFormatter.format(coordValue(Key.VAR_X), setup, view)
            if (setup.input == InputMode.LINE_IO) return head
            return head + "," + second + ResultFormatter.format(coordValue(Key.VAR_Y), setup, view)
        }
        if (solveContinue) return "Continue:[=]"
        if (solveLabel.isNotEmpty()) {
            // Manual hlm. 40: akar SOLVE selalu tampil desimal, berawalan nama variabelnya.
            val value = result ?: return ""
            return solveLabel + ResultFormatter.decimal(value.number, setup)
        }
        if (tableRows.isNotEmpty()) {
            val row = tableRows[tableIndex]
            return "X=" + ResultFormatter.decimal(row.x, setup) +
                "  f(X)=" + ResultFormatter.decimal(row.value, setup)
        }
        if (eqnRoots.isNotEmpty()) {
            val root = eqnRoots[rootIndex]
            return rootLabel() + ResultFormatter.format(eqnRootValue(root), setup)
        }
        val value = result ?: return ""
        if (resultIsDms && !view.decimal) return dmsText(value.number)
        return ResultFormatter.format(value, setup, view)
    }

    fun indicators() = Indicators(
        memory = vars[Key.VAR_M]?.let { scalarText(it) !in ZERO_TEXTS } == true,
        store = pending == Key.MEM_STO,
        recall = pending == Key.MEM_RCL,
        angle = setup.angle,
        fix = setup.format is DisplayFormat.Fix,
        sci = setup.format is DisplayFormat.Sci,
        math = setup.input == InputMode.MATH_IO,
        insert = editor.overwrite || editor.wrapping,
        replayUp = history.isNotEmpty() && historyIndex != 0,
        replayDown = historyIndex in 0 until history.lastIndex,
        mode = mode,
        base = setup.base,
    )

    fun press(key: Key) {
        editor.mode = setup.input
        if (key == Key.MOD_SHIFT || key == Key.MOD_ALPHA) return
        if (error != null) {
            if (key !in RECOVERS_FROM_ERROR) return
            val at = errorAt
            error = null
            // Manual hlm. 72: ◄ dan ► kembali ke ekspresi dengan kursor di tempat error terjadi.
            if (key != Key.EDIT_AC) {
                editor.gotoAfterBuilt(at)
                return
            }
            // AC diteruskan ke penanganan AC milik layar yang sedang aktif, supaya pesan error
            // hilang sekaligus isinya dibersihkan seperti kalkulator fisik.
        }
        menu?.let {
            menuPress(it, key)
            return
        }
        // MODE, SETUP, dan OPTION selalu berlaku, juga di dalam editor kisi dan layar prompt;
        // tanpa ini tombolnya tertelan sebagai isian sel.
        when (key) {
            Key.MENU_MODE -> { menu = CalculatorMenu.MODE; return }
            Key.MENU_SETUP -> { menu = CalculatorMenu.SETUP; return }
            Key.MENU_OPTION -> { menu = CalculatorMenu.OPTION; return }
            Key.MENU_CLR -> { menu = CalculatorMenu.CLR; return }
            else -> Unit
        }
        if (twoDigitKind != null) {
            twoDigitPress(key)
            return
        }
        if (eqnEntering) {
            eqnPress(key)
            return
        }
        if (statEditing) {
            statPress(key)
            return
        }
        if (tablePrompt >= 0) {
            tablePress(key)
            return
        }
        if (editing) {
            editPress(key)
            return
        }
        if (prompting) {
            promptPress(key)
            return
        }
        // Sesi CALC/SOLVE hanya bertahan selama `=` dan CALC ditekan; tombol lain mengakhirinya.
        if (calcSession && key != Key.EXEC_EQ && key != Key.FN_CALC) endCalcSession()
        when {
            // Pada layar solusi EQN, AC kembali ke Coefficient Editor dan `=` menelusuri akar
            // berikutnya; koefisiennya tetap utuh (manual hlm. 61).
            key == Key.EDIT_AC && eqnRoots.isNotEmpty() -> backToCoefficients()
            key == Key.EDIT_AC && tableRows.isNotEmpty() -> backToTableFunction()
            key == Key.EXEC_EQ && eqnRoots.isNotEmpty() -> browseRoots(key)
            key == Key.EDIT_AC -> clear()
            key == Key.EXEC_EQ && solveContinue -> runSolve()
            key == Key.EXEC_EQ && calcSession -> restartPrompts()
            key == Key.FN_CALC && calcSession && solveTarget == null -> restartPrompts()
            key == Key.MENU_BASE -> if (mode == Mode.BASE) menu = CalculatorMenu.BASE_LOGIC
            key == Key.MENU_HYP -> menu = CalculatorMenu.HYP
            key == Key.MENU_DRG -> menu = CalculatorMenu.DRG
            // Manual hlm. 42 dan 44: CONST tak berlaku di BASE-N, CONV juga tidak di TABLE.
            key == Key.MENU_CONST -> if (mode != Mode.BASE) startTwoDigit(TwoDigit.CONST)
            key == Key.MENU_CONV ->
                if (mode != Mode.BASE && mode != Mode.TABLE) startTwoDigit(TwoDigit.CONV)
            key == Key.MENU_CMPLX -> if (mode == Mode.CMPLX) menu = CalculatorMenu.CMPLX_MAIN
            key == Key.MENU_STAT -> if (mode == Mode.STAT) menu = CalculatorMenu.STAT_MAIN
            key == Key.MENU_MATRIX -> if (mode == Mode.MATRIX) menu = CalculatorMenu.MATRIX_MAIN
            key == Key.MENU_VECTOR -> if (mode == Mode.VECTOR) menu = CalculatorMenu.VECTOR_MAIN
            key == Key.BASE_DEC -> setBase(NumberBase.DEC)
            key == Key.BASE_HEX -> setBase(NumberBase.HEX)
            key == Key.BASE_BIN -> setBase(NumberBase.BIN)
            key == Key.BASE_OCT -> setBase(NumberBase.OCT)
            key == Key.FN_SOLVE -> startSolve()
            key == Key.FN_CALC -> startCalc()
            // Manual hlm. 65: di mode MATRIX dan VECTOR, sesudah STO tombol (−), °''' dan hyp
            // menunjuk wadah A, B, dan C sebagai tujuan salinan.
            pending == Key.MEM_STO && key in CONTAINER_DESTINATIONS -> storeContainer(key)
            key == Key.MEM_STO || key == Key.MEM_RCL -> pending = key
            key == Key.EXEC_EQ -> evaluate()
            key == Key.FMT_SD -> toggleDecimal()
            key == Key.FMT_ABC_DC -> view = view.copy(mixed = !view.mixed)
            key == Key.FMT_ENG -> shiftEngineering(-1)
            key == Key.FMT_ENG_BACK -> shiftEngineering(+1)
            key == Key.MEM_MPLUS -> accumulateMemory(add = true)
            key == Key.MEM_MMINUS -> accumulateMemory(add = false)
            key in Key.VARIABLES -> variable(key)
            key == Key.POST_DMS && justEvaluated -> toggleDms()
            // Selalu ditangkap, walau tak ada hasil berbentuk DMS: kalau dibiarkan jatuh
            // ke input biasa, tombolnya mengetik nama tokennya sendiri ke baris ekspresi.
            key == Key.FN_DMS_BACK -> dmsToDecimal()
            key == Key.NAV_UP || key == Key.NAV_DOWN -> when {
                eqnRoots.isNotEmpty() -> browseRoots(key)
                tableRows.isNotEmpty() -> browseTable(key)
                else -> navigateVertically(key)
            }
            key == Key.NAV_LEFT || key == Key.NAV_RIGHT ->
                if (tableRows.isNotEmpty()) browseTable(key) else navigateHorizontally(key)
            else -> input(key)
        }
    }

    fun pressAll(keys: List<Key>) = keys.forEach { press(it) }

    // ------------------------------------------------------------------ input

    private fun input(key: Key) {
        replaying = false
        storedTo = null
        if (justEvaluated) {
            justEvaluated = false
            if (key in CONTINUES_FROM_ANS) {
                // Manual hlm. 64: dari layar MatAns, operator langsung pindah ke layar hitung
                // dengan MatAns sebagai operand pertama.
                editor.load(listOf(KeyNode(answerKey())))
            } else {
                editor.clear()
            }
        }
        editor.press(key)
    }

    private fun clear() {
        editor.clear()
        storedTo = null
        editTarget = null
        statEditing = false
        ansRow = 0
        ansCol = 0
        error = null
        result = null
        view = ResultView()
        pending = null
        justEvaluated = false
        resultIsDms = false
        historyIndex = -1
        replaying = false
        aux = ""
        coordLabels = null
        promptVars = emptyList()
        promptIndex = -1
        promptEditor.clear()
        endCalcSession()
        eqnRoots = emptyList()
        rootIndex = 0
        tableRows = emptyList()
        tableIndex = 0
        // Tanpa syarat, sebab selectMode memanggil clear() sesudah mode diganti: syarat apa pun
        // atas `mode` menguji mode tujuan, bukan mode yang ditinggalkan, sehingga layar EQN dan
        // layar tanya TABLE ikut terbawa ke mode baru.
        tablePrompt = -1
        eqnType = null
        eqnCells.clear()
        eqnIndex = 0
    }

    // ------------------------------------------------------------------ mode, menu, EQN

    /** Memilih mode kalkulasi; isi layar dan hasil lama dibuang seperti kalkulator fisik. */
    fun selectMode(target: Mode) {
        mode = target
        clear()
        // Riwayat perhitungan hilang saat pindah mode (manual hlm. 24).
        history.clear()
        if (target != Mode.BASE) setup = setup.copy(base = NumberBase.DEC)
        // Mode STAT, BASE-N, MATRIX, dan VECTOR selalu Linear Display (manual hlm. 12).
        setup = setup.copy(input = if (target in LINEAR_ONLY) InputMode.LINE_IO else preferredInput)
        menu = when (target) {
            Mode.EQN -> CalculatorMenu.EQN_TYPE
            Mode.STAT -> CalculatorMenu.STAT_TYPE
            else -> null
        }
        if (target == Mode.TABLE) startTable()
        if (target == Mode.MATRIX) menu = CalculatorMenu.MATRIX_NAME
        if (target == Mode.VECTOR) menu = CalculatorMenu.VECTOR_NAME
        syncHexDigits()
    }

    /** Ganti basis hanya mengubah tampilan, nilai tersimpan tetap sama (PRD §3.1). */
    private fun setBase(base: NumberBase) {
        if (mode != Mode.BASE) return
        setup = setup.copy(base = base)
        syncHexDigits()
    }

    private fun syncHexDigits() {
        val hex = mode == Mode.BASE && setup.base == NumberBase.HEX
        editor.hexDigits = hex
        promptEditor.hexDigits = hex
    }

    private fun menuPress(open: CalculatorMenu, key: Key) {
        if (key == Key.EDIT_AC) {
            menu = null
            return
        }
        val shown = statItems(open) ?: open.items
        val from = menuPage * open.perPage
        if (key == Key.NAV_DOWN && from + open.perPage < shown.size) {
            menuPage++
            return
        }
        if (key == Key.NAV_UP && menuPage > 0) {
            menuPage--
            return
        }
        // Layar pembenaran CLR dibenarkan dengan `=`, bukan butir bernomor (manual hlm. 15).
        if (open == CalculatorMenu.CLR_CONFIRM) {
            if (key == Key.EXEC_EQ) {
                menu = null
                applyClear(clrChoice)
            }
            return
        }
        if (!key.isDigit) return
        val digit = key.digitValue
        // Fix dan Sci meminta satu angka langsung, bukan butir bernomor (manual hlm. 12).
        if (open == CalculatorMenu.SETUP_FIX) {
            menu = null
            setup = setup.copy(format = DisplayFormat.Fix(digit))
            return
        }
        if (open == CalculatorMenu.SETUP_SCI) {
            menu = null
            setup = setup.copy(format = DisplayFormat.Sci(if (digit == 0) 10 else digit))
            return
        }
        if (digit == 0) return
        val choice = from + digit - 1
        if (choice !in shown.indices) return
        menu = null
        when (open) {
            CalculatorMenu.MODE -> selectMode(Mode.entries[choice])
            CalculatorMenu.EQN_TYPE -> startEqn(EqnSolver.Type.entries[choice])
            CalculatorMenu.STAT_TYPE -> startStat(StatType.entries[choice])
            // Nomor butir mengikuti manual hlm. 49: butir ketiga Edit di dalam editor dan Sum di
            // layar hitung, butir kelima Distr pada 1-VAR dan Reg pada jenis berpasangan.
            CalculatorMenu.STAT_MAIN, CalculatorMenu.STAT_MAIN_EDIT -> when (choice) {
                0 -> menu = CalculatorMenu.STAT_TYPE
                1 -> openStatEditor()
                2 ->
                    menu =
                        if (open == CalculatorMenu.STAT_MAIN_EDIT) CalculatorMenu.STAT_EDIT
                        else CalculatorMenu.STAT_SUM
                3 -> menu = CalculatorMenu.STAT_VAR
                4 ->
                    menu =
                        if (statData.type.paired) CalculatorMenu.STAT_REG
                        else CalculatorMenu.STAT_DISTR
                else -> menu = CalculatorMenu.STAT_MINMAX
            }
            // Ins menyisipkan baris kosong di kursor, Del-A mengosongkan seluruh data.
            CalculatorMenu.STAT_EDIT -> {
                if (choice == 0) {
                    statData.insert(statRow)
                } else {
                    statData.clear()
                    statRow = 0
                    statCol = 0
                }
                statEditing = true
                promptEditor.clear()
            }
            CalculatorMenu.STAT_SUM -> showStat(SUM_QUERIES[choice])
            CalculatorMenu.STAT_VAR -> showStat(VAR_QUERIES[choice])
            CalculatorMenu.STAT_REG -> selectRegItem(choice)
            CalculatorMenu.STAT_MINMAX -> showStat(MINMAX_QUERIES[choice])
            CalculatorMenu.STAT_DISTR -> input(DISTR_KEYS[choice])
            CalculatorMenu.MATRIX_MAIN -> when (choice) {
                0 -> menu = CalculatorMenu.MATRIX_NAME
                1 -> menu = CalculatorMenu.MATRIX_EDIT
                6 -> input(Key.FN_DET)
                7 -> input(Key.FN_TRN)
                else -> input(MATRIX_REFS[choice - 2])
            }
            CalculatorMenu.MATRIX_NAME -> startDim(MATRIX_REFS[choice])
            CalculatorMenu.MATRIX_EDIT -> editExisting(MATRIX_REFS[choice])
            CalculatorMenu.MATRIX_DIM -> {
                val (rows, cols) = MATRIX_DIMS[choice]
                startEditor(dimTarget ?: Key.MAT_A, rows, cols)
            }
            CalculatorMenu.VECTOR_MAIN -> when (choice) {
                0 -> menu = CalculatorMenu.VECTOR_NAME
                1 -> menu = CalculatorMenu.VECTOR_EDIT
                6 -> input(Key.FN_DOT)
                else -> input(VECTOR_REFS[choice - 2])
            }
            CalculatorMenu.VECTOR_NAME -> startDim(VECTOR_REFS[choice])
            CalculatorMenu.VECTOR_EDIT -> editExisting(VECTOR_REFS[choice])
            CalculatorMenu.VECTOR_DIM -> startEditor(dimTarget ?: Key.VCT_A, 1, 3 - choice)
            // Nomor butirnya dieja satu per satu: cabang tangkap-semua pernah membuat
            // setiap pilihan baru diam-diam membuka panduan.
            CalculatorMenu.OPTION -> when (choice) {
                0 -> menu = CalculatorMenu.OPTION_SKIN
                1 -> menu = CalculatorMenu.OPTION_HAPTIC
                // Panduan bawaan dan papan klip adalah urusan lapisan UI, bukan menu kalkulator;
                // engine hanya menyalakan benderanya.
                2 -> manualOpen = true
                3 -> requestCopy()
                4 -> pasteRequested = true
                5 -> menu = CalculatorMenu.OPTION_RESET
                else -> Unit
            }
            CalculatorMenu.OPTION_RESET -> if (choice == 0) factoryReset()
            CalculatorMenu.CLIPBOARD ->
                if (choice == 0) requestCopy(clipboardFromResult) else pasteRequested = true
            CalculatorMenu.CLR -> {
                clrChoice = choice
                menu = CalculatorMenu.CLR_CONFIRM
            }
            CalculatorMenu.OPTION_SKIN -> skin = choice
            CalculatorMenu.OPTION_HAPTIC -> hapticEnabled = choice == 1
            CalculatorMenu.SETUP -> applySetup(choice)
            CalculatorMenu.BASE_LOGIC -> input(BASE_LOGIC_KEYS[choice])
            CalculatorMenu.HYP -> input(HYP_KEYS[choice])
            CalculatorMenu.CMPLX_MAIN -> input(CMPLX_KEYS[choice])
            CalculatorMenu.DRG -> input(DRG_KEYS[choice])
            CalculatorMenu.SETUP_FIX, CalculatorMenu.SETUP_SCI -> Unit
            CalculatorMenu.SETUP_NORM -> setup = setup.copy(
                format = if (choice == 0) DisplayFormat.Norm1 else DisplayFormat.Norm2
            )
            CalculatorMenu.SETUP_CMPLX -> setup = setup.copy(
                complexForm = if (choice == 0) ComplexForm.RECTANGULAR else ComplexForm.POLAR
            )
            // Mengubah Stat Format menghapus data STAT, seperti manual hlm. 48.
            CalculatorMenu.SETUP_STAT -> {
                setup = setup.copy(statFreq = choice == 0)
                statData.clear()
            }
        }
    }

    private fun applySetup(choice: Int) {
        when (choice) {
            0 -> {
                preferredInput = InputMode.MATH_IO
                if (mode !in LINEAR_ONLY) setup = setup.copy(input = InputMode.MATH_IO)
            }
            1 -> {
                preferredInput = InputMode.LINE_IO
                setup = setup.copy(input = InputMode.LINE_IO)
            }
            2 -> setup = setup.copy(angle = AngleUnit.DEG)
            3 -> setup = setup.copy(angle = AngleUnit.RAD)
            4 -> setup = setup.copy(angle = AngleUnit.GRA)
            5 -> menu = CalculatorMenu.SETUP_FIX
            6 -> menu = CalculatorMenu.SETUP_SCI
            7 -> menu = CalculatorMenu.SETUP_NORM
            8 -> setup = setup.copy(mixedFraction = true)
            9 -> setup = setup.copy(mixedFraction = false)
            10 -> menu = CalculatorMenu.SETUP_CMPLX
            else -> menu = CalculatorMenu.SETUP_STAT
        }
        editor.mode = setup.input
    }

    // ---------------------------------------------------------------- CONST dan CONV

    private enum class TwoDigit { CONST, CONV }

    private var twoDigitKind: TwoDigit? = null
    private val twoDigitBuffer = StringBuilder()

    /** CONST dan CONV dipilih dengan mengetik nomor dua digit, seperti kalkulator fisik. */
    private fun startTwoDigit(kind: TwoDigit) {
        twoDigitKind = kind
        twoDigitBuffer.clear()
    }

    private fun twoDigitPress(key: Key) {
        if (key == Key.EDIT_AC) {
            twoDigitKind = null
            twoDigitBuffer.clear()
            return
        }
        if (!key.isDigit) return
        twoDigitBuffer.append(key.digitValue)
        if (twoDigitBuffer.length < 2) return
        val number = twoDigitBuffer.toString().toInt()
        val kind = twoDigitKind
        twoDigitKind = null
        twoDigitBuffer.clear()
        // Keduanya masuk ke ekspresi sebagai simbol, bukan langsung jadi angka atau hasil:
        // manual hlm. 42 menampilkan `Co` dan hlm. 44 menampilkan `5cm▸in` di baris ekspresi.
        when (kind) {
            TwoDigit.CONST ->
                if (ScientificConstants.byNumber(number) != null) {
                    insertTable(TableKind.CONST, number)
                }
            TwoDigit.CONV ->
                if (UnitConversions.byNumber(number) != null) {
                    insertTable(TableKind.CONV, number)
                }
            null -> Unit
        }
    }

    private fun insertTable(kind: TableKind, number: Int) {
        replaying = false
        storedTo = null
        // Konstanta memulai ekspresi baru sesudah hasil; konversi melanjutkan dari Ans, sebab
        // `100 = SHIFT 8 22` pada unit fisik mengonversi hasil yang sedang tampil.
        if (justEvaluated) {
            justEvaluated = false
            if (kind == TableKind.CONV) editor.load(listOf(KeyNode(Key.MEM_ANS)))
            else editor.clear()
        }
        editor.insertNode(TableNode(kind, number))
    }

    // ------------------------------------------------------------------ simpan dan pulihkan

    /**
     * Nilai sebagai teks desimal untuk disimpan. Matriks dan vektor tidak punya nilai tunggal,
     * dan bilangan kompleks hanya disimpan bagian nyatanya; keduanya tetap utuh di containers
     * dan di layar, jadi penyimpanan tidak boleh gagal karenanya.
     */
    private fun scalarText(value: Value): String = when (value) {
        is Value.Mat, is Value.Vec -> "0"
        is Value.Cx -> value.value.re.toString()
        else -> value.number.toString()
    }

    /** Menyalin status ke rekaman yang dapat diserialisasi (PRD §6.3). */
    fun save(): CalculatorSnapshot = CalculatorSnapshot(
        mode = mode,
        skin = skin,
        setup = SetupSnapshot.from(setup),
        expression = editor.build(),
        history = history.toList(),
        // Hanya hasil skalar yang disimpan; matriks dan vektor sudah ikut di MatAns dan VctAns.
        result = (result as? Value.Num ?: result as? Value.Ex)?.number?.toString() ?: "",
        variables = vars.mapKeys { it.key.name }.mapValues { scalarText(it.value) },
        ans = scalarText(ans),
        preAns = scalarText(preAns),
        matrices = containers.filterValues { it is Value.Mat }
            .mapKeys { it.key.name }
            .mapValues { entry ->
                val m = (entry.value as Value.Mat).value
                MatrixSnapshot(m.rows, m.cols, m.cells.map { it.toString() })
            },
        vectors = containers.filterValues { it is Value.Vec }
            .mapKeys { it.key.name }
            .mapValues { entry ->
                (entry.value as Value.Vec).value.components.map { it.toString() }
            },
        stat = StatSnapshot(
            type = statData.type,
            x = statData.x.map { it.toString() },
            y = statData.y.map { it.toString() },
            freq = statData.freq.map { it.toString() },
        ),
        hapticEnabled = hapticEnabled,
    )

    /** Memulihkan status dari rekaman; nama yang tidak dikenali diabaikan. */
    fun restore(snapshot: CalculatorSnapshot) {
        clear()
        mode = snapshot.mode
        skin = snapshot.skin
        setup = snapshot.setup.toSetup()
        hapticEnabled = snapshot.hapticEnabled
        syncHexDigits()
        editor.mode = setup.input
        editor.load(snapshot.expression)
        history.clear()
        history.addAll(snapshot.history)
        vars.clear()
        snapshot.variables.forEach { (name, text) ->
            Key.byName(name)?.let { vars[it] = Value.Num(CasioNumber.parse(text)) }
        }
        ans = Value.Num(CasioNumber.parse(snapshot.ans))
        preAns = Value.Num(CasioNumber.parse(snapshot.preAns))
        if (snapshot.result.isNotEmpty()) {
            // Layar kembali persis seperti ditinggalkan: hasil ikut tampil, dan tekanan angka
            // berikutnya memulai ekspresi baru, bukan menyambung ekspresi lama (PRD §2).
            result = Value.Num(CasioNumber.parse(snapshot.result))
            // Tetapi hanya bila ekspresi yang tampil memang yang baru dihitung. Ekspresi yang
            // sedang diketik sesudah hasil belum masuk riwayat; menganggapnya baru dihitung
            // membuat ◄ menggantinya dengan entri riwayat terakhir dan ketikannya hilang.
            justEvaluated = snapshot.expression == history.lastOrNull()
        }
        containers.clear()
        snapshot.matrices.forEach { (name, m) ->
            Key.byName(name)?.let {
                containers[it] = Value.Mat(Matrix(m.rows, m.cols, m.cells.map(CasioNumber::parse)))
            }
        }
        snapshot.vectors.forEach { (name, components) ->
            Key.byName(name)?.let {
                containers[it] = Value.Vec(Vector(components.map(CasioNumber::parse)))
            }
        }
        statData.type = snapshot.stat.type
        statData.clear()
        snapshot.stat.x.indices.forEach { i ->
            statData.add(
                CasioNumber.parse(snapshot.stat.x[i]),
                snapshot.stat.y.getOrNull(i)?.let(CasioNumber::parse) ?: CasioNumber.ZERO,
                snapshot.stat.freq.getOrNull(i)?.let(CasioNumber::parse) ?: CasioNumber.ONE,
            )
        }
    }

    // ------------------------------------------------------------------ MATRIX dan VECTOR

    /** Menanyakan ukuran lewat menu dimensi, persis unit fisik (manual hlm. 63). */
    private fun startDim(target: Key) {
        dimTarget = target
        menu = if (target in VECTOR_REFS) CalculatorMenu.VECTOR_DIM else CalculatorMenu.MATRIX_DIM
    }

    /** Membuka editor berisi nol pada ukuran yang dipilih. */
    private fun startEditor(target: Key, rows: Int, cols: Int) {
        openEditor(target, rows, cols, List(rows * cols) { CasioNumber.ZERO })
        storeEdited()
    }

    /** Membuka editor berisi data yang sudah ada; belum ada isi berarti Dimension ERROR. */
    private fun editExisting(target: Key) {
        when (val value = containers[target]) {
            is Value.Mat -> openEditor(target, value.value.rows, value.value.cols, value.value.cells)
            is Value.Vec -> openEditor(target, 1, value.value.components.size, value.value.components)
            // Manual hlm. 63 langkah 4: Data pada matriks yang belum berukuran menanyakan
            // ukurannya lebih dulu, bukan menolak dengan Dimension ERROR.
            else -> startDim(target)
        }
    }

    private fun openEditor(target: Key, rows: Int, cols: Int, cells: List<CasioNumber>) {
        editTarget = target
        editRows = rows
        editCols = cols
        editCells.clear()
        editCells.addAll(cells)
        editRow = 0
        editCol = 0
        promptEditor.clear()
        error = null
    }

    private fun editPress(key: Key) {
        when (key) {
            Key.EDIT_AC -> closeEditor()
            Key.MENU_MATRIX -> {
                closeEditor()
                menu = CalculatorMenu.MATRIX_MAIN
            }
            Key.MENU_VECTOR -> {
                closeEditor()
                menu = CalculatorMenu.VECTOR_MAIN
            }
            Key.EXEC_EQ -> if (commitCell()) advanceCell()
            Key.NAV_UP, Key.NAV_DOWN, Key.NAV_LEFT, Key.NAV_RIGHT ->
                if (commitCell()) moveEditor(key)
            else -> promptEditor.press(key)
        }
    }

    private fun closeEditor() {
        editTarget = null
        promptEditor.clear()
    }

    /** Menyimpan angka yang diketik ke sel terpilih; false bila isinya salah tulis. */
    private fun commitCell(): Boolean {
        val typed = promptEditor.build()
        if (typed.isEmpty()) return true
        val value = try {
            Evaluator.eval(Parser.parse(typed), context()).number
        } catch (e: CalcError) {
            error = e.kind
            return false
        }
        promptEditor.clear()
        editCells[editRow * editCols + editCol] = value
        storeEdited()
        return true
    }

    /** Setelah `=` kursor lanjut ke sel berikutnya: ke kanan, lalu turun satu baris. */
    private fun advanceCell() {
        if (editCol + 1 < editCols) {
            editCol++
        } else if (editRow + 1 < editRows) {
            editCol = 0
            editRow++
        }
    }

    private fun moveEditor(key: Key) {
        when (key) {
            Key.NAV_UP -> if (editRow > 0) editRow--
            Key.NAV_DOWN -> if (editRow + 1 < editRows) editRow++
            Key.NAV_LEFT -> if (editCol > 0) editCol--
            else -> if (editCol + 1 < editCols) editCol++
        }
    }

    /** Isi editor selalu tersimpan ke variabelnya, jadi tidak ada langkah simpan terpisah. */
    private fun storeEdited() {
        val target = editTarget ?: return
        try {
            containers[target] = if (target in VECTOR_REFS) {
                Value.Vec(Vector(editCells.toList()))
            } else {
                Value.Mat(Matrix(editRows, editCols, editCells.toList()))
            }
            result = containers[target]
            error = null
        } catch (e: CalcError) {
            error = e.kind
        }
    }

    // ------------------------------------------------------------------ STAT

    private fun startStat(type: StatType) {
        // Manual hlm. 49: data hanya terhapus saat berpindah antara 1-VAR dan jenis berpasangan,
        // bukan saat berganti model regresi. Contoh 3 hlm. 55 mengganti A+BX menjadi ln X lalu
        // menghitung ulang dengan data yang sama.
        if (statData.type.paired != type.paired) statData.clear()
        statData.type = type
        openStatEditor()
    }

    private fun openStatEditor() {
        statEditing = true
        statRow = 0
        statCol = 0
        promptEditor.clear()
        result = null
        error = null
    }

    /**
     * Statistics Editor: kisi berkolom X, Y, dan FREQ dengan kursor bebas (manual hlm. 47).
     * `=` menyimpan sel lalu turun satu baris; DEL menghapus baris saat tidak sedang mengetik.
     */
    private fun statPress(key: Key) {
        when (key) {
            Key.EDIT_AC -> {
                statEditing = false
                promptEditor.clear()
            }
            Key.MENU_STAT -> {
                promptEditor.clear()
                menu = CalculatorMenu.STAT_MAIN_EDIT
            }
            Key.EXEC_EQ -> if (commitStatCell()) statRow++
            Key.NAV_UP, Key.NAV_DOWN, Key.NAV_LEFT, Key.NAV_RIGHT ->
                if (commitStatCell()) moveStat(key)
            Key.EDIT_DEL -> if (promptEditor.isEmpty) removeStatRow() else promptEditor.press(key)
            else -> promptEditor.press(key)
        }
    }

    /** Baris terakhir selalu baris kosong untuk data baru, seperti Statistics Editor. */
    private val statRows: Int get() = statData.size + 1

    private fun commitStatCell(): Boolean {
        val typed = promptEditor.build()
        if (typed.isEmpty()) return true
        val value = try {
            Evaluator.eval(Parser.parse(typed), context()).number
        } catch (e: CalcError) {
            error = e.kind
            return false
        }
        promptEditor.clear()
        if (statRow >= statData.size) statData.add(CasioNumber.ZERO)
        statData.set(statRow, statColumn(statCol), value)
        result = Value.Num(value)
        error = null
        return true
    }

    private fun moveStat(key: Key) {
        val lastColumn = (if (statData.type.paired) 1 else 0) + (if (setup.statFreq) 1 else 0)
        when (key) {
            // ▲ dan ▼ berputar di ujung kisi: manual hlm. 49 dan 54 memakai `▼ ►` untuk pindah
            // dari baris kosong sesudah data langsung ke baris pertama kolom berikutnya.
            Key.NAV_UP -> statRow = if (statRow > 0) statRow - 1 else statRows - 1
            Key.NAV_DOWN -> statRow = if (statRow + 1 < statRows) statRow + 1 else 0
            Key.NAV_LEFT -> if (statCol > 0) statCol--
            else -> if (statCol < lastColumn) statCol++
        }
    }

    private fun removeStatRow() {
        if (statRow >= statData.size) return
        statData.removeAt(statRow)
        if (statRow > 0 && statRow >= statRows) statRow--
    }

    /**
     * Butir Reg: A, B, C, dan r langsung memberi nilainya, sedangkan x̂, x̂₁, x̂₂, dan ŷ adalah
     * perintah yang disisipkan ke ekspresi dan memungut nilai di kirinya (manual hlm. 53).
     */
    private fun selectRegItem(choice: Int) {
        if (statData.type == StatType.QUADRATIC) {
            when (choice) {
                0 -> showStat(StatQuery.REG_A)
                1 -> showStat(StatQuery.REG_B)
                2 -> showStat(StatQuery.REG_C)
                3 -> input(Key.FN_EST_X1)
                4 -> input(Key.FN_EST_X2)
                else -> input(Key.FN_EST_Y)
            }
            return
        }
        when (choice) {
            0 -> showStat(StatQuery.REG_A)
            1 -> showStat(StatQuery.REG_B)
            2 -> showStat(StatQuery.CORRELATION)
            3 -> input(Key.FN_EST_X)
            else -> input(Key.FN_EST_Y)
        }
    }

    private fun showStat(query: StatQuery) {
        statEditing = false
        try {
            result = Value.Num(statData.query(query))
            aux = query.label
            error = null
        } catch (e: CalcError) {
            error = e.kind
            result = null
        }
        justEvaluated = true
    }

    // ------------------------------------------------------------------ TABLE

    private fun startTable() {
        tableRows = emptyList()
        tableIndex = 0
        tableInputs.clear()
        tableBody = emptyList()
        tablePrompt = 0
        promptEditor.clear()
    }

    private fun tablePress(key: Key) {
        when (key) {
            Key.EDIT_AC -> {
                tablePrompt = -1
                promptEditor.clear()
            }
            Key.EXEC_EQ -> commitTableStep()
            else -> promptEditor.press(key)
        }
    }

    private fun commitTableStep() {
        val typed = promptEditor.build()
        if (tablePrompt == 0) {
            if (typed.isEmpty()) return
            tableBody = typed
            promptEditor.clear()
            tablePrompt = 1
            return
        }
        val value = try {
            if (typed.isEmpty()) TABLE_DEFAULTS[tablePrompt - 1]
            else Evaluator.eval(Parser.parse(typed), context()).number
        } catch (e: CalcError) {
            error = e.kind
            return
        }
        tableInputs.add(value)
        promptEditor.clear()
        if (tableInputs.size < 3) {
            tablePrompt++
            return
        }
        tablePrompt = -1
        try {
            tableRows = TableBuilder.build(
                Parser.parse(tableBody),
                context(),
                tableInputs[0],
                tableInputs[1],
                tableInputs[2],
            )
            tableIndex = 0
            error = null
        } catch (e: CalcError) {
            error = e.kind
            tableRows = emptyList()
        }
    }

    private fun browseTable(key: Key) {
        when (key) {
            Key.NAV_LEFT -> if (tableCol > 0) tableCol--
            Key.NAV_RIGHT -> if (tableCol < 1) tableCol++
            else -> {
                val target = if (key == Key.NAV_UP) tableIndex - 1 else tableIndex + 1
                if (target in tableRows.indices) tableIndex = target
            }
        }
    }

    /** Manual hlm. 67: AC pada layar tabel kembali ke layar isian f(X), bukan mengosongkan. */
    private fun backToTableFunction() {
        tableRows = emptyList()
        tableIndex = 0
        tableCol = 0
        tableInputs.clear()
        tablePrompt = 0
        promptEditor.load(tableBody)
    }

    /**
     * Layar tabel: kisi berkolom X dan F(X) dengan nomor baris, dan nilai sel terpilih ditulis
     * penuh di kanan bawah (manual hlm. 67).
     */
    private fun tableGrid(): GridView {
        val selected = tableRows[tableIndex]
        val cell = if (tableCol == 0) selected.x else selected.value
        return GridView(
            title = "",
            columns = listOf("X", "F(X)"),
            rows = tableRows.map { listOf(cellText(it.x), cellText(it.value)) },
            cursorRow = tableIndex,
            cursorCol = tableCol,
            typed = listOf(NumNode(cellText(cell))),
            numbered = true,
            bracketed = false,
        )
    }

    private fun startEqn(type: EqnSolver.Type) {
        eqnType = type
        // Ganti tipe mengosongkan seluruh koefisien menjadi nol (manual hlm. 61).
        eqnCells.clear()
        repeat(type.coefficients) { eqnCells.add(CasioNumber.ZERO) }
        eqnIndex = 0
        eqnRoots = emptyList()
        rootIndex = 0
        promptEditor.clear()
        result = null
        error = null
    }

    private fun eqnPress(key: Key) {
        when (key) {
            // Manual hlm. 61: AC mengosongkan koefisien jadi nol, bukan keluar dari mode.
            Key.EDIT_AC -> {
                eqnCells.indices.forEach { eqnCells[it] = CasioNumber.ZERO }
                eqnIndex = 0
                promptEditor.clear()
            }
            Key.EXEC_EQ -> commitCoefficient()
            Key.NAV_UP, Key.NAV_DOWN, Key.NAV_LEFT, Key.NAV_RIGHT ->
                if (storeCoefficient()) moveEqnCursor(key)
            else -> promptEditor.press(key)
        }
    }

    /** Menyimpan angka yang sedang diketik ke sel tersorot; false bila ekspresinya salah. */
    private fun storeCoefficient(): Boolean {
        val typed = promptEditor.build()
        if (typed.isEmpty()) return true
        val value = try {
            Evaluator.eval(Parser.parse(typed), context()).number
        } catch (e: CalcError) {
            error = e.kind
            return false
        }
        eqnCells[eqnIndex] = value
        promptEditor.clear()
        return true
    }

    private fun moveEqnCursor(key: Key) {
        val type = eqnType ?: return
        val columns = eqnColumns(type)
        val rows = eqnCells.size / columns
        var row = eqnIndex / columns
        var col = eqnIndex % columns
        when (key) {
            Key.NAV_UP -> row--
            Key.NAV_DOWN -> row++
            Key.NAV_LEFT -> col--
            else -> col++
        }
        if (row !in 0 until rows || col !in 0 until columns) return
        eqnIndex = row * columns + col
    }

    /**
     * `=` menyimpan sel lalu pindah ke sel berikutnya. Pada sel terakhir kursornya diam, dan `=`
     * sekali lagi tanpa ketikan barulah menghitung — persis urutan manual hlm. 61 Contoh 1.
     */
    private fun commitCoefficient() {
        val hadInput = !promptEditor.isEmpty
        if (!storeCoefficient()) return
        if (eqnIndex < eqnCells.lastIndex) {
            eqnIndex++
            return
        }
        if (hadInput) return
        solveEqn()
    }

    private fun solveEqn() {
        val type = eqnType ?: return
        try {
            val roots = when (type) {
                EqnSolver.Type.LINEAR2 -> EqnSolver.linear(2, eqnCells)
                EqnSolver.Type.LINEAR3 -> EqnSolver.linear(3, eqnCells)
                EqnSolver.Type.QUADRATIC ->
                    EqnSolver.quadratic(eqnCells[0], eqnCells[1], eqnCells[2])
                EqnSolver.Type.CUBIC ->
                    EqnSolver.cubic(eqnCells[0], eqnCells[1], eqnCells[2], eqnCells[3])
            }
            // Akar kembar kuadrat tampil sekali dan berlabel X=, bukan X1= (manual hlm. 62).
            eqnRoots =
                if (type == EqnSolver.Type.QUADRATIC && roots.size == 2 && sameRoot(roots[0], roots[1])) {
                    listOf(roots[0])
                } else {
                    roots
                }
            rootIndex = 0
            error = null
        } catch (e: CalcError) {
            error = e.kind
        }
    }

    /**
     * Pada layar solusi, ▼ dan ▲ menelusuri akar, `=` maju ke akar berikutnya, dan `=` pada akar
     * terakhir kembali ke Coefficient Editor dengan koefisiennya utuh (manual hlm. 61).
     */
    private fun browseRoots(key: Key) {
        if (key == Key.EXEC_EQ) {
            if (rootIndex < eqnRoots.lastIndex) rootIndex++ else backToCoefficients()
            return
        }
        val target = if (key == Key.NAV_UP) rootIndex - 1 else rootIndex + 1
        if (target in eqnRoots.indices) rootIndex = target
    }

    /**
     * Dua akar dianggap kembar bila sama sampai sepuluh digit tampilan. Diskriminannya jarang
     * tepat nol: pada `x²-2√2x+2` nilai √2 yang tersimpan 15 digit menyisakan galat kecil.
     */
    private fun sameRoot(a: Complex, b: Complex): Boolean =
        a.re.roundSignificant(10) == b.re.roundSignificant(10) &&
            a.im.roundSignificant(10) == b.im.roundSignificant(10)

    private fun backToCoefficients() {
        eqnRoots = emptyList()
        rootIndex = 0
        promptEditor.clear()
        result = null
    }

    /**
     * Akar sebagai nilai siap format. Bentuk eksak dikenali supaya akar kuadrat tampil √2
     * (manual hlm. 62 Contoh 4), kecuali pada sistem linear: manual hlm. 61 menegaskan
     * solusinya tidak pernah memakai bentuk ber-√ walau Natural Display menyala.
     */
    private fun eqnRootValue(root: Complex): Value {
        if (!root.isReal) return Value.Cx(root)
        val exact = ResultFormatter.scalar(root.re)
        val linear = eqnType == EqnSolver.Type.LINEAR2 || eqnType == EqnSolver.Type.LINEAR3
        if (linear && exact is Value.Ex && exact.exact is Exact.Surd) return Value.Num(root.re)
        return exact
    }

    /** Label akar: X, Y, Z untuk sistem linear; X1, X2, X3 untuk kuadrat dan kubik. */
    private fun rootLabel(): String {
        val type = eqnType ?: return ""
        return when (type) {
            EqnSolver.Type.LINEAR2, EqnSolver.Type.LINEAR3 -> "XYZ"[rootIndex] + "="
            // Akar tunggal tidak bernomor: manual hlm. 62 Contoh 4 menulis X=, bukan X1=.
            else -> if (eqnRoots.size == 1) "X=" else "X${rootIndex + 1}="
        }
    }

    // ------------------------------------------------------------------ SOLVE dan CALC

    /**
     * SOLVE mencari akar ekspresi berjalan dengan Newton-Raphson (PRD §5.3). Ekspresi tanpa `=`
     * diperlakukan sebagai f(X) = 0. Variabel yang dicari adalah X bila ada, jika tidak variabel
     * pertama yang muncul.
     */
    private fun startSolve() {
        // Manual hlm. 39: SOLVE hanya ada di mode COMP.
        if (mode != Mode.COMP) return
        val nodes = editor.build()
        if (nodes.isEmpty()) return
        // Manual hlm. 39: sintaks {persamaan},{variabel} menunjuk sendiri variabel yang dicari.
        val comma = nodes.indexOfLast { it is KeyNode && it.key == Key.SEP_COMMA }
        var chosen: Key? = null
        var equation = nodes
        if (comma >= 0) {
            val named = (nodes.drop(comma + 1).singleOrNull() as? KeyNode)?.key
            if (named == null || named !in Key.VARIABLES) {
                error = ErrorKind.SYNTAX
                return
            }
            chosen = named
            equation = nodes.take(comma)
        }
        val ast = try {
            Parser.parse(equation)
        } catch (e: CalcError) {
            error = e.kind
            return
        }
        val used = variablesOf(ast)
        // Manual hlm. 74: tanpa variabel yang ditunjuk, persamaan wajib memuat X; kalau variabel
        // yang ditunjuk tidak ada di persamaan, keduanya menghasilkan Variable ERROR.
        val target = chosen ?: Key.VAR_X
        if (target !in used) {
            error = ErrorKind.VARIABLE
            return
        }
        // Manual hlm. 40: nilai tiap variabel lain ditanya dulu, tebakan awal X paling akhir.
        calcSession = true
        solveTarget = target
        solveEquation = equation
        solveLabel = ""
        solveContinue = false
        promptVars = used.filter { it != target } + target
        restartPrompts()
    }

    /** Newton dijalankan sesudah semua prompt SOLVE terisi, juga saat `Continue:[=]` diteruskan. */
    private fun runSolve() {
        val target = solveTarget ?: return
        try {
            val ast = Parser.parse(solveEquation)
            val body = if (ast is Equation) Bin(Key.OP_SUB, ast.left, ast.right) else ast
            val guess = (vars[target] ?: ZERO).number.value
            val found = Calculus.solve(body, context(), target, guess)
            vars[target] = Value.Num(found.root)
            result = Value.Num(found.root)
            solveContinue = !found.converged
            solveLabel = if (found.converged) ExprRenderer.symbol(target) + "=" else ""
            aux = if (found.converged) {
                L_MINUS_R + ResultFormatter.decimal(found.leftMinusRight.roundSignificant(10), setup)
            } else {
                ""
            }
            error = null
            resultIsDms = false
            view = ResultView()
            justEvaluated = true
        } catch (e: CalcError) {
            error = e.kind
            result = null
            aux = ""
            solveLabel = ""
            solveContinue = false
            justEvaluated = false
        }
    }

    /** Menanyakan ulang nilai variabel dari variabel pertama (manual hlm. 38 dan 42). */
    private fun restartPrompts() {
        if (promptVars.isEmpty()) return
        promptIndex = 0
        promptEditor.clear()
        aux = ""
        solveLabel = ""
        solveContinue = false
        error = null
        result = vars[promptVars[0]] ?: ZERO
    }

    private fun endCalcSession() {
        calcSession = false
        solveTarget = null
        solveLabel = ""
        solveContinue = false
    }

    /** CALC menanyakan nilai tiap variabel dalam ekspresi, lalu menghitungnya (PRD §3.2). */
    private fun startCalc() {
        val nodes = editor.build()
        if (nodes.isEmpty()) return
        val ast = try {
            Parser.parse(nodes)
        } catch (e: CalcError) {
            error = e.kind
            return
        }
        val used = variablesOf(ast)
        if (used.isEmpty()) {
            evaluate()
            return
        }
        calcSession = true
        solveTarget = null
        promptVars = used
        restartPrompts()
    }

    private fun promptPress(key: Key) {
        when (key) {
            Key.EDIT_AC -> {
                // Manual hlm. 38 dan 40: AC keluar dari CALC/SOLVE, ekspresinya tetap di layar.
                promptVars = emptyList()
                promptIndex = -1
                promptEditor.clear()
                endCalcSession()
                result = null
            }
            Key.EXEC_EQ -> commitPrompt()
            else -> promptEditor.press(key)
        }
    }

    private fun commitPrompt() {
        val typed = promptEditor.build()
        val variable = promptVars[promptIndex]
        if (typed.isNotEmpty()) {
            try {
                vars[variable] = Evaluator.eval(Parser.parse(typed), context())
            } catch (e: CalcError) {
                error = e.kind
                return
            }
        }
        promptEditor.clear()
        promptIndex++
        if (promptIndex >= promptVars.size) {
            // Daftar variabel disimpan supaya `=` dapat menanyakannya ulang (manual hlm. 38).
            promptIndex = -1
            if (solveTarget != null) runSolve() else evaluate()
        } else {
            result = vars[promptVars[promptIndex]] ?: ZERO
        }
    }

    /** Variabel yang muncul dalam pohon, urut kemunculan. */
    private fun variablesOf(ast: Ast): List<Key> {
        val found = LinkedHashSet<Key>()
        fun walk(node: Ast) {
            when (node) {
                is Ref -> if (node.key in Key.VARIABLES) found.add(node.key)
                is Un -> walk(node.x)
                is Bin -> { walk(node.l); walk(node.r) }
                is Fn -> node.args.forEach { walk(it) }
                is Dms -> node.parts.forEach { walk(it) }
                is Equation -> { walk(node.left); walk(node.right) }
                is TableRef -> node.x?.let { walk(it) }
                is Lit -> Unit
            }
        }
        walk(ast)
        return found.toList()
    }

    // ------------------------------------------------------------------ replay dan kursor

    /**
     * Setelah hasil, ▲ dan ▼ menelusuri replay memory. Di dalam ekspresi, keduanya berpindah
     * antar slot struktur vertikal seperti pembilang dan penyebut.
     */
    /**
     * ▲ dan ▼ menelusuri replay memory. Layar kosong sesudah AC tetap bisa menelusuri, sebab
     * manual hlm. 24 hanya menghapus riwayat saat ganti mode atau ganti format tampilan.
     */
    private fun navigateVertically(key: Key) {
        storedTo = null
        val canBrowse = justEvaluated || replaying || editor.isEmpty
        if (!canBrowse || history.isEmpty()) {
            editor.press(key)
            return
        }
        if (!replaying) historyIndex = history.size
        val target = if (key == Key.NAV_UP) historyIndex - 1 else historyIndex + 1
        if (target !in history.indices) return
        historyIndex = target
        editor.load(history[target])
        justEvaluated = false
        replaying = true
    }

    /**
     * Setelah hasil, ◄ dan ► membuka kembali ekspresi terakhir untuk disunting (PRD §4.4).
     * Begitu pula saat layar sudah dikosongkan AC: ekspresi terakhir dipanggil kembali dari
     * replay memory, seperti unit fisik.
     */
    private fun navigateHorizontally(key: Key) {
        storedTo = null
        if (justEvaluated || (editor.isEmpty && !replaying && history.isNotEmpty())) {
            justEvaluated = false
            history.lastOrNull()?.let { editor.load(it) }
            if (key == Key.NAV_LEFT) return
        }
        // ◄ dan ► mengakhiri penelusuran riwayat dan masuk ke mode sunting ekspresi terpilih.
        replaying = false
        editor.press(key)
    }

    // ------------------------------------------------------------------ evaluasi

    private fun evaluate() {
        storedTo = null
        val nodes = editor.build()
        if (nodes.isEmpty()) return
        try {
            val ast = Parser.parse(nodes, setup.base)
            val ctx = context()
            val value = Evaluator.eval(ast, ctx)
            // Pol, Rec, ∫, Σ, d/dx, dan TABLE memakai X sebagai variabel kerja; nilainya
            // memang berubah di kalkulator fisik (manual hlm. 35 dan 67).
            ctx.vars[Key.VAR_X]?.let { vars[Key.VAR_X] = it }
            ctx.vars[Key.VAR_Y]?.let { vars[Key.VAR_Y] = it }
            coordLabels = coordinateLabels(ast)
            preAns = ans
            ans = value
            result = value
            resultIsDms = isSexagesimal(ast)
            usedFraction = usesFraction(nodes)
            error = null
            aux = ""
            when (value) {
                is Value.Mat -> containers[Key.MAT_ANS] = value
                is Value.Vec -> containers[Key.VCT_ANS] = value
                else -> Unit
            }
            remember(nodes)
            justEvaluated = true
        } catch (e: CalcError) {
            error = e.kind
            // Ekspresi dibiarkan utuh supaya dapat langsung disunting setelah pesan error, dan
            // letak errornya disimpan untuk Goto (manual hlm. 72).
            errorAt = e.at
            result = null
            resultIsDms = false
            justEvaluated = false
        }
        view = ResultView(
            mixed = setup.mixedFraction,
            fromFractionInput = usedFraction,
            complexForm = forcedComplexForm(nodes),
        )
    }

    /**
     * `▸r∠θ` dan `▸a+bi` di ujung ekspresi memaksa bentuk hasilnya, menimpa Setup
     * (manual hlm. 47). Null berarti ekspresinya tidak memakai perintah itu.
     */
    private fun forcedComplexForm(nodes: List<Node>): ComplexForm? =
        when ((nodes.lastOrNull() as? KeyNode)?.key) {
            Key.FN_TO_POLAR -> ComplexForm.POLAR
            Key.FN_TO_RECT -> ComplexForm.RECTANGULAR
            else -> null
        }

    /** Menyimpan ekspresi ke replay memory dengan batas seperti kalkulator fisik (PRD §6.2). */
    private fun remember(nodes: List<Node>) {
        history.add(nodes)
        if (history.size > REPLAY_LIMIT) history.removeAt(0)
        historyIndex = -1
        replaying = false
    }

    private fun context() = EvalContext(
        setup = setup,
        vars = vars.toMutableMap(),
        ans = ans,
        preAns = preAns,
        mode = mode,
        stat = statData,
        containers = containers.toMap(),
    )

    /** Apakah ekspresi memakai tombol pecahan; menentukan tampilan pecahan di LineIO. */
    private fun usesFraction(nodes: List<Node>): Boolean = nodes.any { node ->
        when (node) {
            is CallNode ->
                node.key == Key.FRAC || node.key == Key.FRAC_MIXED || node.args.any { usesFraction(it) }
            else -> false
        }
    }

    /** Hasil ditampilkan seksagesimal bila operandnya memang derajat-menit-detik. */
    private fun isSexagesimal(ast: Ast): Boolean = when (ast) {
        is Dms -> true
        is Bin -> when (ast.op) {
            Key.OP_ADD, Key.OP_SUB -> isSexagesimal(ast.l) && isSexagesimal(ast.r)
            Key.OP_MUL, Key.OP_DIV -> isSexagesimal(ast.l) != isSexagesimal(ast.r)
            else -> false
        }
        else -> false
    }

    // ------------------------------------------------------------------ tampilan hasil

    private fun toggleDecimal() {
        if (resultIsDms) {
            toggleDms()
            return
        }
        view = view.copy(decimal = !view.decimal, engActive = false)
    }

    private fun toggleDms() {
        if (result == null) return
        view = view.copy(decimal = !view.decimal)
    }

    /**
     * SHIFT °′″ (◄): hasil berbentuk derajat-menit-detik dikembalikan ke derajat desimal.
     *
     * Satu arah saja, berbeda dari tombol °′″ polos yang bolak-balik (manual hlm. 23 Contoh 2).
     * Arahnya dipastikan pemilik produk pada unit fisik 20 September 2026; manual sendiri tidak
     * menyebut fungsi lapis kuning untuk tombol ini.
     */
    private fun dmsToDecimal() {
        if (!justEvaluated || !resultIsDms) return
        view = view.copy(decimal = true)
    }

    private fun shiftEngineering(direction: Int) {
        if (result == null) return
        view = if (!view.engActive) {
            view.copy(engActive = true, engShift = if (direction > 0) 1 else 0)
        } else {
            view.copy(engShift = view.engShift + direction)
        }
    }

    // ------------------------------------------------------------------ memori

    private fun variable(key: Key) {
        when (pending) {
            Key.MEM_STO -> {
                pending = null
                val value = evaluateForMemory() ?: return
                vars[key] = value
                result = value
                storedTo = key
                justEvaluated = true
            }
            Key.MEM_RCL -> {
                pending = null
                result = vars[key] ?: ZERO
                resultIsDms = false
                view = ResultView()
                justEvaluated = true
            }
            else -> input(key)
        }
    }

    /**
     * Menyalin matriks atau vektor yang sedang tampil ke wadah tujuan, lalu membuka editornya
     * berisi salinan itu (manual hlm. 65).
     */
    private fun storeContainer(key: Key) {
        pending = null
        val slot = CONTAINER_DESTINATIONS.indexOf(key)
        val target = when (mode) {
            Mode.MATRIX -> MATRIX_REFS[slot]
            Mode.VECTOR -> VECTOR_REFS[slot]
            else -> return
        }
        val value = result
        if (value !is Value.Mat && value !is Value.Vec) {
            error = ErrorKind.DIMENSION
            return
        }
        containers[target] = value
        editExisting(target)
    }

    /** Simpul yang dipakai saat melanjutkan dari hasil: Ans, MatAns, atau VctAns. */
    private fun answerKey(): Key = when (result) {
        is Value.Mat -> Key.MAT_ANS
        is Value.Vec -> Key.VCT_ANS
        else -> Key.MEM_ANS
    }

    private fun accumulateMemory(add: Boolean) {
        val current = evaluateForMemory() ?: return
        val stored = vars[Key.VAR_M] ?: ZERO
        val sum = if (add) stored.number + current.number else stored.number - current.number
        vars[Key.VAR_M] = Value.Num(sum)
        result = current
        resultIsDms = false
        view = ResultView()
        justEvaluated = true
    }

    /** STO, M+, dan M− mengevaluasi ekspresi berjalan lebih dulu, seperti kalkulator fisik. */
    private fun evaluateForMemory(): Value? {
        if (!justEvaluated && !editor.isEmpty) {
            evaluate()
            if (error != null) return null
        }
        return result ?: ans
    }

    // ------------------------------------------------------------------ DMS

    /** Menulis nilai desimal sebagai derajat-menit-detik, misal 2.255 menjadi 2°15'18". */
    private fun dmsText(n: CasioNumber): String {
        // Konversi memakai nilai yang sudah dibulatkan ke 10 digit tampilan supaya 3 derajat
        // tidak muncul sebagai 2°59'60".
        val abs = n.abs().roundSignificant(10).value
        var degrees = abs.setScale(0, RoundingMode.DOWN)
        val minutesTotal = abs.subtract(degrees).multiply(BigDecimal(60), CasioNumber.WORK)
        var minutes = minutesTotal.setScale(0, RoundingMode.DOWN)
        var seconds = minutesTotal.subtract(minutes)
            .multiply(BigDecimal(60), CasioNumber.WORK)
            .setScale(2, RoundingMode.HALF_UP)
        if (seconds >= SIXTY) {
            seconds = seconds.subtract(SIXTY)
            minutes = minutes.add(BigDecimal.ONE)
        }
        if (minutes >= SIXTY) {
            minutes = minutes.subtract(SIXTY)
            degrees = degrees.add(BigDecimal.ONE)
        }
        val sign = if (n.isNegative) "-" else ""
        val sec = seconds.stripTrailingZeros().toPlainString()
        return "$sign${degrees.toBigInteger()}°${minutes.toBigInteger()}'$sec\""
    }

    private companion object {
        val ZERO: Value = Value.Num(CasioNumber.ZERO)
        val SIXTY: BigDecimal = BigDecimal(60)

        // Urutan keempat daftar ini mengikuti submenu manual hlm. 51-53.
        val SUM_QUERIES = listOf(
            StatQuery.SUM_X2, StatQuery.SUM_X, StatQuery.SUM_Y2, StatQuery.SUM_Y,
            StatQuery.SUM_XY, StatQuery.SUM_X3, StatQuery.SUM_X2Y, StatQuery.SUM_X4,
        )
        val VAR_QUERIES = listOf(
            StatQuery.N, StatQuery.MEAN_X, StatQuery.SIGMA_X, StatQuery.SAMPLE_X,
            StatQuery.MEAN_Y, StatQuery.SIGMA_Y, StatQuery.SAMPLE_Y,
        )
        val MINMAX_QUERIES = listOf(
            StatQuery.MIN_X, StatQuery.MAX_X, StatQuery.MIN_Y, StatQuery.MAX_Y,
        )
        val DISTR_KEYS = listOf(Key.FN_DIST_P, Key.FN_DIST_Q, Key.FN_DIST_R, Key.FN_TO_T)

        /** Tujuan salinan A, B, dan C sesudah STO di mode MATRIX dan VECTOR (manual hlm. 65). */
        val CONTAINER_DESTINATIONS = listOf(Key.OP_NEG, Key.POST_DMS, Key.MENU_HYP)

        val HYP_KEYS = listOf(
            Key.FN_SINH, Key.FN_COSH, Key.FN_TANH,
            Key.FN_ASINH, Key.FN_ACOSH, Key.FN_ATANH,
        )

        val BASE_LOGIC_KEYS = listOf(
            Key.FN_AND, Key.FN_OR, Key.FN_XOR, Key.FN_XNOR, Key.FN_NOT, Key.FN_NEG,
        )

        val MATRIX_REFS = listOf(Key.MAT_A, Key.MAT_B, Key.MAT_C, Key.MAT_ANS)
        val VECTOR_REFS = listOf(Key.VCT_A, Key.VCT_B, Key.VCT_C, Key.VCT_ANS)

        /** Urutan pertanyaan mode TABLE (PRD §3.1). */
        val TABLE_PROMPTS = listOf("f(X)=", "Start?", "End?", "Step?")

        /** Bawaan Start, End, dan Step bila isiannya dikosongkan (manual hlm. 66-67). */
        val TABLE_DEFAULTS = listOf(CasioNumber.ONE, CasioNumber.of(5), CasioNumber.ONE)

        /** Jumlah ekspresi yang disimpan replay memory. */
        const val REPLAY_LIMIT = 30

        /** Setelah error, hanya AC dan panah penunjuk kesalahan yang diterima (PRD §4.4). */
        val RECOVERS_FROM_ERROR = setOf(Key.EDIT_AC, Key.NAV_LEFT, Key.NAV_RIGHT)

        /** Label sisa kiri dikurangi kanan pada layar solusi SOLVE (manual hlm. 40). */
        const val L_MINUS_R = "L-R="

        /** Penanda satuan sudut pada menu DRG, urut derajat, radian, grad. */
        val DRG_KEYS = listOf(Key.ANGLE_DEG, Key.ANGLE_RAD, Key.ANGLE_GRA)

        /** Urutan butir menu CMPLX dari manual hlm. 47. */
        val CMPLX_KEYS =
            listOf(Key.FN_ARG, Key.FN_CONJG, Key.FN_TO_POLAR, Key.FN_TO_RECT)

        /** Urutan ukuran pada menu dimensi matriks, sesuai layar unit fisik. */
        val MATRIX_DIMS = listOf(
            3 to 3, 3 to 2, 3 to 1,
            2 to 3, 2 to 2, 2 to 1,
            1 to 3, 1 to 2, 1 to 1,
        )

        /** Teks nilai nol dalam berbagai bentuk; dipakai indikator memori. */
        val ZERO_TEXTS = setOf("0", "0.0", "-0")

        /** Mode yang selalu memakai Linear Display pada unit fisik (manual hlm. 12). */
        val LINEAR_ONLY = setOf(Mode.STAT, Mode.BASE, Mode.MATRIX, Mode.VECTOR)

        /** Tombol yang setelah `=` melanjutkan perhitungan dari Ans (PRD §4.4). */
        val CONTINUES_FROM_ANS = setOf(
            Key.OP_ADD, Key.OP_SUB, Key.OP_MUL, Key.OP_DIV,
            Key.OP_NPR, Key.OP_NCR, Key.EXP10,
            Key.POST_SQUARE, Key.POST_CUBE, Key.POST_RECIP,
            Key.POST_FACT, Key.POST_PERCENT,
            Key.FRAC, Key.OP_POW, Key.OP_NROOT,
        )
    }
}
