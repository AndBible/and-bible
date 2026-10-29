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
    testImplementation(libs.kotlinx.serialization.json)
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

// Bundles the Vite-built app/bibleview-js (plus the PoC fixture) as classpath resources under bibleview-js/.
val bibleViewGenerated = layout.buildDirectory.dir("generated/bibleview")
val prepareBibleViewBundle by tasks.registering(Exec::class) {
    val script = layout.projectDirectory.file("scripts/copy-bibleview.sh")
    val jsDir = rootProject.layout.projectDirectory.dir("app/bibleview-js")
    inputs.file(script)
    inputs.dir(jsDir.dir("src"))
    inputs.file(jsDir.file("package-lock.json"))
    inputs.file(jsDir.file("package.json"))
    inputs.file(jsDir.file("ios/poc-document.json"))
    outputs.dir(bibleViewGenerated)
    val dest = bibleViewGenerated.map { it.dir("bibleview-js").asFile.absolutePath }
    commandLine("sh", script.asFile.absolutePath, dest.get())
}
sourceSets.main {
    resources.srcDir(bibleViewGenerated.map { it.asFile })
}
tasks.named("processResources") { dependsOn(prepareBibleViewBundle) }
