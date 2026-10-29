import java.nio.file.Files
import java.security.MessageDigest
import javax.inject.Inject

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

// CEF natives for KCEF (:sharedUi's KcefWebEngine). KCEF 2025.03.23's JCEF Java API matches the JCEF natives of this
// JetBrains Runtime release (KCEF's release notes: "compatible with jbr-release-17.0.14b1367.22"). They are fetched
// once at build time into kcefNativesDir (KCEF's install dir); the app never downloads anything at run time.
val kcefJbrTag = "jbr-release-17.0.14b1367.22"
val kcefNativesDir = layout.buildDirectory.dir("kcef-natives")
val kcefJvmArgs = listOf("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

/**
 * Downloads the JBR JCEF package for this OS/arch (with its `.checksum`), verifies it, and lays its `lib/` out as KCEF's
 * install dir (`install.lock` included, the layout KCEF's own installer produces). Linux x64/aarch64 only for D1.
 */
abstract class PrepareKcefNatives : DefaultTask() {
    @get:Input abstract val packageUrl: Property<String>
    @get:Internal abstract val installDir: DirectoryProperty
    @get:Internal abstract val downloadDir: DirectoryProperty
    @get:OutputFile abstract val installLock: RegularFileProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun prepare() {
        val url = packageUrl.get()
        val downloads = downloadDir.get().asFile.apply { mkdirs() }
        val archive = File(downloads, url.substringAfterLast('/'))
        val checksumFile = File(downloads, archive.name + ".checksum")
        if (!archive.isFile || !checksumMatches(archive, checksumFile)) {
            curl("$url.checksum", checksumFile)
            val part = File(downloads, archive.name + ".part")
            curl(url, part)
            if (!checksumMatches(part, checksumFile)) {
                part.delete()
                throw GradleException("Checksum mismatch for $url (expected ${checksumFile.readText().trim()})")
            }
            archive.delete()
            check(part.renameTo(archive)) { "Could not rename $part" }
        }

        val target = installDir.get().asFile
        val extract = File(target.parentFile, target.name + ".extract")
        extract.deleteRecursively()
        target.deleteRecursively()
        extract.mkdirs()
        exec.exec { commandLine("tar", "-xzf", archive.absolutePath, "-C", extract.absolutePath) }
        val lib = extract.listFiles()?.map { File(it, "lib") }?.firstOrNull { it.isDirectory }
            ?: throw GradleException("No <jbr>/lib directory in ${archive.name}")
        Files.move(lib.toPath(), target.toPath())
        extract.deleteRecursively()
        check(File(target, "libcef.so").isFile) { "${archive.name} has no libcef.so" }
        installLock.get().asFile.writeText("$url\n")

        val bytes = target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val summary = "CEF natives: ${archive.name} (archive ${archive.length() / 1_048_576} MiB) -> $target, ${bytes / 1_048_576} MiB unpacked"
        File(target.parentFile, "kcef-natives-size.txt").writeText(summary + "\n")
        logger.lifecycle(summary)
    }

    private fun curl(url: String, out: File) {
        exec.exec { commandLine("curl", "-fsSL", "--retry", "2", "-o", out.absolutePath, url) }
    }

    /** The `.checksum` holds a hex digest (SHA-256 or SHA-512, by length) followed by the file name. */
    private fun checksumMatches(file: File, checksumFile: File): Boolean {
        if (!file.isFile || !checksumFile.isFile) return false
        val expected = Regex("\\b[0-9a-fA-F]{64,128}\\b").find(checksumFile.readText())?.value?.lowercase()
            ?: throw GradleException("No digest in $checksumFile")
        val algorithm = when (expected.length) {
            64 -> "SHA-256"
            128 -> "SHA-512"
            else -> throw GradleException("Unknown digest length ${expected.length} in $checksumFile")
        }
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected
    }
}

val prepareKcefNatives by tasks.registering(PrepareKcefNatives::class) {
    group = "build"
    description = "Fetches the CEF natives ($kcefJbrTag) into build/kcef-natives, KCEF's install dir."
    val os = System.getProperty("os.name").lowercase()
    val arch = when (System.getProperty("os.arch")) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "aarch64"
        else -> "unsupported"
    }
    val (jbrVersion, jbrBuild) = kcefJbrTag.removePrefix("jbr-release-").split("b", limit = 2)
    packageUrl.set(
        if (os.contains("linux") && arch != "unsupported") {
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-$jbrVersion-linux-$arch-b$jbrBuild.tar.gz"
        } else {
            "unsupported:$os-$arch"
        },
    )
    installDir.set(kcefNativesDir)
    downloadDir.set(layout.buildDirectory.dir("kcef-download"))
    installLock.set(kcefNativesDir.map { it.file("install.lock") })
    doFirst {
        if (packageUrl.get().startsWith("unsupported:")) {
            throw GradleException("D1 bundles CEF natives for Linux x64/aarch64 only (${packageUrl.get()})")
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.bible.desktop.MainKt"
        jvmArgs += kcefJvmArgs
        jvmArgs += "-Dkcef.installDir=${kcefNativesDir.get().asFile.absolutePath}"
    }
}
tasks.matching { it.name == "run" }.configureEach { dependsOn(prepareKcefNatives) }

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
    for (name in listOf("public")) {
        if (jsDir.dir(name).asFile.isDirectory) inputs.dir(jsDir.dir(name))
    }
    jsDir.asFile.listFiles { f -> f.isFile && (f.name == "index.html" || f.name.startsWith("vite.config.") || (f.name.startsWith("tsconfig") && f.name.endsWith(".json"))) }
        ?.forEach { inputs.file(it) }
    inputs.file(jsDir.file("ios/poc-document.json"))
    outputs.dir(bibleViewGenerated)
    val dest = bibleViewGenerated.map { it.dir("bibleview-js").asFile.absolutePath }
    commandLine("sh", script.asFile.absolutePath, dest.get())
}
sourceSets.main {
    resources.srcDir(prepareBibleViewBundle.map { bibleViewGenerated.get().asFile })
}
tasks.named("processResources") { dependsOn(prepareBibleViewBundle) }
