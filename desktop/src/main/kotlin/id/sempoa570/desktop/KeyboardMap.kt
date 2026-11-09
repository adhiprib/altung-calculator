package id.sempoa570.desktop

import androidx.compose.ui.input.key.Key as PcKey
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import id.sempoa570.engine.Clipboard
import id.sempoa570.engine.Key
import id.sempoa570.ui.CalculatorController
import id.sempoa570.ui.Keymap
import id.sempoa570.ui.Layer

/** Yang dilakukan satu tekanan keyboard PC (PRD §4.6). */
internal sealed interface PcAction {
    /**
     * Tekan tombol keymap [id]. [layer] MAIN berarti tekan apa adanya, sehingga SHIFT atau ALPHA
     * yang tertunda (F1, F2) tetap berlaku seperti klik; lapis lain dipasang dulu bila perlu.
     */
    data class Press(val id: String, val layer: Layer = Layer.MAIN) : PcAction
    data object Escape : PcAction
    data object Copy : PcAction
    data object Paste : PcAction
}

/**
 * Keyboard PC sebagai jalan pintas papan tombol (PRD §4.6, revisi 28 September 2026).
 *
 * Karakter diterjemahkan dengan kosakata Tempel ([Clipboard.keysFrom]), lalu dicari tombol
 * keymap asalnya dan ditekan lewat jalur yang sama dengan klik. Sengaja tidak memanggil Tempel
 * langsung: Tempel menutup menu yang terbuka, padahal angka yang diketik harus bisa memilih butir
 * menu.
 */
internal class KeyboardMap(private val keymap: Keymap) {

    /** Tombol dan lapis tempat token engine tercetak; lapis utama didahulukan. */
    private val home: Map<Key, Pair<String, Layer>> = buildMap {
        for (layer in listOf(Layer.MAIN, Layer.SHIFT, Layer.ALPHA)) {
            for (entry in keymap.keys) {
                val printed = when (layer) {
                    Layer.MAIN -> entry.main
                    Layer.SHIFT -> entry.shift
                    Layer.ALPHA -> entry.alpha
                } ?: continue
                val key = printed.key ?: continue
                if (key !in this) put(key, entry.id to layer)
            }
        }
    }

    fun action(key: PcKey, char: Char?, ctrl: Boolean): PcAction? {
        if (ctrl) return when (key) {
            PcKey.C -> PcAction.Copy
            PcKey.V -> PcAction.Paste
            else -> null
        }
        when (key) {
            PcKey.Enter, PcKey.NumPadEnter -> return PcAction.Press("KEY_EQ")
            PcKey.Backspace, PcKey.Delete -> return PcAction.Press("KEY_DEL")
            PcKey.Escape -> return PcAction.Escape
            PcKey.DirectionUp -> return PcAction.Press("KEY_UP")
            PcKey.DirectionDown -> return PcAction.Press("KEY_DOWN")
            PcKey.DirectionLeft -> return PcAction.Press("KEY_LEFT")
            PcKey.DirectionRight -> return PcAction.Press("KEY_RIGHT")
            PcKey.F1 -> return PcAction.Press("KEY_SHIFT")
            PcKey.F2 -> return PcAction.Press("KEY_ALPHA")
            PcKey.F3 -> return PcAction.Press("KEY_MODE")
            PcKey.F4 -> return PcAction.Press("KEY_ON")
        }
        return when (char) {
            null -> null
            '=' -> PcAction.Press("KEY_EQ")
            // Seperti tombol − kalkulator; tanda negatif tetap lewat tombol (−) di layar.
            '-', '−' -> PcAction.Press("KEY_SUB")
            else -> Clipboard.keysFrom(char.toString())?.singleOrNull()
                ?.let { home[it] }
                ?.let { (id, layer) -> PcAction.Press(id, layer) }
        }
    }

    /** Menjalankan satu tekanan keyboard; false bila tombolnya bukan milik kalkulator. */
    fun handle(event: KeyEvent, controller: CalculatorController): Boolean =
        event.type == KeyEventType.KeyDown && !event.isAltPressed &&
            handle(event.key, event.utf16CodePoint, event.isCtrlPressed, controller)

    /** [codePoint] huruf yang dihasilkan tombol, sudah termasuk Shift PC; kode kendali diabaikan. */
    fun handle(key: PcKey, codePoint: Int, ctrl: Boolean, controller: CalculatorController): Boolean {
        val char = codePoint.takeIf { it > 0x1F && it != 0x7F }?.toChar()
        val action = action(key, char, ctrl) ?: return false
        // Selama panduan terbuka, papan tombol tertutup; hanya Esc yang berlaku.
        if (controller.manualOpen) {
            if (action == PcAction.Escape) controller.closeManual()
            return true
        }
        when (action) {
            PcAction.Escape -> controller.onKey(keymap["KEY_AC"])
            PcAction.Copy -> {
                controller.openClipboardMenu(fromResult = true)
                controller.onMenuSelect(1)
            }
            PcAction.Paste -> {
                controller.openClipboardMenu(fromResult = false)
                controller.onMenuSelect(2)
            }
            is PcAction.Press -> {
                if (action.layer != Layer.MAIN && controller.layer != action.layer) {
                    controller.onKey(keymap[if (action.layer == Layer.SHIFT) "KEY_SHIFT" else "KEY_ALPHA"])
                }
                controller.onKey(keymap[action.id])
            }
        }
        return true
    }
}
