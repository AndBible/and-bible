import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Writes `AndBibleVersionCode.kt` (hostMain: iOS and JVM) with `versionCode` from the root `version.properties`, the
 * one version source Android and the desktop launchers already read. iOS reports it as `AppEnvironment.versionNumber`
 * (#3970).
 */
abstract class GenerateAndBibleVersionCode : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val versionFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun generate() {
        val code = Properties().apply { versionFile.get().asFile.inputStream().use { load(it) } }
            .getProperty("versionCode")?.trim()
        require(code != null && Regex("[1-9][0-9]*").matches(code)) { "version.properties: bad versionCode '$code'" }
        val dir = outputDir.get().asFile.resolve("net/bible/sharedcore/platform").apply { mkdirs() }
        dir.resolve("AndBibleVersionCode.kt").writeText(
            "// Generated from version.properties by :sharedCore:generateAndBibleVersionCode; do not edit.\n" +
                "package net.bible.sharedcore.platform\n\n" +
                "/** The AndBible version code (Android `versionCode`) of this build. */\n" +
                "const val ANDBIBLE_VERSION_CODE: Long = ${code}L\n",
        )
    }
}

val generateAndBibleVersionCode = tasks.register<GenerateAndBibleVersionCode>("generateAndBibleVersionCode") {
    versionFile.set(rootProject.layout.projectDirectory.file("version.properties"))
    outputDir.set(layout.buildDirectory.dir("generated/andbibleVersion/hostMain/kotlin"))
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

    // Intermediate source set shared by the iOS targets and the JVM target (not Android): host code that
    // iOS and the desktop app both use. See the desktop port audit, section 2.1.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("host") {
                withJvm()
                group("ios")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("com.squareup.okio:okio:3.9.0")
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)       // HttpClient / HttpClientEngine appear in WebDavClient's API
            implementation(libs.xmlutil.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)   // desktop HTTP engine (DesktopHttpClients)
            implementation(libs.dbus.java.core)
            runtimeOnly(libs.dbus.java.transport.native.unixsocket)
            implementation(libs.java.keyring)   // desktop OS secret store (DesktopSecretStore)
        }
        jvmTest {
            kotlin.srcDir("src/iosTest/kotlin/net/bible/sharedcore/platform/sync/runner")
            // Execute the platform-free iOS publication policy on Linux as well as Native.
            kotlin.srcDir("src/iosMain/kotlin/net/bible/sharedcore/platform/speak/systemsurfaces")
            kotlin.srcDir("src/iosTest/kotlin/net/bible/sharedcore/platform/speak/systemsurfaces")
        }
        // I2 step 0: Darwin engine for iOS HTTP (spec C5; streams C and D)
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // iOS reports it as versionNumber; JVM tests use it to check the add-on version gate (#3970).
        getByName("hostMain").kotlin.srcDir(generateAndBibleVersionCode.map { it.outputDir })
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

// Keep ordinary test runs uninstrumented.
if (providers.gradleProperty("coverage").isPresent) {
    kover {
        currentProject {
            createVariant("shared") { add("jvm") }
        }
    }
} else {
    kover { disable() }
}
