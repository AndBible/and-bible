// Optional WebKitGTK desktop app: the real app (AppMainKt) on the system WebKitGTK, packaged as its own .deb.
// The D5 proof of concept stays runnable through the runPoc task. No CEF natives are staged or packaged here.
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

/** `<versionMajorMinor>.<versionCode>` from the root `version.properties`, as in `:desktopApp` (a .deb needs numeric dotted). */
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
val desktopReleaseKind = providers.gradleProperty("desktopReleaseKind").orElse("dev").get()
val debPackageName = "andbible-webkitgtk"
// Compose passes the project version to jpackage as `--app-version` on Linux.
version = desktopVersion

compose.desktop {
    application {
        mainClass = "net.bible.desktop.webkitgtk.AppMainKt"
        // Same unshrunk jars as :desktopApp (JNI and reflection: Room, Koin).
        buildTypes.release.proguard.isEnabled.set(false)
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
            .get().metadata.installationPath.asFile.absolutePath
        // JNA's Native.getComponentID and the heavyweight canvas need these; the AWT GTK3 file dialog must never load.
        jvmArgs += listOf(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED",
            "-Dsun.awt.disableGtkFileDialogs=true",
            "-Dandbible.version=$desktopVersion",
            "-Dandbible.versionCode=${rootVersionProperties.getProperty("versionCode").trim()}",
            "-Dandbible.releaseKind=$desktopReleaseKind",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = debPackageName
            packageVersion = desktopVersion
            description = "Offline Bible study app (system WebKitGTK)"
            vendor = "AndBible"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE.txt"))
            // The same jlink module set as :desktopApp.
            modules("java.instrument", "java.management", "jdk.unsupported", "jdk.crypto.ec", "java.net.http", "jdk.localedata", "jdk.security.auth")
            linux {
                iconFile.set(rootProject.layout.projectDirectory.file("desktopApp/packaging/linux/andbible.png"))
                // jpackage combines the vendor with this email to form the Maintainer field.
                debMaintainer = "help.andbible@gmail.com"
                menuGroup = "Education"
                appCategory = "Education"
            }
        }
    }
}

// The D5 proof of concept (the previous default main class), with its andbible.poc.* scenario properties.
val runPoc by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Runs the D5 WebKitGTK proof of concept (MainKt)."
    mainClass.set("net.bible.desktop.webkitgtk.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED")
    // andbible.poc.* (scenario, dark, spike, themeFlipMs, overlay) reach the app as system properties.
    listOf("scenario", "dark", "spike", "themeFlipMs", "overlay").forEach { key ->
        providers.gradleProperty("andbible.poc.$key").orNull?.let { jvmArgs("-Dandbible.poc.$key=$it") }
    }
}

// packageDeb runs its own jpackage; its .deb is unpacked, given the AndBible launcher entry, the WebKitGTK dependency and
// the exact version, rebuilt under the same name, and then checked. These checks are the packaging gate.
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
        // Replace the jpackage-registered launcher, not a second desktop menu entry. The file name is jpackage's, so
        // discover it instead of assuming it.
        val libDir = File(unpacked, "opt/$debPackageName/lib")
        val desktopEntries = libDir.listFiles { f -> f.isFile && f.name.endsWith(".desktop") }.orEmpty()
        check(desktopEntries.size == 1) { "Expected exactly one jpackage .desktop file in $libDir, found ${desktopEntries.map { it.name }}" }
        val desktopEntry = desktopEntries.single()
        val desktopTemplate = layout.projectDirectory.file("packaging/linux/org.andbible.AndBible.WebKitGtk.desktop").asFile
        desktopEntry.writeText(desktopTemplate.readText())
        val desktopText = desktopEntry.readText()
        check("Exec=/opt/$debPackageName/bin/$debPackageName %F" in desktopText)
        check("Icon=/opt/$debPackageName/lib/$debPackageName.png" in desktopText)
        check(File(libDir, "$debPackageName.png").isFile) { "Missing icon $debPackageName.png in $libDir" }
        check(unpacked.walkTopDown().count { it.isFile && it.extension == "desktop" } == 1)
        // The system WebKitGTK is the engine; keep jpackage's generated dependencies and add this one once.
        // jpackage appends a release to the version ("6.0.1230-1"); the package version is exactly version.properties.
        val control = File(unpacked, "DEBIAN/control")
        var controlText = control.readText().replace(Regex("(?m)^Version: .*$"), "Version: $desktopVersion")
        val dependsLine = Regex("(?m)^Depends: (.*)$").find(controlText)
        val webkitDep = "libwebkitgtk-6.0-4"
        controlText = if (dependsLine == null) {
            controlText.trimEnd('\n') + "\nDepends: $webkitDep\n"
        } else if (dependsLine.groupValues[1].split(',').any { it.trim() == webkitDep }) {
            controlText
        } else {
            controlText.replace(dependsLine.value, "Depends: ${dependsLine.groupValues[1].trim()}, $webkitDep".replace("Depends: , ", "Depends: "))
        }
        control.writeText(controlText)
        val fixed = File(work, "${debPackageName}_${desktopVersion}_amd64.deb")
        run("dpkg-deb", "--root-owner-group", "-Zxz", "--build", unpacked.absolutePath, fixed.absolutePath)
        deb.delete()
        val finalDeb = File(debDir, fixed.name)
        Files.move(fixed.toPath(), finalDeb.toPath(), StandardCopyOption.REPLACE_EXISTING)
        work.deleteRecursively()

        fun field(name: String) = run("dpkg-deb", "-f", finalDeb.absolutePath, name)
        check(field("Package") == debPackageName) { "Unexpected .deb Package: ${field("Package")}" }
        check(field("Version") == desktopVersion) { "The .deb Version is ${field("Version")}, expected $desktopVersion" }
        check(field("Maintainer") == "AndBible <help.andbible@gmail.com>") { "Unexpected .deb Maintainer: ${field("Maintainer")}" }
        val depends = field("Depends").split(',').map { it.trim() }
        check(depends.count { it == webkitDep } == 1) { "Depends must contain $webkitDep exactly once: $depends" }
        val listing = run("dpkg-deb", "-c", finalDeb.absolutePath).lines()
        val paths = listing.map { it.substringAfterLast(" ./") }
        for (banned in listOf("libcef", "jcef_helper", "kcef-natives")) {
            check(paths.none { banned in it }) { "The .deb must not contain $banned: ${paths.filter { banned in it }}" }
        }
        check(paths.count { it.endsWith(".desktop") } == 1) { "Expected exactly one .desktop file in the .deb" }
        val launcher = listing.singleOrNull { it.endsWith(" ./opt/$debPackageName/bin/$debPackageName") }
        check(launcher != null && launcher.substringBefore(' ').let { it.length >= 10 && it[3] == 'x' }) {
            "The launcher opt/$debPackageName/bin/$debPackageName is missing or not executable: $launcher"
        }
        // The packaged .cfg is what the installed launcher runs with.
        val cfgOut = run("sh", "-c", "dpkg-deb --fsys-tarfile '${finalDeb.absolutePath}' | tar -xO ./opt/$debPackageName/lib/app/$debPackageName.cfg")
        check("app.mainclass=net.bible.desktop.webkitgtk.AppMainKt" in cfgOut) { "Wrong main class in the packaged .cfg:\n$cfgOut" }
        check("-Dsun.awt.disableGtkFileDialogs=true" in cfgOut) { "Missing disableGtkFileDialogs in the packaged .cfg" }
        check("sun.awt.X11" in cfgOut && "--add-opens" in cfgOut) { "Missing sun.awt.X11 add-opens in the packaged .cfg" }
        check("kcef.installDir" !in cfgOut) { "The packaged .cfg must not set kcef.installDir" }
    }
}
