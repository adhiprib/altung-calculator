package id.sempoa570.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sempoa570.engine.GridView
import id.sempoa570.engine.Indicators
import id.sempoa570.engine.Key
import id.sempoa570.engine.MenuView
import id.sempoa570.engine.Node

/**
 * Tema aplikasi: warna badan dari [Skin], yang menyalin unit fisik atas permintaan pemilik
 * produk, dan ripple yang sama dengan material3 dulu (lihat Primitives.kt).
 */
@Composable
fun SempoaTheme(skinIndex: Int = 0, content: @Composable () -> Unit) {
    val skin = Skin.byIndex(skinIndex)
    CompositionLocalProvider(
        LocalSkin provides skin,
        LocalIndication provides SempoaRipple,
        content = content,
    )
}

/** Layar utama: LCD di bagian atas, papan tombol di bawahnya (PRD §2). */
@Composable
fun CalculatorScreen(
    keymap: Keymap,
    expression: List<Node>,
    result: List<Node>,
    aux: String,
    indicators: Indicators,
    layer: Layer,
    menu: MenuView?,
    grid: GridView?,
    onKey: (KeyEntry) -> Unit,
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
    Surface(modifier = modifier.fillMaxSize(), color = skin.body) {
        // Sisakan ruang untuk status bar dan bilah navigasi, supaya baris indikator tidak
        // tertutup di atas dan baris tombol terbawah tidak terpotong.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Bingkai gelap mengelilingi panel LCD, seperti jendela layar unit fisik.
            Surface(
                color = skin.lcdFrame,
                shape = RoundedCornerShape(10.dp),
                // Tinggi dipatok supaya papan tombol tidak bergeser saat menu terbuka.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(218.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                LcdDisplay(
                    expression = expression,
                    result = result,
                    aux = aux,
                    indicators = indicators,
                    layer = layer,
                    menu = menu,
                    grid = grid,
                    modifier = Modifier.padding(5.dp),
                    resultLabel = resultLabel,
                    auxLabel = auxLabel,
                    error = error,
                    prompt = prompt,
                    notice = notice,
                    onMenuSelect = onMenuSelect,
                    onCopyExpr = onCopyExpr,
                    onCopyResult = onCopyResult,
                    onTapExpr = onTapExpr,
                    onTapCell = onTapCell,
                )
            }
            Keypad(
                keymap = keymap,
                onKey = onKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .padding(top = 2.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * Papan tombol yang menyalin susunan dan rupa unit fisik fx-570ES PLUS.
 *
 * Seperti kalkulator aslinya, tulisan fungsi SHIFT, ALPHA, BASE-N, dan CMPLX dicetak di badan
 * kalkulator tepat di atas tombol, bukan pada keycap, sehingga keycap bebas untuk satu label
 * besar. Tombol SHIFT, ALPHA, MODE, dan ON berupa tombol logam kecil di baris paling atas,
 * mengapit bantalan replay.
 */
@Composable
fun Keypad(keymap: Keymap, onKey: (KeyEntry) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TopStrip(keymap, onKey)
        KeypadLayout.functionRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { id ->
                    if (id == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val entry = keymap[id]
                        KeyCell(entry, CapStyle.FUNCTION, { onKey(entry) }, Modifier.weight(1f))
                    }
                }
            }
        }
        KeypadLayout.numberRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1.15f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { id ->
                    val entry = keymap[id]
                    val style = when (entry.main.token) {
                        Key.EDIT_DEL.name, Key.EDIT_AC.name -> CapStyle.CLEAR
                        else -> CapStyle.DIGIT
                    }
                    KeyCell(entry, style, { onKey(entry) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Baris kecil paling atas: SHIFT dan ALPHA di kiri, bantalan replay di tengah, MODE dan ON di
 * kanan. Keempat tombol itu polos seperti unit fisik; namanya tercetak di badan kalkulator.
 */
@Composable
private fun TopStrip(keymap: Keymap, onKey: (KeyEntry) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TopPair(keymap, listOf(KeypadLayout.SHIFT, KeypadLayout.ALPHA), onKey, Modifier.weight(1f))
        // Bantalan tetap bujur sangkar supaya keempat tombolnya proporsional, tetapi digeser
        // turun sehingga menjorok ke celah kosong di baris CALC dan integral seperti unit fisik.
        ReplayPad(keymap, onKey, Modifier.weight(1f).padding(top = 32.dp).requiredHeight(158.dp))
        TopPair(keymap, listOf(KeypadLayout.MODE, KeypadLayout.ON), onKey, Modifier.weight(1f))
    }
}

/**
 * Sepasang tombol logam kecil di atas satu alas memanjang, seperti SHIFT dan ALPHA pada unit
 * fisik. Nama tombol dicetak di badan kalkulator, bukan pada keycap.
 */
@Composable
private fun TopPair(
    keymap: Keymap,
    ids: List<String>,
    onKey: (KeyEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(17.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ids.forEach { id ->
                val entry = keymap[id]
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentWidth(unbounded = true),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = entry.main.label,
                        style = BodyLabelStyle.copy(fontSize = 11.sp),
                        color = when (entry.main.token) {
                            Key.MOD_SHIFT.name -> skin.shiftLabel
                            Key.MOD_ALPHA.name -> skin.alphaLabel
                            else -> skin.bodyLabel
                        },
                        maxLines = 1,
                    )
                    entry.shift?.let {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = it.label,
                            style = BodyLabelStyle.copy(fontSize = 11.sp),
                            color = skin.shiftLabel,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Alas memanjang tempat kedua tombol duduk, seperti cetakan badan unit fisik.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(skin.padWell)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ids.forEach { id ->
                val entry = keymap[id]
                Cap(
                    style = CapStyle.METAL,
                    label = "",
                    description = entry.main.label,
                    onClick = { onKey(entry) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Bantalan replay, ditiru dari unit fisik: cekungan membulat bertekstur sarang lebah dengan
 * empat tombol lonjong menempel ke tepinya, bukan tombol bulat besar di tengah.
 */
@Composable
private fun ReplayPad(keymap: Keymap, onKey: (KeyEntry) -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    // Tanpa alas: keempat tombol duduk langsung di badan kalkulator.
    Box(modifier.padding(vertical = 1.dp)) {
        Arrow(keymap[KeypadLayout.UP], onKey, Modifier.align(Alignment.TopCenter))
        Arrow(keymap[KeypadLayout.LEFT], onKey, Modifier.align(Alignment.CenterStart))
        Arrow(keymap[KeypadLayout.RIGHT], onKey, Modifier.align(Alignment.CenterEnd))
        Arrow(keymap[KeypadLayout.DOWN], onKey, Modifier.align(Alignment.BottomCenter))
    }
}

/** Keempat tombol panah berukuran dan berbentuk sama: kotak membulat, bukan lonjong. */
@Composable
private fun Arrow(entry: KeyEntry, onKey: (KeyEntry) -> Unit, modifier: Modifier) {
    Cap(
        style = CapStyle.METAL,
        label = entry.main.label,
        description = entry.main.label,
        onClick = { onKey(entry) },
        modifier = modifier.fillMaxWidth(0.34f).fillMaxHeight(0.34f),
        shape = RoundedCornerShape(percent = 30),
    )
}

/**
 * Satu sel papan tombol: tulisan di badan kalkulator, lalu keycap di bawahnya.
 *
 * Label kiri adalah lapis SHIFT (kuning), label kanan lapis ALPHA (merah) atau BASE-N (biru).
 * Nama menu mode dan huruf variabel diapit kurung sudut berwarna, persis unit fisik.
 */
@Composable
private fun KeyCell(
    entry: KeyEntry,
    style: CapStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val leftLabel = entry.shift
    val rightLabel = entry.alpha ?: entry.baseN
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 4.dp),
            horizontalArrangement =
                if (leftLabel != null && rightLabel != null) Arrangement.SpaceBetween
                else Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            leftLabel?.let { BodyLabel(it, skin.shiftLabel) }
            rightLabel?.let {
                BodyLabel(it, if (it === entry.baseN) skin.baseLabel else skin.alphaLabel)
            }
        }
        Cap(
            style = style,
            label = capText(entry.main.label),
            description = entry.main.label,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().weight(1f),
            token = entry.main.token,
        )
    }
}

/** Tulisan kecil di badan kalkulator, dengan kurung sudut bila menandai mode. */
@Composable
private fun BodyLabel(layer: KeyLayer, color: Color) {
    GLYPHS[layer.token]?.let { spec ->
        GlyphLabel(spec, color, 18f, Modifier.width(spec.width.dp).height(16.dp))
        return
    }
    val bracket = bracketColor(layer.token, LocalSkin.current)
    val italic = layer.token == Key.CONST_I.name || layer.token == Key.CONST_E.name
    Row(verticalAlignment = Alignment.Bottom) {
        if (bracket != null) {
            Text("┌", style = BodyLabelStyle, color = bracket, maxLines = 1)
        }
        val shown = capText(layer.label)
        Text(
            text = shown,
            style = BodyLabelStyle.copy(
                fontSize = when {
                    shown.length >= 5 -> 9.sp
                    shown.length == 1 -> 13.sp
                    else -> 11.sp
                },
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (bracket != null) {
            Text("┐", style = BodyLabelStyle, color = bracket, maxLines = 1)
        }
    }
}

/**
 * Warna kurung sudut: kuning untuk menu mode biasa, ungu untuk yang hanya berlaku di CMPLX,
 * biru untuk BASE-N, merah untuk huruf variabel.
 */
private fun bracketColor(token: String, skin: Skin): Color? = when (token) {
    Key.MENU_STAT.name, Key.MENU_MATRIX.name, Key.MENU_VECTOR.name -> skin.shiftLabel
    Key.MENU_CMPLX.name, Key.OP_ANGLE.name, Key.CONST_I.name -> skin.cmplxLabel
    Key.MENU_BASE.name -> skin.baseLabel
    else -> if (token.startsWith("VAR_")) skin.alphaLabel else null
}

/** Rupa keycap; blok angka abu, DEL dan AC hijau, sisanya hitam (foto unit fisik). */
private enum class CapStyle { FUNCTION, DIGIT, CLEAR, METAL }

private val BodyLabelStyle = TextStyle(fontSize = 11.sp, lineHeight = 12.sp)

/** Keycap yang digambar sendiri: gradien atas ke bawah, sorotan tipis, dan sedikit bayangan. */
@Composable
private fun Cap(
    style: CapStyle,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    token: String = "",
    shape: Shape? = null,
) {
    val skin = LocalSkin.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (top, bottom) = when (style) {
        CapStyle.FUNCTION -> skin.functionTop to skin.functionBottom
        CapStyle.DIGIT -> skin.digitTop to skin.digitBottom
        CapStyle.CLEAR -> skin.clearTop to skin.clearBottom
        CapStyle.METAL -> skin.metalTop to skin.metalBottom
    }
    val capShape = shape
        ?: if (style == CapStyle.METAL) RoundedCornerShape(percent = 50) else RoundedCornerShape(9.dp)
    val ink = when (style) {
        CapStyle.FUNCTION -> skin.functionInk
        CapStyle.DIGIT -> skin.digitInk
        CapStyle.CLEAR -> skin.clearInk
        CapStyle.METAL -> skin.functionInk.copy(alpha = 0.5f)
    }
    val glyph = GLYPHS[token]
    Box(
        modifier
            .clip(capShape)
            .drawBehind {
                val a = if (pressed) bottom else top
                val b = if (pressed) top else bottom
                drawRect(Brush.verticalGradient(listOf(a, b)))
                // Sorotan tipis di tepi atas keycap, seperti pantulan cahaya pada tombol asli.
                drawLine(
                    color = Color.White.copy(alpha = 0.16f),
                    start = Offset(size.width * 0.12f, 1.5f),
                    end = Offset(size.width * 0.88f, 1.5f),
                    strokeWidth = 2f,
                )
                if (style == CapStyle.METAL) {
                    // Cincin sepusat meniru tombol logam bertekstur pada unit fisik.
                    val rings = 5
                    repeat(rings) { i ->
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.07f),
                            radius = size.minDimension * (0.12f + 0.07f * i),
                            style = Stroke(width = 1.5f),
                        )
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            GlyphLabel(glyph, ink, 26f, Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 3.dp))
        } else if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = when {
                    style == CapStyle.METAL -> 11.sp
                    label.length >= 6 -> 11.sp
                    label.length >= 4 -> 14.sp
                    else -> 19.sp
                },
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                color = ink,
            )
        }
    }
}

/**
 * Tulisan bertingkat pada tombol dan badan kalkulator: pecahan, akar, pangkat, dan kotak slot
 * digambar dengan primitif Natural Display yang sama seperti layar, bukan ditulis apa adanya.
 * `width` adalah lebar gambar dalam dp untuk tulisan di badan; keycap memakai ruangnya sendiri.
 */
private class GlyphSpec(val width: Int, val build: Glyphs.(Float) -> LayoutBox)

private val GLYPHS: Map<String, GlyphSpec> = mapOf(
    // Tulisan pada keycap.
    Key.FRAC.name to GlyphSpec(20) { s ->
        fraction(placeholder(s, true, 1.9f, 0.6f), placeholder(s, false, 1.9f, 0.6f), s)
    },
    Key.FN_SQRT.name to GlyphSpec(22) { s -> radical(null, placeholder(s, true), s) },
    Key.OP_POW.name to GlyphSpec(22) { s -> superscript(text("x", s), placeholder(s * 0.7f, true), s) },
    Key.FN_LOGBASE.name to GlyphSpec(30) { s ->
        row(text("log", s), subscript(placeholder(s * 0.7f, true), s), placeholder(s))
    },
    Key.FN_INTEGRAL.name to GlyphSpec(28) { s ->
        row(text("\u222b", s * 1.25f), subscript(placeholder(s * 0.7f), s), placeholder(s, true))
    },

    // Tulisan di badan kalkulator, lapis SHIFT dan ALPHA.
    Key.FRAC_MIXED.name to GlyphSpec(22) { s ->
        row(
            placeholder(s, true, 1.6f, 0.6f),
            fraction(placeholder(s, false, 1.6f, 0.6f), placeholder(s, false, 1.6f, 0.6f), s),
        )
    },
    Key.FN_CBRT.name to GlyphSpec(16) { s ->
        radical(text("3", s * 0.62f), placeholder(s, true), s)
    },
    Key.OP_NROOT.name to GlyphSpec(16) { s ->
        radical(placeholder(s * 0.62f, true), placeholder(s), s)
    },
    Key.FN_POW10.name to GlyphSpec(16) { s -> superscript(text("10", s), placeholder(s * 0.7f, true), s) },
    Key.FN_EXP.name to GlyphSpec(13) { s -> superscript(text("e", s), placeholder(s * 0.7f, true), s) },
    Key.FN_SIGMA.name to GlyphSpec(14) { s ->
        row(text("\u03a3", s), stack(placeholder(s * 0.7f, true), placeholder(s * 0.7f), s))
    },
    Key.FN_DERIV.name to GlyphSpec(20) { s ->
        row(fraction(text("d", s * 0.8f), text("dx", s * 0.8f), s), placeholder(s * 0.7f, true))
    },
    Key.FMT_ENG_BACK.name to GlyphSpec(16) { s -> leftArrow(s) },
    Key.FN_DMS_BACK.name to GlyphSpec(16) { s -> leftArrow(s) },
    Key.FMT_ABC_DC.name to GlyphSpec(32) { s ->
        row(
            text("a", s),
            fraction(text("b", s * 0.7f), text("c", s * 0.7f), s),
            text("\u21d4", s),
            fraction(text("d", s * 0.7f), text("c", s * 0.7f), s),
        )
    },
)

/** Menggambar satu tulisan bertingkat, rata tengah pada ruang yang diberikan. */
@Composable
private fun GlyphLabel(spec: GlyphSpec, color: Color, sizeSp: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val box = spec.build(Glyphs(measurer, color), sizeSp)
        // Gambar diskalakan sampai memenuhi ruangnya: yang kebesaran mengecil, yang kekecilan
        // membesar, sehingga tiap tulisan terlihat jelas dan utuh.
        val factor = minOf(size.width / box.width, size.height / box.height)
        scale(factor, pivot = Offset(size.width / 2, size.height / 2)) {
            val left = (size.width - box.width) / 2
            translate(left = left) { box.draw(this, (size.height + box.above - box.below) / 2) }
        }
    }
}

/**
 * Tulisan pada keycap dan badan mengikuti unit fisik: tanpa kurung buka dan tanpa tanda pangkat,
 * sehingga `sin(` menjadi `sin` dan `x^▭` menjadi `x▭`.
 */
private fun capText(label: String): String = when (label) {
    "log▭(▭)" -> "log▭▭"
    "x^▭" -> "x▭"
    "10^" -> "10ˣ"
    "e^" -> "eˣ"
    "×10^" -> "×10ˣ"
    "Σ(" -> "Σ▭"
    else -> if (label.length > 1) label.removeSuffix("(") else label
}
