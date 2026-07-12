plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val readerUiDirectoryPilotEnabled = providers
    .gradleProperty("readerUiDirectoryPilotEnabled")
    .orElse("true")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiDirectoryPilotEnabled must be true or false"
        }
        value
    }

// This is intentionally independent from the lock-backed directory Pilot.
// H4-B promotes `book.open` to a Pilot cohort with exactly-once effect
// policy; default true routes the result-dependent Core transaction
// through the runtime before native presentation.
val readerUiBookOpenPilotEnabled = providers
    .gradleProperty("readerUiBookOpenPilotEnabled")
    .orElse("true")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiBookOpenPilotEnabled must be true or false"
        }
        value
    }

// R8 TTS/auto-page paired Pilot. H4-F promotes the TTS and auto-page
// start/stop events to a Pilot cohort with exactly-once effect policy;
// page next/prev remain shadow in the consumer lock. Default true routes
// the result-dependent Core transaction through the runtime before native
// presentation; rebuild with -PreaderUiPlaybackPilotEnabled=false to roll back.
val readerUiPlaybackPilotEnabled = providers
    .gradleProperty("readerUiPlaybackPilotEnabled")
    .orElse("true")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiPlaybackPilotEnabled must be true or false"
        }
        value
    }

// Experimental import Pilot seam. Android production does not currently call
// dispatchImportPilot or construct ReaderImportEffectExecutor, so all three
// import lifecycle events remain covered in live Shadow. Local opt-in keeps the
// isolated transaction tests available without claiming release authority.
val readerUiImportPilotEnabled = providers
    .gradleProperty("readerUiImportPilotEnabled")
    .orElse("false")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiImportPilotEnabled must be true or false"
        }
        value
    }

// Experimental source-switch Pilot seam. Android production does not call
// dispatchSourceSwitchPilot or construct ReaderSourceSwitchEffectExecutor, so
// all six source-switch events remain covered in live Shadow. Local opt-in is
// retained for isolated coordinator/executor tests only.
val readerUiSourceSwitchPilotEnabled = providers
    .gradleProperty("readerUiSourceSwitchPilotEnabled")
    .orElse("false")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiSourceSwitchPilotEnabled must be true or false"
        }
        value
    }

// Experimental replace-rule Pilot seam. Android production does not call
// dispatchReplaceRulePilot or construct ReaderReplaceRuleEffectExecutor, so
// all three replace-rule events remain covered in live Shadow. Local opt-in is
// retained for isolated coordinator/executor tests only.
val readerUiReplaceRulePilotEnabled = providers
    .gradleProperty("readerUiReplaceRulePilotEnabled")
    .orElse("false")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiReplaceRulePilotEnabled must be true or false"
        }
        value
    }

// Experimental RSS Pilot seam. Android production does not call dispatchRssPilot
// or construct ReaderRssEffectExecutor, so all seven RSS lifecycle events
// remain covered in live Shadow. Local opt-in is retained for isolated
// coordinator/executor tests only.
val readerUiRssPilotEnabled = providers
    .gradleProperty("readerUiRssPilotEnabled")
    .orElse("false")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiRssPilotEnabled must be true or false"
        }
        value
    }

// Sync remains in the default-Shadow cohort. The experimental coordinator and
// executor seam is intentionally disabled because Android production does not
// yet wire a sync event source to dispatchSyncPilot/ReaderSyncEffectExecutor.
// A local opt-in may exercise that isolated seam, but it is not release
// authority and must never be the production default.
val readerUiSyncPilotEnabled = providers
    .gradleProperty("readerUiSyncPilotEnabled")
    .orElse("false")
    .map { value ->
        require(value == "true" || value == "false") {
            "readerUiSyncPilotEnabled must be true or false"
        }
        value
    }

require(!readerUiPlaybackPilotEnabled.get().toBoolean() || readerUiBookOpenPilotEnabled.get().toBoolean()) {
    "readerUiPlaybackPilotEnabled=true requires readerUiBookOpenPilotEnabled=true for typed Core reader DomainContext"
}

android {
    namespace = "com.reader.android"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.reader.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // R8 rollback seam: rebuilding with
        // `-PreaderUiDirectoryPilotEnabled=false` returns the directory pair to
        // the native reducer + runtime-shadow path without changing its event
        // contract or the other shadow cohorts.
        buildConfigField(
            "boolean",
            "READER_UI_DIRECTORY_PILOT_ENABLED",
            readerUiDirectoryPilotEnabled.get()
        )
        buildConfigField(
            "boolean",
            "READER_UI_BOOK_OPEN_PILOT_ENABLED",
            readerUiBookOpenPilotEnabled.get()
        )
        buildConfigField(
            "boolean",
            "READER_UI_PLAYBACK_PILOT_ENABLED",
            readerUiPlaybackPilotEnabled.get()
        )
        buildConfigField(
            "boolean",
            "READER_UI_IMPORT_PILOT_ENABLED",
            readerUiImportPilotEnabled.get()
        )
        buildConfigField(
            "boolean",
            "READER_UI_SOURCE_SWITCH_PILOT_ENABLED",
            readerUiSourceSwitchPilotEnabled.get()
        )
        buildConfigField(
            "boolean",
            "READER_UI_REPLACE_RULE_PILOT_ENABLED",
            readerUiReplaceRulePilotEnabled.get()
        )
        buildConfigField(
            "boolean",
            "READER_UI_RSS_PILOT_ENABLED",
            readerUiRssPilotEnabled.get()
        )
        buildConfigField(
            "boolean",
            "READER_UI_SYNC_PILOT_ENABLED",
            readerUiSyncPilotEnabled.get()
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    sourceSets {
        getByName("main") {
            // reader-ui-contract is now consumed via composite build (see settings.gradle.kts).
            jniLibs.srcDirs("src/main/libs")
        }
    }
}

dependencies {
    // Core KTX
    implementation("androidx.core:core-ktx:1.15.0")

    // Kotlin Coroutines (for BookApi/SourceApi suspend facades + withContext)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Reader UI generated Kotlin contracts use kotlinx.serialization annotations.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Reader UI contract (composite build from ../Reader-UI).
    // Provides RouteId, MotionSpecRegistry, TokenRegistry, MotionPolicyRegistry, etc.
    implementation("io.reader.ui:reader-ui-contract")
    // Executable UI state/effect runtime. The R8 directory pair, book.open,
    // and TTS/auto-page playback are production Pilot cohorts.
    implementation("io.reader.ui:reader-ui-runtime")

    // DataStore Preferences (for theme, reading settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // OkHttp (HTTP client for book source fetching)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // jsoup (Android-side clean-room HTML/XML selector adapter)
    implementation("org.jsoup:jsoup:1.16.2")

    // Room (for structured data: bookshelf, progress, cache)
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Reader UI 2.5 background.task.* maps to real WorkManager work IDs.
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // WindowManager (for foldable / viewport class detection - ViewportClassAdapter)
    implementation("androidx.window:window:1.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.work:work-testing:2.10.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // MockWebServer for serving real Legado fixture responses offline (Task 12).
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // 阶段 5 — MockWebServer for JVM tests (media.download savePath proof).
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

}

val verifyReaderUiForegroundTimerHandlers by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the narrow canonical foreground timer Host handler contract proof."
    mainClass.set("org.junit.runner.JUnitCore")
    args("com.reader.host.ForegroundTimerCapabilityHandlersJvmTest")
    inputs.file(project.file("src/main/kotlin/com/reader/host/ForegroundTimerCapabilityHandlers.kt"))
    inputs.file(project.file("src/test/kotlin/com/reader/host/ForegroundTimerCapabilityHandlersJvmTest.kt"))
}

// AGP creates testDebugUnitTest after this script is evaluated. Reuse its
// compiled output/classpath without making the consumer gate run the full JVM
// suite; the full suite remains a separate release proof.
afterEvaluate {
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    verifyReaderUiForegroundTimerHandlers.configure {
        classpath = debugUnitTest.classpath + debugUnitTest.testClassesDirs
    }
}

val verifyReaderUiConsumer by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails when the Android Reader UI runtime lock, version, hash, or dependency wiring drifts."
    dependsOn(verifyReaderUiForegroundTimerHandlers)
    workingDir(rootProject.projectDir)
    commandLine(
        "node",
        rootProject.file("../Reader-UI/tools/runtime/check-host-consumers.mjs").absolutePath,
        "--host",
        "android"
    )
    inputs.file(rootProject.file("READER_UI_CONSUMER.json"))
    inputs.file(rootProject.file("settings.gradle.kts"))
    inputs.file(project.file("build.gradle.kts"))
    inputs.file(rootProject.file("../Reader-UI/contracts/VERSION.json"))
    inputs.file(rootProject.file("../Reader-UI/ui-spec/runtime-actions.json"))
    inputs.file(rootProject.file("../Reader-UI/ui-spec/host-consumers.json"))
    inputs.file(rootProject.file("../Reader-UI/tools/runtime/check-host-consumers.mjs"))
}

tasks.named("check") {
    dependsOn(verifyReaderUiConsumer)
}

// The Android parity test reads the canonical 58/58 Host fixture pair
// directly from the Reader UI composite build. Declare them as test inputs so
// Gradle cannot reuse a stale up-to-date result after a UI contract change.
tasks.withType<Test>().configureEach {
    inputs.files(
        rootProject.file("../Reader-UI/contracts/fixtures/host-request.fixtures.json"),
        rootProject.file("../Reader-UI/contracts/fixtures/host-result.fixtures.json")
    )
}
