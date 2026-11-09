plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Tampilan kalkulator untuk Android dan desktop (PRD §6.1, revisi 28 September 2026). Seluruh
 * kodenya di commonMain; yang berbeda per platform hanya penangan tombol kembali. Membaca
 * keymap.json dan manual.json adalah urusan cangkang (:app dan :desktop).
 */
kotlin {
    jvmToolchain(17)

    android {
        namespace = "id.sempoa570.ui"
        compileSdk = 36
        minSdk = 34
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":engine"))
            api(libs.cmp.ui)
            api(libs.cmp.foundation)
            // Ripple saja, bukan material3: material3 multiplatform masih alpha.
            implementation(libs.cmp.material.ripple)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            // BackHandler pada layar panduan; tombol kembali Android menutupnya.
            implementation(libs.activity.compose)
        }
    }
}
