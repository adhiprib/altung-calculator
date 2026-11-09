package id.sempoa570.engine

fun dimensionError(): Nothing = throw CalcError(ErrorKind.DIMENSION)

/** Matriks hingga 3×3 (PRD §5.1). Elemen disimpan baris demi baris. */
data class Matrix(val rows: Int, val cols: Int, val cells: List<CasioNumber>) {

    init {
        if (rows !in 1..MAX || cols !in 1..MAX || cells.size != rows * cols) dimensionError()
    }

    operator fun get(row: Int, col: Int): CasioNumber = cells[row * cols + col]

    operator fun plus(o: Matrix): Matrix {
        if (rows != o.rows || cols != o.cols) dimensionError()
        return Matrix(rows, cols, cells.mapIndexed { i, v -> v + o.cells[i] })
    }

    operator fun minus(o: Matrix): Matrix {
        if (rows != o.rows || cols != o.cols) dimensionError()
        return Matrix(rows, cols, cells.mapIndexed { i, v -> v - o.cells[i] })
    }

    operator fun times(o: Matrix): Matrix {
        if (cols != o.rows) dimensionError()
        val out = ArrayList<CasioNumber>(rows * o.cols)
        for (r in 0 until rows) {
            for (c in 0 until o.cols) {
                var sum = CasioNumber.ZERO
                for (k in 0 until cols) sum += this[r, k] * o[k, c]
                out.add(sum)
            }
        }
        return Matrix(rows, o.cols, out)
    }

    operator fun times(scalar: CasioNumber) = Matrix(rows, cols, cells.map { it * scalar })

    operator fun unaryMinus() = Matrix(rows, cols, cells.map { -it })

    fun transpose(): Matrix {
        val out = ArrayList<CasioNumber>(cells.size)
        for (c in 0 until cols) for (r in 0 until rows) out.add(this[r, c])
        return Matrix(cols, rows, out)
    }

    /** Determinan; hanya terdefinisi untuk matriks bujur sangkar. */
    fun determinant(): CasioNumber {
        if (rows != cols) dimensionError()
        return when (rows) {
            1 -> this[0, 0]
            2 -> this[0, 0] * this[1, 1] - this[0, 1] * this[1, 0]
            else -> this[0, 0] * (this[1, 1] * this[2, 2] - this[1, 2] * this[2, 1]) -
                this[0, 1] * (this[1, 0] * this[2, 2] - this[1, 2] * this[2, 0]) +
                this[0, 2] * (this[1, 0] * this[2, 1] - this[1, 1] * this[2, 0])
        }
    }

    /** Invers lewat matriks adjoin; determinan nol menghasilkan Math ERROR. */
    fun inverse(): Matrix {
        if (rows != cols) dimensionError()
        val det = determinant()
        if (det.isZero) mathError()
        if (rows == 1) return Matrix(1, 1, listOf(CasioNumber.ONE / det))
        val out = ArrayList<CasioNumber>(cells.size)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                // Elemen adjoin adalah kofaktor yang sudah ditransposisikan.
                val sign = if ((r + c) % 2 == 0) CasioNumber.ONE else -CasioNumber.ONE
                out.add(sign * minor(c, r) / det)
            }
        }
        return Matrix(rows, cols, out)
    }

    /** Determinan submatriks setelah membuang satu baris dan satu kolom. */
    private fun minor(skipRow: Int, skipCol: Int): CasioNumber {
        val kept = ArrayList<CasioNumber>((rows - 1) * (cols - 1))
        for (r in 0 until rows) {
            if (r == skipRow) continue
            for (c in 0 until cols) {
                if (c == skipCol) continue
                kept.add(this[r, c])
            }
        }
        return Matrix(rows - 1, cols - 1, kept).determinant()
    }

    fun power(exponent: Int): Matrix {
        if (rows != cols) dimensionError()
        var acc = identity(rows)
        repeat(exponent) { acc *= this }
        return acc
    }

    override fun toString(): String = (0 until rows).joinToString(",", "[", "]") { r ->
        (0 until cols).joinToString(",", "[", "]") { c -> this[r, c].toString() }
    }

    companion object {
        /** Ukuran maksimum matriks pada fx-570ES PLUS (PRD §3.1). */
        const val MAX = 3

        fun identity(size: Int) = Matrix(
            size,
            size,
            (0 until size * size).map {
                if (it % (size + 1) == 0) CasioNumber.ONE else CasioNumber.ZERO
            },
        )
    }
}

/** Vektor berdimensi 2 atau 3 (PRD §5.1). */
data class Vector(val components: List<CasioNumber>) {

    init {
        if (components.size !in 2..3) dimensionError()
    }

    val dimension: Int get() = components.size

    operator fun plus(o: Vector): Vector {
        if (dimension != o.dimension) dimensionError()
        return Vector(components.mapIndexed { i, v -> v + o.components[i] })
    }

    operator fun minus(o: Vector): Vector {
        if (dimension != o.dimension) dimensionError()
        return Vector(components.mapIndexed { i, v -> v - o.components[i] })
    }

    operator fun times(scalar: CasioNumber) = Vector(components.map { it * scalar })

    operator fun unaryMinus() = Vector(components.map { -it })

    /** Hasil kali titik; kedua vektor harus berdimensi sama. */
    infix fun dot(o: Vector): CasioNumber {
        if (dimension != o.dimension) dimensionError()
        return components.indices.fold(CasioNumber.ZERO) { acc, i ->
            acc + components[i] * o.components[i]
        }
    }

    /** Hasil kali silang; hanya terdefinisi untuk vektor berdimensi tiga. */
    /**
     * Hasil kali silang. Vektor dua komponen dinaikkan ke tiga dengan komponen z nol, sebab
     * manual hlm. 70 Contoh 5 menghitung (1,2)×(3,4) sebagai (0,0,-2).
     */
    infix fun cross(o: Vector): Vector {
        if (dimension !in 2..3 || o.dimension !in 2..3) dimensionError()
        val a = spatial()
        val b = o.spatial()
        return Vector(
            listOf(
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0],
            )
        )
    }

    /** Komponen dalam ruang tiga dimensi; vektor bidang dilengkapi z = 0. */
    private fun spatial(): List<CasioNumber> =
        if (dimension == 3) components else components + CasioNumber.ZERO

    /** Panjang vektor. */
    fun magnitude(): CasioNumber = CasioNumber.of(CasioMath.sqrt((this dot this).value))

    override fun toString(): String = components.joinToString(",", "[", "]") { it.toString() }
}
