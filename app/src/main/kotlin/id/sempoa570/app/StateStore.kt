package id.sempoa570.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import id.sempoa570.engine.CalculatorSnapshot
import java.io.InputStream
import java.io.OutputStream

/**
 * Penyimpanan status kalkulator (PRD §6.3): satu berkas DataStore bertipe, tanpa basis data,
 * tanpa berkas lain. Isinya adalah [CalculatorSnapshot] yang diserialisasi kotlinx-serialization.
 */
object SnapshotSerializer : Serializer<CalculatorSnapshot> {

    override val defaultValue = CalculatorSnapshot()

    override suspend fun readFrom(input: InputStream): CalculatorSnapshot {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            CalculatorSnapshot.decode(text)
        } catch (e: Exception) {
            // Berkas rusak tidak boleh membuat aplikasi gagal terbuka; mulai dari status kosong.
            defaultValue
        }
    }

    override suspend fun writeTo(t: CalculatorSnapshot, output: OutputStream) {
        output.write(CalculatorSnapshot.encode(t).encodeToByteArray())
    }
}

val Context.snapshotStore: DataStore<CalculatorSnapshot> by dataStore(
    fileName = "sempoa-state.json",
    serializer = SnapshotSerializer,
)
