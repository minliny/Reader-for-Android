buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        // AGP built-in Kotlin: pin the supported compiler with its Compose plugin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
