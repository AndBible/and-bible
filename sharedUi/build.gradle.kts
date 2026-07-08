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
