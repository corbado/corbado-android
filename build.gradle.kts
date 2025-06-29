plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    id("org.openapi.generator") version "7.0.1" apply false
    id("com.vanniktech.maven.publish") version "0.33.0" apply false
}