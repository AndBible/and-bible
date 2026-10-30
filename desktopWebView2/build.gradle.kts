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
    implementation(project(":desktopApp"))
    implementation(project(":sharedUi"))
    implementation(project(":sharedCore"))
    implementation(compose.desktop.currentOs)
    // The module is built in the Linux container and run on Windows: ship the Windows Skiko natives too.
    runtimeOnly(compose.desktop.windows_x64)
    implementation(libs.jna)
    implementation("net.java.dev.jna:jna-platform:${libs.versions.jna.get()}")
    testImplementation(kotlin("test"))
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
    from(tasks.named("jar")) { into("lib") }
    from(configurations.named("runtimeClasspath")) { into("lib") }
    w2Loader?.let { from(it) { into("lib") } }
    into(layout.buildDirectory.dir("w2dist"))
}
