plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/** Cangkang Windows 11 untuk Altung (PRD §2 dan §6.1, revisi 28 September 2026). */
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    testImplementation(libs.cmp.ui.test.junit4)
}

/**
 * docs/keymap.json dan docs/manual.json tetap sumber tunggal, seperti di :app. altung.png adalah
 * ikon jendela, sama dengan altung.ico pada Altung.exe; keduanya dibuat dari icon/altung.svg.
 */
val copyDocs = tasks.register<Copy>("copyDocs") {
    from(
        rootProject.file("docs/keymap.json"),
        rootProject.file("docs/manual.json"),
        file("icon/altung.png"),
    )
    into(layout.buildDirectory.dir("generated/docs"))
}

sourceSets.main {
    resources.srcDir(copyDocs)
}

compose.desktop {
    application {
        mainClass = "id.sempoa570.desktop.MainKt"
        // Renderer Direct3D bawaan Skiko memakan ±370 MB untuk jendela sekecil ini; render CPU
        // cukup untuk kalkulator statis dan turun ke ±150 MB (PRD §7.1, target desktop 300 MB).
        // Diukur 28 September 2026 pada Altung.exe: D3D 381 MB, OpenGL 214 MB, perangkat lunak 156 MB.
        jvmArgs += listOf("-Dskiko.renderApi=SOFTWARE_FAST", "-Xmx128m", "-XX:+UseSerialGC")
        nativeDistributions {
            // Zip portabel dari createDistributable (PRD §6.5); MSI tidak dipakai, jadi WiX
            // tidak diperlukan.
            packageName = "Altung"
            // jpackage menuntut tiga angka: 1.1 menjadi 1.1.0, sedangkan 1.1.1 dipakai apa adanya.
            packageVersion = libs.versions.altung.get().let { if (it.count { c -> c == '.' } == 1) "$it.0" else it }
            description = "Altung, kalkulator saintifik Natural Display"
            vendor = "N-Blu Studios"
            copyright = "N-Blu Studios"
            // Hasil tugas suggestRuntimeModules; sisanya dipilih plugin Compose.
            modules("java.instrument", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icon/altung.ico"))
            }
        }
    }
}
