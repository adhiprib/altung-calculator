package id.sempoa570.desktop

import androidx.compose.ui.input.key.Key as PcKey
import id.sempoa570.engine.Mode
import id.sempoa570.ui.CalculatorController
import id.sempoa570.ui.Keymap
import id.sempoa570.ui.Layer
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keyboard PC (PRD §4.6). Kode tombol dan huruf diambil dari AWT seperti yang dikirim Windows,
 * lalu masuk ke `handle` yang sama dengan jendela; hanya pembacaan KeyEvent Compose yang dilewati.
 */
class KeyboardMapTest {

    private val keymap = Keymap.parse(resource("keymap.json"))
    private val map = KeyboardMap(keymap)
    private val controller = CalculatorController(CoroutineScope(Dispatchers.Unconfined))

    /** Baris ekspresi kosong tetap berisi simpul kursor. */
    private val blank = controller.expression

    private fun down(code: Int, char: Char = AwtKeyEvent.CHAR_UNDEFINED, modifiers: Int = 0): Boolean {
        val point = if (char == AwtKeyEvent.CHAR_UNDEFINED) 0 else char.code
        return map.handle(PcKey(code), point, modifiers and InputEvent.CTRL_DOWN_MASK != 0, controller)
    }

    private fun type(text: String) = text.forEach { down(AwtKeyEvent.getExtendedKeyCodeForChar(it.code), it) }

    @Test
    fun mengetikLaluEnter() {
        type("12*3+9")
        down(AwtKeyEvent.VK_ENTER, '\n')
        assertTrue(controller.result.toString(), controller.result.toString().contains("45"))
        down(AwtKeyEvent.VK_BACK_SPACE, '\b')
        down(AwtKeyEvent.VK_ESCAPE, 27.toChar())
        assertEquals(blank, controller.expression)
    }

    @Test
    fun angkaMemilihButirMenu() {
        down(AwtKeyEvent.VK_F3)
        type("2")
        assertEquals(Mode.CMPLX, controller.snapshot().mode)
    }

    @Test
    fun f1LaluAngkaMemakaiLapisShift() {
        down(AwtKeyEvent.VK_F1)
        type("7")
        assertTrue(controller.prompt.toString(), controller.prompt.any { "CONSTANT" in it })
    }

    @Test
    fun karakterDiLapisLainDicariTombolnya() {
        assertEquals(PcAction.Press("KEY_POWER"), map.action(PcKey.Six, '^', false))
        assertEquals(PcAction.Press("KEY_RPAREN", Layer.SHIFT), map.action(PcKey.Comma, ',', false))
        assertEquals(PcAction.Press("KEY_RPAREN", Layer.ALPHA), map.action(PcKey.X, 'X', false))
        assertNull(map.action(PcKey.Q, 'q', false))
    }

    @Test
    fun hurufAsingTidakDitelan() {
        assertFalse(down(AwtKeyEvent.VK_Q, 'q'))
        assertFalse(down(AwtKeyEvent.VK_TAB, '\t'))
    }

    @Test
    fun escMenutupPanduan() {
        down(AwtKeyEvent.VK_F4)
        type("3")
        assertTrue(controller.manualOpen)
        type("5")
        assertEquals("papan tombol tertutup selama panduan terbuka", blank, controller.expression)
        down(AwtKeyEvent.VK_ESCAPE, 27.toChar())
        assertFalse(controller.manualOpen)
    }

    @Test
    fun ctrlCDanCtrlV() {
        type("9+9=")
        down(AwtKeyEvent.VK_C, 3.toChar(), InputEvent.CTRL_DOWN_MASK)
        assertEquals("18", controller.clipboardOut)
        controller.clipboardTaken()
        down(AwtKeyEvent.VK_V, 22.toChar(), InputEvent.CTRL_DOWN_MASK)
        assertTrue(controller.pasteRequested)
    }
}
