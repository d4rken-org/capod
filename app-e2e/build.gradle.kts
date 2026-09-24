plugins {
    id("projectConfig")
    id("com.android.test")
    id("kotlin-android")
}

android {
    namespace = "${projectConfig.packageName}.e2e"
    compileSdk = projectConfig.compileSdk

    defaultConfig {
        minSdk = projectConfig.minSdk
        targetSdk = projectConfig.targetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // The tests run in their own process, so they can clear, stop and relaunch the app under test.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    flavorDimensions.add("version")
    productFlavors {
        create("foss") {
            dimension = "version"
            // Its phases need an APK swap in between, so tools/upgrade-test.sh runs them outside Gradle.
            testInstrumentationRunnerArguments["notClass"] = "eu.darken.capod.e2e.UpgradeTest"
        }
        create("gplay") { dimension = "version" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
