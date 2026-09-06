import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.compose)          // org.jetbrains.compose (CMP runtime/resources)
    alias(libs.plugins.kotlin.compose)   // org.jetbrains.kotlin.plugin.compose (compiler)
}

// Build-time generator output: the pure-Kotlin iOS Strings holder emitted from the Android
// resources + AndroidStrings.kt mapping + the commonMain Strings interface. This dir is wired as an
// iosMain source root (below), so the iOS compile type-checks the generated holder against the real
// interface for all locales. Declared before `kotlin { }` because the iosMain srcDir references it.
val iosStringsOutDir = layout.buildDirectory.dir("generated/iosStrings")

kotlin {
    jvmToolchain(17)

    // AGP-9 KMP Android library: configure Android via `android { }` INSIDE `kotlin { }`
    // (the deprecated `androidLibrary { }` accessor is the same plugin).
    android {
        namespace = "net.bible.sharedui"
        compileSdk = 36
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // Host-side (JVM, no device/emulator) unit tests for this module's `commonTest`/
        // `androidUnitTest` sources. Added for Task 3 fix round 1's `popOrExitOnFailedPop` test —
        // :sharedUi had no test source set at all before this; the test needs no Android API, so
        // no Robolectric runner is configured, only plain JUnit via kotlin("test").
        withHostTest {}
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
            implementation(libs.reorderable)    // CMP drag-reorder engine (wrapped by AbReorderableColumn)
            implementation(libs.materialkolor)  // seed -> M3 ColorScheme derivation (A/B batch 4b)
            // `api`, not `implementation`: :app's NavHostComposeActivity creates the NavController
            // it passes into the graph, so the type must be on its compile classpath.
            api(libs.jetbrains.navigation.compose)
        }
        androidMain.dependencies {
            // PlatformBackHandler's android actual delegates to androidx.activity.compose.BackHandler.
            // This is :sharedUi's first androidMain dependency block.
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            // :sharedUi's first test source set (Task 3 fix round 1); kotlin("test") is enough —
            // the covered logic (popOrExitOnFailedPop) touches no Android/Compose API.
            implementation(kotlin("test"))
        }
        iosMain { kotlin.srcDir(iosStringsOutDir) }
    }
}

val generateIosStrings by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generates the pure-Kotlin iOS Strings holder from Android res + AndroidStrings.kt."
    classpath = project(":strings-gen").the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("net.bible.stringsgen.MainKt")
    val resDir = rootProject.file("app/src/main/res")
    val androidStrings = rootProject.file("app/src/main/java/net/bible/sharedui/strings/AndroidStrings.kt")
    val interfaceFile = file("src/commonMain/kotlin/net/bible/sharedui/strings/Strings.kt")
    args("--res", resDir.absolutePath, "--android-strings", androidStrings.absolutePath,
         "--interface", interfaceFile.absolutePath, "--out", iosStringsOutDir.get().asFile.absolutePath)
    inputs.dir(resDir); inputs.file(androidStrings); inputs.file(interfaceFile)
    outputs.dir(iosStringsOutDir)
}

listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64", "compileIosMainKotlinMetadata")
    .forEach { name -> tasks.matching { it.name == name }.configureEach { dependsOn(generateIosStrings) } }
