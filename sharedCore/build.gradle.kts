import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(17)

    // AGP-9 KMP Android library: configure Android via `android { }` INSIDE `kotlin { }`
    // (the deprecated `androidLibrary { }` accessor is the same plugin).
    android {
        namespace = "net.bible.sharedcore"
        compileSdk = 36
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // jvm() exists ONLY for fast Linux shared-tests (./gradlew :sharedCore:jvmTest).
    jvm()

    // iOS targets: configure + compile-check on Linux; link tasks run only on a Mac.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
