package id.sempoa570.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import id.sempoa570.engine.InputMode
import id.sempoa570.engine.Node
import id.sempoa570.engine.Replay
import id.sempoa570.engine.ScientificConstants
import id.sempoa570.engine.UnitConversions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Satu langkah panduan: urutan tombol yang ditekan, lalu penjelasannya. */
@Serializable
data class ManualStep(val keys: String = "", val text: String)

/**
 * Satu contoh kerja: apa yang dihitung, tombol apa yang ditekan, bentuk yang muncul di baris
 * ekspresi, dan hasilnya.
 *
 * [press] untuk dibaca pemakai, [keys] adalah nama token yang dipakai tes JVM memainkan ulang
 * contohnya — jadi tak ada satu pun angka di panduan yang bisa salah tanpa ketahuan.
 */
@Serializable
data class ManualExample(
    val goal: String,
    val press: String,
    val keys: String = "",
    val screen: String = "",
    val result: String = "",
    val mode: String = "COMP",
    val setup: String = "default",
)

@Serializable
data class ManualChapter(
    val title: String,
    val intro: String = "",
    val steps: List<ManualStep> = emptyList(),
    val examples: List<ManualExample> = emptyList(),
    /**
     * Daftar rujukan yang ditempel di akhir bab: `const` untuk 40 konstanta ilmiah, `conv` untuk
     * 40 perintah konversi satuan. Isinya dibaca langsung dari tabel `:engine`, bukan disalin ke
     * berkas ini, supaya nomor dan lambangnya tidak pernah berbeda dari yang dipakai menghitung.
     */
    val table: String = "",
)

/**
 * Isi panduan bawaan dari `docs/manual.json`, yang disalin ke aset Android dan sumber daya
 * desktop saat build. Teksnya ditulis sendiri untuk aplikasi ini (PRD §7.2).
 */
@Serializable
data class ManualBook(
    val title: String = "",
    val intro: String = "",
    val chapters: List<ManualChapter> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Isi manual.json; berkasnya dibaca cangkang masing-masing platform. */
        fun parse(text: String): ManualBook = json.decodeFromString(text)
    }
}

/**
 * Panduan bawaan, dibuka dari OPTION. Sengaja layar penuh bergulir, bukan daftar bernomor di
 * dalam LCD: panel LCD hanya setinggi sepuluh baris, sedangkan panduannya delapan belas bab.
 * Larangan "bukan dialog Android" pada PRD §4.4 mengenai menu kalkulator, bukan layar bantuan.
 */
@Composable
fun ManualScreen(book: ManualBook, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(-1) }
    val skin = LocalSkin.current
    // Tombol kembali Android menutup bab dulu, baru menutup panduannya.
    PlatformBackHandler { if (open >= 0) open = -1 else onClose() }
    Surface(modifier = modifier.fillMaxSize(), color = skin.body) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (open >= 0) book.chapters[open].title else book.title,
                    color = skin.bodyLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (open >= 0) "Kembali" else "Tutup",
                    color = skin.shiftLabel,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { if (open >= 0) open = -1 else onClose() }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (open < 0) {
                    Paragraph(book.intro, skin.lcdDim)
                    Spacer(Modifier.height(8.dp))
                    book.chapters.forEachIndexed { index, chapter ->
                        ChapterRow(index + 1, chapter.title) { open = index }
                    }
                } else {
                    val chapter = book.chapters[open]
                    Paragraph(chapter.intro, skin.bodyLabel)
                    chapter.steps.forEach { step -> StepBlock(step) }
                    if (chapter.examples.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Contoh",
                            color = skin.shiftLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        chapter.examples.forEach { ExampleCard(it) }
                    }
                    when (chapter.table) {
                        "const" -> ScientificConstants.entries.forEachIndexed { index, entry ->
                            ReferenceRow(index + 1, entry.symbol, entry.description)
                        }
                        "conv" -> UnitConversions.entries.forEachIndexed { index, entry ->
                            ReferenceRow(index + 1, entry.label, "")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Paragraph(text: String, color: Color) {
    if (text.isEmpty()) return
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun ChapterRow(number: Int, title: String, onOpen: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$number.",
            color = skin.lcdDim,
            fontSize = 15.sp,
            modifier = Modifier.width(30.dp),
        )
        Text(text = title, color = skin.bodyLabel, fontSize = 16.sp)
    }
}

/**
 * Satu contoh: tujuannya, urutan tombolnya sebagai deretan chip, lalu layar dan hasilnya
 * ditulis pada panel sewarna LCD supaya jelas mana yang muncul di kalkulator.
 */
@Composable
private fun ExampleCard(example: ManualExample) {
    val skin = LocalSkin.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = example.goal,
            color = skin.bodyLabel,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            example.press.split(" ").filter { it.isNotEmpty() }.forEach { cap ->
                Surface(color = skin.padWell, shape = RoundedCornerShape(5.dp)) {
                    Text(
                        text = cap,
                        color = skin.bodyLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Contoh digambar dengan menjalankan tombolnya sungguhan lalu memakai primitif
        // Natural Display yang sama dengan LCD, jadi pecahan bertingkat, tanda akar, dan
        // pangkat tampil persis seperti di kalkulator — bukan penulisan satu baris.
        val calculator = remember(example.keys, example.mode, example.setup) {
            Replay.runTokens(example.mode, example.setup, example.keys)
        }
        Surface(color = skin.lcdPaper, shape = RoundedCornerShape(6.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                val natural = calculator?.setup?.input != InputMode.LINE_IO
                val expression = calculator?.exprNodes().orEmpty()
                if (expression.isNotEmpty()) {
                    NaturalLine(expression, 19f, skin.lcdDim, natural)
                    Spacer(Modifier.height(4.dp))
                }
                // Mode berkisi — Statistics Editor, editor matriks dan vektor, TABLE, dan layar
                // MatAns — menampilkan tabelnya, bukan cuma angka hasilnya.
                val grid = calculator?.gridView()
                if (grid != null) {
                    GridPanel(
                        grid = grid,
                        blink = 0f,
                        onTapCell = { _, _ -> },
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                val result = calculator?.resultTree().orEmpty()
                if (result.isEmpty()) {
                    Text(
                        text = "= ${example.result}",
                        color = skin.lcdInk,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    NaturalLine(result, 23f, skin.lcdInk, natural, prefix = "= ")
                }
            }
        }
    }
}

/** Satu baris Natural Display setinggi isinya, dipakai contoh di panduan. */
@Composable
private fun NaturalLine(
    nodes: List<Node>,
    size: Float,
    color: androidx.compose.ui.graphics.Color,
    natural: Boolean,
    prefix: String = "",
) {
    val measurer = rememberTextMeasurer()
    val layout = ExprLayout(measurer, color, color, size, 0f, natural)
    val head = if (prefix.isEmpty()) null else ExprLayout(measurer, color, color, size, 0f).layout(
        listOf(id.sempoa570.engine.NumNode(prefix))
    )
    val box = layout.layout(nodes)
    val above = maxOf(box.above, head?.above ?: 0f)
    val below = maxOf(box.below, head?.below ?: 0f)
    val height = with(LocalDensity.current) { (above + below).toDp() }
    Canvas(Modifier.fillMaxWidth().height(height)) {
        head?.draw(this, above)
        translate(left = head?.width ?: 0f) { box.draw(this, above) }
    }
}

/**
 * Satu baris daftar rujukan CONST atau CONV: nomor dua digit yang diketik pemakai, lambangnya,
 * lalu keterangannya bila ada.
 */
@Composable
private fun ReferenceRow(number: Int, symbol: String, description: String) {
    val skin = LocalSkin.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(number),
            color = skin.lcdDim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = symbol,
            color = skin.bodyLabel,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(110.dp),
        )
        if (description.isNotEmpty()) {
            Text(text = description, color = skin.lcdDim, fontSize = 13.sp)
        }
    }
}

/** Satu langkah: urutan tombolnya ditulis di panel sewarna LCD, penjelasannya di bawahnya. */
@Composable
private fun StepBlock(step: ManualStep) {
    val skin = LocalSkin.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (step.keys.isNotEmpty()) {
            Surface(color = skin.lcdPaper, shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = step.keys,
                    color = skin.lcdInk,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(text = step.text, color = skin.bodyLabel, fontSize = 15.sp, lineHeight = 22.sp)
    }
}
