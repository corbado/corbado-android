// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    id("org.openapi.generator") version "7.0.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
}