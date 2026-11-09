package id.sempoa570.engine

/**
 * Konstanta ilmiah dan konversi satuan (PRD §3.2).
 *
 * Nama, simbol, dan penomoran dua digit diambil dari manual resmi fx-570ES PLUS yang dikirim
 * pemilik produk, jadi urutannya sudah pasti.
 *
 * NILAI konstanta ditulis dari CODATA 2014 (versi yang disebut manual) dan **belum diverifikasi
 * terhadap unit fisik**, sama statusnya dengan berkas golden. Faktor konversi bersifat definisi
 * menurut NIST Special Publication 811 (2008), kecuali kalori yang memakai kalori 15 °C sesuai
 * catatan manual.
 */
object ScientificConstants {

    // ε0, μ0, dan Z0 ditulis 15 digit, bukan 10 seperti konstanta terukur lainnya: pada CODATA
    // 2014 ketiganya definisi (μ0 = 4π×10⁻⁷, ε0 = 1/μ0c², Z0 = μ0c), dan manual hlm. 42 Contoh 2
    // menuntut 1/√(ε0μ0) tampil tepat 299792458. Nilai 10 digit memberi 299792458.1.

    /** Satu konstanta: simbol yang ditampilkan, nilainya, dan keterangannya. */
    data class Entry(val symbol: String, val value: String, val description: String)

    /** Indeks 0 sesuai nomor 01 pada kalkulator. */
    val entries: List<Entry> = listOf(
        Entry("mp", "1.672621898E-27", "massa proton"),
        Entry("mn", "1.674927471E-27", "massa neutron"),
        Entry("me", "9.10938356E-31", "massa elektron"),
        Entry("mμ", "1.883531594E-28", "massa muon"),
        Entry("a0", "5.2917721067E-11", "jari-jari Bohr"),
        Entry("h", "6.626070040E-34", "konstanta Planck"),
        Entry("μN", "5.050783699E-27", "magneton inti"),
        Entry("μB", "9.274009994E-24", "magneton Bohr"),
        Entry("ℏ", "1.054571800E-34", "konstanta Planck tereduksi"),
        Entry("α", "7.2973525664E-3", "konstanta struktur halus"),
        Entry("re", "2.8179403227E-15", "jari-jari elektron klasik"),
        Entry("λc", "2.4263102367E-12", "panjang gelombang Compton"),
        Entry("γp", "2.675221900E8", "rasio giromagnetik proton"),
        Entry("λcp", "1.32140985396E-15", "panjang gelombang Compton proton"),
        Entry("λcn", "1.31959090481E-15", "panjang gelombang Compton neutron"),
        Entry("R∞", "1.0973731568508E7", "konstanta Rydberg"),
        Entry("u", "1.660539040E-27", "satuan massa atom"),
        Entry("μp", "1.4106064372E-26", "momen magnetik proton"),
        Entry("μe", "-9.284764620E-24", "momen magnetik elektron"),
        Entry("μn", "-9.6623650E-27", "momen magnetik neutron"),
        Entry("μμ", "-4.49044826E-26", "momen magnetik muon"),
        Entry("F", "96485.33289", "konstanta Faraday"),
        Entry("e", "1.6021766208E-19", "muatan elementer"),
        Entry("NA", "6.022140857E23", "konstanta Avogadro"),
        Entry("k", "1.38064852E-23", "konstanta Boltzmann"),
        Entry("Vm", "0.024789598", "volume molar gas ideal"),
        Entry("R", "8.3144598", "konstanta gas molar"),
        Entry("C0", "299792458", "laju cahaya dalam vakum"),
        Entry("C1", "3.741771790E-16", "konstanta radiasi pertama"),
        Entry("C2", "0.0143877736", "konstanta radiasi kedua"),
        Entry("σ", "5.670367E-8", "konstanta Stefan-Boltzmann"),
        Entry("ε0", "8.85418781762039E-12", "permitivitas vakum"),
        Entry("μ0", "1.25663706143592E-6", "permeabilitas vakum"),
        Entry("Φ0", "2.067833831E-15", "kuantum fluks magnetik"),
        Entry("g", "9.80665", "percepatan gravitasi baku"),
        Entry("G0", "7.7480917310E-5", "kuantum konduktansi"),
        Entry("Z0", "376.730313461771", "impedansi karakteristik vakum"),
        Entry("t", "273.15", "suhu Celsius nol dalam kelvin"),
        Entry("G", "6.67408E-11", "konstanta gravitasi Newton"),
        Entry("atm", "101325", "atmosfer baku"),
    )

    fun byNumber(number: Int): Entry? = entries.getOrNull(number - 1)
}

/** Empat puluh perintah konversi satuan (PRD §3.2), bernomor sesuai manual. */
object UnitConversions {

    /**
     * Satu perintah konversi. Sebagian besar hanya perkalian, tetapi suhu memerlukan pergeseran,
     * jadi tiap perintah membawa faktor dan pergesernya: hasil = nilai × faktor + geser.
     */
    data class Entry(val label: String, val factor: String, val offset: String = "0")

    val entries: List<Entry> = listOf(
        Entry("in▸cm", "2.54"),
        Entry("cm▸in", "0.3937007874015748"),
        Entry("ft▸m", "0.3048"),
        Entry("m▸ft", "3.280839895013123"),
        Entry("yd▸m", "0.9144"),
        Entry("m▸yd", "1.093613298337708"),
        Entry("mile▸km", "1.609344"),
        Entry("km▸mile", "0.6213711922373339"),
        Entry("n mile▸m", "1852"),
        Entry("m▸n mile", "5.399568034557236E-4"),
        Entry("acre▸m²", "4046.8564224"),
        Entry("m²▸acre", "2.471053814671653E-4"),
        Entry("gal(US)▸L", "3.785411784"),
        Entry("L▸gal(US)", "0.2641720523581484"),
        Entry("gal(UK)▸L", "4.54609"),
        Entry("L▸gal(UK)", "0.2199692482990878"),
        Entry("pc▸km", "3.085677581E13"),
        Entry("km▸pc", "3.240779289E-14"),
        Entry("km/h▸m/s", "0.2777777777777778"),
        Entry("m/s▸km/h", "3.6"),
        Entry("oz▸g", "28.349523125"),
        Entry("g▸oz", "0.03527396194958041"),
        Entry("lb▸kg", "0.45359237"),
        Entry("kg▸lb", "2.204622621848776"),
        Entry("atm▸Pa", "101325"),
        Entry("Pa▸atm", "9.869232667160128E-6"),
        Entry("mmHg▸Pa", "133.322"),
        Entry("Pa▸mmHg", "7.50063755419211E-3"),
        Entry("hp▸kW", "0.745699872"),
        Entry("kW▸hp", "1.341022089595028"),
        Entry("kgf/cm²▸Pa", "98066.5"),
        Entry("Pa▸kgf/cm²", "1.019716212977928E-5"),
        Entry("kgf·m▸J", "9.80665"),
        Entry("J▸kgf·m", "0.1019716212977928"),
        Entry("lbf/in²▸kPa", "6.894757"),
        Entry("kPa▸lbf/in²", "0.1450377377302092"),
        Entry("°F▸°C", "0.5555555555555556", "-17.77777777777778"),
        Entry("°C▸°F", "1.8", "32"),
        Entry("J▸cal", "0.2389200286709474"),
        Entry("cal▸J", "4.1855"),
    )

    fun byNumber(number: Int): Entry? = entries.getOrNull(number - 1)

    /** Menerapkan konversi: nilai × faktor + pergeseran. */
    fun apply(entry: Entry, value: CasioNumber): CasioNumber =
        value * CasioNumber.parse(entry.factor) + CasioNumber.parse(entry.offset)
}
