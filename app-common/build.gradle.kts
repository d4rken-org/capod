plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}
apply(plugin = "dagger.hilt.android.plugin")

android {
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("Long", "VERSION_CODE", "${ProjectConfig.Version.code}L")
        buildConfigField("String", "VERSION_NAME", "\"${ProjectConfig.Version.name}\"")
        buildConfigField("String", "APPLICATION_ID", "\"${ProjectConfig.packageName}\"")
        buildConfigField("String", "GITSHA", "\"${lastCommitHash()}\"")
        buildConfigField("String", "BUILDTIME", "\"${buildTime()}\"")
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
        )
    }

    flavorDimensions.add("version")
    productFlavors {
        create("foss") {
            dimension = "version"
        }
        create("gplay") {
            dimension = "version"
        }
    }

    buildTypes {
        val customProguardRules = fileTree(File("../proguard")) {
            include("*.pro")
        }
        debug {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
            proguardFiles("proguard-rules-debug.pro")
        }
        create("beta") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")

    addBaseAndroid()
    addBaseAndroidUi()
    addBaseKotlin()
    addDagger()
    addMoshi()
    addBaseWorkManager()
    addNavigation()

    addTesting()

    "gplayImplementation"("com.bugsnag:bugsnag-android:5.9.2")
    "gplayImplementation"("com.getkeepsafe.relinker:relinker:1.4.3")

}