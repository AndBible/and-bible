@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.probe

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import net.bible.sharedcore.platform.IosAppEnvironment
import net.bible.sharedcore.platform.cloud.IosUbiquityContainer
import net.bible.sharedcore.platform.cloud.iCloudDriveStore
import net.bible.sharedcore.platform.files.*
import net.bible.sharedcore.platform.secrets.IosKeychainSecretStore
import net.bible.sharedcore.platform.speak.*
import net.bible.sharedui.platform.IosPlatformBootstrap
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/**
 * Debug-only consumer of the iOS platform ports, dispatched before the PoC navigation host.
 * English literals are intentional diagnostic output, never shown in release builds. No account
 * credentials are read: the Keychain round trip uses only the controlled `probe` key.
 * Speech, focus and media access stays on Main; filesystem work runs on IO.
 */
@Composable
fun PlatformProbeScreen(darkTheme: Boolean = isSystemInDarkTheme()) {
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateMapOf<String, String>() }
    val probe = remember {
        ProbeServices(scope) { section, message ->
            logs[section] = ((logs[section]?.plus("\n") ?: "") + message).takeLast(6000)
        }
    }
    var busy by remember { mutableStateOf(false) }
    var releaseUpload by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    DisposableEffect(probe) { onDispose { probe.close() } }

    fun action(section: String, block: suspend () -> Unit): () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    probe.log(section, "Failed: ${error.message}")
                } finally {
                    busy = false
                }
            }
        }
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        Surface {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Platform probe (Debug)", style = MaterialTheme.typography.headlineSmall) }
                item {
                    ProbeAction("Speak", logs["Speech"], !busy, action("Speech") { probe.speak() })
                    Button(onClick = action("Speech") { probe.rate(0.7f) }, enabled = !busy) { Text("Rate 0.7x") }
                    Button(onClick = action("Speech") { probe.rate(1.4f) }, enabled = !busy) { Text("Rate 1.4x") }
                    Button(onClick = action("Speech") { probe.pause("Flush") }, enabled = !busy) { Text("Flush queue") }
                }
                item { ProbeAction("Media session", logs["Media"], !busy, action("Media") { probe.media() }) }
                item { ProbeAction("Keychain round trip", logs["Keychain"], !busy, action("Keychain") { probe.keychain() }) }
                item { ProbeAction("Environment dump", logs["Environment"], !busy, action("Environment") { probe.environment() }) }
                item { ProbeAction("Pick file", logs["Picker"], !busy, action("Picker") { probe.pick() }) }
                item { ProbeAction("Share generated text", logs["Share"], !busy, action("Share") { probe.deliver(SaveOrShare.SHARE) }) }
                item { ProbeAction("Save generated text", logs["Save"], !busy, action("Save") { probe.deliver(SaveOrShare.SAVE) }) }
                item {
                    ProbeAction("iCloud round trip and delete", logs["Cloud"], !busy, action("Cloud") { probe.cloud(null) })
                    Button(enabled = !busy, onClick = action("Cloud") {
                        val release = CompletableDeferred<Unit>()
                        releaseUpload = release
                        try { probe.cloud(release) } finally { releaseUpload = null }
                    }) { Text("iCloud: retain until second-device check") }
                    Button(enabled = releaseUpload != null, onClick = { releaseUpload?.complete(Unit) }) {
                        Text("Finish device check and delete upload")
                    }
                }
                item {
                    ProbeAction("Schedule background refresh", logs["Background"], !busy, action("Background") {
                        IosPlatformBootstrap.backgroundRefresh.schedule(60)
                        probe.log("Background", "Schedule requested (60 seconds); inspect system log for scheduling errors. Pause in Xcode, run the command, then resume; observe the I1 system log for 'background refresh fired'.\n" +
                            "e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@\"org.andbible.ios.remote-sync-refresh\"]")
                    })
                }
            }
        }
    }
}

@Composable
private fun ProbeAction(title: String, result: String?, enabled: Boolean, onClick: () -> Unit) {
    Column {
        Button(onClick = onClick, enabled = enabled) { Text(title) }
        Text(result ?: "Not run")
    }
}

private const val FIRST_VERSE = "In the beginning God created the heaven and the earth."
private const val SECOND_VERSE = "And the earth was without form, and void."
private const val PROBE_TEXT = "AndBible platform probe\n"

/** Owns the delegates and observers for the composition; callbacks are serialized onto Main. */
private class ProbeServices(
    private val scope: CoroutineScope,
    private val append: (String, String) -> Unit,
) {
    private val fs = FileSystem.SYSTEM
    private val secrets = IosKeychainSecretStore()
    private val environment = IosAppEnvironment(secrets, isDiscrete = { false }, isDebugMode = true)
    private val focus = IosAudioFocus()
    private val session = IosMediaSession()
    private val picker = IosFilePicker()
    private val delivery = IosFileDelivery()
    private val container = IosUbiquityContainer()
    private val cloud = iCloudDriveStore(container)
    private val earcons = mapOf(
        "[pre-footnote]" to "short_pling",
        "[post-footnote]" to "short_pling_reverse",
        "[pre-title]" to "pageflip",
        "[pre-chapter-change]" to "medium_pling",
        "[pre-book-change]" to "long_pling",
    ).mapNotNull { (name, file) ->
        NSBundle.mainBundle.pathForResource(file, "wav", "earcons")?.let { name to it }
    }.toMap()
    private val engine = IosSpeakEngine(object : SpeakEngineListener {
        override fun onInit(success: Boolean) = log("Speech", "Init: $success")
        override fun onUtteranceStart(utteranceId: String) = log("Speech", "Start: $utteranceId")
        override fun onUtteranceDone(utteranceId: String) = log("Speech", "Done: $utteranceId")
        override fun onUtteranceError(utteranceId: String) = log("Speech", "Error: $utteranceId")
    }, earcons)

    fun log(section: String, message: String) {
        scope.launch(Dispatchers.Main.immediate) { append(section, message) }
    }

    fun rate(value: Float) {
        engine.setSpeechRate(value)
        log("Speech", "Rate: ${value}x (applies to following utterances)")
    }

    fun pause(reason: String) {
        engine.flush()
        session.setPlaybackState(MediaPlaybackState.PAUSED)
        log("Speech", "$reason: queue flushed; explicit Play/Speak restarts the sample")
    }

    private fun requestFocus(): Boolean {
        val granted = focus.requestAudioFocus { gained ->
            scope.launch(Dispatchers.Main.immediate) {
                log("Speech", "Audio focus: $gained")
                // Conservative probe policy: loss flushes; regain never resumes automatically.
                if (!gained) pause("Focus lost")
            }
        }
        log("Speech", "Focus request: $granted; bundled earcons: ${earcons.keys}")
        return granted
    }

    fun speak() {
        if (!requestFocus()) return
        check(earcons.size == 5) { "Missing bundled earcons/ WAV resources" }
        engine.flush()
        log("Speech", "Language en: ${engine.setLanguage("en")}")
        engine.playEarcon("[pre-title]", 1f, "title")
        engine.speakText(FIRST_VERSE, "Genesis 1:1")
        engine.playSilence(700, "silence-700ms")
        engine.playEarcon("[pre-chapter-change]", 1f, "chapter")
        engine.speakText(SECOND_VERSE, "Genesis 1:2")
        session.setPlaybackState(MediaPlaybackState.PLAYING)
    }

    fun media() {
        fun command(name: String, action: () -> Unit) {
            scope.launch(Dispatchers.Main.immediate) {
                try {
                    log("Media", "Command: $name")
                    action()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { log("Media", "Failed: ${error.message}") }
            }
        }
        session.activate(object : MediaCommands {
            override fun play() = command("play", ::speak)
            override fun pause() = command("pause") { pause("Remote pause") }
            override fun stop() = command("stop") {
                engine.flush()
                session.setPlaybackState(MediaPlaybackState.STOPPED)
                focus.abandonAudioFocus()
            }
            override fun next() = command("next") { sample(SECOND_VERSE) }
            override fun previous() = command("previous") { sample(FIRST_VERSE) }
            override fun fastForward() = command("fastForward") { sample(SECOND_VERSE) }
            override fun rewind() = command("rewind") { sample(FIRST_VERSE) }
        })
        session.setNowPlaying("Genesis 1:1", "Genesis")
        session.setPlaybackState(MediaPlaybackState.PLAYING)
        log("Media", "Active: Genesis 1:1 / Genesis / PLAYING. Play restarts; pause flushes.")
    }

    private fun sample(text: String) {
        // This diagnostic has no seek model: all navigation commands replace the sample.
        if (!requestFocus()) return
        engine.flush()
        engine.setLanguage("en")
        engine.speakText(text, "remote-sample")
        session.setPlaybackState(MediaPlaybackState.PLAYING)
    }

    suspend fun keychain() = withContext(Dispatchers.IO) {
        try {
            secrets.put("probe", "platform-probe")
            log("Keychain", "Read back: ${secrets.get("probe")}")
        } finally { secrets.remove("probe") }
    }

    suspend fun environment() = withContext(Dispatchers.IO) {
        log("Environment", with(environment) {
            "initialized=$isInitialized discrete=$isDiscrete beta=$isBeta debug=$isDebugMode\n" +
                "app=$applicationNameMedium package=$packageName version=$versionNumber\n" +
                "metered=$isMeteredNetwork freeMB=$megabytesFree\n" +
                "tmp=$tmpDir cache=$cacheDir backup=$dbBackupPath modules=$modulesDir\n" +
                "database=${databasePath("probe")} lastCrash=$lastCrashTime apk=$apkPath\n" +
                "deviceIdentifier omitted (persistent private identifier)"
        })
    }

    suspend fun pick() {
        val picked = picker.pickBackupFile() ?: return log("Picker", "Cancelled or unavailable")
        try {
            val result = withContext(Dispatchers.IO) {
                val size = fs.metadata(picked.token.toPath()).size
                val source = picker.openInput(picked)
                // The sniffer owns and closes the source, including error paths.
                "${picked.displayName}: $size bytes; ${BackupFileSniffer.sniff { source }}"
            }
            log("Picker", result)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { fs.delete(picked.token.toPath()) }
        }
    }

    private suspend fun <T> temporaryFile(block: suspend (Path) -> T): T {
        val directory = NSTemporaryDirectory().toPath() / "platform-probe-${NSUUID().UUIDString}"
        try {
            val path = withContext(Dispatchers.IO) {
                fs.createDirectories(directory)
                (directory / "probe.txt").also { fs.write(it) { writeUtf8(PROBE_TEXT) } }
            }
            return block(path)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { fs.deleteRecursively(directory) }
        }
    }

    suspend fun deliver(mode: SaveOrShare) = temporaryFile { path ->
        val result = delivery.deliver(path.toString(), "probe.txt", mode, "text/plain", "text/plain",
            "Platform probe", null, "Platform probe")
        log(if (mode == SaveOrShare.SAVE) "Save" else "Share", "$result")
    }

    suspend fun cloud(release: CompletableDeferred<Unit>?) {
        log("Cloud", "Resolving iCloud container...")
        if (container.awaitReady() == null) return log("Cloud", "Container unavailable after initialization; signedIn=${cloud.signedIn}")
        temporaryFile { path ->
            withContext(Dispatchers.IO) {
                log("Cloud", "signedIn=${cloud.signedIn}")
                val folder = cloud.createNewFolder("probe")
                val name = "probe-${(NSDate().timeIntervalSince1970 * 1000).toLong()}.txt"
                val uploaded = cloud.upload(name, path, folder.id)
                try {
                    log("Cloud", "Uploaded ${uploaded.id}; list=${cloud.listFiles(listOf(folder.id)).map { it.name }}")
                    val downloaded = Buffer()
                    cloud.download(uploaded.id, downloaded)
                    check(downloaded.readUtf8() == PROBE_TEXT) { "Downloaded contents differ" }
                    log("Cloud", "Download matches. This is a local round trip, not proof of propagation.")
                    if (release != null) {
                        log("Cloud", "Retained ${uploaded.id}. Observe on the second device, then tap Finish device check and delete upload. Leaving the screen also deletes it.")
                        release.await()
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { cloud.delete(uploaded.id) }
                    log("Cloud", "Deleted ${uploaded.id}")
                }
            }
        }
    }

    fun close() {
        engine.shutdown()
        session.release()
        focus.abandonAudioFocus()
        environment.close()
        scope.cancel()
    }
}
