package id.sempoa570.desktop

import id.sempoa570.engine.CalculatorSnapshot
import id.sempoa570.ui.SnapshotStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Status kalkulator di satu berkas JSON (PRD §6.3), isinya sama dengan DataStore Android.
 * Ditulis ke berkas sementara lalu dipindah atomik, supaya jendela yang ditutup di tengah
 * penulisan tidak meninggalkan berkas setengah jadi.
 */
class FileSnapshotStore(private val file: File) : SnapshotStore {

    override suspend fun load(): CalculatorSnapshot = withContext(Dispatchers.IO) {
        // Berkas belum ada atau rusak tidak boleh membuat aplikasi gagal terbuka.
        runCatching { CalculatorSnapshot.decode(file.readText()) }.getOrDefault(CalculatorSnapshot())
    }

    override suspend fun save(snapshot: CalculatorSnapshot) = withContext(Dispatchers.IO) {
        write(snapshot)
    }

    /** Dipanggil langsung saat jendela ditutup, tanpa menunggu debounce 200 ms. */
    @Synchronized
    fun write(snapshot: CalculatorSnapshot) {
        file.parentFile.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(CalculatorSnapshot.encode(snapshot))
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
