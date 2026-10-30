import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
    implementation(compose.material3) // the splash and startup-failed screens (DesktopAppRoot)
    // Dispatchers.Main on the AWT event thread: the hoisted host code (hostMain) uses Dispatchers.Main.
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

// CEF natives for KCEF (:sharedUi's KcefWebEngine). KCEF 2025.03.23's JCEF Java API matches the JCEF natives of this
// JetBrains Runtime release (KCEF's release notes: "compatible with jbr-release-17.0.14b1367.22"). They are fetched
// once at build time into kcefNativesDir (KCEF's install dir); the app never downloads anything at run time.
// The dir is `build/kcef-natives/lib` (the package's JBR `lib`, as KCEF's installer lays it out); KcefWebEngine passes
// its jcef_helper, resources and locales paths to CEF explicitly.
val kcefJbrTag = "jbr-release-17.0.14b1367.22"

/**
 * The pinned SHA-512 digests of the natives archives of [kcefJbrTag], by `<os>-<arch>`. A `.checksum` served next to an
 * archive that disagrees with its pin fails the build.
 * - linux-x64: jbr_jcef-17.0.14-linux-x64-b1367.22.tar.gz, 193 MiB (SHA-256
 *   496e9a8b4bec55741b3f6cf1111ca3fab870aa6a176d62d870629e5c675a199e).
 * - windows-x64: jbr_jcef-17.0.14-windows-x64-b1367.22.tar.gz, 210 MiB; the pin equals both the JetBrains `.checksum`
 *   and a hash of our own download (W1, 2026-10-30).
 * Other OS/arch packages (linux-aarch64) are fetched unpinned, checked only against their `.checksum`.
 */
val kcefNativesDigests = mapOf(
    "linux-x64" to "1c875b43de4d20deb429a686331e4ea5c7c34c519569d1a3b785867d761ed002941203f79b19a338e4a49fab48a6cd4d35408a7356f55781e95c3d60c6c5a8d1",
    "windows-x64" to "1f737ebb5f389dc7cee875d5308b44cbd8e3cdaa239cb1a6f39761e3c449ea54c8117777ca37cd8b04fee488dc212a70575139308afd84e4d17bd6909a5b5bf7",
)
val kcefNativesDir = layout.buildDirectory.dir("kcef-natives/lib")

/** The build host as `<os>-<arch>` (`linux-x64`, `windows-x64`, ...): the packages are built on the OS they are for. */
val hostOs: String = System.getProperty("os.name").lowercase().let {
    when {
        it.contains("linux") -> "linux"
        it.contains("windows") -> "windows"
        it.contains("mac") -> "macos"
        else -> "unsupported"
    }
}
val hostArch: String = when (System.getProperty("os.arch")) {
    "amd64", "x86_64" -> "x64"
    "aarch64", "arm64" -> "aarch64"
    else -> "unsupported"
}
val hostTarget = "$hostOs-$hostArch"
val isWindowsHost = hostOs == "windows"
/** The targets the desktop distribution packages are built for (each on its own OS). */
val packageTargets = setOf("linux-x64", "windows-x64")

// sun.awt.X11 is only needed by the windowed-pane fallback (X11 focus helper); off-screen rendering does not use it.
// java.desktop has no sun.awt.X11 on Windows, where the JVM would warn about the unknown package on every start.
val kcefJvmArgs = listOf("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED") +
    if (isWindowsHost) emptyList() else listOf("--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED")

/**
 * Downloads the JBR JCEF package for this OS/arch (with its `.checksum`), verifies it, and lays it out as KCEF's install
 * dir (`install.lock` included), the layout KCEF's own installer produces: the package's `lib/` on Linux; on Windows its
 * `lib/` with the contents of `bin/` (libcef.dll, jcef.dll, jcef_helper.exe, ...) merged in. Linux x64/aarch64 and
 * Windows x64.
 */
abstract class PrepareKcefNatives : DefaultTask() {
    @get:Input abstract val packageUrl: Property<String>
    @get:Input @get:Optional abstract val expectedDigest: Property<String>
    @get:Internal abstract val installDir: DirectoryProperty
    @get:Internal abstract val downloadDir: DirectoryProperty
    /** True for the Windows layout (`bin/` merged into `lib/`, `libcef.dll`) and the Windows `tar.exe`. */
    @get:Input abstract val windowsLayout: Property<Boolean>
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
        // The whole build/kcef-natives tree is this task's: wipe it, so nothing of an earlier layout lingers.
        target.parentFile.deleteRecursively()
        val extract = File(target.parentFile, target.name + ".extract")
        extract.mkdirs()
        val windows = windowsLayout.get()
        // Windows: the system bsdtar, not a tar from a Unix tool set that may come first on the PATH (Git's GNU tar
        // reads `C:` as a remote host).
        val tar = if (windows) File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\tar.exe").path else "tar"
        exec.exec { commandLine(tar, "-xzf", archive.absolutePath, "-C", extract.absolutePath) }
        val jbr = extract.listFiles()?.firstOrNull { File(it, "lib").isDirectory }
            ?: throw GradleException("No <jbr>/lib directory in ${archive.name}")
        Files.move(File(jbr, "lib").toPath(), target.toPath())
        if (windows) {
            val bin = File(jbr, "bin")
            if (!bin.isDirectory) throw GradleException("No <jbr>/bin directory in ${archive.name}")
            bin.listFiles().orEmpty().forEach { Files.move(it.toPath(), File(target, it.name).toPath()) }
        }
        extract.deleteRecursively()
        val cefLibrary = if (windows) "libcef.dll" else "libcef.so"
        check(File(target, cefLibrary).isFile) { "${archive.name} has no $cefLibrary" }
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
    val (jbrVersion, jbrBuild) = kcefJbrTag.removePrefix("jbr-release-").split("b", limit = 2)
    val supported = hostTarget in setOf("linux-x64", "linux-aarch64", "windows-x64")
    packageUrl.set(
        if (supported) {
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-$jbrVersion-$hostTarget-b$jbrBuild.tar.gz"
        } else {
            "unsupported:$hostTarget"
        },
    )
    windowsLayout.set(isWindowsHost)
    installDir.set(kcefNativesDir)
    kcefNativesDigests[hostTarget]?.let { expectedDigest.set(it) }
    downloadDir.set(layout.buildDirectory.dir("kcef-download"))
    installLock.set(kcefNativesDir.map { it.file("install.lock") })
    doFirst {
        if (packageUrl.get().startsWith("unsupported:")) {
            throw GradleException("CEF natives are bundled for Linux x64/aarch64 and Windows x64 only (${packageUrl.get()})")
        }
    }
}

/**
 * The distribution version of the desktop packages: `<versionMajorMinor>.<versionCode>` from the root
 * `version.properties`, e.g. `6.0.1230`. A .deb needs a numeric dotted version, so `versionNameSuffix` is not part of it.
 */
fun desktopPackageVersion(props: Properties): String {
    val majorMinor = props.getProperty("versionMajorMinor")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("version.properties has no versionMajorMinor")
    val code = props.getProperty("versionCode")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("version.properties has no versionCode")
    if (code.toIntOrNull() == null) throw GradleException("versionCode is not an integer: $code")
    return "$majorMinor.$code"
}

val rootVersionProperties = Properties().apply {
    rootProject.layout.projectDirectory.file("version.properties").asFile.inputStream().use { load(it) }
}
val desktopVersion = desktopPackageVersion(rootVersionProperties)
val windowsIdentity = mapOf("NAME" to "AndBible.Desktop", "PUBLISHER" to "CN=AndBible-Unsigned")
val windowsUpgradeUuid = "8D66BF6B-67E6-4EFD-88AA-6C2C20D794A8"
val windowsPackageVersion = providers.gradleProperty("windowsPackageVersion").orElse(desktopVersion).get()
val windowsVersionParts = windowsPackageVersion.split('.')
require(windowsVersionParts.size == 3 && windowsVersionParts.all { it.matches(Regex("0|[1-9][0-9]*")) && (it.toIntOrNull() ?: -1) in 0..65535 }) {
    "windowsPackageVersion must have three numeric components in 0..65535"
}
require(windowsVersionParts[0].toInt() in 1..255 && windowsVersionParts[1].toInt() <= 255) { "MSI major/minor exceed 255" }
// Compose passes the project version to jpackage as `--app-version` (packageVersion/debPackageVersion do not reach it
// for Linux); unset it would be 1.0.0.
version = desktopVersion


compose.desktop {
    application {
        mainClass = "net.bible.desktop.MainKt"
        nativeDistributions {
            appResourcesRootDir.set(layout.buildDirectory.dir("desktop-resources"))
            targetFormats(if (isWindowsHost) TargetFormat.Msi else TargetFormat.Deb)
            // The launcher's name: /opt/andbible/bin/andbible on Linux, AndBible.exe on Windows.
            packageName = if (isWindowsHost) "AndBible" else "andbible"
            packageVersion = if (isWindowsHost) windowsPackageVersion else desktopVersion
            description = "Offline Bible study app"
            vendor = "AndBible"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE.txt"))
            // jlink module set: the suggestRuntimeModules result plus what TLS (jdk.crypto.ec) and HTTP clients
            // (java.net.http) need at run time, and jdk.localedata the non-English locales, which static analysis cannot see.
            modules("java.instrument", "java.management", "jdk.unsupported", "jdk.crypto.ec", "java.net.http", "jdk.localedata", "jdk.security.auth")
            windows {
                upgradeUuid = windowsUpgradeUuid
                msiPackageVersion = windowsPackageVersion
                menu = true
                shortcut = true
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("packaging/linux/andbible.png"))
                debMaintainer = "AndBible <and-bible-support@googlegroups.com>"
                menuGroup = "Education"
                appCategory = "Education"
            }
        }
        // The JCEF natives are JBR 17 builds: run on the JDK 17 toolchain, never on whatever JDK runs Gradle.
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
            .get().metadata.installationPath.asFile.absolutePath
        jvmArgs += kcefJvmArgs
        // The version the app reports (DesktopAppEnvironment.versionName): `run` and the packaged launcher (.cfg) alike.
        jvmArgs += "-Dandbible.version=$desktopVersion"
    }
}
tasks.matching { it.name == "run" }.configureEach { dependsOn(prepareKcefNatives) }
// `run` points the app at the natives in this build dir by absolute path. This must stay out of
// compose.desktop.application.jvmArgs: those land in the packaged app's .cfg and would beat the resources dir that
// the installed app resolves its natives from (CefNativesLocation). verifyPackagedConfig enforces that.
tasks.matching { it.name == "run" }.withType<JavaExec>().configureEach {
    jvmArgs("-Dkcef.installDir=${kcefNativesDir.get().asFile.absolutePath}")
    // Optional extra Chromium switches for `run`, e.g. -Pkcef.args=--remote-debugging-port=9222 (debugging, CDP).
    providers.gradleProperty("kcef.args").orNull?.let { jvmArgs("-Dkcef.args=$it") }
}

// The CEF natives as app resources of this host's distribution (appResourcesRootDir/<os-arch>/kcef-natives), found
// at run time through compose.application.resources.dir.
val stageKcefNatives by tasks.registering(Sync::class) {
    dependsOn(prepareKcefNatives)
    from(kcefNativesDir)
    into(layout.buildDirectory.dir("desktop-resources/$hostTarget/kcef-natives"))
}
// Distribution packages: Linux x64 (on Linux) and Windows x64 (on Windows), each with its own JBR natives build.
val packagingTaskNames = setOf("prepareAppResources", "createDistributable", "createReleaseDistributable", "packageDeb", "packageReleaseDeb")
stageKcefNatives.configure {
    doFirst {
        if (hostTarget !in packageTargets) {
            throw GradleException("The desktop packages are built for ${packageTargets.joinToString()} only (this host is $hostTarget)")
        }
    }
}
tasks.matching { it.name in packagingTaskNames }.configureEach { dependsOn(stageKcefNatives) }

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

// Raw resources the real app reads from the classpath (iOS copies the same files into its bundle, iosApp/project.yml):
// the repository site map, the licence page of the help dialog and the two LLM system prompts. Copied at build time from
// app/src/main/res/raw, so they are not duplicated in git; the packaged app gets them inside the app jar.
val desktopRawResources = layout.buildDirectory.dir("generated/desktop-raw")
val desktopRawResourceNames = listOf("repositories.txt", "license.html", "llm_agent_system_prompt.md", "llm_text_transformation_system_prompt.md")
val prepareDesktopRawResources by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("app/src/main/res/raw")) { include(desktopRawResourceNames) }
    into(desktopRawResources)
    doLast {
        val missing = desktopRawResourceNames
            .filterNot { File(desktopRawResources.get().asFile, it).isFile }
        if (missing.isNotEmpty()) throw GradleException("Missing raw resources in app/src/main/res/raw: $missing")
    }
}
sourceSets.main {
    resources.srcDir(prepareDesktopRawResources)
}

// Reading plans and a deterministic index, available from directories and packaged jars.
val desktopReadingPlans = layout.buildDirectory.dir("generated/desktop-readingplans")
val prepareDesktopReadingPlans by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("app/src/main/assets/readingplan")) { include("*.properties"); into("readingplan") }
    into(desktopReadingPlans)
    doLast {
        val dir = desktopReadingPlans.get().dir("readingplan").asFile
        dir.mkdirs()
        File(dir, "index.txt").writeText(dir.listFiles().orEmpty().filter { it.name.endsWith(".properties") }
            .map { it.name.removeSuffix(".properties") }.sorted().joinToString("\n", postfix = "\n"))
    }
}
sourceSets.main { resources.srcDir(prepareDesktopReadingPlans) }

tasks.named("check") { dependsOn("verifyDesktopVersion") }
tasks.register("verifyDesktopVersion") {
    group = "verification"
    description = "Checks desktopPackageVersion on in-memory property sets."
    doLast {
        fun props(vararg pairs: Pair<String, String>) = Properties().apply { pairs.forEach { (k, v) -> setProperty(k, v) } }
        check(desktopPackageVersion(props("versionMajorMinor" to "6.0", "versionCode" to "1230")) == "6.0.1230")
        for ((label, bad) in listOf(
            "missing versionCode" to props("versionMajorMinor" to "6.0"),
            "missing versionMajorMinor" to props("versionCode" to "1230"),
            "non-integer versionCode" to props("versionMajorMinor" to "6.0", "versionCode" to "abc"),
        )) {
            val threw = runCatching { desktopPackageVersion(bad) }.isFailure
            check(threw) { "desktopPackageVersion should throw for $label" }
        }
    }
}

// A relocatable tar.gz of the createDistributable app image. eachFile below keeps the executable bits, so jcef_helper
// stays executable.
val desktopTarball by tasks.registering(Tar::class) {
    group = "distribution"
    description = "Packs the app image into build/compose/binaries/main/andbible-<version>-linux-x64.tar.gz."
    val image = tasks.named("createDistributable")
    dependsOn(image)
    doFirst { if (hostTarget != "linux-x64") throw GradleException("desktopTarball is Linux x64 only (this host is $hostTarget)") }
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    compression = Compression.GZIP
    // Gradle's copy spec writes 0644 for every file unless told otherwise: carry the executable bit over explicitly.
    eachFile { if (file.canExecute()) permissions { unix("rwxr-xr-x") } }
    doLast {
        val listing = ProcessBuilder("tar", "-tvzf", archiveFile.get().asFile.absolutePath).redirectErrorStream(true).start()
            .inputStream.bufferedReader().readLines()
        val helperLine = listing.singleOrNull { it.endsWith("/kcef-natives/jcef_helper") }
        check(helperLine != null && helperLine.startsWith("-rwx")) { "jcef_helper is not executable in the tarball: $helperLine" }
    }
    archiveFileName.set("andbible-$desktopVersion-linux-x64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main"))
}

/**
 * jpackage copies app resources without their file modes, so the natives that must be executable (`jcef_helper`,
 * `cef_server`, ...) arrive as 0644. Gives every file under [target] the executable bits it has in [source] (the staged
 * natives). Done at package time so the app never has to chmod anything at run time.
 */
fun restoreNativeModes(source: File, target: File) {
    source.walkTopDown().filter { it.isFile && it.canExecute() }.forEach { src ->
        val dst = File(target, src.relativeTo(source).path)
        if (dst.isFile) dst.setExecutable(true, false)
    }
}

// The jpackage app image layout differs per OS: <name>/lib/app on Linux, <name>/app on Windows.
val appImageAppDir = if (isWindowsHost) "compose/binaries/main/app/AndBible/app" else "compose/binaries/main/app/andbible/lib/app"
val appImageNatives = layout.buildDirectory.dir("$appImageAppDir/resources/kcef-natives")
val appImageCfg = layout.buildDirectory.file(if (isWindowsHost) "$appImageAppDir/AndBible.cfg" else "$appImageAppDir/andbible.cfg")
tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
    doLast {
        // Windows has no executable bit: jcef_helper.exe runs as it is.
        if (!isWindowsHost) restoreNativeModes(kcefNativesDir.get().asFile, appImageNatives.get().asFile)
        val helper = File(appImageNatives.get().asFile, if (isWindowsHost) "jcef_helper.exe" else "jcef_helper")
        check(helper.isFile && helper.canExecute()) { "jcef_helper is missing or not executable in the app image ($helper)" }
        val cfg = appImageCfg.get().asFile
        check(cfg.isFile) { "No app image .cfg at $cfg" }
        // A build-machine path here would beat the resources dir on the user's machine (CefNativesLocation).
        check("kcef.installDir" !in cfg.readText()) { "$cfg must not contain kcef.installDir" }
    }
}

// packageDeb runs its own jpackage, so its .deb is unpacked, given the executable bits back and rebuilt under the same
// name. The Version field is then checked against version.properties, so the .deb can never drift from the tarball.
tasks.matching { it.name == "packageDeb" || it.name == "packageReleaseDeb" }.configureEach {
    doLast {
        val debDir = layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile
        val deb = debDir.listFiles { f -> f.name.endsWith(".deb") }?.singleOrNull()
            ?: throw GradleException("Expected exactly one .deb in $debDir")
        fun run(vararg cmd: String): String {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) throw GradleException("${cmd.joinToString(" ")} failed:\n$out")
            return out.trim()
        }
        val work = layout.buildDirectory.dir("deb-fix").get().asFile.apply { deleteRecursively(); mkdirs() }
        val unpacked = File(work, "pkg")
        run("dpkg-deb", "-R", deb.absolutePath, unpacked.absolutePath)
        // D3b: replace the jpackage-registered launcher, not a second desktop menu entry.
        val desktopTemplate = layout.projectDirectory.file("packaging/linux/andbible.desktop").asFile
        val desktopEntry = File(unpacked, "opt/andbible/lib/andbible-andbible.desktop")
        check(desktopEntry.isFile) { "Missing jpackage desktop entry: $desktopEntry" }
        desktopEntry.writeText(desktopTemplate.readText())
        check("Exec=/opt/andbible/bin/andbible %F" in desktopEntry.readText())
        check("MimeType=application/zip;application/epub+zip;font/ttf;text/csv;image/jpeg;image/png;image/webp;application/vnd.sqlite3;" in desktopEntry.readText())
        check("Icon=/opt/andbible/lib/andbible.png" in desktopEntry.readText())
        check(File(unpacked, "opt/andbible/lib/andbible.png").isFile)
        check(unpacked.walkTopDown().count { it.isFile && it.extension == "desktop" } == 1)
        check("andbible-andbible.desktop" in File(unpacked, "DEBIAN/postinst").readText())
        val natives = File(unpacked, "opt/andbible/lib/app/resources/kcef-natives")
        check(natives.isDirectory) { "No kcef-natives in the .deb ($natives)" }
        restoreNativeModes(kcefNativesDir.get().asFile, natives)
        // jpackage appends a release to the version ("6.0.1230-1"); the package version is exactly version.properties.
        val control = File(unpacked, "DEBIAN/control")
        control.writeText(control.readText().replace(Regex("(?m)^Version: .*$"), "Version: $desktopVersion"))
        val fixed = File(work, "andbible_${desktopVersion}_amd64.deb")
        run("dpkg-deb", "--root-owner-group", "-Zxz", "--build", unpacked.absolutePath, fixed.absolutePath)
        deb.delete()
        val finalDeb = File(debDir, fixed.name)
        Files.move(fixed.toPath(), finalDeb.toPath(), StandardCopyOption.REPLACE_EXISTING)
        work.deleteRecursively()
        val version = run("dpkg-deb", "-f", finalDeb.absolutePath, "Version")
        check(version == desktopVersion) { "The .deb Version is $version, expected $desktopVersion" }
        val helperLine = run("dpkg-deb", "-c", finalDeb.absolutePath).lines().singleOrNull { it.endsWith("/kcef-natives/jcef_helper") }
        check(helperLine != null && helperLine.startsWith("-rwxr-xr-x")) { "jcef_helper is not executable in the .deb: $helperLine" }
    }
}

// Windows x64: the app image (AndBible.exe, its runtime and the windows-x64 CEF natives) and a zip of it. Built on
// Windows only: jpackage cannot cross-build, and the natives are the windows-x64 JBR build. Unsigned (W3 decides that).
val createWindowsAppImage by tasks.registering {
    group = "distribution"
    description = "Builds the Windows x64 app image build/compose/binaries/main/app/AndBible (AndBible.exe)."
    dependsOn("createDistributable")
    doFirst { if (hostTarget != "windows-x64") throw GradleException("createWindowsAppImage needs a Windows x64 host (this host is $hostTarget)") }
}
val packageWindowsZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packs the Windows app image into build/compose/binaries/main/andbible-<version>-windows-x64.zip."
    dependsOn(createWindowsAppImage)
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("andbible-$desktopVersion-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main"))
    doFirst { if (hostTarget != "windows-x64") throw GradleException("packageWindowsZip needs a Windows x64 host (this host is $hostTarget)") }
    doLast {
        val names = ZipFile(archiveFile.get().asFile).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        for (required in listOf("AndBible/AndBible.exe", "AndBible/app/AndBible.cfg", "AndBible/app/resources/kcef-natives/libcef.dll",
            "AndBible/app/resources/kcef-natives/jcef_helper.exe", "AndBible/runtime/bin/java.dll")) {
            check(required in names) { "$required is missing from ${archiveFile.get().asFile.name}" }
        }
    }
}


val windowsMsixStage = layout.buildDirectory.dir("windows-msix/staging")
val generateWindowsManifest by tasks.registering {
    val template = layout.projectDirectory.file("packaging/windows/AppxManifest.xml.template")
    val icon = layout.projectDirectory.file("packaging/linux/andbible.png")
    inputs.files(template, icon)
    inputs.property("identity", windowsIdentity)
    inputs.property("version", windowsPackageVersion)
    outputs.dir(windowsMsixStage)
    doLast {
        val stage = windowsMsixStage.get().asFile.apply { mkdirs() }
        var manifest = template.asFile.readText()
        (windowsIdentity + ("VERSION" to "$windowsPackageVersion.0")).forEach { (key, value) ->
            val escaped = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
            manifest = manifest.replace("@$key@", escaped)
        }
        File(stage, "AppxManifest.xml").writeText(manifest)
        val source = javax.imageio.ImageIO.read(icon.asFile) ?: error("Cannot read app icon")
        val assets = File(stage, "Assets").apply { mkdirs() }
        for ((name, size) in listOf("StoreLogo" to 50, "Square150x150Logo" to 150, "Square44x44Logo" to 44)) {
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(source, 0, 0, size, size, null)
            graphics.dispose()
            check(javax.imageio.ImageIO.write(image, "png", File(assets, "$name.png")))
        }
    }
}
tasks.named<Test>("test") { dependsOn(generateWindowsManifest) }

fun windowsSdkTool(name: String): String {
    val sdk = System.getenv("WindowsSdkDir")?.let(::File)
        ?: File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Windows Kits\\10")
    val bin = File(sdk, "bin")
    return bin.listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("[0-9]+(\\.[0-9]+)+")) }
        .sortedByDescending { it.name.split('.').joinToString("") { part -> part.padStart(8, '0') } }
        .map { File(it, "x64/$name.exe") }.firstOrNull { it.isFile }?.absolutePath
        ?: throw GradleException("Windows SDK $name.exe not found under $bin; install Windows SDK desktop tools")
}

fun runWindowsTool(command: List<String>) {
    val process = ProcessBuilder(command).inheritIO().start()
    check(process.waitFor() == 0) { "Windows packaging tool failed: ${command.first()}" }
}

fun signWindowsArtifact(file: File) {
    val cert = System.getenv("ANDBIBLE_WIN_CERT")
    val password = System.getenv("ANDBIBLE_WIN_CERT_PASSWORD")
    if (cert.isNullOrBlank() && password.isNullOrBlank()) {
        logger.lifecycle("${file.name}: unsigned")
    } else {
        require(!cert.isNullOrBlank() && !password.isNullOrBlank()) { "Both ANDBIBLE_WIN_CERT and ANDBIBLE_WIN_CERT_PASSWORD are required" }
        runWindowsTool(listOf(windowsSdkTool("signtool"), "sign", "/fd", "SHA256", "/f", cert, "/p", password, file.absolutePath))
    }
}

tasks.register("packageWindowsMsix") {
    group = "distribution"
    dependsOn("createWindowsAppImage", generateWindowsManifest)
    doLast {
        check(hostTarget == "windows-x64") { "Windows x64 is required" }
        val stage = layout.buildDirectory.dir("windows-msix/package").get().asFile
        sync {
            from(windowsMsixStage)
            from(layout.buildDirectory.dir("compose/binaries/main/app/AndBible"))
            into(stage)
        }
        val output = layout.buildDirectory.file("compose/binaries/main/andbible-$windowsPackageVersion-windows-x64.msix").get().asFile
        output.parentFile.mkdirs()
        runWindowsTool(listOf(windowsSdkTool("makeappx"), "pack", "/o", "/d", stage.absolutePath, "/p", output.absolutePath))
        signWindowsArtifact(output)
    }
}
tasks.register("packageWindowsMsi") {
    group = "distribution"
    dependsOn("packageMsi")
    doLast {
        check(hostTarget == "windows-x64") { "Windows x64 is required" }
        val dir = layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile
        val msi = dir.listFiles { f -> f.extension.equals("msi", true) }?.singleOrNull()
            ?: throw GradleException("Expected exactly one MSI in $dir")
        signWindowsArtifact(msi)
    }
}
