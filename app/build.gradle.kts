import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val nativeOutput = rootProject.layout.projectDirectory.dir(".build/native-m2")

android {
    namespace = "io.reader.rebuild"
    buildFeatures.compose = true
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "io.reader.minliny.rebuild.android.dev"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters += "arm64-v8a"
    }
    signingConfigs.named("debug") {
        storeFile = null
        storePassword = null
        keyAlias = null
        keyPassword = null
    }
    buildTypes.configureEach { signingConfig = null }
    packaging.jniLibs.useLegacyPackaging = false
    packaging.jniLibs.keepDebugSymbols.add("**/libreader_core_jni.so")
    sourceSets.getByName("main").jniLibs.srcDir(nativeOutput.dir("android-jni/libs"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        it.jvmArgs("-Xcheck:jni", "-Djava.library.path=${nativeOutput.dir("host-jni/lib").asFile}")
        it.systemProperty("reader.fixture", file("src/androidTest/assets/fixtures/m1-window.epub").absolutePath)
        it.systemProperty("reader.hostFixtures", file("src/test/resources/m1").absolutePath)
        it.doFirst { it.systemProperty("reader.cold.classpath", it.classpath.asPath) }
        it.testLogging.showStandardStreams = true
    }
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_17)

// The loopback HTTP fixture runs on the host JDK. Android and instrumentation
// compilation keep the Android bootclasspath.
tasks.withType<KotlinJvmCompile>().matching { it.name.endsWith("UnitTestKotlin") }.configureEach {
    compilerOptions.noJdk.set(false)
    compilerOptions.freeCompilerArgs.add("-Xadd-modules=jdk.httpserver")
}

dependencies {
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.compose.ui:ui:1.10.0")
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

val pinnedInputs = tasks.register<Exec>("checkPinnedInputs") {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/sync-pinned.py", "--check")
}
tasks.named("preBuild") { dependsOn(pinnedInputs) }

val checkAndroidJni = tasks.register("checkAndroidJni") {
    val library = nativeOutput.file("android-jni/libs/arm64-v8a/libreader_core_jni.so")
    doLast { check(library.asFile.isFile) { "Build the pinned arm64 JNI with scripts/build-native.sh" } }
}
tasks.matching { it.name == "mergeDebugJniLibFolders" || it.name == "mergeReleaseJniLibFolders" }.configureEach {
    dependsOn(checkAndroidJni)
}
