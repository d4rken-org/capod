import com.android.build.gradle.LibraryExtension
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.*

object ProjectConfig {
    const val packageName = "eu.darken.capod"

    const val minSdk = 26
    const val compileSdk = 33
    const val targetSdk = 33

    object Version {
        const val major = 2
        const val minor = 0
        const val patch = 0
        const val build = 0

        const val name = "${major}.${minor}.${patch}-rc${build}"
        const val code = major * 1000000 + minor * 10000 + patch * 100 + build
    }
}

fun lastCommitHash(): String = Runtime.getRuntime().exec("git rev-parse --short HEAD").let { process ->
    process.waitFor()
    val output = process.inputStream.use { input ->
        input.bufferedReader().use {
            it.readText()
        }
    }
    process.destroy()
    output.trim()
}

fun buildTime(): Instant = Instant.now()

/**
 * Configures the [kotlinOptions][org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions] extension.
 */
private fun LibraryExtension.kotlinOptions(configure: Action<org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions>): Unit =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", configure)

fun LibraryExtension.setupLibraryDefaults() {
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.FlowPreview",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }

    packagingOptions {
        resources.excludes += "DebugProbesKt.bin"
    }
}

fun com.android.build.api.dsl.SigningConfig.setupCredentials(
    signingPropsPath: File? = null
) {

    val keyStoreFromEnv = System.getenv("STORE_PATH")?.let { File(it) }

    if (keyStoreFromEnv?.exists() == true) {
        println("Using signing data from environment variables.")
        storeFile = keyStoreFromEnv
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    } else {
        println("Using signing data from properties file.")
        val props = Properties().apply {
            signingPropsPath?.takeIf { it.canRead() }?.let { load(FileInputStream(it)) }
        }

        val keyStorePath = props.getProperty("release.storePath")?.let { File(it) }

        if (keyStorePath?.exists() == true) {
            storeFile = keyStorePath
            storePassword = props.getProperty("release.storePassword")
            keyAlias = props.getProperty("release.keyAlias")
            keyPassword = props.getProperty("release.keyPassword")
        }
    }
}

fun getBugSnagApiKey(
    propertiesPath: File?
): String? {
    val bugsnagProps = Properties().apply {
        propertiesPath?.takeIf { it.canRead() }?.let { load(FileInputStream(it)) }
    }

    return System.getenv("BUGSNAG_API_KEY") ?: bugsnagProps.getProperty("bugsnag.apikey")
}