// Experimental Desktop D5 module (WebKitGTK engine experiment). Not shipped.
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
    implementation(libs.jna)
    testImplementation(kotlin("test"))
}

// The live test (WebKitGtkLiveTest) needs a real X11 display: `-Pd5.live=true` forwards d5.live and keeps AWT non-headless.
val d5Live = providers.gradleProperty("d5.live").orNull == "true"

tasks.withType<Test>().configureEach {
    if (d5Live) {
        systemProperty("d5.live", "true")
        // Let the JVM talk to the same X11 display as the GTK thread.
        environment("GDK_BACKEND", "x11")
    } else {
        systemProperty("java.awt.headless", "true")
    }
    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "net.bible.desktop.webkitgtk.MainKt"
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
            .get().metadata.installationPath.asFile.absolutePath
        // JNA's Native.getComponentID and the heavyweight canvas need these.
        jvmArgs += listOf("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED")
        // andbible.poc.* (scenario, dark, spike, themeFlipMs, overlay) reach the app as system properties.
        listOf("scenario", "dark", "spike", "themeFlipMs", "overlay").forEach { key ->
            providers.gradleProperty("andbible.poc.$key").orNull?.let { jvmArgs += "-Dandbible.poc.$key=$it" }
        }
    }
}
