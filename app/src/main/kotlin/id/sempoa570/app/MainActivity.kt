package id.sempoa570.app

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sempoa570.ui.LocalSkin
import kotlinx.coroutines.delay
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import id.sempoa570.ui.CalculatorApp
import id.sempoa570.ui.ManualBook
import id.sempoa570.ui.ManualScreen
import id.sempoa570.ui.Keymap
import id.sempoa570.ui.SempoaTheme

class MainActivity : ComponentActivity() {

    private val holder: CalculatorViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CalculatorViewModel(applicationContext.snapshotStore) as T
        }
    }

    /**
     * Getaran tombol memakai layanan getar, bukan umpan balik haptik tampilan: XOS pada Note 40
     * Pro menerima panggilan haptik lalu mengabaikannya, sehingga tidak pernah terasa.
     */
    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    /** Ketukan pendek dan lembut, sekadar menandai tombol tertekan. */
    private fun tap() {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(18, 80))
    }

    /** keymap.json dan manual.json disalin dari docs/ ke aset saat build. */
    private fun asset(name: String): String =
        assets.open(name).use { it.readBytes().decodeToString() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(applicationContext)
        setContent {
            val keymap = remember { Keymap.parse(asset("keymap.json")) }
            val model = holder.controller
            val clipboard = LocalClipboard.current
            // Engine tidak boleh menyentuh Android, jadi ia hanya menyalakan bendera dan
            // lapisan Compose ini yang mengurus papan klipnya. LaunchedEffect memang coroutine,
            // jadi API papan klip yang bersifat suspend dipanggil langsung di sini.
            LaunchedEffect(model.clipboardOut) {
                model.clipboardOut?.let { text ->
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Altung", text)))
                    model.clipboardTaken()
                }
            }
            LaunchedEffect(model.pasteRequested) {
                if (!model.pasteRequested) return@LaunchedEffect
                val item = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(applicationContext)
                model.paste(item?.toString().orEmpty())
            }
            SempoaTheme(skinIndex = model.skin) {
                Box(Modifier.fillMaxSize()) {
                    // Getaran singkat menggantikan rasa tombol fisik (PRD §2).
                    CalculatorApp(model, keymap, onPress = ::tap)
                    if (model.manualOpen) {
                        val book = remember { ManualBook.parse(asset("manual.json")) }
                        ManualScreen(book = book, onClose = model::closeManual)
                    }
                    var opening by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1300)
                        opening = false
                    }
                    AnimatedVisibility(visible = opening, exit = fadeOut()) { OpeningScreen() }
                }
            }
        }
    }
}

/** Layar pembuka singkat: ikon kalkulator dengan nama pembuatnya di bawahnya. */
@Composable
private fun OpeningScreen() {
    val skin = LocalSkin.current
    Box(
        Modifier.fillMaxSize().background(skin.body),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Altung",
                modifier = Modifier
                    .size(148.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(skin.functionBottom),
            )
            Box(Modifier.height(20.dp))
            Text(
                text = "N-Blu Studios",
                color = skin.bodyLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
