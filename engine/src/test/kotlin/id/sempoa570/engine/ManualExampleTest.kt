package id.sempoa570.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Setiap contoh di panduan bawaan dimainkan ulang lewat engine, lalu layar dan hasilnya dituntut
 * sama dengan yang tertulis di `docs/manual.json`.
 *
 * Tanpa ini panduan gampang melenceng: angkanya diketik tangan dan tidak pernah dijalankan.
 * Mesinnya memakai [Golden] yang sudah ada, jadi kolom mode dan setup berarti persis sama dengan
 * berkas golden.
 */
class ManualExampleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everyManualExampleMatchesTheEngine() {
        val file = Golden.keymapFile().resolveSibling("manual.json")
        assertTrue(file.isFile, "docs/manual.json harus ada")
        val chapters = json.parseToJsonElement(file.readText())
            .jsonObject.getValue("chapters").jsonArray

        val mismatches = mutableListOf<String>()
        var total = 0
        for (chapter in chapters) {
            val title = chapter.jsonObject["title"]?.jsonPrimitive?.content.orEmpty()
            val examples = chapter.jsonObject["examples"]?.jsonArray ?: continue
            for (example in examples) {
                val fields = example.jsonObject
                fun text(name: String) = fields[name]?.jsonPrimitive?.content.orEmpty()
                val goal = text("goal")
                val tokens = text("keys").split(" ").filter { it.isNotEmpty() }
                if (tokens.isEmpty()) {
                    mismatches += "$title / $goal: kolom keys kosong"
                    continue
                }
                total++
                val case = GoldenCase(
                    file = "manual.json",
                    line = 0,
                    mode = text("mode").ifEmpty { "COMP" },
                    setup = text("setup").ifEmpty { "default" },
                    keys = tokens.map { token ->
                        Key.byName(token) ?: fail("$title / $goal: token tak dikenal $token")
                    },
                    expectedExpr = text("screen"),
                    expectedResult = text("result"),
                    source = "panduan",
                )
                val actual = try {
                    Golden.run(case)
                } catch (e: Exception) {
                    mismatches += "$title / $goal: lempar ${e::class.simpleName} ${e.message}"
                    continue
                }
                if (actual.first != case.expectedExpr) {
                    mismatches +=
                        "$title / $goal layar: harap '${case.expectedExpr}' dapat '${actual.first}'"
                }
                if (actual.second != case.expectedResult) {
                    mismatches +=
                        "$title / $goal hasil: harap '${case.expectedResult}' dapat '${actual.second}'"
                }
            }
        }

        assertTrue(total >= 20, "panduan harus punya minimal 20 contoh, ada $total")
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} selisih dari $total contoh:\n" + mismatches.joinToString("\n"))
        }
    }
}
