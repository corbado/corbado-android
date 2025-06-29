plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.vanniktech.maven.publish")
}

group = "com.corbado"
version = "0.1.0"

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
    api("com.corbado:simplecredentialmanager:0.1.0")
    implementation(libs.okhttp)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.biometric:biometric:1.1.0")
    
    // Google Play Services for availability checking
    implementation("com.google.android.gms:play-services-base:18.2.0")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.corbado", "connect-core", "0.1.0")

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