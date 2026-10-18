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
