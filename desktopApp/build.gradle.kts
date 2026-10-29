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
// The dir is `build/kcef-natives/lib`, a JBR-shaped `<home>/lib`: JBR's JCEF finds jcef_helper and the .pak resources
// under `java.home/lib`, so KcefWebEngine points java.home at `build/kcef-natives` while CEF initialises.
val kcefJbrTag = "jbr-release-17.0.14b1367.22"

/**
 * The pinned SHA-512 of the Linux x64 natives archive of [kcefJbrTag] (jbr_jcef-17.0.14-linux-x64-b1367.22.tar.gz,
 * 193 MiB; its SHA-256 is 496e9a8b4bec55741b3f6cf1111ca3fab870aa6a176d62d870629e5c675a199e). A `.checksum` served
 * next to the archive that disagrees with it fails the build. Other OS/arch packages are not supported in D1.
 */
val kcefNativesDigest: String? =
    "1c875b43de4d20deb429a686331e4ea5c7c34c519569d1a3b785867d761ed002941203f79b19a338e4a49fab48a6cd4d35408a7356f55781e95c3d60c6c5a8d1"
val kcefNativesDir = layout.buildDirectory.dir("kcef-natives/lib")
val kcefJvmArgs = listOf("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")

/**
 * Downloads the JBR JCEF package for this OS/arch (with its `.checksum`), verifies it, and lays its `lib/` out as KCEF's
 * install dir (`install.lock` included, the layout KCEF's own installer produces). Linux x64/aarch64 only for D1.
 */
abstract class PrepareKcefNatives : DefaultTask() {
    @get:Input abstract val packageUrl: Property<String>
    @get:Input @get:Optional abstract val expectedDigest: Property<String>
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
        val cached = runCatching { pinMatches(checksumFile) && checksumMatches(archive, checksumFile) }.getOrDefault(false)
        if (!cached) {
            curl("$url.checksum", checksumFile)
            if (!pinMatches(checksumFile)) {
                throw GradleException(
                    "The .checksum served for $url (${digestIn(checksumFile)}) disagrees with the pinned digest " +
                        "${expectedDigest.get()}; refusing the natives",
                )
            }
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
        target.parentFile.mkdirs()
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

    /** The hex digest in a `.checksum` file (a SHA-256 or SHA-512 digest followed by the file name). */
    private fun digestIn(checksumFile: File): String =
        Regex("\\b[0-9a-fA-F]{64,128}\\b").find(checksumFile.readText())?.value?.lowercase()
            ?: throw GradleException("No digest in $checksumFile")

    /** True when no digest is pinned, or [checksumFile] carries exactly the pinned one. */
    private fun pinMatches(checksumFile: File): Boolean =
        !expectedDigest.isPresent || digestIn(checksumFile) == expectedDigest.get().lowercase()

    /** Whether [file] hashes to the digest in [checksumFile] (algorithm chosen by the digest's length). */
    private fun checksumMatches(file: File, checksumFile: File): Boolean {
        if (!file.isFile || !checksumFile.isFile) return false
        val expected = digestIn(checksumFile)
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
    description = "Fetches the CEF natives ($kcefJbrTag) into build/kcef-natives/lib, KCEF's install dir."
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
    kcefNativesDigest?.let { expectedDigest.set(it) }
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
        // The JCEF natives are JBR 17 builds: run on the JDK 17 toolchain, never on whatever JDK runs Gradle.
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
            .get().metadata.installationPath.asFile.absolutePath
        jvmArgs += kcefJvmArgs
        // D1 only: `run` points the app at the natives in this build dir by absolute path. Shipping the natives inside
        // the packaged distribution (and resolving them relative to it) is D4a.
        jvmArgs += "-Dkcef.installDir=${kcefNativesDir.get().asFile.absolutePath}"
        // Optional extra Chromium switches for `run`, e.g. -Pkcef.args=--remote-debugging-port=9222 (debugging, CDP).
        providers.gradleProperty("kcef.args").orNull?.let { jvmArgs += "-Dkcef.args=$it" }
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
