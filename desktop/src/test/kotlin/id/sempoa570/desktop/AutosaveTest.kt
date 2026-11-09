package id.sempoa570.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.awaitApplication
import androidx.compose.ui.window.rememberWindowState
import id.sempoa570.ui.CalculatorController
import id.sempoa570.ui.Keymap
import java.awt.GraphicsEnvironment
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * Simpan otomatis selama jendela terbuka (PRD §6.3), dengan susunan yang sama dengan Main.kt:
 * scope dari `application`, bukan dari tes Compose. Jendela di luar layar dan tanpa fokus.
 */
class AutosaveTest {

    @Test
    fun tombolTersimpanTanpaMenutupJendela() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val keymap = Keymap.parse(resource("keymap.json"))
        val file = File.createTempFile("altung-simpan", ".json").also { it.delete(); it.deleteOnExit() }
        var seen = ""
        runBlocking {
            awaitApplication {
                val scope = rememberCoroutineScope()
                val controller = remember { CalculatorController(scope, FileSnapshotStore(file)) }
                Window(
                    onCloseRequest = ::exitApplication,
                    state = rememberWindowState(position = WindowPosition(-6000.dp, -6000.dp)),
                    focusable = false,
                ) {
                    LaunchedEffect(Unit) {
                        delay(500)
                        listOf("KEY_7", "KEY_MUL", "KEY_6").forEach { controller.onKey(keymap[it]) }
                        // Debounce 200 ms; beri waktu cukup lalu baca berkasnya sambil jendela terbuka.
                        delay(1500)
                        seen = if (file.exists()) file.readText() else ""
                        exitApplication()
                    }
                }
            }
        }
        assertTrue("status harus tersimpan selama jendela terbuka, berkas: '$seen'", seen.contains("OP_MUL"))
    }
}
