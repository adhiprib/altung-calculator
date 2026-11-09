package id.sempoa570.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rekaman status kalkulator untuk disimpan dan dipulihkan (PRD §6.3).
 *
 * Nilai numerik disimpan sebagai teks desimal 15 digit, yaitu nilai internal yang memang dipakai
 * kalkulator untuk perhitungan lanjutan. Bentuk eksak seperti pecahan atau √ adalah urusan
 * tampilan, jadi tidak ikut disimpan.
 */
@Serializable
data class CalculatorSnapshot(
    val mode: Mode = Mode.COMP,
    val skin: Int = 0,
    val setup: SetupSnapshot = SetupSnapshot(),
    val expression: List<Node> = emptyList(),
    val history: List<List<Node>> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    /** Hasil yang sedang tampil; kosong berarti layar hasil memang kosong. */
    val result: String = "",
    val ans: String = "0",
    val preAns: String = "0",
    val matrices: Map<String, MatrixSnapshot> = emptyMap(),
    val vectors: Map<String, List<String>> = emptyMap(),
    val stat: StatSnapshot = StatSnapshot(),
    val hapticEnabled: Boolean = false,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(snapshot: CalculatorSnapshot): String = json.encodeToString(snapshot)

        fun decode(text: String): CalculatorSnapshot = json.decodeFromString(text)
    }
}

@Serializable
data class SetupSnapshot(
    val input: InputMode = InputMode.MATH_IO,
    val angle: AngleUnit = AngleUnit.DEG,
    val format: String = "Norm1",
    val base: NumberBase = NumberBase.DEC,
    val complexForm: ComplexForm = ComplexForm.RECTANGULAR,
    val statFreq: Boolean = false,
) {
    fun toSetup(): Setup = Setup(
        input = input,
        angle = angle,
        format = when {
            format.startsWith("Fix") -> DisplayFormat.Fix(format.removePrefix("Fix").toInt())
            format.startsWith("Sci") -> DisplayFormat.Sci(format.removePrefix("Sci").toInt())
            format == "Norm2" -> DisplayFormat.Norm2
            else -> DisplayFormat.Norm1
        },
        base = base,
        complexForm = complexForm,
        statFreq = statFreq,
    )

    companion object {
        fun from(setup: Setup) = SetupSnapshot(
            input = setup.input,
            angle = setup.angle,
            format = when (val f = setup.format) {
                is DisplayFormat.Fix -> "Fix${f.places}"
                is DisplayFormat.Sci -> "Sci${f.digits}"
                DisplayFormat.Norm2 -> "Norm2"
                DisplayFormat.Norm1 -> "Norm1"
            },
            base = setup.base,
            complexForm = setup.complexForm,
            statFreq = setup.statFreq,
        )
    }
}

@Serializable
data class MatrixSnapshot(val rows: Int, val cols: Int, val cells: List<String>)

@Serializable
data class StatSnapshot(
    val type: StatType = StatType.ONE_VAR,
    val x: List<String> = emptyList(),
    val y: List<String> = emptyList(),
    val freq: List<String> = emptyList(),
)
