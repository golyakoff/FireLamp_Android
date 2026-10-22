import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Machine-local, not committed:
// - `github.token=...` - optional GitHub API token (app update check: higher rate limits)
// - `signing.storeFile/storePassword/keyAlias/keyPassword` - release key for local release builds;
//   CI signs with its own secrets via -Pandroid.injected.signing.* instead
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// The app version: bump both together with a new section of CHANGELOG.md
val appVersionCode = 13
val appVersionName = "0.5.3"

// APK name: firelamp-vX.Y.Z-<build type>.apk
base {
    archivesName = "firelamp-v$appVersionName"
}

android {
    namespace = "net.agolyakov.firelamp"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.agolyakov.firelamp"
        minSdk = 28     // Android 9
        targetSdk = 36  // Android 16
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "GITHUB_TOKEN",
            "\"${localProperties.getProperty("github.token") ?: ""}\""
        )
    }

    val localSigning = localProperties.getProperty("signing.storeFile")
        ?.takeIf { localProperties.getProperty("signing.storePassword") != null }
        ?.let { storePath ->
        signingConfigs.create("localRelease") {
            storeFile = file(storePath)
            storePassword = localProperties.getProperty("signing.storePassword")
            keyAlias = localProperties.getProperty("signing.keyAlias")
            keyPassword = localProperties.getProperty("signing.keyPassword")
        }
    }

    buildTypes {
        release {
            localSigning?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// ---------- Effect previews ----------
// GIFs named by effect code (fire.gif, ...) live in the firmware repo (FireLamp_ESP32/preview) and
// are copied into generated assets (assets/previews/) on every build, so an APK always carries
// the previews of the firmware it is built next to; they are not committed here. The source is
// -Pfirelamp.previewsDir=..., else $FIRELAMP_PREVIEWS_DIR (CI checks the firmware out), else the
// sibling checkout. Missing source: an error on CI, a warning locally (the app then downloads
// the previews from GitHub on demand).
abstract class SyncEffectPreviews : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val required: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val out = outputDir.get().asFile.resolve("previews")
        out.deleteRecursively()
        out.mkdirs()
        val source = sourceDir.orNull?.asFile
        val gifs = source?.listFiles { file -> file.extension == "gif" }.orEmpty()
        if (gifs.isEmpty()) {
            val message = "No effect previews (*.gif) in ${source ?: "the firmware checkout"}: " +
                "set -Pfirelamp.previewsDir or FIRELAMP_PREVIEWS_DIR to FireLamp_ESP32/preview"
            if (required.get()) throw GradleException(message)
            logger.warn("w: $message; the app will download them")
            return
        }
        gifs.forEach { it.copyTo(out.resolve(it.name)) }
        logger.lifecycle("Effect previews: ${gifs.size} from $source")
    }
}

val previewsDir = providers.gradleProperty("firelamp.previewsDir")
    .orElse(providers.environmentVariable("FIRELAMP_PREVIEWS_DIR"))
    .orElse(rootDir.resolve("../../FireLampBLE/FireLamp_ESP32/preview").path)
    .map { file(it) }

val syncEffectPreviews = tasks.register<SyncEffectPreviews>("syncEffectPreviews") {
    // An absent directory stays unset: Gradle rejects a missing @InputDirectory
    previewsDir.get().takeIf { it.isDirectory }?.let { sourceDir.set(it) }
    required.set(providers.environmentVariable("CI").map { it == "true" }.orElse(false))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncEffectPreviews, SyncEffectPreviews::outputDir)
    }
}

// AGP 9 compiles Kotlin itself (built-in Kotlin): no kotlin-android plugin
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.material3)
    implementation(libs.material3.window)
    implementation(libs.material.icons.extended)
    implementation(libs.bundles.networking)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.runtime)
    ksp(libs.hilt.compiler)
    implementation(libs.nordic.ble)
    implementation(libs.nordic.ble.ktx)
    // Effect preview GIFs: bundled ones from assets, missing ones from GitHub
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}