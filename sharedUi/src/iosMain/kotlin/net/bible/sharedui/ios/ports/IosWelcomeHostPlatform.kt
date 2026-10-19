@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedui.assembly.IosStringTableHolder
import kotlinx.coroutines.launch
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.welcome.WelcomeFlowCore
import net.bible.sharedui.app.welcome.WelcomeHostPlatform
import net.bible.sharedui.assembly.iosStringTable
import net.bible.sharedui.icons.AbIcons
import net.bible.sharedui.strings.keyed.StringTable
import org.jetbrains.compose.resources.painterResource
import org.koin.core.Koin
import platform.Foundation.NSBundle

/** iOS device seams around the same Welcome flow used by Android. */
@kotlin.native.HiddenFromObjC
class IosWelcomeHostPlatform(
    private val koin: Koin,
    private val scope: CoroutineScope,
    private val table: StringTable = iosStringTable("en"),
    private val initialise: suspend () -> Unit,
    private val navigate: (String) -> Unit,
) : WelcomeHostPlatform {
    private val environment get() = koin.get<AppEnvironment>()
    private val discrete get() = environment.isDiscrete
    override val appName: String get() = table.text(if (discrete) "app_name_calculator" else "app_name_long")
    override val windowTitle: String get() = appName
    override val logo: @Composable () -> Painter? = {
        painterResource(AbIcons.identityIconResources.getValue(if (discrete) "ic_calculator_color" else "ic_logo"))
    }

    private val core by lazy {
        WelcomeFlowCore(
            versionText = {
                table.fmt("version_text", NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "")
            },
            supportedFormatsText = {
                val zip = table.fmt("format_zip", table.text(if (discrete) "app_name_calculator" else "app_name_andbible"))
                table.fmt("supported_formats", "$zip, ${table.text("format_mybible")}, ${table.text("format_mysword")}, ${table.text("format_epub")}")
            },
            language = { koin.getOrNull<IosStringTableHolder>()?.current?.tag ?: table.tag }, isDiscrete = { discrete },
            knownInstalled = { DatabaseContainer.openForUse().repoDb.swordDocumentInfoDao().getKnownInstalled() },
            // IosAppEnvironment uses NSURLVolumeAvailableCapacityForImportantUsageKey.
            megabytesFree = { environment.megabytesFree },
            refuseDownload = { koin.get<UserNotifier>().showError(koin.get<CoreStrings>().storageSpaceWarning) },
            initialise = initialise,
            ensureSyncLoaded = { koin.get<DocumentSyncSettings>().ensureLoaded() },
            navigate = navigate,
            bookLabel = { table.fmt("something_with_parenthesis", it.name, it.language) },
        )
    }
    private val controllerLazy = lazy {
        core.controller.also { scope.launch { refreshInfo() } }
    }
    override val controller get() = controllerLazy.value
    suspend fun refreshInfo() = core.refreshInfo()
    fun refreshTextIfCreated() {
        if (controllerLazy.isInitialized()) core.refreshTextIfCreated()
    }

    /** Bound after AppHosts exists; resolving the platform itself must not construct another host graph. */
    var openImport: () -> Unit = {}
    var afterRestore: () -> Unit = {}
    private var leaving = false
    private fun launchLeave(action: suspend () -> Unit) {
        if (leaving) return
        leaving = true
        scope.launch {
            try { action() }
            catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
            catch (e: Throwable) { net.bible.sharedcore.log.Log.e("IosWelcome", "Leaving Welcome failed", e) }
            finally { leaving = false }
        }
    }
    override fun onDownload() = launchLeave { core.onDownload() }
    override fun onEasyStart() = launchLeave { core.onEasyStart() }
    override fun onImport() = launchLeave { core.initialiseThen(openImport) }
    override fun onRestore() {
        scope.launch {
            val host = koin.get<BackupHost>()
            net.bible.sharedui.app.welcome.restoreWelcomeBackup(
                pick = { host.pickBackupFile() },
                restore = { koin.get<BackupControl>().restoreAppDatabaseFromSourceWithUI(host, it) },
                reportError = { message, cause -> host.showErrorReport(message, cause) },
                fallbackError = { table.text("error_occurred") },
                afterRestore = { afterRestore() },
            )
        }
    }
    override fun onRedownload() { scope.launch { core.onRedownload() } }
    override fun onOpenHomepage() = koin.get<ExternalLinks>().open(table.text("homepage"))
    override fun onOpenGithub() = koin.get<ExternalLinks>().open(table.text("github_page"))
    @Composable
    override fun Dialogs() = core.RedownloadDialog(scope)
}
