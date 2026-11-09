package id.sempoa570.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.createRippleModifierNode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/*
 * Pengganti Text dan Surface material3 (PRD §6.1, revisi 28 September 2026). Material3
 * multiplatform masih alpha, jadi :ui memakai foundation saja. Nilai di sini menyalin material3
 * 1.4.0 yang dipakai Altung 1.0, supaya rupa layar Android tidak berubah.
 */

/** Typography.bodyLarge material3 1.4.0: gaya bawaan Text bila pemanggil tidak memberi style. */
private val BodyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.5.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** Tanda tangan sama dengan material3 Text, sebatas parameter yang dipakai aplikasi ini. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = BodyLarge,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            lineHeight = lineHeight,
            textAlign = textAlign,
        ),
        overflow = overflow,
        maxLines = maxLines,
    )
}

/**
 * Seperti material3 Surface tanpa elevasi: latar berbentuk, anak mewarisi ukuran minimum, dan
 * sentuhan tidak tembus ke lapisan di bawahnya (panduan menutupi papan tombol).
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(color, shape)
            .clip(shape)
            .semantics(mergeDescendants = false) { isTraversalGroup = true }
            .pointerInput(Unit) {},
        propagateMinConstraints = true,
    ) { content() }
}

/**
 * Ripple butir menu dan panduan, sama dengan yang dulu dipasang MaterialTheme: warna konten
 * onBackground skema gelap material3 1.4.0 dengan tingkat transparansi materialnya.
 */
internal object SempoaRipple : IndicationNodeFactory {
    private val ink = Color(0xFFE6E0E9)
    private val alpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0.10f,
        hoveredAlpha = 0.08f,
        pressedAlpha = 0.10f,
    )

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        createRippleModifierNode(interactionSource, true, Dp.Unspecified, { ink }, { alpha })

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
}

/** Tombol kembali Android menutup panduan; desktop memakai Esc lewat pemetaan keyboardnya. */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)
