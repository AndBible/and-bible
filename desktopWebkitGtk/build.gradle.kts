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

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "net.bible.desktop.webkitgtk.MainKt"
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
            .get().metadata.installationPath.asFile.absolutePath
    }
}
