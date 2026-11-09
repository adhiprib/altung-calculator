package id.sempoa570.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sempoa570.engine.AngleUnit
import id.sempoa570.engine.MenuView
import id.sempoa570.engine.CallNode
import id.sempoa570.engine.CursorNode
import id.sempoa570.engine.ExprRenderer
import id.sempoa570.engine.GridView
import id.sempoa570.engine.Indicators
import id.sempoa570.engine.Key
import id.sempoa570.engine.KeyNode
import id.sempoa570.engine.Mode
import id.sempoa570.engine.Node
import id.sempoa570.engine.NumberBase
import id.sempoa570.engine.NumNode
import id.sempoa570.engine.TableNode

internal class ExprLayout(
    private val measurer: TextMeasurer,
    private val color: Color,
    private val cursorColor: Color,
    private val baseSize: Float,
    private val cursorAlpha: Float,
    /** Natural Display menyusun kalkulus bertingkat; Linear Display menulisnya sebaris. */
    private val natural: Boolean = true,
) {

    private val g = Glyphs(measurer, color)

    /** Isi slot ke-[index] sebuah struktur, ditandai supaya ketukan bisa masuk ke dalamnya. */
    private fun slot(nodes: List<Node>, index: Int, size: Float = baseSize): LayoutBox =
        layout(nodes, size).also { it.tag = SlotTag(index) }

    private fun horizontal(boxes: List<LayoutBox>) = g.horizontal(boxes)
    private fun placeholder(size: Float) = g.placeholder(size)
    private fun glyph(size: Float) = g.glyph(size)
    private fun text(value: String, size: Float) = g.text(value, size)
    private fun fraction(num: LayoutBox, den: LayoutBox, size: Float) = g.fraction(num, den, size)
    private fun radical(index: LayoutBox?, content: LayoutBox, size: Float) =
        g.radical(index, content, size)
    private fun superscript(base: LayoutBox, exponent: LayoutBox, size: Float) =
        g.superscript(base, exponent, size)
    private fun subscript(content: LayoutBox, size: Float) = g.subscript(content, size)

    fun layout(nodes: List<Node>, size: Float = baseSize): LayoutBox {
        // Slot yang hanya berisi kursor tetap selebar kursor, supaya tidak menyempit jadi nol.
        if (nodes.size == 1 && nodes[0] === CursorNode) return cursor(size, solo = true)
        val boxes = mutableListOf<LayoutBox>()
        var dmsIndex = 0
        var i = 0
        // Tiap kotak ditandai simpul terakhir yang dilahapnya, supaya ketukan tahu celah mana
        // yang terdekat. POST_DMS dan ×10 melahap lebih dari satu simpul.
        fun record(lastNode: Int) {
            boxes.last().tag = NodeTag(lastNode)
        }
        while (i < nodes.size) {
            val node = nodes[i]
            when {
                node is KeyNode && node.key == Key.POST_DMS -> {
                    boxes += text(DMS_MARKS[dmsIndex.coerceAtMost(2)], size)
                    record(i)
                    dmsIndex++
                    i++
                }
                // ×10 dengan eksponen sebagai superskrip, bukan huruf E (unit fisik).
                node is KeyNode && node.key == Key.EXP10 -> {
                    val end = exponentEnd(nodes, i + 1)
                    boxes += superscript(
                        text("×10", size),
                        layout(nodes.subList(i + 1, end), size * 0.62f),
                        size,
                    )
                    record(end - 1)
                    i = end
                }
                // Kursor di ujung daftar digambar sesudah karakter terakhir, bukan menimpanya.
                node === CursorNode -> {
                    boxes += cursor(size, atEnd = i == nodes.lastIndex)
                    record(i)
                    i++
                }
                else -> {
                    if (node is KeyNode && node.key in RESET_DMS) dmsIndex = 0
                    boxes += layoutNode(node, size)
                    record(i)
                    i++
                }
            }
        }
        if (boxes.isEmpty()) return placeholder(size)
        return horizontal(boxes)
    }

    /** Batas akhir eksponen ×10: tanda negatif opsional, satu runtun angka, dan kursor. */
    private fun exponentEnd(nodes: List<Node>, from: Int): Int {
        var j = from
        var tookNumber = false
        while (j < nodes.size) {
            val next = nodes[j]
            val fits = when {
                next === CursorNode -> true
                next is KeyNode && next.key == Key.OP_NEG -> !tookNumber
                next is NumNode -> !tookNumber
                else -> false
            }
            if (!fits) break
            if (next is NumNode) tookNumber = true
            j++
        }
        return j
    }

    private fun layoutNode(node: Node, size: Float): LayoutBox = when (node) {
        is NumNode -> text(node.text, size)
        is KeyNode -> text(ExprRenderer.symbol(node.key), size)
        CursorNode -> cursor(size)
        is TableNode -> text(ExprRenderer.tableLabel(node), size)
        is CallNode -> layoutCall(node, size)
    }

    private fun layoutCall(node: CallNode, size: Float): LayoutBox {
        val small = size * 0.58f
        return when (node.key) {
            Key.FRAC -> fraction(slot(node.args[0], 0, size), slot(node.args[1], 1, size), size)
            Key.FRAC_MIXED -> horizontal(
                listOf(
                    slot(node.args[0], 0, size),
                    fraction(slot(node.args[1], 1, size), slot(node.args[2], 2, size), size),
                )
            )
            Key.FN_SQRT -> radical(null, slot(node.args[0], 0, size), size)
            Key.FN_CBRT -> radical(text("3", small), slot(node.args[0], 0, size), size)
            Key.OP_NROOT -> radical(slot(node.args[0], 0, small), slot(node.args[1], 1, size), size)
            Key.OP_POW -> superscript(slot(node.args[0], 0, size), slot(node.args[1], 1, small), size)
            Key.FN_POW10 -> superscript(text("10", size), slot(node.args[0], 0, small), size)
            Key.FN_EXP -> superscript(text("e", size), slot(node.args[0], 0, small), size)
            Key.FN_LOGBASE -> horizontal(
                listOf(
                    text("log", size),
                    subscript(slot(node.args[0], 0, small), size),
                    text("(", size),
                    slot(node.args[1], 1, size),
                    text(")", size),
                )
            )
            // Kurung tutup baru dilukis setelah `)` ditekan; unit fisik menampilkan
            // `4×sin(30)×(30+1▸` selama grupnya belum ditutup (manual hlm. 16 Contoh 1).
            Key.PAREN_OPEN -> horizontal(
                listOfNotNull(
                    text("(", size),
                    slot(node.args[0], 0, size),
                    if (node.closed) text(")", size) else null,
                )
            )
            Key.FN_ABS -> horizontal(
                listOf(text("Abs(", size), slot(node.args[0], 0, size), text(")", size))
            )
            // Linear Display memakai sintaks sebaris ∫(f,a,b,tol), d/dx(f,a,tol), dan Σ(f,a,b)
            // sesuai manual hlm. 30, 32, dan 33; Natural Display menyusunnya bertingkat.
            Key.FN_INTEGRAL, Key.FN_DERIV, Key.FN_SIGMA ->
                if (!natural || node.args.size > NATURAL_ARGS.getValue(node.key)) {
                    linearCall(node, size)
                } else when (node.key) {
                    Key.FN_INTEGRAL -> integral(
                node.args.getOrNull(2), node.args.getOrNull(1), size,
                horizontal(listOf(slot(node.args[0], 0, size), text("dx", size))),
            )
                    Key.FN_SIGMA -> bigOperator(
                        "Σ",
                        node.args.getOrNull(2),
                        node.args.getOrNull(1),
                        size,
                        slot(node.args[0], 0, size),
                        lowerPrefix = "X=",
                    )
                    else -> horizontal(
                listOf(
                    fraction(text("d", small), text("dx", small), size),
                    text("(", size),
                    slot(node.args[0], 0, size),
                    text(")", size),
                    // Garis tegak lalu X= sebagai subskrip, persis unit fisik.
                    text("|", size),
                            subscript(
                                horizontal(listOf(text("x=", small), slot(node.args[1], 1, small))),
                                size,
                            ),
                        )
                    )
                }
            else -> {
                val parts = mutableListOf(text(ExprRenderer.symbol(node.key), size))
                node.args.forEachIndexed { index, arg ->
                    if (index > 0) parts += text(",", size)
                    parts += slot(arg, index, size)
                }
                parts += text(")", size)
                horizontal(parts)
            }
        }
    }

    /**
     * Slot kosong pada Natural Display digambar sebagai kotak garis, persis seperti templat
     * pecahan atau akar yang belum diisi di kalkulator aslinya.
     */

    /**
     * Ukuran satu angka pada ukuran huruf tertentu. Kursor dan kotak slot kosong diukur dari
     * sini, bukan dari angka piksel tetap, supaya besarnya selalu sepadan dengan yang diketik.
     */

    /** Kotak kursor dari tata letak terakhir; penggulir baris ekspresi mencari letaknya. */
    var cursorBox: LayoutBox? = null
        private set

    /**
     * Kursor berkedip setinggi angka di sebelahnya, seperti Natural Display. Seperti unit fisik,
     * kursor tidak mendorong teks: lebarnya nol dan digambar di tengah celah antar-karakter.
     * Kalau kursor ikut memakan lebar, pembilang atau penyebut yang memuatnya jadi lebih lebar
     * sehingga pecahan tidak lagi segaris, dan teks bergeser tiap kursor dipindah.
     * [solo]: satu-satunya isi slot, jadi tetap diberi lebar agar slotnya terlihat.
     * [atEnd]: di ujung daftar, jadi digambar mulai tepat sesudah karakter terakhir.
     */
    private fun cursor(size: Float, solo: Boolean = false, atEnd: Boolean = false): LayoutBox {
        val digit = glyph(size)
        val w = maxOf(digit.width * 0.16f, 3f)
        val left = if (solo || atEnd) 0f else -w / 2
        return LayoutBox(if (solo) w else 0f, digit.above, digit.below) { baseline ->
            drawRect(
                color = cursorColor.copy(alpha = cursorAlpha),
                topLeft = Offset(left, baseline - digit.above),
                size = Size(w, digit.height),
            )
        }.also { cursorBox = it }
    }

    /**
     * Integral: tanda seru panjang, batas atas dan bawah berdiri di kanan tanda, lalu
     * integrannya. Persis tampilan unit fisik pada contoh cos(X)dx dari 0 sampai setengah pi.
     */
    /** Bentuk sebaris: nama fungsi, argumen dipisah koma, lalu kurung tutup. */
    private fun linearCall(node: CallNode, size: Float): LayoutBox {
        val parts = mutableListOf(text(ExprRenderer.symbol(node.key), size))
        node.args.forEachIndexed { index, arg ->
            if (index > 0) parts += text(",", size)
            parts += slot(arg, index, size)
        }
        parts += text(")", size)
        return horizontal(parts)
    }

    private fun integral(
        upper: List<Node>?,
        lower: List<Node>?,
        size: Float,
        body: LayoutBox,
    ): LayoutBox {
        val small = size * 0.58f
        val sign = text("\u222b", size * 2.0f)
        val top = (upper?.let { layout(it, small) } ?: placeholder(small)).also { it.tag = SlotTag(2) }
        val bottom = (lower?.let { layout(it, small) } ?: placeholder(small)).also { it.tag = SlotTag(1) }
        val limits = maxOf(top.width, bottom.width)
        val gap = size * 0.12f
        val width = sign.width + limits + gap + body.width
        val placed = listOf(
            Placed(sign.width, -sign.above + top.above, top),
            Placed(sign.width, sign.below - bottom.below, bottom),
            Placed(sign.width + limits + gap, 0f, body),
        )
        return LayoutBox(width, sign.above, sign.below, placed) { baseline ->
            sign.draw(this, baseline)
            translate(left = sign.width) {
                top.draw(this, baseline - sign.above + top.above)
                bottom.draw(this, baseline + sign.below - bottom.below)
            }
            translate(left = sign.width + limits + gap) { body.draw(this, baseline) }
        }
    }

    /** Operator besar seperti sigma: batas atas dan bawah menempel pada simbolnya. */
    private fun bigOperator(
        glyph: String,
        upper: List<Node>?,
        lower: List<Node>?,
        size: Float,
        body: LayoutBox,
        /** Batas bawah sigma ditulis X=1, bukan sekadar 1, seperti unit fisik. */
        lowerPrefix: String = "",
    ): LayoutBox {
        val small = size * 0.6f
        val sign = text(glyph, size * 1.5f)
        val top = (upper?.let { layout(it, small) } ?: placeholder(small)).also { it.tag = SlotTag(2) }
        val value = (lower?.let { layout(it, small) } ?: placeholder(small)).also { it.tag = SlotTag(1) }
        val bottom =
            if (lowerPrefix.isEmpty()) value
            else horizontal(listOf(text(lowerPrefix, small), value))
        val headWidth = maxOf(sign.width, top.width, bottom.width)
        val head = LayoutBox(
            headWidth,
            sign.above + top.height + size * 0.1f,
            sign.below + bottom.height + size * 0.1f,
            listOf(
                Placed((headWidth - top.width) / 2, -sign.above - size * 0.05f, top),
                Placed(
                    (headWidth - bottom.width) / 2,
                    sign.below + bottom.above + size * 0.05f,
                    bottom,
                ),
            ),
        ) { baseline ->
            translate(left = (headWidth - sign.width) / 2) { sign.draw(this, baseline) }
            translate(left = (headWidth - top.width) / 2) {
                top.draw(this, baseline - sign.above - size * 0.05f)
            }
            translate(left = (headWidth - bottom.width) / 2) {
                bottom.draw(this, baseline + sign.below + bottom.above + size * 0.05f)
            }
        }
        return horizontal(listOf(head, body))
    }

    private companion object {
        /** Jumlah argumen yang muat pada bentuk bertingkat tiap kalkulus. */
        val NATURAL_ARGS = mapOf(Key.FN_INTEGRAL to 3, Key.FN_DERIV to 2, Key.FN_SIGMA to 3)

        val DMS_MARKS = listOf("°", "'", "\"")
        val RESET_DMS = setOf(Key.OP_ADD, Key.OP_SUB, Key.OP_MUL, Key.OP_DIV)
    }
}

private val LcdSmall = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
private val LcdBody = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace)

/** Layar error memakai satu ukuran untuk pesan dan petunjuk, seperti dot-matrix unit fisik. */
private val LcdError = TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Monospace)

/**
 * Layar tanya nomor dua digit CONST dan CONV (manual hlm. 42 dan 44): judul dan pertanyaan rata
 * kiri, kotak isian rata kanan.
 */
@Composable
private fun PromptPanel(lines: List<String>) {
    val skin = LocalSkin.current
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        lines.dropLast(1).forEach {
            Text(it, style = LcdError, color = skin.lcdInk, maxLines = 1)
        }
        Text(
            text = lines.last(),
            style = LcdError,
            color = skin.lcdInk,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Layar error unit fisik (manual hlm. 72): pesan di baris atas, lalu dua baris petunjuk. AC
 * membatalkan beserta perhitungannya, ◄ dan ► kembali ke ekspresi untuk diperbaiki.
 */
@Composable
private fun ErrorPanel(message: String) {
    val skin = LocalSkin.current
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(message, style = LcdError, color = skin.lcdInk, maxLines = 1)
        Spacer(Modifier.height(18.dp))
        Text("[AC]  :Cancel", style = LcdError, color = skin.lcdInk, maxLines = 1)
        Text("[◄][►]:Goto", style = LcdError, color = skin.lcdInk, maxLines = 1)
    }
}

/**
 * Layar LCD: baris indikator, baris ekspresi Natural Display yang dapat digulir, dan baris hasil
 * rata kanan (PRD §4). Semuanya digambar vektor, bukan tiruan dot matrix (keputusan 4).
 */
@Composable
fun LcdDisplay(
    expression: List<Node>,
    result: List<Node>,
    aux: String,
    indicators: Indicators,
    layer: Layer,
    menu: MenuView? = null,
    grid: GridView? = null,
    modifier: Modifier = Modifier,
    resultLabel: String = "",
    auxLabel: String = "",
    error: String = "",
    prompt: List<String> = emptyList(),
    notice: String = "",
    onMenuSelect: (Int) -> Unit = {},
    onCopyExpr: () -> Unit = {},
    onCopyResult: () -> Unit = {},
    onTapExpr: (IntArray) -> Unit = {},
    onTapCell: (Int, Int) -> Unit = { _, _ -> },
) {
    val skin = LocalSkin.current
    val measurer = rememberTextMeasurer()
    val hits = remember { ExprHits() }
    val blink by rememberInfiniteTransition(label = "kursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kedip",
    )

    Column(modifier.fillMaxSize().background(skin.lcdPaper)) {
        if (menu != null) {
            MenuList(menu, onMenuSelect)
            return@Column
        }
        IndicatorRow(indicators, layer, notice)
        if (error.isNotEmpty()) {
            ErrorPanel(error)
            return@Column
        }
        if (prompt.isNotEmpty()) {
            PromptPanel(prompt)
            return@Column
        }
        if (grid != null) {
            GridPanel(grid, blink, onTapCell, Modifier.fillMaxWidth().weight(1f))
            return@Column
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(horizontal = 10.dp)
                // Ketukan menaruh kursor pada celah terdekat antar simpul tingkat atas.
                // Tekan lama menyalin baris ekspresi; ketukan biasa menaruh kursor.
                .pointerInput(onTapExpr, onCopyExpr) {
                    detectTapGestures(
                        onLongPress = { onCopyExpr() },
                        onTap = { offset -> hits.pathAt(offset.x, offset.y)?.let(onTapExpr) },
                    )
                }
                // Geser jari mendatar menggulir ekspresi panjang tanpa memindah kursor.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        hits.shift -= dx
                    }
                },
        ) {
            val layout = ExprLayout(measurer, skin.lcdInk, skin.lcdInk, 26f, blink, indicators.math)
            val box = layout.layout(expression)
            val baseline = box.above.coerceAtLeast(size.height * 0.4f)
            hits.root = box
            hits.baseline = baseline
            hits.scroll(box, layout.cursorBox, size.width)
            translate(left = -hits.shift) { box.draw(this, baseline) }
        }
        // Baris hasil juga digambar dua dimensi: pecahan bertingkat, bukan teks datar.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 12.dp)
                // Tekan lama menyalin hasilnya; baris ini tidak punya ketukan biasa.
                .pointerInput(onCopyResult) {
                    detectTapGestures(onLongPress = { onCopyResult() })
                },
        ) {
            if (result.isEmpty()) return@Canvas
            val layout = ExprLayout(measurer, skin.lcdInk, skin.lcdInk, 30f, 0f, indicators.math)
            val box = layout.layout(result)
            val left = (size.width - box.width).coerceAtLeast(0f)
            val baseline = box.above.coerceAtMost((size.height - box.below).coerceAtLeast(box.above))
            // Unit fisik menulis label seperti `X=` rata kiri, angkanya rata kanan pada baris
            // yang sama (manual hlm. 40).
            if (resultLabel.isNotEmpty()) layout.layout(listOf(NumNode(resultLabel))).draw(this, baseline)
            translate(left = left) { box.draw(this, baseline) }
        }
        // Baris ketiga: `L-R=` pada layar solusi SOLVE, di bawah akarnya (manual hlm. 40).
        if (aux.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                if (auxLabel.isNotEmpty()) {
                    Text(auxLabel, style = LcdBody, color = skin.lcdDim, maxLines = 1)
                }
                Text(
                    text = aux.removePrefix(auxLabel),
                    style = LcdBody,
                    color = skin.lcdDim,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Geseran agar kursor di [x] (selebar [width]) terlihat pada layar selebar [view]: berubah hanya
 * bila kursor keluar layar, sejauh yang perlu, menyisakan kira-kira satu angka di tepi supaya isi
 * di balik tepi tetap terasa ada.
 */
private fun follow(x: Float, width: Float, view: Float, previous: Float): Float {
    val margin = view * 0.06f
    var shift = previous
    if (x - shift < margin) shift = x - margin
    if (x + width - shift > view - margin) shift = x + width - view + margin
    return shift
}

/** Letak mendatar [target] di dalam [box], menelusuri anak-anaknya; null bila tak ditemukan. */
private fun xOf(box: LayoutBox, target: LayoutBox, x: Float = 0f): Float? {
    if (box === target) return x
    for (part in box.parts) xOf(part.box, target, x + part.dx)?.let { return it }
    return null
}

/** Simpul ke-[index] di dalam daftar yang sedang dirangkai [ExprLayout.layout]. */
private class NodeTag(val index: Int)

/** Isi slot ke-[index] sebuah struktur berslot, misalnya pembilang pecahan. */
private class SlotTag(val index: Int)

/**
 * Pohon kotak baris ekspresi pada gambar terakhir, supaya penangan sentuhan dapat menelusuri
 * susunan yang sama dengan yang digambar. Sengaja bukan state Compose: isinya ditulis tiap
 * gambar dan tidak boleh memicu penyusunan ulang.
 */
private class ExprHits {
    var root: LayoutBox? = null
    var baseline = 0f

    /**
     * Geseran gulir horizontal; geser jari mengubahnya langsung. State Compose supaya seretan
     * memicu gambar ulang sendiri, tanpa bergantung pada kursor yang berkedip.
     */
    var shift by mutableFloatStateOf(0f)

    private var lastCursorX: Float? = Float.NaN
    private var lastWidth = -1f

    /**
     * Gulir baris ekspresi (PRD §4.4): mengikuti kursor hanya saat kursor berpindah atau isinya
     * berubah, jadi ◄ yang mencapai tepi kiri menggulir ke awal ekspresi seperti unit fisik,
     * sedangkan hasil geser jari bertahan sampai tombol berikutnya. Tanpa kursor, misalnya
     * sesudah `=`, ekspresi baru tampil dari ujung kanannya.
     */
    fun scroll(box: LayoutBox, cursor: LayoutBox?, view: Float) {
        val furthest = (box.width - view).coerceAtLeast(0f)
        val x = cursor?.let { xOf(box, it) }
        if (x != lastCursorX || box.width != lastWidth) {
            shift = if (x == null || cursor == null) furthest else follow(x, cursor.width, view, shift)
        }
        lastCursorX = x
        lastWidth = box.width
        shift = shift.coerceIn(0f, furthest)
    }

    /**
     * Jalur ketukan: pasangan (indeks simpul, indeks slot) berulang sedalam struktur yang
     * ditembus, lalu satu bilangan penutup yaitu simpul yang kursornya berada tepat sesudahnya;
     * -1 berarti di awal daftar itu.
     */
    fun pathAt(x: Float, y: Float): IntArray? {
        val box = root ?: return null
        val steps = mutableListOf<Int>()
        descend(box, x + shift, baseline, y, steps)
        return steps.toIntArray()
    }

    private fun descend(
        container: LayoutBox,
        x: Float,
        originY: Float,
        y: Float,
        steps: MutableList<Int>,
        originX: Float = 0f,
    ) {
        for (part in container.parts) {
            val index = (part.box.tag as? NodeTag)?.index ?: continue
            val left = originX + part.dx
            if (x < left || x > left + part.box.width) continue
            val inner = slotAt(part.box, left, originY + part.dy, x, y) ?: continue
            steps += index
            steps += inner.index
            descend(inner.box, x, inner.baseline, y, steps, inner.left)
            return
        }
        steps += nearestGap(container, originX, x)
    }

    /** Celah terdekat di satu tingkat: -1 di depan simpul pertama, selain itu simpul di kirinya. */
    private fun nearestGap(container: LayoutBox, originX: Float, x: Float): Int {
        var best = -1
        var nearest = kotlin.math.abs(x - originX)
        for (part in container.parts) {
            val index = (part.box.tag as? NodeTag)?.index ?: continue
            val edge = originX + part.dx + part.box.width
            val distance = kotlin.math.abs(x - edge)
            if (distance <= nearest) {
                nearest = distance
                best = index
            }
        }
        return best
    }

    private class Found(val index: Int, val box: LayoutBox, val left: Float, val baseline: Float)

    /** Slot terluar yang memuat titik ketuk, dicari turun dari kotak sebuah simpul. */
    private fun slotAt(box: LayoutBox, left: Float, baseline: Float, x: Float, y: Float): Found? {
        for (part in box.parts) {
            val childLeft = left + part.dx
            val childBaseline = baseline + part.dy
            val slot = part.box.tag as? SlotTag
            val inside = x >= childLeft && x <= childLeft + part.box.width &&
                y >= childBaseline - part.box.above && y <= childBaseline + part.box.below
            if (slot != null) {
                if (inside) return Found(slot.index, part.box, childLeft, childBaseline)
                continue
            }
            slotAt(part.box, childLeft, childBaseline, x, y)?.let { return it }
        }
        return null
    }
}

/** Letak sel kisi, dipakai bersama oleh penggambar dan penangan ketukan. */
private class GridGeometry(
    val unit: Float,
    val pad: Float,
    val rowHeight: Float,
    val widths: FloatArray,
    val numberWidth: Float,
    val bracket: Float,
    val left: Float,
    val hasHeader: Boolean,
    val headerTop: Float,
    val bodyTop: Float,
    val first: Int,
    val last: Int,
) {
    val gridWidth: Float get() = numberWidth + widths.sum()

    fun columnLeft(column: Int): Float = left + numberWidth + widths.take(column).sum()

    /** Sel pada titik ketuk, atau null bila di luar kisi. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        if (y < bodyTop) return null
        val row = first + ((y - bodyTop) / rowHeight).toInt()
        if (row >= last) return null
        for (c in widths.indices) {
            val start = columnLeft(c)
            if (x >= start && x < start + widths[c]) return row to c
        }
        return null
    }
}

private fun gridGeometry(
    grid: GridView,
    measurer: TextMeasurer,
    cell: TextStyle,
    head: TextStyle,
    height: Float,
): GridGeometry {
    // Ukuran kisi diturunkan dari tinggi angka yang sebenarnya, bukan dari piksel tetap.
    val unit = measurer.measure("0", cell).size.height.toFloat()
    val pad = unit * 0.32f
    val rowHeight = unit * 1.3f
    val widths = FloatArray(grid.columns.size) { c ->
        var w = measurer.measure(grid.columns[c], head).size.width.toFloat()
        for (row in grid.rows) {
            val text = row.getOrElse(c) { "" }
            if (text.isNotEmpty()) {
                w = maxOf(w, measurer.measure(text, cell).size.width.toFloat())
            }
        }
        maxOf(w + pad * 2, unit * 2.1f)
    }
    val numberWidth = if (grid.numbered) unit * 1.1f else 0f
    val bracket = if (grid.bracketed) unit * 0.5f else 0f
    // Kisi diletakkan di sepertiga atas panel, menyisakan baris bawah untuk angka yang diketik.
    // Tajuk kolom dan nomor baris berdiri sendiri: Coefficient Editor kuadrat bertajuk
    // a b c tanpa nomor baris, sedangkan editor matriks tanpa keduanya.
    val hasHeader = grid.columns.any { it.isNotEmpty() }
    val headerHeight = if (hasHeader) unit * 0.9f else 0f
    val bodyHeight = headerHeight + rowHeight * grid.rows.size.coerceAtMost(3)
    val headerTop = ((height - unit * 1.6f - bodyHeight) / 2).coerceAtLeast(unit * 0.2f)
    // Tiga baris terlihat sekaligus, digulir mengikuti kursor seperti LCD aslinya.
    val visible = 3
    val first = (grid.cursorRow - visible + 1).coerceAtLeast(0)
        .coerceAtMost((grid.rows.size - visible).coerceAtLeast(0))
    return GridGeometry(
        unit = unit,
        pad = pad,
        rowHeight = rowHeight,
        widths = widths,
        numberWidth = numberWidth,
        bracket = bracket,
        left = unit * 0.7f + bracket,
        hasHeader = hasHeader,
        headerTop = headerTop,
        bodyTop = headerTop + headerHeight,
        first = first,
        last = (first + visible).coerceAtMost(grid.rows.size),
    )
}

/**
 * Kisi data: Statistics Editor, Matrix dan Vector Editor, serta layar MatAns dan VctAns.
 *
 * Sel terpilih digambar terbalik warnanya, kisi matriks diapit kurung siku, dan angka yang
 * sedang diketik muncul rata kanan di baris bawah, persis unit fisik.
 */
@Composable
internal fun GridPanel(
    grid: GridView,
    blink: Float,
    onTapCell: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val measurer = rememberTextMeasurer()
    val cell = TextStyle(fontSize = 17.sp, fontFamily = FontFamily.Monospace, color = skin.lcdInk)
    val head = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = skin.lcdDim)
    Canvas(
        modifier.pointerInput(grid, onTapCell) {
            detectTapGestures { offset ->
                gridGeometry(grid, measurer, cell, head, size.height.toFloat())
                    .cellAt(offset.x, offset.y)
                    ?.let { (row, column) -> onTapCell(row, column) }
            }
        }
    ) {
        val g = gridGeometry(grid, measurer, cell, head, size.height)
        val unit = g.unit
        val pad = g.pad
        val rowHeight = g.rowHeight
        val widths = g.widths
        val numberWidth = g.numberWidth
        val bracket = g.bracket
        val left = g.left
        if (grid.title.isNotEmpty()) {
            drawText(measurer.measure(grid.title, head), topLeft = Offset(2f, 2f))
        }

        if (g.hasHeader) {
            grid.columns.forEachIndexed { c, name ->
                val measured = measurer.measure(name, head)
                val x = g.columnLeft(c) + (widths[c] - measured.size.width) / 2
                drawText(measured, topLeft = Offset(x, g.headerTop))
            }
        }

        val top = g.bodyTop
        val first = g.first
        val last = g.last

        for (index in first until last) {
            val y = top + (index - first) * rowHeight
            if (grid.numbered) {
                drawText(
                    measurer.measure("${index + 1}", head),
                    topLeft = Offset(left, y + (rowHeight - unit * 0.7f) / 2),
                )
            }
            grid.rows[index].forEachIndexed { c, text ->
                val x = g.columnLeft(c)
                val selected = index == grid.cursorRow && c == grid.cursorCol
                if (selected) {
                    drawRect(
                        color = skin.lcdInk,
                        topLeft = Offset(x, y + rowHeight * 0.08f),
                        size = Size(widths[c], rowHeight * 0.84f),
                    )
                }
                if (text.isNotEmpty()) {
                    val measured = measurer.measure(
                        text,
                        if (selected) cell.copy(color = skin.lcdPaper) else cell,
                    )
                    drawText(
                        measured,
                        topLeft = Offset(
                            x + widths[c] - measured.size.width - pad,
                            y + (rowHeight - measured.size.height) / 2,
                        ),
                    )
                }
            }
        }

        if (grid.bracketed) {
            val gridTop = top
            val bottom = top + (last - first) * rowHeight
            val arm = unit * 0.28f
            val stroke = unit * 0.07f
            val leftEdge = left - bracket
            val rightEdge = left + g.gridWidth + bracket * 0.4f
            drawLine(skin.lcdInk, Offset(leftEdge, gridTop), Offset(leftEdge, bottom), stroke)
            drawLine(skin.lcdInk, Offset(leftEdge, gridTop), Offset(leftEdge + arm, gridTop), stroke)
            drawLine(skin.lcdInk, Offset(leftEdge, bottom), Offset(leftEdge + arm, bottom), stroke)
            drawLine(skin.lcdInk, Offset(rightEdge, gridTop), Offset(rightEdge, bottom), stroke)
            drawLine(skin.lcdInk, Offset(rightEdge - arm, gridTop), Offset(rightEdge, gridTop), stroke)
            drawLine(skin.lcdInk, Offset(rightEdge - arm, bottom), Offset(rightEdge, bottom), stroke)
        }

        // Baris bawah: angka yang sedang diketik, rata kanan seperti baris hasil.
        if (grid.typed.isNotEmpty()) {
            val typed = ExprLayout(measurer, skin.lcdInk, skin.lcdInk, 26f, blink).layout(grid.typed)
            val x = (size.width - typed.width - unit * 0.3f).coerceAtLeast(0f)
            translate(left = x) { typed.draw(this, size.height - typed.below - unit * 0.2f) }
        }
    }
}

/**
 * Menu dirender sebagai daftar bernomor yang dipilih dengan tombol angka, bukan sebagai dialog
 * Android (PRD §4.4). Menu berbutir pendek disusun dua kolom seperti unit fisik.
 */
@Composable
private fun MenuList(menu: MenuView, onSelect: (Int) -> Unit) {
    val skin = LocalSkin.current
    val cols = menu.columns
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = menu.title, style = LcdSmall, color = skin.lcdDim, maxLines = 1)
            val arrows = buildString {
                if (menu.hasPrev) append("▲")
                if (menu.hasNext) append("▼")
            }
            Text(text = arrows, style = LcdSmall, color = skin.lcdDim, maxLines = 1)
        }
        menu.items.chunked(cols).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIndex, item ->
                    // Nomor yang tercetak memang nomor tombol angkanya, jadi ketukan cukup
                    // mengirim angka itu ke engine lewat jalur yang sama.
                    val number = rowIndex * cols + colIndex + 1
                    Text(
                        text = "$number:$item",
                        style = LcdBody,
                        color = skin.lcdInk,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).clickable { onSelect(number) },
                    )
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (menu.footer.isNotEmpty()) {
            // Nama pembuat duduk di tepi bawah panel, sebesar butir menu selama masih muat.
            Spacer(Modifier.weight(1f))
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val room = with(LocalDensity.current) { maxWidth.toPx() }
                var points = LcdBody.fontSize.value
                while (points > 9f &&
                    measurer.measure(menu.footer, LcdBody.copy(fontSize = points.sp)).size.width > room
                ) {
                    points -= 0.5f
                }
                Text(
                    text = menu.footer,
                    style = LcdBody.copy(fontSize = points.sp),
                    color = skin.lcdDim,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** Baris indikator kecil di tepi atas LCD: status di kiri, satuan dan format di kanan (PRD §4.4). */
@Composable
private fun IndicatorRow(indicators: Indicators, layer: Layer, notice: String = "") {
    val skin = LocalSkin.current
    val left = buildList {
        if (layer == Layer.SHIFT) add("SHIFT")
        if (layer == Layer.ALPHA) add("ALPHA")
        modeMark(indicators)?.let { add(it) }
        if (indicators.store) add("STO")
        if (indicators.recall) add("RCL")
        if (indicators.memory) add("M")
    }
    val right = buildList {
        add(
            when (indicators.angle) {
                AngleUnit.DEG -> "D"
                AngleUnit.RAD -> "R"
                AngleUnit.GRA -> "G"
            }
        )
        if (indicators.fix) add("FIX")
        if (indicators.sci) add("SCI")
        if (indicators.math) add("Math")
        if (indicators.insert) add("INS")
        if (indicators.replayUp) add("▲")
        if (indicators.replayDown) add("▼")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = left.joinToString(" "), style = LcdSmall, color = skin.lcdDim, maxLines = 1)
        // Tulisan sesaat seperti `Tersalin` menumpang baris indikator, tetap di dalam panel LCD
        // dan bukan Toast Android (PRD §4.4).
        Text(
            text = if (notice.isEmpty()) right.joinToString(" ") else notice,
            style = LcdSmall,
            color = if (notice.isEmpty()) skin.lcdDim else skin.lcdInk,
            maxLines = 1,
        )
    }
}

/** Penanda mode di tepi atas LCD; BASE-N menampilkan basis aktif seperti unit fisik. */
private fun modeMark(indicators: Indicators): String? = when (indicators.mode) {
    Mode.CMPLX -> "CMPLX"
    Mode.STAT -> "STAT"
    Mode.MATRIX -> "MAT"
    Mode.VECTOR -> "VCT"
    Mode.BASE -> when (indicators.base) {
        NumberBase.DEC -> "Dec"
        NumberBase.HEX -> "Hex"
        NumberBase.BIN -> "Bin"
        NumberBase.OCT -> "Oct"
    }
    else -> null
}
