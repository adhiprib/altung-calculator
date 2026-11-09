package id.sempoa570.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Kotak terukur untuk satu simpul: lebar, tinggi di atas garis dasar, dan di bawahnya
 * (PRD §6.4). Menggambar selalu relatif terhadap garis dasar, sehingga pecahan, akar, dan
 * pangkat dapat disusun bertingkat.
 */
internal class LayoutBox(
    val width: Float,
    val above: Float,
    val below: Float,
    /**
     * Anak-anak beserta letaknya. Diisi tiap kombinator supaya ketukan layar bisa menelusuri
     * susunan yang sama dengan yang digambar; tanpa ini letak anak hanya hidup di dalam
     * closure `draw` dan tidak bisa dibaca lagi.
     */
    val parts: List<Placed> = emptyList(),
    val draw: DrawScope.(baselineY: Float) -> Unit,
) {
    val height: Float get() = above + below

    /** Penanda milik pemakai: simpul ke berapa, atau slot ke berapa (lihat ExprLayout). */
    var tag: Any? = null
}

/** Satu anak yang sudah ditempatkan: geseran mendatar, dan geseran garis dasarnya. */
internal class Placed(val dx: Float, val dy: Float, val box: LayoutBox)

/** Ukuran terukur satu angka: lebar, tinggi di atas garis dasar, dan di bawahnya. */
internal class Glyph(val width: Float, val above: Float, val below: Float) {
    val height: Float get() = above + below
}

/**
 * Primitif gambar Natural Display: teks, kotak slot, pecahan bertingkat, akar, pangkat, dan
 * subskrip. Dipakai baris ekspresi LCD sekaligus tulisan pada tombol dan badan kalkulator,
 * supaya keduanya memakai bentuk yang sama persis seperti unit fisik.
 */
internal class Glyphs(private val measurer: TextMeasurer, private val color: Color) {

    fun horizontal(boxes: List<LayoutBox>): LayoutBox {
        val width = boxes.fold(0f) { acc, b -> acc + b.width }
        val above = boxes.maxOf { it.above }
        val below = boxes.maxOf { it.below }
        val placed = mutableListOf<Placed>()
        var at = 0f
        for (b in boxes) {
            placed += Placed(at, 0f, b)
            at += b.width
        }
        return LayoutBox(width, above, below, placed) { baseline ->
            var x = 0f
            for (b in boxes) {
                translate(left = x) { b.draw(this, baseline) }
                x += b.width
            }
        }
    }

    /**
     * Kotak slot. `aspect` melebarkan kotak dan `scale` memendekkannya, dipakai untuk tulisan
     * tombol pembagian yang pada unit fisik memang gepeng, bukan bujur sangkar.
     */
    fun placeholder(
        size: Float,
        filled: Boolean = false,
        aspect: Float = 1f,
        scale: Float = 1f,
    ): LayoutBox {
        val digit = glyph(size)
        val side = digit.height * 0.7f * scale
        val wide = side * aspect
        val pad = digit.height * 0.06f
        return LayoutBox(wide + pad * 2, side + pad, digit.below) { baseline ->
            drawRect(
                color = color,
                topLeft = Offset(pad, baseline - side),
                size = Size(wide, side),
                style = if (filled) Fill else Stroke(digit.height * 0.055f),
            )
        }
    }

    fun glyph(size: Float): Glyph {
        val measured = measurer.measure(
            "0",
            TextStyle(fontSize = size.sp, fontFamily = FontFamily.Monospace),
        )
        return Glyph(
            width = measured.size.width.toFloat(),
            above = measured.firstBaseline,
            below = measured.size.height - measured.firstBaseline,
        )
    }

    fun text(value: String, size: Float): LayoutBox {
        if (value.isEmpty()) return LayoutBox(0f, size * 0.8f, size * 0.25f) {}
        val style = TextStyle(fontSize = size.sp, fontFamily = FontFamily.Monospace, color = color)
        val measured = measurer.measure(value, style)
        val above = measured.firstBaseline
        val below = measured.size.height - measured.firstBaseline
        return LayoutBox(measured.size.width.toFloat(), above, below) { baseline ->
            drawText(measured, topLeft = Offset(0f, baseline - above))
        }
    }

    fun fraction(num: LayoutBox, den: LayoutBox, size: Float): LayoutBox {
        val unit = glyph(size).height
        val pad = unit * 0.12f
        val width = maxOf(num.width, den.width) + pad * 2
        val gap = unit * 0.14f
        val placed = listOf(
            Placed((width - num.width) / 2, -gap - num.below, num),
            Placed((width - den.width) / 2, gap + den.above, den),
        )
        return LayoutBox(width, num.height + gap, den.height + gap, placed) { baseline ->
            translate(left = (width - num.width) / 2) { num.draw(this, baseline - gap - num.below) }
            translate(left = (width - den.width) / 2) { den.draw(this, baseline + gap + den.above) }
            drawLine(
                color = color,
                start = Offset(pad * 0.4f, baseline),
                end = Offset(width - pad * 0.4f, baseline),
                strokeWidth = unit * 0.05f,
            )
        }
    }

    fun radical(index: LayoutBox?, content: LayoutBox, size: Float): LayoutBox {
        val unit = glyph(size).height
        val hook = unit * 0.34f
        val indexWidth = index?.width ?: 0f
        val bar = unit * 0.09f
        val width = indexWidth + hook + content.width + unit * 0.12f
        val above = content.above + bar + (index?.height ?: 0f) * 0.2f
        val placed = listOfNotNull(
            index?.let { Placed(0f, -content.above * 0.6f, it) },
            Placed(indexWidth + hook, 0f, content),
        )
        return LayoutBox(width, above, content.below, placed) { baseline ->
            index?.draw(this, baseline - content.above * 0.6f)
            val x0 = indexWidth
            val top = baseline - content.above - bar
            val bottom = baseline + content.below
            val path = Path().apply {
                moveTo(x0, bottom - (bottom - top) * 0.45f)
                lineTo(x0 + hook * 0.35f, bottom - (bottom - top) * 0.3f)
                lineTo(x0 + hook * 0.62f, bottom)
                lineTo(x0 + hook, top)
                lineTo(width, top)
            }
            drawPath(path, color = color, style = Stroke(unit * 0.05f))
            translate(left = x0 + hook) { content.draw(this, baseline) }
        }
    }

    /** Pangkat diangkat sejauh separuh tinggi angka, supaya jelas terbaca sebagai superskrip. */
    fun superscript(base: LayoutBox, exponent: LayoutBox, size: Float): LayoutBox {
        val lift = glyph(size).above * 0.62f
        val above = maxOf(base.above, exponent.height + lift)
        val placed = listOf(Placed(0f, 0f, base), Placed(base.width, -lift, exponent))
        return LayoutBox(base.width + exponent.width, above, base.below, placed) { baseline ->
            base.draw(this, baseline)
            translate(left = base.width) { exponent.draw(this, baseline - lift) }
        }
    }

    fun subscript(content: LayoutBox, size: Float): LayoutBox {
        val drop = glyph(size).above * 0.3f
        val placed = listOf(Placed(0f, drop, content))
        return LayoutBox(content.width, content.above, content.below + drop, placed) { baseline ->
            content.draw(this, baseline + drop)
        }
    }

    /** Dua kotak bertumpuk tanpa garis, seperti batas atas dan bawah pada tombol sigma. */
    fun stack(top: LayoutBox, bottom: LayoutBox, size: Float): LayoutBox {
        val gap = size * 0.08f
        val width = maxOf(top.width, bottom.width)
        val placed = listOf(
            Placed((width - top.width) / 2, -gap - top.below, top),
            Placed((width - bottom.width) / 2, gap + bottom.above, bottom),
        )
        return LayoutBox(width, top.height + gap, bottom.height + gap, placed) { baseline ->
            translate(left = (width - top.width) / 2) { top.draw(this, baseline - gap - top.below) }
            translate(left = (width - bottom.width) / 2) {
                bottom.draw(this, baseline + gap + bottom.above)
            }
        }
    }

    /** Panah kiri yang digambar sendiri; huruf panah dari fonta terlalu tipis dan kecil. */
    fun leftArrow(size: Float): LayoutBox {
        val unit = glyph(size).height
        val h = unit * 0.5f
        val w = unit * 1.15f
        return LayoutBox(w, h * 0.8f, h * 0.2f) { baseline ->
            val midY = baseline - h * 0.3f
            val stroke = h * 0.22f
            drawLine(color, Offset(w * 0.38f, midY), Offset(w, midY), stroke)
            val head = Path().apply {
                moveTo(0f, midY)
                lineTo(w * 0.46f, midY - h * 0.46f)
                lineTo(w * 0.46f, midY + h * 0.46f)
                close()
            }
            drawPath(head, color = color)
        }
    }

    fun row(vararg boxes: LayoutBox): LayoutBox = horizontal(boxes.toList())
}
