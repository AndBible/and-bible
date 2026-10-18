import java.util.Base64 as JBase64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.kotlin.serialization)
}

// The schema-export fixtures of :sharedData's commonTest (ExportFixtures.kt, plus the generated SchemaExports.kt) are
// shared by copying, not by adding sharedData's whole test source dir: that dir holds unrelated tests and expect
// declarations without actuals here. The copy is regenerated on every build, so it cannot drift.
val sharedDataFixtures by tasks.registering(Sync::class) {
    dependsOn(":sharedData:generateSchemaFixtures")
    from(project(":sharedData").layout.projectDirectory.file("src/commonTest/kotlin/net/bible/shareddata/fixtures/ExportFixtures.kt"))
    from(project(":sharedData").layout.buildDirectory.dir("generated/schemaFixtures/commonTest/kotlin/net/bible/shareddata/fixtures"))
    into(layout.buildDirectory.dir("generated/sharedDataFixtures/commonTest/kotlin/net/bible/shareddata/fixtures"))
}

// The Native test runner has no resource files, and the startup acceptance tests of :sharedLogic and :sharedUi need a
// real SWORD module. Embed the small K4RawText module of jsword's frozen test corpus as bytes in a generated
// `StartupFixture`; the tests write it into a temp directory (modelled on jsword's K4FixtureResources generator).
val startupFixtureDir = layout.buildDirectory.dir("generated/startupFixture/kotlin")
val generateStartupFixture by tasks.registering {
    val root = rootProject.layout.projectDirectory.dir("jsword/src/commonTest/resources/k4-modules/space dir/\u00e4")
    val wanted = listOf("mods.d/K4RawText.conf", "modules/K4RawText/nt", "modules/K4RawText/nt.vss", "modules/K4RawText/ot", "modules/K4RawText/ot.vss")
    inputs.files(wanted.map { root.file(it) }).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(startupFixtureDir)
    doLast {
        val out = startupFixtureDir.get().file("net/bible/sharedlogic/fixtures/StartupFixture.kt").asFile
        out.parentFile.mkdirs()
        out.bufferedWriter().use { w ->
            w.appendLine("// GENERATED test-only fixture bytes. Do not edit.")
            w.appendLine("package net.bible.sharedlogic.fixtures")
            w.appendLine("import kotlin.io.encoding.Base64")
            w.appendLine("import kotlin.io.encoding.ExperimentalEncodingApi")
            w.appendLine("/** The K4RawText module (relative to a SWORD root: mods.d/, modules/). */")
            w.appendLine("@OptIn(ExperimentalEncodingApi::class)")
            w.appendLine("object StartupFixture {")
            w.appendLine("    val files: Map<String, ByteArray> by lazy { mapOf(")
            for (name in wanted) {
                val file = root.file(name).asFile
                check(file.isFile) { "Missing startup fixture file: $file" }
                val chunks = JBase64.getEncoder().encodeToString(file.readBytes()).chunked(60_000)
                w.appendLine("        \"$name\" to Base64.decode(arrayOf<String>(")
                chunks.forEach { w.appendLine("            \"$it\",") }
                w.appendLine("        ).joinToString(\"\")),")
            }
            w.appendLine("    ) }")
            w.appendLine("}")
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    android {
        namespace = "net.bible.sharedlogic"
        compileSdk = 36
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":sharedData"))
            api(project(":sharedCore"))
            api(project(":jsword"))
            api(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kaml)
        }
        // I2 step 0: Darwin engine for iOS HTTP (spec C5; streams C and D)
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.jetbrains.markdown)
        }
        commonTest {
            kotlin.srcDir(sharedDataFixtures.map { layout.buildDirectory.dir("generated/sharedDataFixtures/commonTest/kotlin").get().asFile })
        }
        commonTest.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation("com.squareup.okio:okio-fakefilesystem:3.9.0")
        }
        jvmTest.dependencies {
            implementation(libs.androidx.sqlite.bundled.jvm)
        }
    }
}

// The default hierarchy creates iosTest after this script is evaluated.
kotlin.sourceSets.configureEach {
    if (name == "iosTest" || name == "jvmTest") kotlin.srcDir(generateStartupFixture)
}
