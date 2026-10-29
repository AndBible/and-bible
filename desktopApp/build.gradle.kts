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
    testImplementation(kotlin("test"))
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

compose.desktop {
    application {
        mainClass = "net.bible.desktop.MainKt"
    }
}

// Headless desktop Compose (no X server in the container / CI).
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}
