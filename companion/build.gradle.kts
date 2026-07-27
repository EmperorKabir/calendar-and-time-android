import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kabirbhasin.statuscalendar.slot"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "slot"
    productFlavors {
        create("slot1") {
            dimension = "slot"
            applicationId = "com.kabirbhasin.statuscalendar.slot1"
            resValue("string", "slot_label", "Status Calendar slot 1")
        }
        create("slot2") {
            dimension = "slot"
            applicationId = "com.kabirbhasin.statuscalendar.slot2"
            resValue("string", "slot_label", "Status Calendar slot 2")
        }
        create("slot3") {
            dimension = "slot"
            applicationId = "com.kabirbhasin.statuscalendar.slot3"
            resValue("string", "slot_label", "Status Calendar slot 3")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
