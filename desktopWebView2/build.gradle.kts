// Experimental Desktop W2 module (WebView2 engine experiment, Windows). Not shipped.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedUi"))
    implementation(project(":sharedCore"))
    implementation(compose.desktop.currentOs)
    implementation(libs.jna)
    implementation("net.java.dev.jna:jna-platform:${libs.versions.jna.get()}")
    testImplementation(kotlin("test"))
}

// The Windows Skiko natives for the VM test dists only. They must not be a runtimeOnly dependency of the module: that
// leaked them through :desktopApp into the Linux runtime classpath and the .deb.
val w2WindowsNatives by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false // only the Skiko Windows runtime jar itself, never a second version of a shared library
}
dependencies {
    // The Skiko version the runtime classpath resolves (skiko-awt), so the natives always match it.
    w2WindowsNatives(provider {
        val skiko = configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents
            .map { it.moduleVersion }.first { it?.group == "org.jetbrains.skiko" && it.name == "skiko-awt" }!!
        "org.jetbrains.skiko:skiko-awt-runtime-windows-x64:${skiko.version}"
    })
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

// The WebView2 loader (WebView2Loader.dll, x64) from the Microsoft.Web.WebView2 NuGet package (BSD-3-Clause licence).
// Not vendored: `-Pw2.loader=<path to WebView2Loader.dll>` copies it into the dist, else the dist expects it next to
// the jars or on PATH.
val w2Loader = providers.gradleProperty("w2.loader").orNull

/** A plain directory to copy to the Windows VM: `lib/` (all jars) plus `run.cmd`. */
val w2Dist by tasks.registering(Sync::class) {
    group = "distribution"
    from(project(":desktopApp").tasks.named("jar")) { into("lib") }
    from(project(":desktopApp").configurations.named("runtimeClasspath")) { into("lib") }
    from(w2WindowsNatives) { into("lib") }
    w2Loader?.let { from(it) { into("lib") } }
    into(layout.buildDirectory.dir("w2dist"))
}

/** The module's tests as a plain directory for the Windows VM: `lib/` plus `run-tests.cmd` (JUnit 4 runner). */
val w2TestDist by tasks.registering(Sync::class) {
    group = "verification"
    val testSources = sourceSets.named("test")
    from(testSources.map { it.output }) { into("classes") }
    from(tasks.named("jar")) { into("lib") }
    from(configurations.named("testRuntimeClasspath")) { into("lib") }
    from(w2WindowsNatives) { into("lib") }
    w2Loader?.let { from(it) { into("lib") } }
    from(layout.projectDirectory.file("src/test/run-tests.cmd"))
    into(layout.buildDirectory.dir("w2testdist"))
}
