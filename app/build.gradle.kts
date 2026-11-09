import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Keystore rilis dibaca dari local.properties supaya kata sandinya tidak pernah masuk repo.
 * Tanpa isian itu, task release tetap jalan tetapi APK-nya belum ditandatangani.
 */
val keystoreProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) FileInputStream(file).use { stream -> load(stream) }
}

android {
    namespace = "id.sempoa570.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "id.sempoa570.app"
        minSdk = 34
        targetSdk = 36
        // Naikkan tiap rilis supaya APK baru bisa dipasang menimpa yang lama.
        versionCode = 6
        versionName = libs.versions.altung.get()
    }
    signingConfigs {
        create("release") {
            val path = keystoreProperties.getProperty("sempoa.keystore")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = keystoreProperties.getProperty("sempoa.keystorePassword")
                keyAlias = keystoreProperties.getProperty("sempoa.keyAlias")
                keyPassword = keystoreProperties.getProperty("sempoa.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.getProperty("sempoa.keystore") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Berkas di docs/ adalah sumber tunggalnya: keymap.json memetakan tombol (PRD §4.2) dan
 * manual.json berisi panduan bawaan. Keduanya disalin ke aset saat build supaya UI, golden test,
 * dan APK tidak pernah memakai salinan yang berbeda. :desktop menyalinnya ke sumber dayanya.
 */
abstract class CopyDocsTask : DefaultTask() {

    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyDocs() {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        sources.forEach { it.copyTo(directory.resolve(it.name), overwrite = true) }
    }
}

val copyDocs = tasks.register<CopyDocsTask>("copyDocs") {
    sources.from(
        rootProject.layout.projectDirectory.file("docs/keymap.json"),
        rootProject.layout.projectDirectory.file("docs/manual.json"),
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyDocs, CopyDocsTask::outputDirectory)
    }
}

dependencies {
    implementation(project(":ui"))
    // BOM mengunci androidx Compose 1.12.1, sama dengan Compose Multiplatform 1.12.1 di :ui.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(project(":engine"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore)
}
