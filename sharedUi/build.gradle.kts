import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.compose)          // org.jetbrains.compose (CMP runtime/resources)
    alias(libs.plugins.kotlin.compose)   // org.jetbrains.kotlin.plugin.compose (compiler)
    alias(libs.plugins.kotlin.serialization)  // Selection (page package) is @Serializable
}

// Build-time generator output: the pure-Kotlin iOS Strings holder emitted from the Android
// resources + AndroidStrings.kt mapping + the commonMain Strings interface. This dir is wired as an
// hostMain source root (below; hostMain is shared by iOS and JVM), so those compiles type-check the
// generated holder against the real interface for all locales. Declared before `kotlin { }` because the
// hostMain srcDir references it.
val iosStringsOutDir = layout.buildDirectory.dir("generated/iosStrings")

compose.resources {
    packageOfResClass = "net.bible.sharedui.generated.resources"
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
        // Host-side (JVM, no device/emulator) unit tests for this module's `commonTest`/
        // `androidUnitTest` sources. Added for Task 3 fix round 1's `popOrExitOnFailedPop` test —
        // :sharedUi had no test source set at all before this; the test needs no Android API, so
        // no Robolectric runner is configured, only plain JUnit via kotlin("test").
        withHostTest {}
    }

    // jvm() is the desktop product target (consumed by :desktopApp) and also runs the fast Linux
    // shared-tests (./gradlew :sharedUi:jvmTest): headless desktop Compose with real Skia, so
    // iOS-host logic is tested without a Mac (I0 spec §2).
    jvm()

    // iOS targets: configure + compile-check on Linux; the framework LINK tasks run only
    // on a Mac. The static framework (baseName "SharedUi") is what the iOS host embeds.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedUi"
            isStatic = true
        }
    }

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
            implementation(project(":sharedCore"))
            // I2 step 0: the shared app root and reading host (I2 streams A/B) call controllers in :sharedLogic.
            implementation(project(":sharedLogic"))
            implementation(compose.runtime)
            implementation(compose.components.resources)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.reorderable)    // CMP drag-reorder engine (wrapped by AbReorderableColumn)
            implementation(libs.materialkolor)  // seed -> M3 ColorScheme derivation (A/B batch 4b)
            // `api`, not `implementation`: :app's NavHostComposeActivity creates the NavController
            // it passes into the graph, so the type must be on its compile classpath.
            api(libs.jetbrains.navigation.compose)
            // Declared explicitly (Task 5) rather than left to arrive transitively through
            // navigation-compose's own dependency graph — see the version-catalog comment.
            // `implementation`: only AiNavGraph.kt's LifecycleEventEffect uses it, never exposed
            // through :app's compile classpath the way NavHostController is.
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            // JsonElement parsing for the BibleView bridge (no plugin needed):
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
        }
        androidMain.dependencies {
            // PlatformBackHandler's android actual delegates to androidx.activity.compose.BackHandler.
            // This is :sharedUi's first androidMain dependency block.
            implementation(libs.androidx.activity.compose)
            // HostSystemBars.android.kt: WindowInsetsControllerCompat.
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // The generated holder is pure Kotlin; iOS and the desktop app (and jvmTest) both compile it.
        getByName("hostMain").kotlin.srcDir(iosStringsOutDir)
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain {
            dependencies {
                // Desktop D1 web engine (package net.bible.sharedui.desktop.webengine.kcef only). JOGL/gluegen are
                // jcef's runtime deps for GL off-screen rendering; they are not on Maven Central (jogamp.org only)
                // and the windowed rendering D1 uses does not need them.
                // KCEF pulls ktor 3.0.0 modules (okhttp engine, content negotiation, kotlinx-json) next to the
                // catalog's ktor core; the BOM aligns every ktor module on `ktor` so no 3.0.0/3.5.2 mix reaches the
                // classpath. KCEFBuilder builds its (unused) HttpClient at construction, so linkage is exercised.
                implementation(project.dependencies.platform("io.ktor:ktor-bom:${libs.versions.ktor.get()}"))
                implementation(libs.kcef.get().toString()) {
                    exclude(group = "org.jogamp.gluegen")
                    exclude(group = "org.jogamp.jogl")
                }
            }
        }
        jvmTest {
            kotlin.srcDir("src/iosTest/kotlin/net/bible/sharedui/ios/report/logic")
            kotlin.srcDir("src/iosMain/kotlin/net/bible/sharedui/ios/startup/logic")
            kotlin.srcDir("src/iosTest/kotlin/net/bible/sharedui/ios/startup/logic")
            kotlin.srcDir("src/iosMain/kotlin/net/bible/sharedui/ios/discrete/systemsurfaces")
            kotlin.srcDir("src/iosTest/kotlin/net/bible/sharedui/ios/discrete/systemsurfaces")
            dependencies {
                implementation(project(":strings-gen")) // Test-only access to the real resource/API mapping parsers.
                implementation(compose.desktop.currentOs)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(libs.kotlinx.coroutines.test)
                // Dispatchers.Main of the desktop app (the AWT event thread), as :desktopApp has it at run time: the
                // shared startup reaches Dispatchers.Main (e.g. MyDocumentBookManager's scope).
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
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

listOf("compileKotlinIosArm64", "compileKotlinIosSimulatorArm64", "compileIosMainKotlinMetadata",
    "compileHostMainKotlinMetadata", "compileKotlinJvm", "compileTestKotlinJvm")
    .forEach { name -> tasks.matching { it.name == name }.configureEach { dependsOn(generateIosStrings) } }

// The startup fixture (a real SWORD module as bytes) is generated by :sharedLogic; its tests and these share it.
evaluationDependsOn(":sharedLogic")
kotlin.sourceSets.configureEach {
    if (name == "iosTest" || name == "jvmTest") {
        kotlin.srcDir(files(project(":sharedLogic").layout.buildDirectory.dir("generated/startupFixture/kotlin"))
            .builtBy(project(":sharedLogic").tasks.named("generateStartupFixture")))
    }
}

// Like sharedCore, UIKit/provider tests need a booted simulator's system daemons, not standalone spawn.
providers.environmentVariable("IOS_TEST_SIMULATOR").orNull?.let { simulator ->
    tasks.withType<KotlinNativeSimulatorTest>().matching { it.name == "iosSimulatorArm64Test" }.configureEach {
        device.set(simulator)
        standalone.set(false)
    }
}

// Headless desktop Compose (no X server in the container / CI).
tasks.matching { it.name == "jvmTest" }.configureEach {
    (this as Test).systemProperty("java.awt.headless", "true")
    systemProperty("repoRoot", rootProject.projectDir.absolutePath)
}

// Live KCEF tests (KcefLiveTest, in the jvmTest source set): a windowed Chromium needs a non-headless AWT, which the
// headless jvmTest run above cannot give, so they run in their own task. `kcef.installDir` is passed only when the
// natives that `:desktopApp:prepareKcefNatives` produces (desktopApp/build/kcef-natives/lib) exist at execution time (no task dependency on :desktopApp);
// without them, or without DISPLAY, the tests skip themselves. In jvmTest they always skip (headless).
val kcefNativesDir = rootProject.layout.projectDirectory.dir("desktopApp/build/kcef-natives/lib").asFile
tasks.register<Test>("jvmKcefTest") {
    group = "verification"
    description = "Runs the live KCEF tests (needs desktopApp/build/kcef-natives and DISPLAY)."
    val testCompilation = kotlin.jvm().compilations.getByName("test")
    testClassesDirs = testCompilation.output.classesDirs
    classpath = testCompilation.output.allOutputs + testCompilation.runtimeDependencyFiles!!
    filter { includeTestsMatching("net.bible.sharedui.desktop.webengine.kcef.KcefLiveTest") }
    systemProperty("java.awt.headless", "false")
    // The JCEF natives are JBR 17 builds: the test JVM must be the JDK 17 toolchain.
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
    // Extra Chromium switches for diagnosis (space-separated), e.g. -Pkcef.args="--enable-logging=stderr --v=1".
    providers.gradleProperty("kcef.args").orNull?.let { systemProperty("kcef.args", it) }
    providers.gradleProperty("kcef.logFile").orNull?.let { systemProperty("kcef.logFile", it) }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        if (File(kcefNativesDir, "install.lock").isFile) listOf("-Dkcef.installDir=${kcefNativesDir.absolutePath}") else emptyList()
    })
    outputs.upToDateWhen { false }
}
