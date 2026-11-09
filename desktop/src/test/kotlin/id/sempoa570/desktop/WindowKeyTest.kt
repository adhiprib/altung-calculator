package id.sempoa570.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import id.sempoa570.ui.CalculatorController
import id.sempoa570.ui.Keymap
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * Jalur keyboard utuh di jendela Compose sungguhan: event AWT → KeyEvent Compose →
 * onPreviewKeyEvent → [KeyboardMap]. Jendela diletakkan di luar layar dan tidak boleh merebut
 * fokus, jadi pekerjaan pemakai di layar tidak terganggu; event dikirim langsung ke komponennya.
 */
class WindowKeyTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun ketikanSampaiKeKalkulator() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val keymap = Keymap.parse(resource("keymap.json"))
        val keyboard = KeyboardMap(keymap)
        val controller = CalculatorController(CoroutineScope(Dispatchers.Unconfined))
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                focusableWindowState = false
                isAutoRequestFocus = false
                setBounds(-6000, -6000, 420, 800)
                setContent(onPreviewKeyEvent = { keyboard.handle(it, controller) }) {
                    AltungContent(controller, keymap)
                }
                isVisible = true
            }
        }
        try {
            Thread.sleep(800)
            val target = listeningComponent(window)
            // Tata letak AS: * dan + memakai Shift, persis event yang dikirim Windows.
            val strokes = listOf(
                Triple(KeyEvent.VK_1, '1', 0),
                Triple(KeyEvent.VK_2, '2', 0),
                Triple(KeyEvent.VK_8, '*', InputEvent.SHIFT_DOWN_MASK),
                Triple(KeyEvent.VK_3, '3', 0),
                Triple(KeyEvent.VK_EQUALS, '+', InputEvent.SHIFT_DOWN_MASK),
                Triple(KeyEvent.VK_9, '9', 0),
                Triple(KeyEvent.VK_ENTER, '\n', 0),
            )
            for ((code, char, modifiers) in strokes) {
                SwingUtilities.invokeAndWait {
                    val now = System.currentTimeMillis()
                    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, modifiers, code, char))
                    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, modifiers, code, char))
                }
            }
            SwingUtilities.invokeAndWait { }
            assertTrue(controller.result.toString(), controller.result.toString().contains("45"))
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    /** Komponen tempat Compose memasang pendengar keyboardnya. */
    private fun listeningComponent(root: Component): Component {
        if (root.keyListeners.isNotEmpty()) return root
        if (root is Container) root.components.forEach { child ->
            runCatching { return listeningComponent(child) }
        }
        error("tidak ada komponen yang mendengar keyboard di ${root.javaClass.name}")
    }
}
