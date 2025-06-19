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

rootProject.name = "corbado-android"
include(":api")
include(":sdk")
include(":example")

includeBuild("../simple-credential-manager") {
    dependencySubstitution {
        substitute(module("io.corbado:simple-credential-manager")).using(project(":library"))
    }
} 