import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.kotlin.serialization)
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
            api("com.squareup.okio:okio:3.9.0")
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)       // HttpClient / HttpClientEngine appear in WebDavClient's API
            implementation(libs.xmlutil.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation("com.squareup.okio:okio-fakefilesystem:3.9.0")
            implementation(libs.ktor.client.mock)
        }
    }
}

// iOS simulator tests: log each test start and the full failure, so a hung or crashed test is named in the CI log.
tasks.withType<AbstractTestTask>().matching { it.name == "iosSimulatorArm64Test" }.configureEach {
    testLogging {
        showStandardStreams = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("started", "passed", "failed", "skipped")
    }
}

// CI boots a simulator and names it in IOS_TEST_SIMULATOR. KGP's default standalone spawn runs the test
// binary without the simulator's system daemons, so UTType lookups, NSFileCoordinator and iCloud
// resource values all fail there; on a booted device they behave as in the app.
providers.environmentVariable("IOS_TEST_SIMULATOR").orNull?.let { simulator ->
    tasks.withType<KotlinNativeSimulatorTest>().matching { it.name == "iosSimulatorArm64Test" }.configureEach {
        device.set(simulator)
        standalone.set(false)
    }
}
