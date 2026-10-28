plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val readerCoreDependency = groovy.json.JsonSlurper()
    .parse(rootProject.file("scripts/core-dependency.json")) as Map<*, *>

android {
    namespace = "io.reader.android"
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "io.reader.minliny"
        minSdk = 24
        targetSdk = 36
        // Product version is intentionally undeclared. This is only an install revision.
        versionCode = providers.gradleProperty("readerVersionCode").orElse("1").get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "CORE_COMMIT", "\"${readerCoreDependency["commit"]}\"")
        buildConfigField("String", "HARMONY_REFERENCE", "\"3b297fdc7e724cf655eca3bac2a8efeda2001c56\"")
    }
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/main/kotlin/io", "src/core/java"))
            jniLibs.setSrcDirs(listOf("src/core/jniLibs"))
        }
        getByName("test") { java.setSrcDirs(listOf("src/test/kotlin/io")) }
        getByName("androidTest") { java.setSrcDirs(listOf("src/androidTest/kotlin/io")) }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = false; keepDebugSymbols += "**/*.so" } }
    testOptions { execution = "HOST" }
}

tasks.register("writeDebugRuntimeArtifacts") {
    val output = layout.buildDirectory.file("reports/debug-runtime-artifacts.json")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val artifacts = configurations.getByName("debugRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.map {
                mapOf("group" to it.moduleVersion.id.group, "name" to it.name,
                    "version" to it.moduleVersion.id.version, "file" to it.file.absolutePath)
            }.sortedBy { it["group"] + ":" + it["name"] }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.toJson(mapOf("schemaVersion" to 1,
                "configuration" to "debugRuntimeClasspath",
                "runId" to providers.gradleProperty("readerArtifactRunId").get(), "artifacts" to artifacts)))
        }
    }
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        getByName("main").kotlin.setSrcDirs(listOf("src/main/kotlin/io"))
        getByName("test").kotlin.setSrcDirs(listOf("src/test/kotlin/io"))
        getByName("androidTest").kotlin.setSrcDirs(listOf("src/androidTest/kotlin/io"))
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
