package id.sempoa570.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.sempoa570.engine.Calculator
import id.sempoa570.engine.CalculatorSnapshot
import id.sempoa570.engine.GridView
import id.sempoa570.engine.Indicators
import id.sempoa570.engine.Key
import id.sempoa570.engine.MenuView
import id.sempoa570.engine.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tempat status kalkulator disimpan (PRD §6.3): DataStore di Android, berkas di desktop. */
interface SnapshotStore {
    suspend fun load(): CalculatorSnapshot
    suspend fun save(snapshot: CalculatorSnapshot)
}

/**
 * Menjembatani papan tombol dengan engine, dipakai Android dan desktop. Lapis SHIFT dan ALPHA
 * diselesaikan di sini memakai keymap, sehingga engine hanya menerima token lapis yang sudah
 * tepat (PRD §4.3). [scope] milik cangkang: viewModelScope di Android, scope jendela di desktop.
 */
class CalculatorController(
    private val scope: CoroutineScope,
    private val store: SnapshotStore? = null,
) {

    private val calculator = Calculator()
    private val modifier = ModifierState()
    private var saveJob: Job? = null

    var expression by mutableStateOf<List<Node>>(emptyList())
        private set
    var result by mutableStateOf<List<Node>>(emptyList())
        private set
    var aux by mutableStateOf("")
        private set
    var resultLabel by mutableStateOf("")
        private set
    var auxLabel by mutableStateOf("")
        private set
    var error by mutableStateOf("")
        private set
    var prompt by mutableStateOf<List<String>>(emptyList())
        private set
    var menu by mutableStateOf<MenuView?>(null)
        private set
    var grid by mutableStateOf<GridView?>(null)
        private set
    var hapticEnabled by mutableStateOf(true)
        private set
    var skin by mutableStateOf(0)
        private set
    var manualOpen by mutableStateOf(false)
        private set

    /** Teks yang menunggu ditaruh ke papan klip; lapisan Compose yang punya aksesnya. */
    var clipboardOut by mutableStateOf<String?>(null)
        private set

    /** Pemakai memilih Tempel dari menu OPTION; lapisan Compose membaca papan klip. */
    var pasteRequested by mutableStateOf(false)
        private set

    /** Tulisan sesaat di baris indikator, mis. `Tersalin`. */
    var notice by mutableStateOf("")
        private set

    private var noticeJob: Job? = null
    var indicators by mutableStateOf(Indicators())
        private set
    var layer by mutableStateOf(Layer.MAIN)
        private set

    init {
        refresh()
        // Status dipulihkan sekali saat aplikasi dibuka (PRD §6.3).
        store?.let { saved ->
            scope.launch {
                calculator.restore(saved.load())
                refresh()
            }
        }
    }

    /** Status saat ini, untuk disimpan segera saat jendela desktop ditutup. */
    fun snapshot(): CalculatorSnapshot = calculator.save()

    fun onKey(entry: KeyEntry) {
        if (!modifier.onPress(entry)) {
            // STO dan RCL menunggu nama variabel; tombol huruf berlaku langsung tanpa ALPHA.
            val waitingForVariable = indicators.store || indicators.recall
            modifier.resolve(entry, calculator.mode, waitingForVariable)
                ?.let { calculator.press(it) }
        }
        refresh()
        scheduleSave()
    }

    /**
     * Ketukan layar. Sengaja tidak lewat [ModifierState]: nomor menu yang tercetak sudah pasti,
     * jadi lapis SHIFT atau ALPHA yang masih tertunda tidak boleh mengubahnya.
     */
    fun onMenuSelect(number: Int) {
        Key.byName("DIGIT_$number")?.let { calculator.press(it) }
        refresh()
        scheduleSave()
    }

    /** Ketukan baris ekspresi; [steps] jalur simpul dan slot dari penggambar. */
    fun onTapExpr(steps: IntArray) {
        calculator.tapExpr(steps)
        refresh()
        scheduleSave()
    }

    /** Tekan lama pada layar membuka menu papan klip: Salin atau Tempel. */
    fun openClipboardMenu(fromResult: Boolean) {
        calculator.openClipboardMenu(fromResult)
        refresh()
    }

    /** Dipanggil sesudah teksnya benar-benar masuk papan klip. */
    fun clipboardTaken() {
        calculator.clipboardTaken()
        showNotice("Tersalin")
        refresh()
    }

    /** Menempel teks papan klip; pesan singkat memberi tahu bila teksnya tak terbaca. */
    fun paste(text: String) {
        val ok = calculator.paste(text)
        showNotice(if (ok) "Ditempel" else "Tak terbaca")
        refresh()
        scheduleSave()
    }

    /** Tulisan hilang sendiri, jadi tidak ada tombol yang harus ditekan untuk menutupnya. */
    private fun showNotice(text: String) {
        noticeJob?.cancel()
        notice = text
        noticeJob = scope.launch {
            delay(1200)
            notice = ""
        }
    }

    /** Menutup panduan bawaan. */
    fun closeManual() {
        calculator.closeManual()
        refresh()
    }

    /** Ketukan sel kisi. */
    fun onTapCell(row: Int, column: Int) {
        calculator.tapCell(row, column)
        refresh()
        scheduleSave()
    }

    /** Penyimpanan di-debounce 200 ms supaya mengetik cepat tidak menulis berkas tiap tombol. */
    private fun scheduleSave() {
        val saved = store ?: return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(200)
            saved.save(calculator.save())
        }
    }

    private fun refresh() {
        expression = calculator.exprTree()
        result = calculator.resultTree()
        aux = calculator.auxText()
        resultLabel = calculator.resultLabelText()
        auxLabel = calculator.auxLabelText()
        error = calculator.errorText()
        prompt = calculator.twoDigitPrompt() ?: emptyList()
        menu = calculator.menuView()
        grid = calculator.gridView()
        hapticEnabled = calculator.hapticEnabled
        skin = calculator.skin
        manualOpen = calculator.manualOpen
        clipboardOut = calculator.clipboardOut
        pasteRequested = calculator.pasteRequested
        indicators = calculator.indicators()
        layer = modifier.layer
    }
}

/**
 * Layar kalkulator yang terikat ke [controller], dipakai Android dan desktop. [onPress] dipanggil
 * tiap tekan bila getaran dinyalakan di OPTION; desktop tidak memberinya.
 */
@Composable
fun CalculatorApp(controller: CalculatorController, keymap: Keymap, onPress: () -> Unit = {}) {
    val c = controller
    val feedback = { if (c.hapticEnabled) onPress() }
    CalculatorScreen(
        keymap = keymap,
        expression = c.expression,
        result = c.result,
        aux = c.aux,
        resultLabel = c.resultLabel,
        auxLabel = c.auxLabel,
        error = c.error,
        prompt = c.prompt,
        menu = c.menu,
        grid = c.grid,
        indicators = c.indicators,
        layer = c.layer,
        notice = c.notice,
        onKey = { entry -> feedback(); c.onKey(entry) },
        onMenuSelect = { number -> feedback(); c.onMenuSelect(number) },
        onTapExpr = { steps -> feedback(); c.onTapExpr(steps) },
        onTapCell = { row, column -> feedback(); c.onTapCell(row, column) },
        onCopyExpr = { feedback(); c.openClipboardMenu(fromResult = false) },
        onCopyResult = { feedback(); c.openClipboardMenu(fromResult = true) },
    )
}
