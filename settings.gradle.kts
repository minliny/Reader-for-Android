pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Reader-for-Android"

// Composite build: consume Reader UI generated Kotlin contracts directly from the source repo,
// eliminating manual source-set copies. The dependencySubstitution maps the coordinate
// `io.reader.ui:reader-ui-contract` to the included build's `:reader-ui-contract` project.
includeBuild("../Reader UI") {
    dependencySubstitution {
        substitute(module("io.reader.ui:reader-ui-contract")).using(project(":reader-ui-contract"))
    }
}

include(":app")
