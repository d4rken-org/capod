import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope

private fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? =
    add("implementation", dependencyNotation)

private fun DependencyHandler.testImplementation(dependencyNotation: Any): Dependency? =
    add("testImplementation", dependencyNotation)

private fun DependencyHandler.kapt(dependencyNotation: Any): Dependency? =
    add("kapt", dependencyNotation)

private fun DependencyHandler.kaptTest(dependencyNotation: Any): Dependency? =
    add("kaptTest", dependencyNotation)

private fun DependencyHandler.androidTestImplementation(dependencyNotation: Any): Dependency? =
    add("androidTestImplementation", dependencyNotation)

private fun DependencyHandler.kaptAndroidTest(dependencyNotation: Any): Dependency? =
    add("kaptAndroidTest", dependencyNotation)

private fun DependencyHandler.`testRuntimeOnly`(dependencyNotation: Any): Dependency? =
    add("testRuntimeOnly", dependencyNotation)

private fun DependencyHandler.`debugImplementation`(dependencyNotation: Any): Dependency? =
    add("debugImplementation", dependencyNotation)

fun DependencyHandlerScope.addBaseKotlin() {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Kotlin.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.Kotlin.coroutines}")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin.core}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}")

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}")
//    {
//        // conflicts with mockito due to direct inclusion of byte buddy
//        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
//    }
}

fun DependencyHandlerScope.addDagger() {
    implementation("com.google.dagger:dagger:${Versions.Dagger.core}")
    implementation("com.google.dagger:dagger-android:${Versions.Dagger.core}")
    implementation("androidx.hilt:hilt-common:1.0.0")

    kapt("com.google.dagger:dagger-compiler:${Versions.Dagger.core}")
    kapt("com.google.dagger:dagger-android-processor:${Versions.Dagger.core}")

    implementation("com.google.dagger:hilt-android:${Versions.Dagger.core}")
    kapt("androidx.hilt:hilt-compiler:1.0.0")
    kapt("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    testImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kaptTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    androidTestImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")
}

fun DependencyHandlerScope.addMoshi() {
    implementation("com.squareup.moshi:moshi:${Versions.Moshi.core}")
    implementation("com.squareup.moshi:moshi-adapters:${Versions.Moshi.core}")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi.core}")
}

fun DependencyHandlerScope.addOkio() {
    implementation("com.squareup.okio:okio:3.1.0")
}

fun DependencyHandlerScope.addNavigation() {
    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.Navigation.core}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.Navigation.core}")
    androidTestImplementation("androidx.navigation:navigation-testing:${Versions.AndroidX.Navigation.core}")
}

fun DependencyHandlerScope.addBaseWorkManager() {
    implementation("androidx.work:work-runtime:${Versions.AndroidX.WorkManager.core}")
    testImplementation("androidx.work:work-testing:${Versions.AndroidX.WorkManager.core}")
    implementation("androidx.work:work-runtime-ktx:${Versions.AndroidX.WorkManager.core}")
    implementation("androidx.hilt:hilt-work:1.0.0")
}

fun DependencyHandlerScope.addBaseAndroid() {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("androidx.collection:collection-ktx:1.2.0")
    implementation("androidx.preference:preference-ktx:1.2.0")
}

fun DependencyHandlerScope.addBaseAndroidUi() {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.fragment:fragment-ktx:1.4.1")

    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.1")

    implementation("com.google.android.material:material:1.5.0-rc01")

    val lifecycleVers = "2.6.2"
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVers")
}

fun DependencyHandlerScope.addTesting(junit: Boolean = true, instrumentation: Boolean = true) {
    testImplementation("junit:junit:${Versions.Junit.legacy}")

    testImplementation("org.junit.vintage:junit-vintage-engine:${Versions.Junit.jupiter}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Versions.Junit.jupiter}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.Junit.jupiter}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.Junit.jupiter}")

    testImplementation("androidx.test:core-ktx:${Versions.AndroidX.Testing.coreKtx}")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    testImplementation("io.mockk:mockk:${Versions.Mockk.core}")
    androidTestImplementation("io.mockk:mockk-android:${Versions.Mockk.android}")

    testImplementation("io.kotest:kotest-runner-junit5:${Versions.Kotest.core}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${Versions.Kotest.core}")
    testImplementation("io.kotest:kotest-property-jvm:${Versions.Kotest.core}")
    androidTestImplementation("io.kotest:kotest-assertions-core-jvm:${Versions.Kotest.core}")
    androidTestImplementation("io.kotest:kotest-property-jvm:${Versions.Kotest.core}")

    debugImplementation("androidx.fragment:fragment-testing:1.4.1")
}