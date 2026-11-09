package id.sempoa570.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.sempoa570.ui.CalculatorApp
import id.sempoa570.ui.CalculatorController
import id.sempoa570.ui.Keymap
import id.sempoa570.ui.ManualBook
import id.sempoa570.ui.ManualScreen
import id.sempoa570.ui.SempoaTheme
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import javax.imageio.ImageIO

/** Folder status per mesin (PRD §6.3): %LOCALAPPDATA%\Altung. */
private val dataDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Altung")

/** keymap.json dan manual.json disalin dari docs/ ke sumber daya saat build. */
internal fun resource(name: String): String =
    requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(name)) { name }
        .use { it.readBytes().decodeToString() }

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--shot") return shot(args.drop(1))
    installCrashLog()
    val keymap = Keymap.parse(resource("keymap.json"))
    val store = FileSnapshotStore(File(dataDir, "sempoa-state.json"))
    val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    application {
        val scope = rememberCoroutineScope()
        val controller = remember { CalculatorController(scope, store) }
        val keyboard = remember { KeyboardMap(keymap) }
        val state = rememberWindowState(
            position = WindowPosition(androidx.compose.ui.Alignment.Center),
            width = 420.dp,
            height = minOf(900, area.height).dp,
        )
        Window(
            onCloseRequest = {
                // Debounce 200 ms bisa belum lewat; simpan sekarang supaya tekanan terakhir tidak hilang.
                store.write(controller.snapshot())
                exitApplication()
            },
            state = state,
            title = "Altung",
            icon = remember { windowIcon() },
            onPreviewKeyEvent = { keyboard.handle(it, controller) },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(360, 640) }
            SystemClipboard(controller)
            AltungContent(controller, keymap)
        }
    }
}

/** Isi jendela: kalkulator, dan panduan bawaan menutupinya bila dibuka dari OPTION. */
@Composable
internal fun AltungContent(controller: CalculatorController, keymap: Keymap) {
    SempoaTheme(skinIndex = controller.skin) {
        Box(Modifier.fillMaxSize()) {
            CalculatorApp(controller, keymap)
            if (controller.manualOpen) {
                val book = remember { ManualBook.parse(resource("manual.json")) }
                ManualScreen(book = book, onClose = controller::closeManual)
            }
        }
    }
}

/**
 * Engine hanya menyalakan bendera Salin dan Tempel; papan klip Windows diurus di sini lewat AWT,
 * tanpa pustaka tambahan.
 */
@Composable
internal fun SystemClipboard(
    controller: CalculatorController,
    clipboard: Clipboard = remember { Toolkit.getDefaultToolkit().systemClipboard },
) {
    LaunchedEffect(controller.clipboardOut) {
        controller.clipboardOut?.let { text ->
            clipboard.setContents(StringSelection(text), null)
            controller.clipboardTaken()
        }
    }
    LaunchedEffect(controller.pasteRequested) {
        if (!controller.pasteRequested) return@LaunchedEffect
        // Papan klip bisa berisi gambar atau sedang dikunci aplikasi lain; anggap kosong.
        val text = runCatching { clipboard.getData(DataFlavor.stringFlavor) as String }.getOrDefault("")
        controller.paste(text)
    }
}

/**
 * Ikon jendela dan bilah tugas, sama dengan ikon berkas Altung.exe: ikon adaptif Android (latar
 * bergradien dan kalkulator) dipotong ke area tengah yang juga tampil di peluncur Android.
 * Sumbernya desktop/icon/altung.svg.
 */
private fun windowIcon(): BitmapPainter =
    requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("altung.png"))
        .use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }

/** Galat tak tertangkap ditulis ke crash.log di folder status saja (PRD §6.5), tanpa jaringan. */
private fun installCrashLog() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching {
            val file = File(dataDir, "crash.log")
            file.parentFile.mkdirs()
            if (file.length() > 64 * 1024) file.delete()
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            file.appendText("thread=${thread.name}\n$trace\n")
        }
        previous?.uncaughtException(thread, error)
    }
}

/**
 * Pemeriksaan tampilan tanpa jendela: `--shot berkas.png [skala] [KEY_... ...]` menekan tombol
 * keymap dari status kosong, lalu merender layar 420 × 900 dp ke PNG. Tidak menyentuh status
 * tersimpan pemakai.
 */
private fun shot(args: List<String>) {
    val out = File(args[0])
    val scale = args.getOrNull(1)?.toFloatOrNull() ?: 1.5f
    val keys = args.drop(if (args.getOrNull(1)?.toFloatOrNull() != null) 2 else 1)
    val keymap = Keymap.parse(resource("keymap.json"))
    val controller = CalculatorController(CoroutineScope(Dispatchers.Unconfined))
    keys.forEach { controller.onKey(keymap[it]) }
    val scene = ImageComposeScene(
        width = (420 * scale).toInt(),
        height = (900 * scale).toInt(),
        density = Density(scale),
    ) { AltungContent(controller, keymap) }
    val image = scene.render()
    out.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
    scene.close()
    println("ok ${out.path}")
}
