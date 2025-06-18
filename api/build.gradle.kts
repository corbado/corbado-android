plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
} 