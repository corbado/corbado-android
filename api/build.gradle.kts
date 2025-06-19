import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.openapi.generator")
}

val generatedSourcesDir = "${buildDir}/generated/openapi"

tasks.withType<GenerateTask> {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/../openapi/corbado_public_api.yml")
    outputDir.set(generatedSourcesDir)
    packageName.set("com.corbado.api")
    apiPackage.set("com.corbado.api.v1")
    modelPackage.set("com.corbado.api.models")

    configOptions.set(mapOf(
        "library" to "jvm-okhttp4",
        "dateLibrary" to "java8",
    ))
}

sourceSets.main {
    java.srcDir(generatedSourcesDir)
}

tasks.named("openApiGenerate") {
    mustRunAfter(tasks.named("clean"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("openApiGenerate")
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
} 