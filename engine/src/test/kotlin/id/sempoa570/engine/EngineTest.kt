package id.sempoa570.engine

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Nama token di docs/keymap.json harus persis sama dengan enum [Key]. */
class KeymapSyncTest {

    @Test
    fun everyKeymapTokenHasEnumEntry() {
        val text = Golden.keymapFile().readText()
        val tokens = Regex("\"token\"\\s*:\\s*\"([A-Z0-9_]+)\"")
            .findAll(text).map { it.groupValues[1] }.toSortedSet()
        assertTrue(tokens.size > 100, "keymap harus memuat banyak token, ada ${tokens.size}")
        val missing = tokens.filter { Key.byName(it) == null }
        assertEquals(emptyList(), missing, "token keymap tanpa padanan di enum Key")
    }

    /**
     * Panduan bawaan hidup di docs/manual.json dan disalin ke aset saat build, sama seperti
     * keymap. Tesnya menjaga berkas itu tetap terurai dan tiap babnya berjudul.
     */
    @Test
    fun manualParsesAndEveryChapterHasATitle() {
        val file = Golden.keymapFile().resolveSibling("manual.json")
        assertTrue(file.isFile, "docs/manual.json harus ada")
        val book = Json.parseToJsonElement(file.readText()).jsonObject
        val chapters = book.getValue("chapters").jsonArray
        assertTrue(chapters.size >= 10, "panduan terlalu pendek, ada ${chapters.size} bab")
        val untitled = chapters.count { it.jsonObject["title"]?.jsonPrimitive?.content.isNullOrBlank() }
        assertEquals(0, untitled, "ada bab tanpa judul")
    }

    @Test
    fun keymapHasFiftyKeys() {
        val text = Golden.keymapFile().readText()
        val ids = Regex("\"id\"\\s*:\\s*\"[A-Z0-9_]+\"").findAll(text).count()
        assertEquals(50, ids, "keymap harus berisi 50 tombol (PRD §4.2)")
    }
}

class CasioNumberTest {

    @Test
    fun keepsFifteenSignificantDigits() {
        val a = CasioNumber.parse("1") / CasioNumber.parse("3")
        assertEquals("0.333333333333333", a.toString())
    }

    @Test
    fun decimalAdditionHasNoBinaryArtifact() {
        val sum = CasioNumber.parse("0.1") + CasioNumber.parse("0.2")
        assertEquals(CasioNumber.parse("0.3"), sum)
    }

    @Test
    fun divisionByZeroIsMathError() {
        val e = assertFailsWith<CalcError> { CasioNumber.ONE / CasioNumber.ZERO }
        assertEquals(ErrorKind.MATH, e.kind)
    }

    @Test
    fun overflowBeyondRangeIsMathError() {
        val big = CasioNumber.parse("9.9E99")
        assertFailsWith<CalcError> { big * CasioNumber.of(10) }
    }
}

class CasioMathTest {

    private fun round(v: BigDecimal, digits: Int) =
        CasioNumber.of(v).roundSignificant(digits).toString()

    @Test
    fun lnAndExpAgreeWithKnownValues() {
        assertEquals("2.302585093", round(CasioMath.ln(BigDecimal.TEN), 10))
        assertEquals("2.718281828", round(CasioMath.exp(BigDecimal.ONE), 10))
    }

    @Test
    fun sineOfThirtyDegreesIsExactlyOneHalf() {
        val radians = CasioMath.PI.divide(BigDecimal(6), CasioNumber.WORK)
        assertEquals(CasioNumber.parse("0.5"), CasioNumber.of(CasioMath.sin(radians)).roundSignificant(10))
    }

    @Test
    fun arcTangentOfOneIsQuarterPi() {
        assertEquals("0.7853981634", round(CasioMath.atan(BigDecimal.ONE), 10))
    }

    @Test
    fun squareRootIsAccurate() {
        assertEquals("1.414213562", round(CasioMath.sqrt(BigDecimal(2)), 10))
    }
}

class ExactTest {

    @Test
    fun squareRootOfEightSimplifies() {
        val e = ExactMath.sqrt(Exact.int(8))!!
        assertEquals("2√(2)", ResultFormatter.format(Value.Ex(e), Setup()))
    }

    @Test
    fun fractionsAddExactly() {
        val sum = ExactMath.add(
            Exact.rat(2.toBigInteger(), 3.toBigInteger()),
            Exact.rat(1.toBigInteger(), 2.toBigInteger()),
        )!!
        assertEquals("7⌟6", ResultFormatter.format(Value.Ex(sum), Setup()))
    }

    @Test
    fun longFractionsFallBackToDecimal() {
        val wide = Exact.rat(123456.toBigInteger(), 999983.toBigInteger())
        assertTrue(!ExactMath.isDisplayable(wide))
    }

    private fun run(setup: Setup = Setup(), vararg keys: Key): Calculator {
        val c = Calculator(setup)
        c.pressAll(keys.toList())
        return c
    }

    /** Manual hlm. 20 Contoh 2: (√2 + 2) x √3 tampil sebagai √6 + 2√3. */
    @Test
    fun productOfSurdsKeepsTwoTerms() {
        val c = run(
            Setup(),
            Key.PAREN_OPEN, Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT, Key.OP_ADD, Key.DIGIT_2,
            Key.PAREN_CLOSE, Key.OP_MUL, Key.FN_SQRT, Key.DIGIT_3, Key.EXEC_EQ,
        )
        assertEquals("√(6)+2√(3)", c.resultText())
    }

    /** Manual hlm. 18: 10√2 + 15 x 3√3 tampil 45√3 + 10√2, radikan besar dulu. */
    @Test
    fun surdSumOrdersLargerRadicandFirst() {
        val c = run(
            Setup(),
            Key.DIGIT_1, Key.DIGIT_0, Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT, Key.OP_ADD,
            Key.DIGIT_1, Key.DIGIT_5, Key.OP_MUL, Key.DIGIT_3, Key.FN_SQRT, Key.DIGIT_3,
            Key.EXEC_EQ,
        )
        assertEquals("45√(3)+10√(2)", c.resultText())
    }

    /** Manual hlm. 18: 99√999 lewat batas koefisien, jadi desimal, bukan 297√111. */
    @Test
    fun surdOutsideCoefficientRangeFallsBackToDecimal() {
        val c = run(
            Setup(),
            Key.DIGIT_9, Key.DIGIT_9, Key.FN_SQRT, Key.DIGIT_9, Key.DIGIT_9, Key.DIGIT_9,
            Key.EXEC_EQ,
        )
        assertEquals("3129.089165", c.resultText())
    }

    /** Manual hlm. 17: (2 + √2) dibagi (1 + √2) memberi √2 lewat perkalian sekawan. */
    @Test
    fun divisionByTwoTermSurdRationalises() {
        val c = run(
            Setup(),
            Key.FRAC,
            Key.DIGIT_2, Key.OP_ADD, Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT,
            Key.NAV_DOWN,
            Key.DIGIT_1, Key.OP_ADD, Key.FN_SQRT, Key.DIGIT_2,
            Key.EXEC_EQ,
        )
        assertEquals("√(2)", c.resultText())
    }

    /** Kuadrat dua suku menyusut jadi dua suku lagi: (√2 + √3) pangkat 2 = 5 + 2√6. */
    @Test
    fun squareOfTwoTermSurdStaysExact() {
        val c = run(
            Setup(),
            Key.PAREN_OPEN, Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT, Key.OP_ADD,
            Key.FN_SQRT, Key.DIGIT_3, Key.NAV_RIGHT, Key.PAREN_CLOSE, Key.POST_SQUARE,
            Key.EXEC_EQ,
        )
        assertEquals("5+2√(6)", c.resultText())
    }

    /** Tiga suku berakar di luar bentuk manual hlm. 17, jadi jatuh ke desimal. */
    @Test
    fun threeSurdTermsFallBackToDecimal() {
        val c = run(
            Setup(),
            Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT, Key.OP_ADD,
            Key.FN_SQRT, Key.DIGIT_5, Key.NAV_RIGHT, Key.OP_ADD,
            Key.FN_SQRT, Key.DIGIT_7, Key.EXEC_EQ,
        )
        assertEquals("6.296032851", c.resultText())
    }

    /** Dua suku berpenyebut sama ditulis di atas satu garis. */
    @Test
    fun twoTermSurdSharesOneDenominator() {
        val sum = ExactMath.add(
            Exact.surd(1.toBigInteger(), 5.toBigInteger(), 6.toBigInteger()),
            Exact.surd(2.toBigInteger(), 5.toBigInteger(), 3.toBigInteger()),
        )!!
        assertEquals("(√(6)+2√(3))⌟5", ResultFormatter.format(Value.Ex(sum), Setup()))
    }
}

/** Tempel dari papan klip: teks dimainkan ulang sebagai tekanan tombol (Clipboard.kt). */
class ClipboardTest {

    private fun typed(vararg keys: Key): String {
        val c = Calculator()
        c.pressAll(keys.toList())
        return c.exprText()
    }

    private fun pasted(text: String): String {
        val c = Calculator()
        assertTrue(c.paste(text), "teks harusnya terbaca: $text")
        return c.exprText()
    }

    /** Angka biasa dari aplikasi lain, termasuk desimal dan notasi eksponen. */
    @Test
    fun pastesPlainNumbers() {
        assertEquals("12.5", pasted("12.5"))
        assertEquals(
            typed(Key.DIGIT_1, Key.DOT, Key.DIGIT_5, Key.EXP10, Key.DIGIT_3),
            pasted("1.5E3"),
        )
    }

    /** Teks yang disalin dari aplikasi ini sendiri kembali utuh. */
    @Test
    fun roundTripsItsOwnRendering() {
        val original = typed(
            Key.FN_SQRT, Key.DIGIT_2, Key.PAREN_CLOSE, Key.OP_MUL, Key.DIGIT_3,
        )
        assertEquals("\u221a(2)\u00d73", original)
        assertEquals(original, pasted(original))
    }

    /** Pecahan memungut operand di kirinya, sama seperti saat diketik tangan. */
    @Test
    fun roundTripsAFraction() {
        val original = typed(Key.DIGIT_2, Key.FRAC, Key.DIGIT_3)
        assertEquals("2\u231f3", original)
        assertEquals(original, pasted(original))
    }

    /** Tanda kurang di awal menjadi tanda negatif, di tengah menjadi pengurangan. */
    @Test
    fun readsMinusAsSignOrSubtraction() {
        assertEquals("-5", pasted("-5"))
        assertEquals("8-5", pasted("8-5"))
        assertEquals("8\u00d7-5", pasted("8\u00d7-5"))
    }

    /** Huruf yang tak dikenal membatalkan seluruh tempelan, bukan menempel separuhnya. */
    @Test
    fun refusesUnreadableText() {
        assertEquals(null, Clipboard.keysFrom("12 apel"))
        assertEquals(null, Clipboard.keysFrom("   "))
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_7))
        assertEquals(false, c.paste("12 apel"))
        assertEquals("7", c.exprText())
    }
}

class FormatTest {

    private fun show(text: String, setup: Setup) =
        ResultFormatter.decimal(CasioNumber.parse(text), setup)

    @Test
    fun normOneSwitchesToScientificBelowHundredth() {
        assertEquals("3.333333333E-5", show("0.0000333333333333333", Setup()))
        assertEquals("0.2", show("0.2", Setup()))
    }

    @Test
    fun normOneSwitchesToScientificAtTenDigits() {
        assertEquals("1.23456789E11", show("123456789000", Setup()))
    }

    @Test
    fun fixKeepsRequestedDecimals() {
        assertEquals("10.000", show("9.99999999999999", Setup(format = DisplayFormat.Fix(3))))
    }

    @Test
    fun sciKeepsRequestedSignificantDigits() {
        assertEquals("4.50E0", show("4.49980967033027", Setup(format = DisplayFormat.Sci(3))))
    }
}

class ParserTest {

    private fun value(vararg keys: Key): String {
        val calc = Calculator()
        calc.pressAll(keys.toList())
        calc.press(Key.EXEC_EQ)
        return calc.resultText()
    }

    @Test
    fun multiplicationBindsTighterThanAddition() {
        assertEquals("14", value(Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_3, Key.OP_MUL, Key.DIGIT_4))
    }

    @Test
    fun squareBindsTighterThanUnaryMinus() {
        assertEquals("-4", value(Key.OP_NEG, Key.DIGIT_2, Key.POST_SQUARE))
    }

    /** Konversi satuan sudut, contoh manual hlm. 28 pada satuan Deg. */
    @Test
    fun angleUnitConversionFollowsTheManual() {
        assertEquals(
            "90",
            value(
                Key.PAREN_OPEN, Key.CONST_PI, Key.OP_DIV, Key.DIGIT_2, Key.PAREN_CLOSE,
                Key.ANGLE_RAD,
            ),
        )
        assertEquals("45", value(Key.DIGIT_5, Key.DIGIT_0, Key.ANGLE_GRA))
    }

    @Test
    fun incompleteExpressionIsSyntaxError() {
        assertEquals("Syntax ERROR", value(Key.DIGIT_2, Key.OP_MUL))
    }
}

/** Kursor bebas, penyuntingan, dan replay memory (PRD §4.4, Tahap 2). */
class EditorTest {

    private fun calc(vararg keys: Key): Calculator {
        val c = Calculator()
        c.pressAll(keys.toList())
        return c
    }

    private fun Calculator.screen() = ExprRenderer.render(exprTree())

    @Test
    fun cursorStartsAtEndOfExpression() {
        assertEquals("1+2|", calc(Key.DIGIT_1, Key.OP_ADD, Key.DIGIT_2).screen())
    }

    @Test
    fun cursorMovesInsideANumber() {
        val c = calc(Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3)
        c.press(Key.NAV_LEFT)
        assertEquals("12|3", c.screen())
        c.press(Key.DIGIT_9)
        assertEquals("129|3", c.screen())
        assertEquals("1293", c.exprText())
    }

    @Test
    fun deleteRemovesDigitBeforeCursor() {
        val c = calc(Key.DIGIT_4, Key.DIGIT_5, Key.DIGIT_6)
        c.press(Key.NAV_LEFT)
        c.press(Key.EDIT_DEL)
        assertEquals("4|6", c.screen())
    }

    @Test
    fun cursorEntersAndLeavesAFraction() {
        val c = calc(Key.DIGIT_2, Key.FRAC, Key.DIGIT_3)
        assertEquals("2⌟3|", c.screen())
        c.press(Key.NAV_RIGHT)
        assertEquals("2⌟3|", c.screen())
        c.press(Key.OP_ADD)
        assertEquals("2⌟3+|", c.screen())
    }

    @Test
    fun fractionTakesWholeNumberAsNumerator() {
        val c = calc(Key.DIGIT_1, Key.DIGIT_2, Key.FRAC, Key.DIGIT_5)
        assertEquals("12⌟5", c.exprText())
    }

    @Test
    fun verticalKeysMoveBetweenNumeratorAndDenominator() {
        val c = calc(Key.DIGIT_2, Key.FRAC, Key.DIGIT_3)
        c.press(Key.NAV_UP)
        assertEquals("2|⌟3", c.screen())
        c.press(Key.NAV_DOWN)
        assertEquals("2⌟3|", c.screen())
    }

    @Test
    fun replayBrowsesPreviousExpressions() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_1, Key.OP_ADD, Key.DIGIT_1, Key.EXEC_EQ))
        c.pressAll(listOf(Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_2, Key.EXEC_EQ))
        assertEquals("4", c.resultText())
        c.press(Key.NAV_UP)
        assertEquals("2+2", c.exprText())
        c.press(Key.NAV_UP)
        assertEquals("1+1", c.exprText())
    }

    /** Layar yang sudah dikosongkan AC tetap bisa memanggil ekspresi terakhir. */
    @Test
    fun cursorKeyAfterClearRecallsLastExpression() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_9, Key.OP_ADD, Key.DIGIT_4, Key.EXEC_EQ))
        c.press(Key.EDIT_AC)
        assertEquals("", c.exprText())
        c.press(Key.NAV_LEFT)
        assertEquals("9+4", c.exprText())
        c.press(Key.EXEC_EQ)
        assertEquals("13", c.resultText())
    }

    /** ▲ juga menelusuri riwayat dari layar kosong, bukan hanya sesudah hasil. */
    @Test
    fun upArrowBrowsesHistoryFromEmptyScreen() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_1, Key.OP_ADD, Key.DIGIT_1, Key.EXEC_EQ))
        c.pressAll(listOf(Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_2, Key.EXEC_EQ))
        c.press(Key.EDIT_AC)
        c.press(Key.NAV_UP)
        assertEquals("2+2", c.exprText())
        c.press(Key.NAV_UP)
        assertEquals("1+1", c.exprText())
    }

    /** Pindah mode menghapus riwayat, seperti catatan manual. */
    @Test
    fun changingModeClearsHistory() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_5, Key.OP_ADD, Key.DIGIT_5, Key.EXEC_EQ))
        c.selectMode(Mode.COMP)
        c.press(Key.NAV_UP)
        assertEquals("", c.exprText())
    }

    /** INS lalu akar membungkus nilai di kanan kursor, bukan menyisipkan akar kosong. */
    @Test
    fun insertWrapsValueToTheRightIntoTheNextTemplate() {
        val c = calc(Key.DIGIT_7, Key.FRAC, Key.DIGIT_6)
        c.press(Key.EDIT_INS)
        // Empat kali ◄ membawa kursor keluar dari pecahan, bukan sekadar ke pembilang.
        repeat(4) { c.press(Key.NAV_LEFT) }
        c.press(Key.FN_SQRT)
        assertEquals("√(7⌟6)", c.exprText())
        c.press(Key.EXEC_EQ)
        // Bentuk eksak: √(7/6) sama dengan √42 per 6, seperti tampilan MthIO unit fisik.
        assertEquals("√(42)⌟6", c.resultText())
        c.press(Key.FMT_SD)
        assertEquals("1.08012345", c.resultText())
    }

    /** Pecahan sesudah INS memakai nilai kanan sebagai pembilang. */
    @Test
    fun insertWrapsValueIntoFractionNumerator() {
        val c = calc(Key.DIGIT_1, Key.OP_ADD, Key.DIGIT_6)
        c.press(Key.EDIT_INS)
        c.press(Key.NAV_LEFT)
        c.press(Key.FRAC)
        c.press(Key.DIGIT_7)
        assertEquals("1+6⌟7", c.exprText())
    }

    @Test
    fun cursorKeyAfterResultReopensLastExpression() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_7, Key.OP_MUL, Key.DIGIT_6, Key.EXEC_EQ))
        c.press(Key.NAV_LEFT)
        assertEquals("7×6", c.exprText())
    }

    @Test
    fun errorLeavesExpressionEditable() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_1, Key.OP_DIV, Key.DIGIT_0, Key.EXEC_EQ))
        assertEquals("Math ERROR", c.resultText())
        c.press(Key.NAV_LEFT)
        assertEquals("1÷0", c.exprText())
        assertEquals("", c.resultText())
    }

    /**
     * SHIFT °′″ mengembalikan hasil derajat-menit-detik ke derajat desimal, satu arah.
     * Arahnya dipastikan pemilik produk pada unit fisik; manual hlm. 23 Contoh 2 hanya
     * mendokumentasikan tombol polosnya yang bolak-balik.
     */
    @Test
    fun shiftDegreeKeyConvertsSexagesimalToDecimal() {
        val c = calc(
            Key.DIGIT_2, Key.POST_DMS,
            Key.DIGIT_1, Key.DIGIT_5, Key.POST_DMS,
            Key.DIGIT_1, Key.DIGIT_8, Key.POST_DMS,
            Key.EXEC_EQ,
        )
        assertEquals("2°15'18\"", c.resultText())
        c.press(Key.FN_DMS_BACK)
        assertEquals("2.255", c.resultText())
        // Satu arah: menekannya lagi tidak mengembalikan bentuk DMS.
        c.press(Key.FN_DMS_BACK)
        assertEquals("2.255", c.resultText())
        // Tombol polosnya tetap bolak-balik.
        c.press(Key.POST_DMS)
        assertEquals("2°15'18\"", c.resultText())
    }

    /** Tanpa hasil berbentuk DMS tombolnya diam, bukan mengetik nama tokennya ke layar. */
    @Test
    fun shiftDegreeKeyTypesNothingWhenThereIsNoSexagesimalResult() {
        val c = calc(Key.DIGIT_1, Key.DIGIT_2)
        c.press(Key.FN_DMS_BACK)
        assertEquals("12", c.exprText())
    }

    /** OPTION 3 membuka panduan bawaan; bendera itu tidak ikut disimpan ke DataStore. */
    @Test
    fun optionMenuOpensTheBuiltInManual() {
        val c = Calculator()
        assertEquals(6, CalculatorMenu.OPTION.items.size)
        c.press(Key.MENU_OPTION)
        assertEquals(false, c.manualOpen)
        c.press(Key.DIGIT_3)
        assertEquals(true, c.manualOpen)
        assertEquals(null, c.menu())
        c.closeManual()
        assertEquals(false, c.manualOpen)
    }

    /**
     * Butir 4 dan 5 menyalin dan menempel, bukan membuka panduan. Dulu cabang tangkap-semua
     * membuat setiap butir sesudah Manual diam-diam membuka panduan.
     */
    @Test
    fun optionMenuCopyAndPasteDoNotOpenTheManual() {
        val c = calc(Key.DIGIT_4, Key.OP_ADD, Key.DIGIT_5, Key.EXEC_EQ)
        c.press(Key.MENU_OPTION)
        c.press(Key.DIGIT_4)
        assertEquals(false, c.manualOpen)
        assertEquals("9", c.clipboardOut)
        c.clipboardTaken()
        assertEquals(null, c.clipboardOut)

        c.press(Key.MENU_OPTION)
        c.press(Key.DIGIT_5)
        assertEquals(false, c.manualOpen)
        assertEquals(true, c.pasteRequested)
    }

    /**
     * Reset menanyakan dulu, lalu mengembalikan seluruh isi kalkulator ke setelan pabrik.
     * Menjawab Tidak tidak mengubah apa pun.
     */
    @Test
    fun optionResetAsksFirstThenClearsEverything() {
        val c = calc(Key.DIGIT_7, Key.MEM_STO, Key.VAR_A)
        c.press(Key.MENU_OPTION)
        c.press(Key.DIGIT_1)
        c.press(Key.DIGIT_2)
        assertEquals(1, c.skin)

        c.press(Key.MENU_OPTION)
        c.press(Key.DIGIT_6)
        assertEquals("Reset semua?", c.menu()?.title)
        c.press(Key.DIGIT_2)
        assertEquals(1, c.skin, "menjawab Tidak tidak boleh mengubah apa pun")

        c.press(Key.MENU_OPTION)
        c.press(Key.DIGIT_6)
        c.press(Key.DIGIT_1)
        assertEquals(0, c.skin)
        assertEquals(Mode.COMP, c.mode)
        assertEquals("", c.exprText())
        c.pressAll(listOf(Key.MEM_RCL, Key.VAR_A, Key.EXEC_EQ))
        assertEquals("0", c.resultText())
    }

    /**
     * CLR bertingkat tiga sesuai manual hlm. 15, 26, dan 4, dan tiap tingkat dibenarkan dengan
     * `=`, bukan butir bernomor.
     */
    @Test
    fun clearMenuFollowsTheManual() {
        assertEquals(listOf("Setup", "Memory", "All"), CalculatorMenu.CLR.items)

        // CLR 1 Setup: satuan sudut kembali Deg, tetapi isi variabel tetap.
        val c = calc(Key.DIGIT_7, Key.MEM_STO, Key.VAR_A)
        c.pressAll(listOf(Key.MENU_SETUP, Key.DIGIT_4))
        assertEquals(AngleUnit.RAD, c.setup.angle)
        c.pressAll(listOf(Key.MENU_CLR, Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals(AngleUnit.DEG, c.setup.angle)
        c.pressAll(listOf(Key.MEM_RCL, Key.VAR_A, Key.EXEC_EQ))
        assertEquals("7", c.resultText())

        // CLR 2 Memory: isi variabel hilang, setelan tidak disentuh.
        c.pressAll(listOf(Key.MENU_SETUP, Key.DIGIT_4))
        c.pressAll(listOf(Key.MENU_CLR, Key.DIGIT_2, Key.EXEC_EQ))
        assertEquals(AngleUnit.RAD, c.setup.angle)
        c.pressAll(listOf(Key.MEM_RCL, Key.VAR_A, Key.EXEC_EQ))
        assertEquals("0", c.resultText())
    }

    /** AC membatalkan pembenaran CLR tanpa menghapus apa pun. */
    @Test
    fun clearCanBeCancelled() {
        val c = calc(Key.DIGIT_7, Key.MEM_STO, Key.VAR_A)
        c.pressAll(listOf(Key.MENU_CLR, Key.DIGIT_3, Key.EDIT_AC))
        assertEquals(null, c.menu())
        c.pressAll(listOf(Key.MEM_RCL, Key.VAR_A, Key.EXEC_EQ))
        assertEquals("7", c.resultText())
    }

    /** SHIFT 9 tidak boleh lagi mengetik nama tokennya sendiri ke baris ekspresi. */
    @Test
    fun clearKeyOpensAMenuInsteadOfTypingItself() {
        val c = calc(Key.DIGIT_1, Key.MENU_CLR)
        assertEquals("CLR", c.menu()?.title)
        assertEquals("1", c.exprText())
    }

    /** Tanpa hasil, Salin mengambil baris ekspresinya. */
    @Test
    fun copyFallsBackToTheExpressionLine() {
        val c = calc(Key.DIGIT_1, Key.OP_ADD, Key.DIGIT_2)
        c.requestCopy()
        assertEquals("1+2", c.clipboardOut)
    }

    @Test
    fun insertToggleShowsIndicator() {
        val c = Calculator()
        assertEquals(false, c.indicators().insert)
        c.press(Key.EDIT_INS)
        assertEquals(true, c.indicators().insert)
    }

    /**
     * `(` mencetak satu kurung buka saja; kurung tutupnya baru muncul setelah `)` ditekan.
     * Manual hlm. 16 Contoh 1 menampilkan `4×sin(30)×(30+1▸` selama grupnya belum ditutup.
     */
    @Test
    fun openParenDoesNotDrawItsClosingBracket() {
        assertEquals("(|", calc(Key.PAREN_OPEN).screen())
        assertEquals("(2+3|", calc(Key.PAREN_OPEN, Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_3).screen())
    }

    /** `)` melukis kurung tutupnya lalu memindahkan kursor keluar grup. */
    @Test
    fun closeParenDrawsTheBracketAndLeavesTheGroup() {
        val c = calc(Key.PAREN_OPEN, Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_3, Key.PAREN_CLOSE)
        assertEquals("(2+3)|", c.screen())
        c.press(Key.OP_MUL)
        assertEquals("(2+3)×|", c.screen())
    }

    /** Kurung tutup terakhir boleh dihilangkan sebelum `=` (manual hlm. 16 catatan *3). */
    @Test
    fun unclosedGroupStillEvaluates() {
        val c = calc(Key.PAREN_OPEN, Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_3, Key.EXEC_EQ)
        assertEquals("5", c.resultText())
    }

    /** `)` tetap menutup templat fungsi seperti sebelumnya. */
    @Test
    fun closeParenStillClosesFunctionTemplates() {
        val c = calc(Key.FN_SIN, Key.DIGIT_3, Key.DIGIT_0, Key.PAREN_CLOSE)
        assertEquals("sin(30)", c.exprText())
    }

    /**
     * Ketukan layar menaruh kursor sesudah simpul yang diketuk; -1 berarti awal baris.
     *
     * Indeksnya indeks pada pohon yang sedang tergambar, dan kursor sendiri satu simpul di situ,
     * jadi indeks bergeser tiap kursor pindah. UI menghitung ulang letak simpul tiap gambar,
     * sehingga selalu sejalan.
     */
    @Test
    fun tapMovesCursorBetweenTopLevelNodes() {
        val c = calc(Key.DIGIT_1, Key.OP_ADD, Key.DIGIT_2)
        c.tapExpr(intArrayOf(0))
        assertEquals("1|+2", c.screen())
        c.tapExpr(intArrayOf(-1))
        assertEquals("|1+2", c.screen())
        // Kursor kini simpul ke-0, jadi `+` berada di indeks 2.
        c.tapExpr(intArrayOf(2))
        assertEquals("1+|2", c.screen())
    }

    /**
     * Runtun digit digambar sebagai satu simpul walau di dalam editor tiap tekanan berdiri
     * sendiri, jadi indeks ketukan harus diterjemahkan, bukan dipakai apa adanya.
     */
    @Test
    fun tapCountsAMultiDigitNumberAsOneNode() {
        val c = calc(Key.DIGIT_1, Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_3, Key.DIGIT_4)
        c.tapExpr(intArrayOf(0))
        assertEquals("12|+34", c.screen())
        c.press(Key.DIGIT_9)
        assertEquals("129|+34", c.screen())
    }

    /** Mengetuk di sisi pecahan menaruh kursor di luar pecahannya. */
    @Test
    fun tapBesideAStructureLandsOutsideIt() {
        // Tombol pecahan memungut 1 sebagai pembilang, jadi barisnya 1⌟2 lalu 3 di kanannya.
        val c = calc(Key.DIGIT_1, Key.FRAC, Key.DIGIT_2, Key.NAV_RIGHT, Key.DIGIT_3)
        assertEquals("1⌟23|", c.screen())
        c.tapExpr(intArrayOf(0))
        assertEquals("1⌟2|3", c.screen())
        c.tapExpr(intArrayOf(-1))
        assertEquals("|1⌟23", c.screen())
    }

    /**
     * Ketukan di dalam slot masuk ke slot itu: jalurnya (simpul, slot, simpul di kirinya).
     * Pembilang pecahan adalah slot 0, penyebutnya slot 1.
     */
    @Test
    fun tapInsideAFractionEntersTheSlot() {
        val c = calc(Key.DIGIT_1, Key.DIGIT_2, Key.FRAC, Key.DIGIT_3, Key.DIGIT_4)
        assertEquals("12⌟34|", c.screen())
        // Pembilang, sesudah simpul pertamanya.
        c.tapExpr(intArrayOf(0, 0, 0))
        assertEquals("12|⌟34", c.screen())
        c.press(Key.DIGIT_9)
        assertEquals("129|⌟34", c.screen())
        // Penyebut, di awalnya.
        c.tapExpr(intArrayOf(0, 1, -1))
        assertEquals("129⌟|34", c.screen())
    }

    /** Slot pangkat juga tertembus: 2 pangkat 5, ketukan masuk ke eksponennya. */
    @Test
    fun tapInsideAPowerEntersTheExponent() {
        val c = calc(Key.DIGIT_2, Key.OP_POW, Key.DIGIT_5)
        c.tapExpr(intArrayOf(0, 1, -1))
        c.press(Key.DIGIT_3)
        assertEquals("2^(35)", c.exprText())
    }

    /** Batas integral: slot 1 batas bawah, slot 2 batas atas. */
    @Test
    fun tapInsideAnIntegralEntersItsLimits() {
        val c = calc(
            Key.FN_INTEGRAL, Key.VAR_X, Key.SEP_COMMA, Key.DIGIT_0, Key.SEP_COMMA, Key.DIGIT_1,
        )
        assertEquals("∫(X,0,1)", c.exprText())
        c.tapExpr(intArrayOf(0, 1, 0))
        c.press(Key.DIGIT_7)
        assertEquals("∫(X,07,1)", c.exprText())
    }
}

/** Kalkulus numerik, SOLVE, dan CALC (PRD §5.3, Tahap 3). */
class CalculusTest {

    private fun run(setup: Setup = Setup(), vararg keys: Key): Calculator {
        val c = Calculator(setup)
        c.pressAll(keys.toList())
        return c
    }

    @Test
    fun integralOfSquareIsExactOnPolynomials() {
        val c = run(
            Setup(),
            Key.FN_INTEGRAL, Key.VAR_X, Key.POST_SQUARE, Key.NAV_RIGHT,
            Key.DIGIT_0, Key.NAV_RIGHT, Key.DIGIT_3, Key.EXEC_EQ,
        )
        assertEquals("9", c.resultText())
    }

    @Test
    fun integralHandlesReversedLimits() {
        val c = run(
            Setup(),
            Key.FN_INTEGRAL, Key.VAR_X, Key.NAV_RIGHT,
            Key.DIGIT_2, Key.NAV_RIGHT, Key.DIGIT_0, Key.EXEC_EQ,
        )
        assertEquals("-2", c.resultText())
    }

    @Test
    fun derivativeOfCubeMatchesAnalytic() {
        val c = run(
            Setup(),
            Key.FN_DERIV, Key.VAR_X, Key.POST_CUBE, Key.NAV_RIGHT, Key.DIGIT_2, Key.EXEC_EQ,
        )
        assertEquals("12", c.resultText())
    }

    @Test
    fun summationAddsIntegerRange() {
        val c = run(
            Setup(),
            Key.FN_SIGMA, Key.VAR_X, Key.NAV_RIGHT, Key.DIGIT_1, Key.NAV_RIGHT,
            Key.DIGIT_1, Key.DIGIT_0, Key.EXEC_EQ,
        )
        assertEquals("55", c.resultText())
    }

    @Test
    fun summationRejectsNonIntegerBounds() {
        val c = run(
            Setup(),
            Key.FN_SIGMA, Key.VAR_X, Key.NAV_RIGHT, Key.DIGIT_1, Key.DOT, Key.DIGIT_5,
            Key.NAV_RIGHT, Key.DIGIT_3, Key.EXEC_EQ,
        )
        assertEquals("Math ERROR", c.resultText())
    }

    @Test
    fun solveFindsRootAndReportsResidual() {
        val c = run(
            Setup(),
            Key.VAR_X, Key.POST_SQUARE, Key.OP_SUB, Key.DIGIT_4, Key.FN_SOLVE, Key.EXEC_EQ,
        )
        assertEquals("X=2", c.resultText())
        assertEquals("L-R=0", c.auxText())
    }

    @Test
    fun solveHandlesAnEquationWithBothSides() {
        val c = run(
            Setup(),
            Key.DIGIT_2, Key.VAR_X, Key.OP_ADD, Key.DIGIT_6, Key.REL_EQ, Key.DIGIT_0,
            Key.FN_SOLVE, Key.EXEC_EQ,
        )
        assertEquals("X=-3", c.resultText())
    }

    /** Manual hlm. 39–40: Y=AX²+B dipecahkan untuk X saat Y=0, A=1, B=-2, tebakan awal 1. */
    @Test
    fun solvePromptsEveryOtherVariableThenTheInitialGuess() {
        val c = Calculator()
        c.pressAll(
            listOf(
                Key.VAR_Y, Key.REL_EQ, Key.VAR_A, Key.VAR_X, Key.POST_SQUARE,
                Key.OP_ADD, Key.VAR_B,
            )
        )
        c.press(Key.FN_SOLVE)
        assertEquals("Y?", c.exprText())
        c.pressAll(listOf(Key.DIGIT_0, Key.EXEC_EQ))
        assertEquals("A?", c.exprText())
        c.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("B?", c.exprText())
        c.pressAll(listOf(Key.OP_NEG, Key.DIGIT_2, Key.EXEC_EQ))
        assertEquals("Solve for X", c.exprText())
        c.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("X=1.414213562", c.resultText())
        assertEquals("L-R=0", c.auxText())
    }

    /** Manual hlm. 41–42: Y=X²-X+1 dipecahkan berulang untuk Y = 3, 7, lalu 13. */
    @Test
    fun solveRepeatsFromTheSolutionScreen() {
        val c = Calculator()
        c.pressAll(
            listOf(
                Key.VAR_Y, Key.REL_EQ, Key.VAR_X, Key.POST_SQUARE, Key.OP_SUB, Key.VAR_X,
                Key.OP_ADD, Key.DIGIT_1,
            )
        )
        c.pressAll(listOf(Key.FN_SOLVE, Key.DIGIT_3, Key.EXEC_EQ, Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("X=2", c.resultText())
        c.pressAll(listOf(Key.EXEC_EQ, Key.DIGIT_7, Key.EXEC_EQ, Key.EXEC_EQ))
        assertEquals("X=3", c.resultText())
        c.pressAll(listOf(Key.EXEC_EQ, Key.DIGIT_1, Key.DIGIT_3, Key.EXEC_EQ, Key.EXEC_EQ))
        assertEquals("X=4", c.resultText())
    }

    /** Manual hlm. 41: Newton yang tak bertemu akar menawarkan `Continue:[=]`, bukan error. */
    @Test
    fun solveOffersTheContinueScreen() {
        val c = Calculator()
        c.pressAll(listOf(Key.VAR_X, Key.POST_SQUARE, Key.OP_ADD, Key.DIGIT_1))
        c.pressAll(listOf(Key.FN_SOLVE, Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("Continue:[=]", c.resultText())
        c.press(Key.EDIT_AC)
        assertEquals("", c.resultText())
    }

    /**
     * Manual hlm. 32 Contoh 3: ∫₀^π (sin X + cos X)² dX tampil sebagai π di MthIO-MathO,
     * bukan 3.141592654. Hasil kalkulus lain juga diutamakan bentuk naturalnya.
     */
    @Test
    fun calculusResultsPreferNaturalDisplay() {
        val rad = Setup(angle = AngleUnit.RAD)
        val pi = run(
            rad,
            Key.FN_INTEGRAL, Key.PAREN_OPEN, Key.FN_SIN, Key.VAR_X, Key.PAREN_CLOSE,
            Key.OP_ADD, Key.FN_COS, Key.VAR_X, Key.PAREN_CLOSE, Key.PAREN_CLOSE,
            Key.POST_SQUARE, Key.SEP_COMMA, Key.DIGIT_0, Key.SEP_COMMA, Key.CONST_PI,
            Key.EXEC_EQ,
        )
        assertEquals("π", pi.resultText())
        val third = run(
            rad,
            Key.FN_INTEGRAL, Key.VAR_X, Key.POST_SQUARE, Key.SEP_COMMA, Key.DIGIT_0,
            Key.SEP_COMMA, Key.DIGIT_1, Key.EXEC_EQ,
        )
        assertEquals("1⌟3", third.resultText())
    }

    /** sin 60° bernilai √3⌟2 tepat, jadi Natural Display menampilkannya begitu, bukan desimal. */
    @Test
    fun trigonometryPrefersNaturalDisplay() {
        val c = run(Setup(), Key.FN_SIN, Key.DIGIT_6, Key.DIGIT_0, Key.PAREN_CLOSE, Key.EXEC_EQ)
        assertEquals("√(3)⌟2", c.resultText())
    }

    /** Masukan desimal tetap menghasilkan desimal: `4×3+2.5` bukan `29⌟2` (golden comp-basic). */
    @Test
    fun decimalInputKeepsDecimalResult() {
        val c = run(
            Setup(),
            Key.DIGIT_4, Key.OP_MUL, Key.DIGIT_3, Key.OP_ADD, Key.DIGIT_2, Key.DOT,
            Key.DIGIT_5, Key.EXEC_EQ,
        )
        assertEquals("14.5", c.resultText())
    }

    /** Manual hlm. 74: tanpa variabel yang ditunjuk, persamaan wajib memuat X. */
    @Test
    fun solveWithoutXIsAVariableError() {
        val c = Calculator()
        c.pressAll(listOf(Key.VAR_A, Key.OP_ADD, Key.DIGIT_1, Key.FN_SOLVE))
        assertEquals("Variable ERROR", c.resultText())
        assertEquals("Variable ERROR", c.errorText())
        c.press(Key.EDIT_AC)
        assertEquals("", c.errorText())
        assertEquals("", c.exprText())
    }

    /** Manual hlm. 39: sintaks {persamaan},{variabel} memecahkan Y pada `Y=X+5,Y`. */
    @Test
    fun solveAcceptsAnExplicitSolutionVariable() {
        val c = Calculator()
        c.pressAll(
            listOf(
                Key.VAR_Y, Key.REL_EQ, Key.VAR_X, Key.OP_ADD, Key.DIGIT_5,
                Key.SEP_COMMA, Key.VAR_Y,
            )
        )
        c.pressAll(listOf(Key.FN_SOLVE, Key.DIGIT_3, Key.EXEC_EQ, Key.DIGIT_0, Key.EXEC_EQ))
        assertEquals("Y=8", c.resultText())
    }

    /** Manual hlm. 74: variabel yang ditunjuk harus ada di persamaannya. */
    @Test
    fun solveRejectsASolutionVariableOutsideTheEquation() {
        val c = Calculator()
        c.pressAll(
            listOf(
                Key.VAR_Y, Key.REL_EQ, Key.VAR_X, Key.OP_ADD, Key.DIGIT_5,
                Key.SEP_COMMA, Key.VAR_C, Key.FN_SOLVE,
            )
        )
        assertEquals("Variable ERROR", c.resultText())
    }

    /**
     * Manual hlm. 72: 14÷0×2 keliru, ► menaruh kursor di titik error, lalu ◄1= membetulkannya
     * menjadi 14÷10×2 = 1.
     */
    @Test
    fun arrowKeysGoToTheErrorLocation() {
        val c = Calculator()
        c.pressAll(
            listOf(Key.DIGIT_1, Key.DIGIT_4, Key.OP_DIV, Key.DIGIT_0, Key.OP_MUL, Key.DIGIT_2)
        )
        c.press(Key.EXEC_EQ)
        assertEquals("Math ERROR", c.errorText())
        c.press(Key.NAV_RIGHT)
        assertEquals("", c.errorText())
        assertEquals("14÷0×2", c.exprText())
        c.pressAll(listOf(Key.NAV_LEFT, Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("14÷10×2", c.exprText())
        // MthIO menampilkan hasilnya sebagai pecahan 14/5, persis layar manual hlm. 72.
        assertEquals("14⌟5", c.resultText())
    }

    /** Kursor Goto juga berlaku untuk error di dalam fungsi: letaknya di ujung fungsi itu. */
    @Test
    fun goToPlacesTheCursorAfterAFailingFunction() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_9, Key.OP_ADD, Key.FN_LN, Key.DIGIT_0, Key.PAREN_CLOSE))
        c.press(Key.EXEC_EQ)
        assertEquals("Math ERROR", c.errorText())
        c.pressAll(listOf(Key.NAV_LEFT, Key.EDIT_DEL))
        assertEquals("9+", c.exprText())
    }

    /**
     * Pindah mode meninggalkan layar mode lama: Coefficient Editor EQN dan layar tanya TABLE
     * tidak boleh ikut terbawa ke COMP.
     */
    @Test
    fun leavingAModeClosesItsEditor() {
        val eqn = Calculator()
        eqn.selectMode(Mode.EQN)
        eqn.press(Key.DIGIT_1)
        eqn.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ))
        assertTrue(eqn.gridView() != null, "Coefficient Editor harus tampil di mode EQN")
        eqn.selectMode(Mode.COMP)
        assertEquals(null, eqn.gridView())

        val table = Calculator()
        table.selectMode(Mode.TABLE)
        assertTrue(table.exprText().isNotEmpty(), "TABLE membuka layar tanya Start")
        table.selectMode(Mode.COMP)
        assertEquals("", table.exprText())
    }

    /**
     * Manual hlm. 54 Contoh 2: data 1-VAR {1,2,3,4,5} dengan FREQ {1,2,3,2,1} memberi x̄ = 3
     * dan σx = 1.154700538. Menu diambil sesudah AC, lewat 4 (Var) seperti manual.
     */
    @Test
    fun singleVariableStatisticsFollowTheManual() {
        val c = Calculator()
        c.selectMode(Mode.STAT)
        c.press(Key.DIGIT_1)
        c.setup = c.setup.copy(statFreq = true)
        c.pressAll(
            listOf(
                Key.DIGIT_1, Key.EXEC_EQ, Key.DIGIT_2, Key.EXEC_EQ, Key.DIGIT_3, Key.EXEC_EQ,
                Key.DIGIT_4, Key.EXEC_EQ, Key.DIGIT_5, Key.EXEC_EQ,
                Key.NAV_DOWN, Key.NAV_RIGHT,
                Key.DIGIT_1, Key.EXEC_EQ, Key.DIGIT_2, Key.EXEC_EQ, Key.DIGIT_3, Key.EXEC_EQ,
                Key.DIGIT_2, Key.EXEC_EQ, Key.DIGIT_1, Key.EXEC_EQ,
            )
        )
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_STAT, Key.DIGIT_4, Key.DIGIT_2))
        assertEquals("3", c.resultText())
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_STAT, Key.DIGIT_4, Key.DIGIT_3))
        assertEquals("1.154700538", c.resultText())
    }

    /** Manual hlm. 49-53: nomor butir menu STAT berubah menurut jenis dan menurut layarnya. */
    @Test
    fun statisticsMenuNumbersFollowTheManual() {
        val one = Calculator()
        one.selectMode(Mode.STAT)
        one.press(Key.DIGIT_1)
        one.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ, Key.EDIT_AC, Key.MENU_STAT))
        assertEquals(
            listOf("Type", "Data", "Sum", "Var", "Distr", "MinMax"),
            one.menuView()!!.items,
        )
        one.press(Key.DIGIT_3)
        assertEquals(listOf("Σx²", "Σx"), one.menuView()!!.items)

        val paired = Calculator()
        paired.selectMode(Mode.STAT)
        paired.press(Key.DIGIT_2)
        paired.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ, Key.EDIT_AC, Key.MENU_STAT))
        assertEquals(
            listOf("Type", "Data", "Sum", "Var", "Reg", "MinMax"),
            paired.menuView()!!.items,
        )
        paired.press(Key.DIGIT_5)
        assertEquals(listOf("A", "B", "r", "x̂", "ŷ"), paired.menuView()!!.items)

        // Di dalam Statistics Editor butir ketiga menjadi Edit (manual hlm. 48 dan 49).
        val editing = Calculator()
        editing.selectMode(Mode.STAT)
        editing.press(Key.DIGIT_1)
        editing.press(Key.MENU_STAT)
        assertEquals("Edit", editing.menuView()!!.items[2])
    }

    /**
     * Distribusi normal baku (manual hlm. 51 dan 56): P(0) = 0.5, Q(0) = 0, √(0) = 0.5, dan
     * P(1.96) mendekati 0.975.
     */
    @Test
    fun normalDistributionCommands() {
        fun run(key: Key, digits: List<Key>): String {
            val c = Calculator()
            c.selectMode(Mode.STAT)
            c.press(Key.DIGIT_1)
            c.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ, Key.EDIT_AC))
            c.press(key)
            c.pressAll(digits + Key.PAREN_CLOSE + Key.EXEC_EQ)
            return c.resultText()
        }
        assertEquals("0.5", run(Key.FN_DIST_P, listOf(Key.DIGIT_0)))
        assertEquals("0", run(Key.FN_DIST_Q, listOf(Key.DIGIT_0)))
        assertEquals("0.5", run(Key.FN_DIST_R, listOf(Key.DIGIT_0)))
        assertEquals(
            "0.9750021049",
            run(Key.FN_DIST_P, listOf(Key.DIGIT_1, Key.DOT, Key.DIGIT_9, Key.DIGIT_6)),
        )
    }

    /**
     * Manual hlm. 55 Contoh 3 dan 4 pada data (20,3150) (110,7310) (200,8800) (290,9310),
     * Fix 3: r linear 0.923, r logaritmik 0.998, A = -3857.984, B = 2357.532, dan x̂ untuk
     * y = -130 adalah 4.861.
     */
    @Test
    fun regressionFollowsTheManual() {
        val c = Calculator()
        c.selectMode(Mode.STAT)
        c.press(Key.DIGIT_2)
        c.setup = c.setup.copy(format = DisplayFormat.Fix(3))
        c.pressAll(
            listOf(
                Key.DIGIT_2, Key.DIGIT_0, Key.EXEC_EQ,
                Key.DIGIT_1, Key.DIGIT_1, Key.DIGIT_0, Key.EXEC_EQ,
                Key.DIGIT_2, Key.DIGIT_0, Key.DIGIT_0, Key.EXEC_EQ,
                Key.DIGIT_2, Key.DIGIT_9, Key.DIGIT_0, Key.EXEC_EQ,
                Key.NAV_DOWN, Key.NAV_RIGHT,
                Key.DIGIT_3, Key.DIGIT_1, Key.DIGIT_5, Key.DIGIT_0, Key.EXEC_EQ,
                Key.DIGIT_7, Key.DIGIT_3, Key.DIGIT_1, Key.DIGIT_0, Key.EXEC_EQ,
                Key.DIGIT_8, Key.DIGIT_8, Key.DIGIT_0, Key.DIGIT_0, Key.EXEC_EQ,
                Key.DIGIT_9, Key.DIGIT_3, Key.DIGIT_1, Key.DIGIT_0, Key.EXEC_EQ,
            )
        )
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_STAT, Key.DIGIT_5, Key.DIGIT_3))
        assertEquals("0.923", c.resultText())

        // Ganti jenis ke ln X; data tetap, seperti urutan manual.
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_STAT, Key.DIGIT_1, Key.DIGIT_4))
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_STAT, Key.DIGIT_5, Key.DIGIT_3))
        assertEquals("0.998", c.resultText())
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_STAT, Key.DIGIT_5, Key.DIGIT_1))
        assertEquals("-3857.984", c.resultText())
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_STAT, Key.DIGIT_5, Key.DIGIT_2))
        assertEquals("2357.532", c.resultText())

        // Contoh 4: AC (-)130 SHIFT 1 (STAT) 5 (Reg) 4 (x̂) =
        c.pressAll(listOf(Key.EDIT_AC, Key.OP_NEG, Key.DIGIT_1, Key.DIGIT_3, Key.DIGIT_0))
        c.pressAll(listOf(Key.MENU_STAT, Key.DIGIT_5, Key.DIGIT_4, Key.EXEC_EQ))
        assertEquals("4.861", c.resultText())
    }

    /**
     * Manual hlm. 67: f(X) = X² + 1⌟2 untuk -1 ≤ X ≤ 1 langkah 0.5 memberi tiga baris pertama
     * (-1, 1.5), (-0.5, 0.75), (0, 0.5). Layar tabel berupa kisi X dan F(X).
     */
    @Test
    fun numericalTableFollowsTheManual() {
        val c = Calculator()
        c.selectMode(Mode.TABLE)
        assertEquals("f(X)=", c.exprText())
        c.pressAll(
            listOf(
                Key.VAR_X, Key.POST_SQUARE, Key.OP_ADD, Key.DIGIT_1, Key.FRAC, Key.DIGIT_2,
                Key.EXEC_EQ,
            )
        )
        assertEquals("Start?", c.exprText())
        c.pressAll(listOf(Key.OP_NEG, Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("End?", c.exprText())
        c.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("Step?", c.exprText())
        c.pressAll(listOf(Key.DIGIT_0, Key.DOT, Key.DIGIT_5, Key.EXEC_EQ))

        val grid = c.gridView()!!
        assertEquals(listOf("X", "F(X)"), grid.columns)
        assertEquals(listOf("-1", "1.5"), grid.rows[0])
        assertEquals(listOf("-0.5", "0.75"), grid.rows[1])
        assertEquals(listOf("0", "0.5"), grid.rows[2])
        assertEquals(5, grid.rows.size)

        // AC kembali ke layar isian f(X), bukan mengosongkan semuanya (manual hlm. 67).
        c.press(Key.EDIT_AC)
        assertEquals(null, c.gridView())
        assertEquals("f(X)=X²+1⌟2", c.exprText())
    }

    /** Manual hlm. 67: lebih dari 30 nilai X adalah error, bukan tabel yang dipotong. */
    @Test
    fun tableBeyondThirtyRowsIsAnError() {
        val c = Calculator()
        c.selectMode(Mode.TABLE)
        c.pressAll(listOf(Key.VAR_X, Key.EXEC_EQ))
        c.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ))
        c.pressAll(listOf(Key.DIGIT_4, Key.DIGIT_0, Key.EXEC_EQ))
        c.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ))
        assertEquals("Insufficient MEM", c.errorText())
    }

    /** Manual hlm. 66-67: isian kosong memakai bawaan Start 1, End 5, Step 1. */
    @Test
    fun tablePromptsHaveDefaults() {
        val c = Calculator()
        c.selectMode(Mode.TABLE)
        c.pressAll(listOf(Key.VAR_X, Key.EXEC_EQ, Key.EXEC_EQ, Key.EXEC_EQ, Key.EXEC_EQ))
        val grid = c.gridView()!!
        assertEquals(5, grid.rows.size)
        assertEquals(listOf("1", "1"), grid.rows[0])
        assertEquals(listOf("5", "5"), grid.rows[4])
    }

    /**
     * Manual hlm. 63 dan 65 dengan MatA = [[2,1],[1,1]] dan MatB = [[2,-1],[-1,2]]:
     * MatA×MatB, MatA+MatB, 3×MatA, det(MatA) = 1, dan Abs(MatB) per elemen (Contoh 7).
     */
    @Test
    fun matrixExamplesFollowTheManual() {
        val c = Calculator()
        c.selectMode(Mode.MATRIX)
        // 1 (MatA) 5 (2×2), lalu isinya.
        c.pressAll(listOf(Key.DIGIT_1, Key.DIGIT_5))
        c.pressAll(
            listOf(
                Key.DIGIT_2, Key.EXEC_EQ, Key.DIGIT_1, Key.EXEC_EQ,
                Key.DIGIT_1, Key.EXEC_EQ, Key.DIGIT_1, Key.EXEC_EQ,
            )
        )
        // SHIFT 4 (MATRIX) 2 (Data) 2 (MatB) 5 (2×2): MatB belum berukuran, jadi ditanya.
        c.pressAll(listOf(Key.MENU_MATRIX, Key.DIGIT_2, Key.DIGIT_2, Key.DIGIT_5))
        c.pressAll(
            listOf(
                Key.DIGIT_2, Key.EXEC_EQ, Key.OP_NEG, Key.DIGIT_1, Key.EXEC_EQ,
                Key.OP_NEG, Key.DIGIT_1, Key.EXEC_EQ, Key.DIGIT_2, Key.EXEC_EQ,
            )
        )
        c.press(Key.EDIT_AC)
        c.pressAll(listOf(Key.MENU_MATRIX, Key.DIGIT_3, Key.OP_MUL, Key.MENU_MATRIX, Key.DIGIT_4))
        assertEquals("MatA×MatB", c.exprText())
        c.press(Key.EXEC_EQ)
        assertEquals("[[3,0],[1,1]]", c.resultText())

        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_MATRIX, Key.DIGIT_3, Key.OP_ADD))
        c.pressAll(listOf(Key.MENU_MATRIX, Key.DIGIT_4, Key.EXEC_EQ))
        assertEquals("[[4,0],[0,3]]", c.resultText())

        c.pressAll(listOf(Key.EDIT_AC, Key.DIGIT_3, Key.OP_MUL, Key.MENU_MATRIX, Key.DIGIT_3))
        c.press(Key.EXEC_EQ)
        assertEquals("[[6,3],[3,3]]", c.resultText())

        // Contoh 4: det(MatA) = 1.
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_MATRIX, Key.DIGIT_7))
        c.pressAll(listOf(Key.MENU_MATRIX, Key.DIGIT_3, Key.PAREN_CLOSE, Key.EXEC_EQ))
        assertEquals("1", c.resultText())

        // Contoh 7: Abs(MatB) bekerja per elemen.
        c.pressAll(listOf(Key.EDIT_AC, Key.FN_ABS, Key.MENU_MATRIX, Key.DIGIT_4))
        c.pressAll(listOf(Key.PAREN_CLOSE, Key.EXEC_EQ))
        assertEquals("[[2,1],[1,2]]", c.resultText())
    }

    /** Manual hlm. 64: dari layar MatAns, operator melanjutkan dengan MatAns sebagai operand. */
    @Test
    fun matrixAnswerContinuesAsMatAns() {
        val c = Calculator()
        c.selectMode(Mode.MATRIX)
        c.pressAll(listOf(Key.DIGIT_1, Key.DIGIT_9))
        c.pressAll(listOf(Key.DIGIT_5, Key.EXEC_EQ))
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_MATRIX, Key.DIGIT_3, Key.EXEC_EQ))
        c.press(Key.OP_ADD)
        assertEquals("MatAns+", c.exprText())
    }

    /**
     * Manual hlm. 68–70 dengan VctA = (1,2), VctB = (3,4), VctC = (2,-1,2): penjumlahan,
     * perkalian skalar lalu lanjut dari VctAns, hasil kali titik, hasil kali silang, dan Abs.
     */
    @Test
    fun vectorExamplesFollowTheManual() {
        val c = Calculator()
        c.selectMode(Mode.VECTOR)
        // 1 (VctA) 2 (2), lalu isinya.
        c.pressAll(listOf(Key.DIGIT_1, Key.DIGIT_2))
        c.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ, Key.DIGIT_2, Key.EXEC_EQ))
        // SHIFT 5 (VECTOR) 2 (Data) 2 (VctB) 2 (2): VctB belum berukuran, jadi ditanya.
        c.pressAll(listOf(Key.MENU_VECTOR, Key.DIGIT_2, Key.DIGIT_2, Key.DIGIT_2))
        c.pressAll(listOf(Key.DIGIT_3, Key.EXEC_EQ, Key.DIGIT_4, Key.EXEC_EQ))
        c.press(Key.EDIT_AC)
        c.pressAll(listOf(Key.MENU_VECTOR, Key.DIGIT_3, Key.OP_ADD, Key.MENU_VECTOR, Key.DIGIT_4))
        c.press(Key.EXEC_EQ)
        assertEquals("[4,6]", c.resultText())

        // Contoh 3: 3×VctA = (3,6), lalu lanjut dari VctAns dengan -VctB = (0,2).
        c.pressAll(listOf(Key.EDIT_AC, Key.DIGIT_3, Key.OP_MUL, Key.MENU_VECTOR, Key.DIGIT_3))
        c.press(Key.EXEC_EQ)
        assertEquals("[3,6]", c.resultText())
        c.press(Key.OP_SUB)
        assertEquals("VctAns-", c.exprText())
        c.pressAll(listOf(Key.MENU_VECTOR, Key.DIGIT_4, Key.EXEC_EQ))
        assertEquals("[0,2]", c.resultText())

        // Contoh 4: hasil kali titik lewat butir 7 (Dot).
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_VECTOR, Key.DIGIT_3, Key.MENU_VECTOR, Key.DIGIT_7))
        c.pressAll(listOf(Key.MENU_VECTOR, Key.DIGIT_4, Key.EXEC_EQ))
        assertEquals("11", c.resultText())

        // Contoh 5: hasil kali silang dua vektor bidang memberi vektor ruang.
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_VECTOR, Key.DIGIT_3, Key.OP_MUL))
        c.pressAll(listOf(Key.MENU_VECTOR, Key.DIGIT_4, Key.EXEC_EQ))
        assertEquals("[0,0,-2]", c.resultText())

        // Contoh 6: Abs(VctC) = 3 untuk VctC = (2,-1,2).
        c.pressAll(listOf(Key.EDIT_AC, Key.MENU_VECTOR, Key.DIGIT_1, Key.DIGIT_3, Key.DIGIT_1))
        c.pressAll(
            listOf(
                Key.DIGIT_2, Key.EXEC_EQ, Key.OP_NEG, Key.DIGIT_1, Key.EXEC_EQ,
                Key.DIGIT_2, Key.EXEC_EQ, Key.EDIT_AC,
            )
        )
        c.pressAll(listOf(Key.FN_ABS, Key.MENU_VECTOR, Key.DIGIT_5, Key.PAREN_CLOSE))
        c.press(Key.EXEC_EQ)
        assertEquals("Abs(VctC)", c.exprText())
        assertEquals("3", c.resultText())
    }

    /**
     * Manual hlm. 35: Pol dan Rec. Hasilnya dua nilai sekaligus, disimpan ke variabel X dan Y,
     * dan θ dibatasi -180° < θ ≤ 180°.
     */
    @Test
    fun polarAndRectangularFollowTheManual() {
        val pol = Calculator()
        pol.pressAll(
            listOf(
                Key.FN_POL, Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT, Key.SEP_COMMA,
                Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT, Key.PAREN_CLOSE, Key.EXEC_EQ,
            )
        )
        assertEquals("Pol(√(2),√(2))", pol.exprText())
        assertEquals("r=2,θ=45", pol.resultText())
        // Nilainya tersimpan: RCL X memberi r kembali.
        pol.pressAll(listOf(Key.EDIT_AC, Key.MEM_RCL, Key.VAR_X))
        assertEquals("2", pol.resultText())

        val rec = Calculator()
        rec.pressAll(
            listOf(
                Key.FN_REC, Key.FN_SQRT, Key.DIGIT_2, Key.NAV_RIGHT, Key.SEP_COMMA,
                Key.DIGIT_4, Key.DIGIT_5, Key.PAREN_CLOSE, Key.EXEC_EQ,
            )
        )
        assertEquals("X=1,Y=1", rec.resultText())

        // Linear Display menaruh nilai keduanya di baris bawah (manual hlm. 35).
        val line = Calculator()
        line.setup = line.setup.copy(input = InputMode.LINE_IO)
        line.pressAll(
            listOf(
                Key.FN_POL, Key.DIGIT_1, Key.SEP_COMMA, Key.DIGIT_1, Key.PAREN_CLOSE, Key.EXEC_EQ,
            )
        )
        assertEquals("θ=45", line.auxText())
    }

    /** Sederet contoh bab fungsi umum manual hlm. 23–37, semuanya dengan hasil tercetak. */
    @Test
    fun commonFunctionExamplesFollowTheManual() {
        fun value(setup: Setup, vararg keys: Key): String {
            val c = Calculator()
            c.setup = setup
            c.pressAll(keys.toList())
            c.press(Key.EXEC_EQ)
            return c.resultText()
        }
        val d = Setup()
        val line = Setup(input = InputMode.LINE_IO)

        // Persen, manual hlm. 23.
        assertEquals("30", value(d, Key.DIGIT_1, Key.DIGIT_5, Key.DIGIT_0, Key.OP_MUL, Key.DIGIT_2, Key.DIGIT_0, Key.POST_PERCENT))
        assertEquals("75", value(d, Key.DIGIT_6, Key.DIGIT_6, Key.DIGIT_0, Key.OP_DIV, Key.DIGIT_8, Key.DIGIT_8, Key.DIGIT_0, Key.POST_PERCENT))

        // Trigonometri dan hiperbolik, manual hlm. 28–29.
        assertEquals("0.5", value(line, Key.FN_SIN, Key.DIGIT_3, Key.DIGIT_0, Key.PAREN_CLOSE))
        assertEquals("1.175201194", value(d, Key.FN_SINH, Key.DIGIT_1, Key.PAREN_CLOSE))

        // Logaritma berbasis, manual hlm. 30 Contoh 3.
        assertEquals("6", value(d, Key.FN_LOGBASE, Key.DIGIT_2, Key.NAV_RIGHT, Key.DIGIT_4, Key.OP_POW, Key.DIGIT_3))

        // Akar pangkat tiga bilangan negatif, manual hlm. 31 Contoh 6.
        assertEquals("-1.290024053", value(d, Key.FN_CBRT, Key.DIGIT_5, Key.NAV_RIGHT, Key.OP_ADD, Key.FN_CBRT, Key.OP_NEG, Key.DIGIT_2, Key.DIGIT_7))

        // Faktorial, Abs, nPr, nCr: manual hlm. 36–37.
        assertEquals("40320", value(d, Key.PAREN_OPEN, Key.DIGIT_5, Key.OP_ADD, Key.DIGIT_3, Key.PAREN_CLOSE, Key.POST_FACT))
        assertEquals("10", value(d, Key.FN_ABS, Key.DIGIT_2, Key.OP_SUB, Key.DIGIT_7, Key.PAREN_CLOSE, Key.OP_MUL, Key.DIGIT_2))
        assertEquals("5040", value(d, Key.DIGIT_1, Key.DIGIT_0, Key.OP_NPR, Key.DIGIT_4))
        assertEquals("210", value(d, Key.DIGIT_1, Key.DIGIT_0, Key.OP_NCR, Key.DIGIT_4))

        // Derajat-menit-detik, manual hlm. 24 Contoh 1.
        assertEquals(
            "3°0'0\"",
            value(
                d, Key.DIGIT_2, Key.POST_DMS, Key.DIGIT_2, Key.DIGIT_0, Key.POST_DMS,
                Key.DIGIT_3, Key.DIGIT_0, Key.POST_DMS, Key.OP_ADD, Key.DIGIT_0, Key.POST_DMS,
                Key.DIGIT_3, Key.DIGIT_9, Key.POST_DMS, Key.DIGIT_3, Key.DIGIT_0, Key.POST_DMS,
            ),
        )

        // Rnd dengan Fix 3, manual hlm. 37.
        assertEquals(
            "9.999",
            value(
                Setup(input = InputMode.LINE_IO, format = DisplayFormat.Fix(3)),
                Key.FN_RND, Key.DIGIT_1, Key.DIGIT_0, Key.OP_DIV, Key.DIGIT_3,
                Key.PAREN_CLOSE, Key.OP_MUL, Key.DIGIT_3,
            ),
        )
    }

    /** Manual hlm. 39: SOLVE hanya ada di mode COMP. */
    @Test
    fun solveIsIgnoredOutsideCompMode() {
        val c = Calculator()
        c.selectMode(Mode.CMPLX)
        c.pressAll(listOf(Key.VAR_X, Key.POST_SQUARE, Key.OP_SUB, Key.DIGIT_4, Key.FN_SOLVE))
        assertEquals("X²-4", c.exprText())
    }

    @Test
    fun calcPromptsForEachVariableThenEvaluates() {
        val c = Calculator()
        c.pressAll(listOf(Key.VAR_A, Key.OP_MUL, Key.VAR_B))
        c.press(Key.FN_CALC)
        assertEquals("A?", c.exprText())
        c.pressAll(listOf(Key.DIGIT_6, Key.EXEC_EQ))
        assertEquals("B?", c.exprText())
        c.pressAll(listOf(Key.DIGIT_7, Key.EXEC_EQ))
        assertEquals("A×B", c.exprText())
        assertEquals("42", c.resultText())
    }

    /** Manual hlm. 38: 3A+B dengan (A,B) = (5,10) lalu (7,20); CALC atau `=` menanya ulang. */
    @Test
    fun calcRepeatsWithCalcOrEquals() {
        val c = Calculator()
        c.pressAll(listOf(Key.DIGIT_3, Key.VAR_A, Key.OP_ADD, Key.VAR_B))
        c.pressAll(listOf(Key.FN_CALC, Key.DIGIT_5, Key.EXEC_EQ, Key.DIGIT_1, Key.DIGIT_0, Key.EXEC_EQ))
        assertEquals("3A+B", c.exprText())
        assertEquals("25", c.resultText())
        c.press(Key.FN_CALC)
        assertEquals("A?", c.exprText())
        assertEquals("5", c.resultText())
        c.pressAll(listOf(Key.DIGIT_7, Key.EXEC_EQ, Key.DIGIT_2, Key.DIGIT_0, Key.EXEC_EQ))
        assertEquals("41", c.resultText())
        c.press(Key.EXEC_EQ)
        assertEquals("A?", c.exprText())
        c.press(Key.EDIT_AC)
        assertEquals("3A+B", c.exprText())
    }
}

/** Simpan dan pulihkan status (PRD §6.3, Tahap 7). */
class PersistenceTest {

    @Test
    fun snapshotRoundTripKeepsStateAndStaysSmall() {
        val calc = Calculator()
        calc.pressAll(listOf(Key.DIGIT_3, Key.OP_ADD, Key.DIGIT_5, Key.EXEC_EQ))
        calc.pressAll(listOf(Key.MEM_STO, Key.VAR_A))
        calc.pressAll(listOf(Key.DIGIT_2, Key.FRAC, Key.DIGIT_7))
        val text = CalculatorSnapshot.encode(calc.save())
        assertTrue(text.length < 10_000, "ukuran status harus di bawah 10 kB, ada ${text.length}")

        val restored = Calculator()
        restored.restore(CalculatorSnapshot.decode(text))
        assertEquals("2⌟7", restored.exprText())
        restored.pressAll(listOf(Key.EXEC_EQ))
        assertEquals("2⌟7", restored.resultText())
        restored.pressAll(listOf(Key.EDIT_AC, Key.VAR_A, Key.OP_MUL, Key.DIGIT_2, Key.EXEC_EQ))
        assertEquals("16", restored.resultText())
    }

    /**
     * Ekspresi yang sedang diketik sesudah hasil, lalu aplikasi dibuka ulang (atau dimatikan
     * XOS): ◄ harus menggeser kursor di ekspresi itu, bukan menggantinya dengan entri riwayat.
     */
    @Test
    fun restoredUnevaluatedExpressionStaysEditable() {
        val calc = Calculator()
        calc.pressAll(listOf(Key.DIGIT_2, Key.EXEC_EQ, Key.DIGIT_5, Key.OP_ADD, Key.DIGIT_6))

        val restored = Calculator()
        restored.restore(CalculatorSnapshot.decode(CalculatorSnapshot.encode(calc.save())))
        assertEquals("5+6", restored.exprText())
        restored.pressAll(listOf(Key.NAV_LEFT, Key.DIGIT_0))
        assertEquals("5+06", restored.exprText())
    }

    @Test
    fun snapshotRestoresVisibleResultAndStartsFreshOnNextDigit() {
        val calc = Calculator()
        calc.pressAll(listOf(Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_3, Key.OP_MUL, Key.DIGIT_4, Key.EXEC_EQ))
        assertEquals("14", calc.resultText())

        val restored = Calculator()
        restored.restore(CalculatorSnapshot.decode(CalculatorSnapshot.encode(calc.save())))
        assertEquals("2+3×4", restored.exprText())
        assertEquals("14", restored.resultText())
        restored.pressAll(listOf(Key.DIGIT_7, Key.EXEC_EQ))
        assertEquals("7", restored.exprText())
        assertEquals("7", restored.resultText())
    }

    @Test
    fun snapshotKeepsModeSetupAndContainers() {
        val calc = Calculator()
        calc.selectMode(Mode.VECTOR)
        // VctA berukuran 3 dipilih dari menu, lalu Vector Editor diisi dan ditutup dengan AC.
        calc.pressAll(listOf(Key.DIGIT_1, Key.DIGIT_1))
        calc.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ, Key.DIGIT_2, Key.EXEC_EQ, Key.DIGIT_3, Key.EXEC_EQ))
        calc.press(Key.EDIT_AC)
        val snapshot = CalculatorSnapshot.decode(CalculatorSnapshot.encode(calc.save()))

        val restored = Calculator()
        restored.restore(snapshot)
        assertEquals(Mode.VECTOR, restored.mode)
        restored.pressAll(listOf(Key.VCT_A, Key.FN_DOT, Key.VCT_A, Key.EXEC_EQ))
        assertEquals("14", restored.resultText())
    }

    /** Hasil matriks pernah membuat penyimpanan status mogok karena Ans tidak bernilai tunggal. */
    @Test
    fun snapshotSurvivesMatrixAndComplexAnswers() {
        val calc = Calculator()
        calc.selectMode(Mode.MATRIX)
        calc.pressAll(listOf(Key.DIGIT_1, Key.DIGIT_5))
        calc.pressAll(listOf(Key.DIGIT_1, Key.EXEC_EQ, Key.DIGIT_2, Key.EXEC_EQ))
        calc.pressAll(listOf(Key.DIGIT_3, Key.EXEC_EQ, Key.DIGIT_4, Key.EXEC_EQ, Key.EDIT_AC))
        calc.pressAll(listOf(Key.MAT_A, Key.OP_MUL, Key.DIGIT_2, Key.EXEC_EQ))
        assertEquals("[[2,4],[6,8]]", calc.resultText())

        val restored = Calculator()
        restored.restore(CalculatorSnapshot.decode(CalculatorSnapshot.encode(calc.save())))
        restored.press(Key.EDIT_AC)
        restored.pressAll(listOf(Key.MAT_A, Key.EXEC_EQ))
        assertEquals("[[1,2],[3,4]]", restored.resultText())
    }

    @Test
    fun setupMenuTogglesAngleUnitAndHaptic() {
        val calc = Calculator()
        calc.press(Key.MENU_SETUP)
        calc.press(Key.DIGIT_4)
        assertEquals(AngleUnit.RAD, calc.setup.angle)
        // Getaran pindah ke menu OPTION butir 2, submenu 1 mati dan 2 nyala; bawaannya mati.
        assertEquals(false, calc.hapticEnabled)
        calc.pressAll(listOf(Key.MENU_OPTION, Key.DIGIT_2, Key.DIGIT_2))
        assertEquals(true, calc.hapticEnabled)
        calc.pressAll(listOf(Key.MENU_OPTION, Key.DIGIT_2, Key.DIGIT_1))
        assertEquals(false, calc.hapticEnabled)
        calc.pressAll(listOf(Key.MENU_OPTION, Key.DIGIT_1, Key.DIGIT_3))
        assertEquals(2, calc.skin)
    }
}

/** Baris hasil Natural Display: pecahan dan akar digambar sebagai struktur, bukan teks datar. */
class ResultTreeTest {

    private fun tree(vararg keys: Key): List<Node> {
        val c = Calculator()
        c.pressAll(keys.toList())
        c.press(Key.EXEC_EQ)
        return c.resultTree()
    }

    /**
     * Manual hlm. 46: (2+6i)÷(2i) tampil 3-i, bukan 3-1i. Baris hasil yang digambar LCD harus
     * memakai aturan koefisien 1 yang sama dengan baris teksnya.
     */
    @Test
    fun unitImaginaryCoefficientIsNotWritten() {
        val c = Calculator()
        c.selectMode(Mode.CMPLX)
        c.pressAll(
            listOf(
                Key.PAREN_OPEN, Key.DIGIT_2, Key.OP_ADD, Key.DIGIT_6, Key.CONST_I,
                Key.PAREN_CLOSE, Key.OP_DIV, Key.PAREN_OPEN, Key.DIGIT_2, Key.CONST_I,
                Key.PAREN_CLOSE, Key.EXEC_EQ,
            )
        )
        assertEquals("3-i", c.resultText())
        assertEquals(
            listOf(NumNode("3"), KeyNode(Key.OP_SUB), KeyNode(Key.CONST_I)),
            c.resultTree(),
        )
    }

    @Test
    fun fractionResultIsAStackedNode() {
        val nodes = tree(Key.DIGIT_2, Key.FRAC, Key.DIGIT_3)
        val call = nodes.single() as CallNode
        assertEquals(Key.FRAC, call.key)
        assertEquals(listOf(NumNode("2")), call.args[0])
        assertEquals(listOf(NumNode("3")), call.args[1])
    }

    @Test
    fun surdResultKeepsTheRootSymbol() {
        val nodes = tree(Key.FN_SQRT, Key.DIGIT_8)
        assertEquals(NumNode("2"), nodes[0])
        assertEquals(Key.FN_SQRT, (nodes[1] as CallNode).key)
    }

    @Test
    fun complexResultKeepsImaginaryUnit() {
        val c = Calculator()
        c.selectMode(Mode.CMPLX)
        c.pressAll(listOf(Key.DIGIT_1, Key.OP_ADD, Key.CONST_I, Key.EXEC_EQ))
        val nodes = c.resultTree()
        assertEquals(KeyNode(Key.CONST_I), nodes.last())
    }

    @Test
    fun decimalResultStaysASingleText() {
        val nodes = tree(Key.DIGIT_1, Key.DIGIT_4)
        assertEquals(listOf(NumNode("14")), nodes)
    }

    @Test
    fun hyperbolicSineMatchesKnownValue() {
        val c = Calculator()
        c.press(Key.MENU_HYP)
        c.press(Key.DIGIT_1)
        c.pressAll(listOf(Key.DIGIT_1, Key.PAREN_CLOSE, Key.EXEC_EQ))
        assertEquals("1.175201194", c.resultText())
    }

    @Test
    fun constantMenuInsertsSpeedOfLight() {
        val c = Calculator()
        c.press(Key.MENU_CONST)
        c.pressAll(listOf(Key.DIGIT_2, Key.DIGIT_8, Key.EXEC_EQ))
        assertEquals("299792458", c.resultText())
    }

    @Test
    fun conversionMenuConvertsCentimetreToInch() {
        // Manual hlm. 44 Contoh 1-3: perintahnya masuk ke ekspresi, `=` yang menghitung.
        val cm = Calculator()
        cm.pressAll(listOf(Key.DIGIT_5, Key.MENU_CONV, Key.DIGIT_0, Key.DIGIT_2))
        assertEquals("5cm▸in", cm.exprText())
        cm.press(Key.EXEC_EQ)
        assertEquals("1.968503937", cm.resultText())

        val gram = Calculator()
        gram.pressAll(
            listOf(Key.DIGIT_1, Key.DIGIT_0, Key.DIGIT_0, Key.MENU_CONV, Key.DIGIT_2, Key.DIGIT_2)
        )
        assertEquals("100g▸oz", gram.exprText())
        gram.press(Key.EXEC_EQ)
        // Manual mencetak 3.527396584, tetapi manual sendiri menyebut sumbernya NIST SP 811,
        // yang menetapkan 1 oz = 28.349523125 g tepat sehingga 100 g = 3.527396195 oz. Dua
        // contoh lain di halaman itu cocok persis, jadi angka ini menunggu cek unit fisik.
        assertEquals("3.527396195", gram.resultText())

        val celsius = Calculator()
        celsius.pressAll(
            listOf(
                Key.OP_NEG, Key.DIGIT_3, Key.DIGIT_1, Key.MENU_CONV, Key.DIGIT_3, Key.DIGIT_8,
                Key.EXEC_EQ,
            )
        )
        assertEquals("-23.8", celsius.resultText())
    }

    /** Manual hlm. 42 Contoh 1: SHIFT 7 28 menuliskan simbol C₀, dan `=` memberi nilainya. */
    @Test
    fun constantIsWrittenAsItsSymbol() {
        val c = Calculator()
        c.pressAll(listOf(Key.MENU_CONST, Key.DIGIT_2, Key.DIGIT_8))
        assertEquals(ScientificConstants.byNumber(28)!!.symbol, c.exprText())
        c.press(Key.EXEC_EQ)
        assertEquals("299792458", c.resultText())
    }

    /** Manual hlm. 42 Contoh 2: C₀ = 1/√(ε₀μ₀). */
    @Test
    fun constantsCombineInsideAnExpression() {
        val c = Calculator()
        c.pressAll(
            listOf(
                Key.FRAC, Key.DIGIT_1, Key.NAV_DOWN, Key.FN_SQRT,
                Key.MENU_CONST, Key.DIGIT_3, Key.DIGIT_2,
                Key.MENU_CONST, Key.DIGIT_3, Key.DIGIT_3, Key.EXEC_EQ,
            )
        )
        assertEquals("299792458", c.resultText())
    }

    /** Manual hlm. 42 dan 44: CONST dilarang di BASE-N, CONV dilarang di BASE-N dan TABLE. */
    @Test
    fun constantAndConversionAreBlockedByMode() {
        val base = Calculator()
        base.selectMode(Mode.BASE)
        base.press(Key.MENU_CONST)
        assertEquals(null, base.twoDigitPrompt())
        base.press(Key.MENU_CONV)
        assertEquals(null, base.twoDigitPrompt())

        val table = Calculator()
        table.selectMode(Mode.TABLE)
        table.press(Key.MENU_CONV)
        assertEquals(null, table.twoDigitPrompt())
    }

    /** Layar tanya nomor dua digit seperti manual hlm. 42 dan 44. */
    @Test
    fun twoDigitPromptMatchesTheManual() {
        val c = Calculator()
        c.press(Key.MENU_CONST)
        assertEquals(listOf("CONSTANT", "Number 01~40?", "[__]"), c.twoDigitPrompt())
        c.press(Key.DIGIT_2)
        assertEquals(listOf("CONSTANT", "Number 01~40?", "[2_]"), c.twoDigitPrompt())
        c.press(Key.DIGIT_8)
        assertEquals(null, c.twoDigitPrompt())
    }
}
