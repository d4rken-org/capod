plugins {
    id("projectConfig")
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}
apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "androidx.navigation.safeargs.kotlin")

android {
    compileSdk = projectConfig.compileSdk

    defaultConfig {
        namespace = projectConfig.packageName

        minSdk = projectConfig.minSdk
        targetSdk = projectConfig.targetSdk

        versionCode = projectConfig.version.code.toInt()
        versionName = projectConfig.version.name

        testInstrumentationRunner = "eu.darken.capod.HiltTestRunner"

        buildConfigField("String", "PACKAGENAME", "\"${projectConfig.packageName}\"")
        buildConfigField("String", "VERSION_CODE", "\"${projectConfig.version.code}\"")
        buildConfigField("String", "VERSION_NAME", "\"${projectConfig.version.name}\"")
    }

    // Enable automatic per-app language preferences generation
    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    signingConfigs {
        val basePath = File(System.getProperty("user.home"), ".config/projects/${projectConfig.packageName}")
        create("releaseFoss") {
            setupCredentials(File(basePath, "signing-foss.properties"))
        }
        create("releaseGplay") {
            setupCredentials(File(basePath, "signing-gplay-upload.properties"))
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("foss") {
            dimension = "version"
            signingConfig = signingConfigs["releaseFoss"]
            // The info block is encrypted and can only be read by google
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
        }
        create("gplay") {
            dimension = "version"
            signingConfig = signingConfigs["releaseGplay"]
        }
    }

    buildTypes {
        val customProguardRules = fileTree(File(projectDir, "proguard")) {
            include("*.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
            proguardFiles("proguard-rules-debug.pro")
        }
        create("beta") {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
        release {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xannotation-default-target=param-property"
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        //noinspection WrongGradleMethod
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: return@onVariants
        if (buildType != "release" && buildType != "beta") return@onVariants

        val formattedVariantName = variant.name
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .uppercase()

        val apkFolder = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        val packageName = projectConfig.packageName

        val renameTask = tasks.register("rename${variant.name.replaceFirstChar { it.uppercase() }}Apk") {
            inputs.files(apkFolder)
            outputs.upToDateWhen { false }

            doLast {
                val builtArtifacts = loader.load(apkFolder.get()) ?: return@doLast

                builtArtifacts.elements.forEach { element ->
                    val apkFile = File(element.outputFile)
                    val outputFileName = "$packageName-v${element.versionName}-${element.versionCode}-$formattedVariantName.apk"
                    if (apkFile.exists() && apkFile.name != outputFileName) {
                        apkFile.copyTo(File(apkFile.parentFile, outputFileName), overwrite = true)
                    }
                }
            }
        }

        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { it.uppercase() }}" }.configureEach {
            finalizedBy(renameTask)
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    addBaseKotlin()

    addDagger()

    addMoshi()

    addOkio()

    addBaseAndroid()
    addBaseAndroidUi()

    implementation("androidx.core:core-splashscreen:1.0.0-alpha02")

    addNavigation()

    addTesting()

    "gplayImplementation"("com.android.billingclient:billing:8.0.0")
    "gplayImplementation"("com.android.billingclient:billing-ktx:8.0.0")
}