import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.compose)          // org.jetbrains.compose (CMP runtime/resources)
    alias(libs.plugins.kotlin.compose)   // org.jetbrains.kotlin.plugin.compose (compiler)
}

kotlin {
    jvmToolchain(17)

    // AGP-9 KMP Android library: configure Android via `android { }` INSIDE `kotlin { }`
    // (the deprecated `androidLibrary { }` accessor is the same plugin).
    android {
        namespace = "net.bible.sharedui"
        compileSdk = 36
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iOS targets: configure + compile-check on Linux; the framework LINK tasks run only
    // on a Mac. The static framework (baseName "SharedUi") is what the iOS host embeds.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedUi"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sharedCore"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
        }
    }
}
