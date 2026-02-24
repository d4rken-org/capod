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

private fun DependencyHandler.testRuntimeOnly(dependencyNotation: Any): Dependency? =
    add("testRuntimeOnly", dependencyNotation)

private fun DependencyHandler.debugImplementation(dependencyNotation: Any): Dependency? =
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
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-adapters:1.15.2")
    add("ksp", "com.squareup.moshi:moshi-kotlin-codegen:1.15.2")
}

fun DependencyHandlerScope.addOkio() {
    implementation("com.squareup.okio:okio:3.1.0")
}

fun DependencyHandlerScope.addNavigation() {
    implementation("androidx.navigation:navigation-common:${Versions.AndroidX.Navigation.core}")
}

fun DependencyHandlerScope.addBaseAndroid() {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("androidx.collection:collection-ktx:1.2.0")
    implementation("androidx.preference:preference-ktx:1.2.0")
}

fun DependencyHandlerScope.addBaseAndroidUi() {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")

    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.1")

    implementation("com.google.android.material:material:1.12.0")

    val lifecycleVers = "2.6.2"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVers")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVers")
}

fun DependencyHandlerScope.addCompose() {
    val composeBom = platform("androidx.compose:compose-bom:${Versions.Compose.bom}")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    implementation("androidx.hilt:hilt-navigation-compose:1.3.0-alpha01")
}

fun DependencyHandlerScope.addNavigation3() {
    implementation("androidx.navigation3:navigation3-runtime:${Versions.Navigation3.core}")
    implementation("androidx.navigation3:navigation3-ui:${Versions.Navigation3.core}")

    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")

    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0-alpha06")
}

fun DependencyHandlerScope.addSerialization() {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.Serialization.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.Serialization.core}")
}

fun DependencyHandlerScope.addTesting() {
    testImplementation("junit:junit:4.13.2")

    testImplementation("org.junit.vintage:junit-vintage-engine:5.7.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.1")

    testImplementation("androidx.test:core-ktx:1.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    testImplementation("io.mockk:mockk:1.12.4")
    androidTestImplementation("io.mockk:mockk-android:1.12.4")

    testImplementation("io.kotest:kotest-runner-junit5:4.6.4")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.6.4")
    testImplementation("io.kotest:kotest-property-jvm:4.6.4")
    androidTestImplementation("io.kotest:kotest-assertions-core-jvm:4.6.4")
    androidTestImplementation("io.kotest:kotest-property-jvm:4.6.4")

    debugImplementation("androidx.fragment:fragment-testing:1.6.1")
}