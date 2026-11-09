package id.sempoa570.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import id.sempoa570.engine.CalculatorSnapshot
import id.sempoa570.engine.Mode
import id.sempoa570.ui.CalculatorController
import id.sempoa570.ui.Keymap
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Masukan mouse lewat jalur pointer Compose yang sama dengan jendela sungguhan, tanpa menyentuh
 * layar atau papan klip pemakai: papan klip Windows diganti papan klip tiruan.
 */
class DesktopTest {

    @get:Rule
    val rule = createComposeRule()

    private val keymap = Keymap.parse(resource("keymap.json"))
    private val clipboard = Clipboard("uji")
    private lateinit var controller: CalculatorController

    private fun start() = rule.setContent {
        val scope = rememberCoroutineScope()
        controller = remember { CalculatorController(scope) }
        SystemClipboard(controller, clipboard)
        AltungContent(controller, keymap)
    }

    private fun press(vararg labels: String) = labels.forEach {
        rule.onNodeWithContentDescription(it).performClick()
        rule.waitForIdle()
    }

    private fun resultText() = controller.snapshot().let { CalculatorSnapshot.encode(it) }

    @Test
    fun klikTombolMenghitung() {
        start()
        press("9", "+", "9", "=")
        assertTrue(resultText(), controller.result.toString().contains("18"))
    }

    @Test
    fun ketukButirMenuMemilihMode() {
        start()
        press("MODE")
        rule.onNodeWithText("2:CMPLX").performClick()
        rule.waitForIdle()
        assertEquals(Mode.CMPLX, controller.snapshot().mode)
    }

    @Test
    fun tekanLamaHasilLaluSalin() {
        start()
        press("9", "+", "9", "=")
        // Baris hasil ada di kanan bawah panel LCD yang tingginya dipatok 218 dp.
        val dp = rule.density.density
        rule.onRoot().performTouchInput { longClick(Offset(width - 60 * dp, 170 * dp)) }
        rule.waitForIdle()
        press("1")
        assertEquals("18", clipboard.getData(DataFlavor.stringFlavor))
    }

    @Test
    fun tempelDariOption() {
        start()
        clipboard.setContents(StringSelection("2×3"), null)
        press("OPTION", "5")
        press("=")
        assertTrue(resultText(), controller.result.toString().contains("6"))
    }

    @Test
    fun statusBerkasPulih() = runBlocking {
        val file = File.createTempFile("altung", ".json").also { it.deleteOnExit() }
        val store = FileSnapshotStore(file)
        store.save(CalculatorSnapshot(skin = 2))
        assertEquals(2, store.load().skin)
        file.writeText("rusak{")
        assertEquals(CalculatorSnapshot(), store.load())
    }
}
