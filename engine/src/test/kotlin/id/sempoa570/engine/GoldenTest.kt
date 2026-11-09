package id.sempoa570.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Satu baris golden test (docs/golden/README.md). */
data class GoldenCase(
    val file: String,
    val line: Int,
    val mode: String,
    val setup: String,
    val keys: List<Key>,
    val expectedExpr: String,
    val expectedResult: String,
    val source: String,
)

object Golden {

    private val docsDir: File by lazy {
        var dir = File("").absoluteFile
        while (!File(dir, "docs/golden").isDirectory) {
            dir = dir.parentFile ?: error("folder docs/golden tidak ditemukan")
        }
        File(dir, "docs/golden")
    }

    fun keymapFile(): File = File(docsDir.parentFile, "keymap.json")

    fun load(name: String): List<GoldenCase> {
        val file = File(docsDir, name)
        assertTrue(file.isFile, "berkas golden tidak ada: ${file.path}")
        return file.readLines().mapIndexedNotNull { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
            val cols = line.split("|").map { it.trim() }
            assertEquals(6, cols.size, "$name baris ${index + 1}: jumlah kolom harus 6")
            GoldenCase(
                file = name,
                line = index + 1,
                mode = cols[0],
                setup = cols[1],
                keys = cols[2].split(" ").filter { it.isNotEmpty() }.map { token ->
                    Key.byName(token) ?: fail("$name baris ${index + 1}: token tak dikenal $token")
                },
                expectedExpr = cols[3],
                expectedResult = cols[4],
                source = cols[5],
            )
        }
    }

    /**
     * Aturan kolom mode dan setup hidup di [Replay] pada modul utama, sebab panduan bawaan
     * memainkan contohnya dengan aturan yang sama persis.
     */
    fun setupOf(spec: String): Setup = Replay.setupOf(spec)

    fun run(case: GoldenCase): Pair<String, String> {
        val calc = Replay.run(case.mode, case.setup, case.keys)
        return calc.exprText() to calc.resultText()
    }
}

/**
 * Menjalankan seluruh golden COMP dasar.
 *
 * Berkas golden masih bersumber `manual`/`manual?` (belum diverifikasi unit fisik), jadi semua
 * selisih dikumpulkan dan dilaporkan sekaligus agar mudah dicocokkan dengan kalkulator.
 */
class GoldenTest {

    @Test
    fun compBasic() {
        val cases = Golden.load("comp-basic.txt")
        assertTrue(cases.size >= 50, "golden COMP dasar minimal 50 kasus, ada ${cases.size}")

        report(check(cases), cases.size)
    }

    @Test
    fun compCalculus() {
        val cases = Golden.load("comp-calculus.txt")
        assertTrue(cases.size >= 10, "golden kalkulus minimal 10 kasus, ada ${cases.size}")
        report(check(cases), cases.size)
    }

    @Test
    fun modes() {
        val cases = Golden.load("modes.txt")
        assertTrue(cases.size >= 15, "golden mode minimal 15 kasus, ada ${cases.size}")
        report(check(cases), cases.size)
    }

    @Test
    fun statAndTable() {
        val cases = Golden.load("stat-table.txt")
        assertTrue(cases.size >= 15, "golden STAT dan TABLE minimal 15 kasus, ada ${cases.size}")
        report(check(cases), cases.size)
    }

    @Test
    fun matrixAndVector() {
        val cases = Golden.load("matrix-vector.txt")
        assertTrue(cases.size >= 15, "golden MATRIX dan VECTOR minimal 15 kasus, ada ${cases.size}")
        report(check(cases), cases.size)
    }

    private fun check(cases: List<GoldenCase>): List<String> {
        val mismatches = mutableListOf<String>()
        for (case in cases) {
            val actual = try {
                Golden.run(case)
            } catch (e: Exception) {
                mismatches += "baris ${case.line}: lempar ${e::class.simpleName} ${e.message}"
                continue
            }
            if (actual.first != case.expectedExpr) {
                mismatches += "baris ${case.line} ekspresi: harap '${case.expectedExpr}' dapat '${actual.first}'"
            }
            if (actual.second != case.expectedResult) {
                mismatches += "baris ${case.line} hasil: harap '${case.expectedResult}' dapat '${actual.second}'"
            }
        }
        return mismatches
    }

    private fun report(mismatches: List<String>, total: Int) {
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} selisih dari $total kasus:\n" + mismatches.joinToString("\n"))
        }
    }
}
