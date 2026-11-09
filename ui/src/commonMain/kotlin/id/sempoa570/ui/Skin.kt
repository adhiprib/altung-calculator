package id.sempoa570.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Warna badan kalkulator, dipilih lewat OPTION → Tema. Tiga palet ini milik Altung sendiri
 * (PRD §7.2, revisi 9 November 2026): susunan dan label tombol tetap sama dengan fx-570ES PLUS
 * supaya hafalan urutan tombol berlaku, tetapi warnanya sengaja tidak meniru unit Casio, karena
 * Altung dibagikan publik. Jangan kembalikan warna khas Casio (SHIFT kuning, ALPHA merah, DEL dan
 * AC hijau, atau tiga warna badan resminya).
 */
data class Skin(
    val name: String,
    val body: Color,
    val functionTop: Color,
    val functionBottom: Color,
    val functionInk: Color,
    val digitTop: Color,
    val digitBottom: Color,
    val digitInk: Color,
    val clearTop: Color,
    val clearBottom: Color,
    val clearInk: Color,
    val metalTop: Color,
    val metalBottom: Color,
    val padWell: Color,
    /** Tulisan nama tombol di badan, misal MODE dan OPTION. */
    val bodyLabel: Color,
    val shiftLabel: Color,
    val alphaLabel: Color,
    val baseLabel: Color,
    val cmplxLabel: Color,
    val lcdPaper: Color,
    val lcdInk: Color,
    val lcdDim: Color,
    val lcdFrame: Color,
) {
    companion object {

        /** Biru tua dengan DEL dan AC koral; bawaan. */
        val night = Skin(
            name = "Malam",
            body = Color(0xFF1B2838),
            functionTop = Color(0xFF34465C),
            functionBottom = Color(0xFF1F2C3D),
            functionInk = Color(0xFFEAF1F7),
            digitTop = Color(0xFF51657E),
            digitBottom = Color(0xFF3A4C63),
            digitInk = Color(0xFFFFFFFF),
            clearTop = Color(0xFFE8836B),
            clearBottom = Color(0xFFC9614A),
            clearInk = Color(0xFFFFFFFF),
            metalTop = Color(0xFFC5D3E0),
            metalBottom = Color(0xFF8295A8),
            padWell = Color(0xFF111B27),
            bodyLabel = Color(0xFFDCE6EF),
            shiftLabel = Color(0xFF45C9B8),
            alphaLabel = Color(0xFFF2A65A),
            baseLabel = Color(0xFF7FB2F0),
            cmplxLabel = Color(0xFFC39BF0),
            lcdPaper = Color(0xFFDCE7EC),
            lcdInk = Color(0xFF0F1A24),
            lcdDim = Color(0xFF4D5D6B),
            lcdFrame = Color(0xFF0A121B),
        )

        /** Krem terang dengan tombol angka putih dan DEL serta AC mustard. */
        val paper = Skin(
            name = "Kertas",
            body = Color(0xFFECE6DB),
            functionTop = Color(0xFF4A4E57),
            functionBottom = Color(0xFF2E3138),
            functionInk = Color(0xFFF5F2EC),
            digitTop = Color(0xFFFFFFFF),
            digitBottom = Color(0xFFE3DED4),
            digitInk = Color(0xFF23262C),
            clearTop = Color(0xFFE3A93B),
            clearBottom = Color(0xFFC88A1E),
            clearInk = Color(0xFF2A2014),
            metalTop = Color(0xFFFBF8F2),
            metalBottom = Color(0xFFC9C2B5),
            padWell = Color(0xFFDDD5C7),
            bodyLabel = Color(0xFF2E3138),
            shiftLabel = Color(0xFF2563C9),
            alphaLabel = Color(0xFFB0298F),
            baseLabel = Color(0xFF0F8A7E),
            cmplxLabel = Color(0xFF6A45B8),
            lcdPaper = Color(0xFFEEE6CF),
            lcdInk = Color(0xFF2A2418),
            lcdDim = Color(0xFF6B624C),
            lcdFrame = Color(0xFF3A3D44),
        )

        /** Hijau tua dengan DEL dan AC kuning tua. */
        val forest = Skin(
            name = "Hutan",
            body = Color(0xFF1E2A24),
            functionTop = Color(0xFF3A4A41),
            functionBottom = Color(0xFF24312A),
            functionInk = Color(0xFFECF3EE),
            digitTop = Color(0xFF5A6B61),
            digitBottom = Color(0xFF414F47),
            digitInk = Color(0xFFFFFFFF),
            clearTop = Color(0xFFEBA845),
            clearBottom = Color(0xFFC9872A),
            clearInk = Color(0xFF1E1A12),
            metalTop = Color(0xFFD3DDD6),
            metalBottom = Color(0xFF8FA197),
            padWell = Color(0xFF131B17),
            bodyLabel = Color(0xFFE3ECE6),
            shiftLabel = Color(0xFF7EE0B5),
            alphaLabel = Color(0xFFF57F95),
            baseLabel = Color(0xFF8EC5F0),
            cmplxLabel = Color(0xFFC8A6F0),
            lcdPaper = Color(0xFFF0EDE2),
            lcdInk = Color(0xFF1C2420),
            lcdDim = Color(0xFF5E655F),
            lcdFrame = Color(0xFF0E1511),
        )

        /** Urutannya sama dengan butir menu OPTION di engine. */
        val all = listOf(night, paper, forest)

        fun byIndex(index: Int): Skin = all.getOrElse(index) { night }
    }
}

/** Skin aktif untuk seluruh pohon composable. */
val LocalSkin = staticCompositionLocalOf { Skin.night }
