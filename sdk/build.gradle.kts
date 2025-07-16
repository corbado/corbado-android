plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.corbado.connect.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":api"))
    api(libs.simple.credential.manager)
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)

    implementation(libs.kotlinx.serialization.json)

    // Google Play Services for availability checking
    implementation(libs.google.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.corbado", "connect-core", "0.2.1")

    pom {
        name.set("Corbado Connect SDK")
        description.set("Android SDK for Corbado Connect.")
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