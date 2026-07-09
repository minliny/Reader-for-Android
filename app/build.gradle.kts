plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
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
            isMinifyEnabled = false
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

    // Reader UI contract (composite build from /Users/minliny/Documents/Reader UI).
    // Provides RouteId, MotionSpecRegistry, TokenRegistry, MotionPolicyRegistry, etc.
    implementation("io.reader.ui:reader-ui-contract")

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
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // WindowManager (for foldable / viewport class detection - ViewportClassAdapter)
    implementation("androidx.window:window:1.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
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
