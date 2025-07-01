import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.openapi.generator")
    id("com.vanniktech.maven.publish")
}

val generatedSourcesDir = "${buildDir}/generated/openapi"

tasks.withType<GenerateTask> {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/../openapi/corbado_public_api.yml")
    outputDir.set(generatedSourcesDir)
    packageName.set("com.corbado.connect.api")
    apiPackage.set("com.corbado.connect.api.v1")
    modelPackage.set("com.corbado.connect.api.models")

    configOptions.set(mapOf(
        "library" to "jvm-okhttp4",
        "dateLibrary" to "java8",
    ))
}

sourceSets.main {
    java.srcDir(files(generatedSourcesDir).builtBy(tasks.named("openApiGenerate")))
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
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.corbado", "connect-api", "0.1.3")

    pom {
        name.set("Corbado Connect API")
        description.set("API client for Corbado Connect generated from OpenAPI.")
        url.set("https://github.com/corbado/corbado-android")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("corbado")
                name.set("Corbado Team")
                email.set("support@corbado.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/corbado/corbado-android.git")
            developerConnection.set("scm:git:ssh://github.com:corbado/corbado-android.git")
            url.set("https://github.com/corbado/corbado-android/tree/main")
        }
    }
} 