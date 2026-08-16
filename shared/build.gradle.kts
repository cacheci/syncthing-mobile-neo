import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "moe.https.syncthing.shared"
        compileSdk = 37
        minSdk = 26

        androidResources.enable = true

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.lifecycle.viewmodel)
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.miuix.ui)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.preference)
            implementation(libs.qrose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.http)
            implementation(libs.uri.kmp)
        }
    }
}

compose {
    resources {
        publicResClass = true
        packageOfResClass = "moe.https.syncthing.generated.resources"
        generateResClass = auto
    }
}