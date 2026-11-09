package id.sempoa570.engine

/**
 * Token tombol. Nama enum wajib sama persis dengan field `token` di `docs/keymap.json`
 * (dijaga oleh KeymapSyncTest), sehingga UI dan golden test memakai id yang sama.
 */
@kotlinx.serialization.Serializable
enum class Key {
    MOD_SHIFT, MOD_ALPHA,
    NAV_UP, NAV_LEFT, NAV_RIGHT, NAV_DOWN,
    MENU_MODE, MENU_SETUP, POWER_ON, POWER_OFF, MENU_OPTION,

    FN_CALC, FN_SOLVE, REL_EQ,
    FN_INTEGRAL, FN_DERIV,
    POST_RECIP, POST_FACT,
    ANGLE_DEG, ANGLE_RAD, ANGLE_GRA,
    FN_LOGBASE, FN_SIGMA,
    FRAC, FRAC_MIXED,
    FN_SQRT, FN_CBRT,
    POST_SQUARE, POST_CUBE,
    OP_POW, OP_NROOT,
    FN_LOG, FN_POW10,
    FN_LN, FN_EXP,
    OP_NEG, VAR_A,
    POST_DMS, FN_DMS_BACK, VAR_B,
    MENU_HYP, FN_ABS, VAR_C,
    FN_SIN, FN_ASIN, VAR_D,
    FN_COS, FN_ACOS, VAR_E,
    FN_TAN, FN_ATAN, VAR_F,

    MEM_RCL, MEM_STO,
    FMT_ENG, FMT_ENG_BACK, CONST_I,
    PAREN_OPEN, POST_PERCENT,
    PAREN_CLOSE, SEP_COMMA, VAR_X,
    FMT_SD, FMT_ABC_DC, VAR_Y,
    MEM_MPLUS, MEM_MMINUS, VAR_M,

    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    MENU_CONST, MENU_CONV, MENU_CLR, SEP_COLON, DISP_SEP,
    EDIT_DEL, EDIT_INS, EDIT_AC,
    MENU_MATRIX, MENU_VECTOR, MENU_UNKNOWN_6,
    MENU_STAT, MENU_CMPLX, MENU_BASE,

    OP_MUL, OP_NPR, FN_GCD,
    OP_DIV, OP_NCR, FN_LCM,
    OP_ADD, FN_POL, FN_INT,
    OP_SUB, FN_REC, FN_INTG, OP_ANGLE,

    FN_RND, DOT, FN_RAN, FN_RANINT,
    EXP10, CONST_PI, CONST_E,
    MEM_ANS, MENU_DRG, MEM_PREANS,
    EXEC_EQ, EXEC_APPROX,

    BASE_DEC, BASE_HEX, BASE_BIN, BASE_OCT,

    // Fungsi yang pada kalkulator fisik dicapai lewat menu SHIFT 2 (CMPLX) dan SHIFT 3 (BASE),
    // bukan lewat satu tombol tersendiri. Nomor menunya belum diverifikasi ke unit fisik.
    FN_CONJG, FN_ARG, FN_TO_POLAR, FN_TO_RECT,
    FN_AND, FN_OR, FN_XOR, FN_XNOR, FN_NOT, FN_NEG,

    // Matriks dan vektor; pada kalkulator fisik dicapai lewat menu SHIFT 4 dan SHIFT 5.
    MAT_A, MAT_B, MAT_C, MAT_ANS, FN_DET, FN_TRN,
    VCT_A, VCT_B, VCT_C, VCT_ANS, FN_DOT,

    // Fungsi hiperbolik; pada kalkulator fisik dipilih dari menu tombol hyp.
    FN_SINH, FN_COSH, FN_TANH, FN_ASINH, FN_ACOSH, FN_ATANH,

    // Distribusi normal baku dan nilai taksiran regresi, dipilih dari menu STAT
    // (manual hlm. 51-53). P(, Q(, dan R( berargumen di dalam kurung; ▸t, x̂, x̂₁, x̂₂, dan ŷ
    // adalah perintah yang memungut nilai di kirinya.
    FN_DIST_P, FN_DIST_Q, FN_DIST_R, FN_TO_T,
    FN_EST_X, FN_EST_X1, FN_EST_X2, FN_EST_Y;

    val isDigit: Boolean get() = this in DIGITS

    /** Nilai digit 0..9; -1 bila bukan digit. */
    val digitValue: Int get() = DIGITS.indexOf(this)

    companion object {
        private val DIGITS = listOf(
            DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
            DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
        )

        /** Variabel A..F, X, Y, M. */
        val VARIABLES = listOf(VAR_A, VAR_B, VAR_C, VAR_D, VAR_E, VAR_F, VAR_X, VAR_Y, VAR_M)

        fun byName(name: String): Key? = entries.firstOrNull { it.name == name }
    }
}
