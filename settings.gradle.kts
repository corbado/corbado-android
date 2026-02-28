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

// Use local simple-credential-manager source instead of Maven artifact when available
if (file("../simple-credential-manager").exists()) {
    logger.lifecycle(">>> Using LOCAL simple-credential-manager source (composite build)")
    includeBuild("../simple-credential-manager")
}
