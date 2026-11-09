package id.sempoa570.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Penangan crash yang hanya menulis ke filesDir (PRD §6.5). Tidak ada pengiriman ke jaringan
 * dan tidak ada pustaka pelaporan galat.
 */
object CrashLog {

    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 64 * 1024

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(context, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, threadName: String, error: Throwable) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.length() > MAX_BYTES) file.delete()
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        file.appendText("thread=$threadName\n$trace\n")
    }
}
