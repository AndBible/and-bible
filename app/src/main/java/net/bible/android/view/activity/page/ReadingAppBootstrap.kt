/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.page

import android.Manifest
import android.content.Context
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.event.UserMessages
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import net.bible.service.common.betaIntroVideo
import net.bible.service.common.newFeaturesIntroVideo
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import net.bible.service.sword.SwordDocumentFacade
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.max
import kotlin.system.exitProcess

const val DEFAULT_SYNC_INTERVAL = 5*60L // 5 minutes

// Bump this when introducing additional sync targets to re-trigger the
// "new sync targets available" notice for users who already dismissed it.
private const val NEW_SYNC_TARGETS_ANNOUNCE_VERSION = 1

/**
 * The scope the cloud-sync calls are launched on. Process-wide and file-private-by-convention
 * (`internal`, same package as its two callers): moved here VERBATIM from `MainBibleActivity.kt`,
 * where it was a top-level `private val` in the same package, so every existing call site
 * (`syncScope.launch { ... }` in `MainBibleActivity`'s app-to-background handler (now the host's [CurrentActivityHolder.appPositionChanges] subscription)) resolves to
 * the same object it always did.
 */
internal val syncScope = CoroutineScope(Dispatchers.IO)

/**
 * The request code `requestSdcardPermission` sends and `NavHostComposeActivity.onRequestPermissionsResult`
 * reads (slice 8 M2; classic `MainBibleActivity` read it before that). It lives at package level
 * rather than in either class because the request half moved here and the result half did not -- see
 * [ReadingAppBootstrap.requestSdcardPermission].
 */
internal const val SDCARD_READ_REQUEST = 2

private const val REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref"

/**
 * The app bootstrap, lifted out of `MainBibleActivity.onCreate` -- reading-host re-typing Task R7
 * (design spec §3.4, `2026-09-16-compose-reading-host-retyping`).
 *
 * §2.3 measured eight responsibilities that `MainBibleActivity.onCreate` performs on behalf of the
 * whole app rather than on behalf of the reading view: `CommonUtils.prepareData()`, the
 * `WindowRepository`'s creation, the first-run notices, the `openLink` deep-link branch, the network
 * callback, the soft-keyboard mode, the sdcard permission request and the cloud-sync loop.
 * `NavHostComposeActivity` had NONE of them. They are extracted ONCE here and called by both hosts,
 * rather than copied into the second one: two copies of the `WindowRepository` creation is exactly
 * the silent duplication §2.3 warns about, because `WindowControl`'s lazy fallback hands out an
 * uninitialised repository instead of crashing. One shared unit cannot duplicate, and when
 * `MainBibleActivity` is deleted its caller simply disappears.
 *
 * **The host type is [ActivityBase], not `ReadingHostActivity`'s reading-view surface** -- an app
 * entry point is not the same thing as a reading host, and nothing here is reading chrome.
 * [ActivityBase] is the nearest common supertype of the two hosts and is what most of the measured
 * code needs: `window` ([setSoftKeyboardMode]'s `SOFT_INPUT_ADJUST_*` calls),
 * `checkSelfPermission`/`requestPermissions` ([requestSdcardPermission]), a `Context` for
 * `getString` (every notice's texts -- Task 28 moved the notices themselves off `AlertDialog.Builder`
 * onto [AppDialogController.await]), `lifecycleScope` (the repository's scope), and -- the
 * binding constraint -- `ErrorReportControl.checkCrash`, `CommonUtils.checkPoorTranslations` and
 * `CloudSync.signIn` all take an `ActivityBase` parameter, so a `ComponentActivity` would not
 * compile without exactly the downcast to the concrete Activity that this batch forbids.
 *
 * **F59 fix round 1 widened `host`'s type to `T where T : ActivityBase, T : ReadingHostActivity`**
 * (still no concrete-Activity downcast anywhere -- both hosts genuinely ARE both).
 * [setSoftKeyboardMode] needs a PER-HOST threshold (`host.appOwnsImeInsetFromSdk`,
 * `ReadingHostActivity`'s member) rather than the single SDK constant it used before: see that
 * member's kdoc for why a shared constant would have regressed `MainBibleActivity` on API 30-34. A
 * second constructor parameter carrying just that `Int` was considered and rejected -- it would read
 * `host.appOwnsImeInsetFromSdk` at CONSTRUCTION time (`ReadingAppBootstrap(this)` runs from each
 * host's property initializer, before that host's own `override val appOwnsImeInsetFromSdk`
 * necessarily has a value in every possible initialization order) instead of at each call to
 * [setSoftKeyboardMode], and would let the bootstrap's copy silently disagree with a future host
 * that computed the threshold dynamically. The generic bound reads it fresh, off the live host,
 * exactly like every other `host.*` access in this class.
 *
 * **Order is load-bearing and is preserved by the CALLERS, not by this class.** Nothing here calls
 * anything else here; each member is a step the host's `onCreate` invokes in classic's original
 * sequence. In particular `ReadingHostPresence.setForeground(this)` +
 * `ReadingViewVisibility.setActivityVisible(this, true)` must still run before
 * [prepareData] and long before [openDeepLink] -- see the 14-line comment that survives at the top
 * of `MainBibleActivity.onCreate` for why (`openDeepLink` -> `windowControl.showLink` ->
 * `setKey(addHistoryItem = true)` calls `HistoryManager.recordIfCreated` synchronously, and with the flag false that
 * records a WRONG `IntentHistoryItem` carrying the deep-link intent).
 *
 * **The network callback is balanced (T8a item 4; R7 recorded it as a known leak and deferred the
 * fix).** [networkCallback] is registered by [registerNetworkCallback] and removed by
 * [unregisterNetworkCallback], which BOTH hosts call from their own `onDestroy`. Before T8a there
 * was no `unregisterNetworkCallback` call anywhere in the repository and had never been one; R7 was
 * right to preserve that inside an extraction commit and wrong to leave it after T8b, whose boot
 * handoff keeps `FLAG_ACTIVITY_MULTIPLE_TASK` for `ACTION_VIEW` deep links -- so every deep link
 * would spawn another host, another bootstrap and another registration nothing removed.
 *
 * Unregistering is safe with two live hosts BECAUSE the callback is per-bootstrap and the bootstrap
 * is per-host: [networkCallback] is an instance property of this class, each host constructs its own
 * `ReadingAppBootstrap(this)`, and `ConnectivityManager.unregisterNetworkCallback(cb)` removes the
 * one object it is handed. The `ConnectivityManager` itself is the process-wide system service --
 * which is exactly why it is the callback's IDENTITY, not the manager's, that keeps two hosts apart.
 * `ReadingAppBootstrapTest.destroyingOneHostLeavesTheOtherLiveHostsCallbackRegistered` measures it.
 */
class ReadingAppBootstrap<T>(private val host: T) : KoinComponent where T : ActivityBase, T : ReadingHostActivity {
    private val windowControl: WindowControl by inject()
    private val appDialogs: AppDialogController by inject()
    private val preferences get() = CommonUtils.settings
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val TAG get() = host.TAG

    /**
     * The repository this bootstrap created, and the one its host publishes as its own
     * `windowRepository`. It is a settable `var` (not a private-set one) because
     * `MainBibleActivity.windowRepository` is a settable `var` today and seven of the batch's
     * untouchable Robolectric classes assign it directly after `controller.create()`; the Activity's
     * property is a view onto THIS field, so those assignments keep reaching the same one object
     * [synchronize] and [lastTouched] read.
     */
    lateinit var windowRepository: WindowRepository

    /**
     * Whether the host is paused -- [networkCallback]'s `onAvailable` gate, classic's
     * `MainBibleActivity.paused`. The host writes it from `onPause`/`onResume`; it is NOT derived
     * from `host.lifecycle` because the two are not the same instant (classic sets the flag BEFORE
     * `super.onPause()`/`super.onResume()`, and it is `false` from construction, while the lifecycle
     * registry is not `RESUMED` until after `super.onResume()`) and a network callback that arrives
     * inside that window would behave differently.
     */
    var hostPaused = false

    // ——— §2.3 row 2: data preparation ————————————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.onCreate:514`. Genuinely idempotent -- `CommonUtils.prepareData`
     * is guarded by a persisted `"data-version"` setting -- and this is its only call site in the
     * repository, so calling it from two Activities is safe.
     */
    fun prepareData() {
        CommonUtils.prepareData()
    }

    // ——— §2.3 row 1: the WindowRepository ————————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.onCreate:530-532`, verbatim including the ORDER of the three
     * statements: construct, publish to [WindowControl], then `initialize()`. `initialize()` ->
     * `loadFromDb` posts events synchronously, and anything that reads `windowControl.windowRepository`
     * while they unwind must already see this instance.
     *
     * `WindowRepository.initialize()` is idempotent per INSTANCE only (`if(initialized ||
     * loadingFromDb) return`, where `initialized` reads that instance's own `_activeWindow`); there
     * is no cross-instance guard at all, and the repository binds to the creating host's
     * `lifecycleScope`. So this creates ONE repository per host -- exactly today's behaviour, where
     * two live `MainBibleActivity` instances already create two. This batch does not change that.
     *
     * **Idempotent per host (R7 fix round 1, review Important 3).** A second call returns the
     * repository this host already has instead of replacing it. Nothing calls it twice today -- each
     * host calls it exactly once, which `ReadingAppBootstrapTest` pins by counting call sites -- but
     * a site count cannot see one site EXECUTED twice, and that is the one shape of this task's
     * silent defect it would miss: the replacement would not crash, it would strand every
     * collaborator holding the first repository on a workspace `WindowControl` no longer publishes.
     */
    fun createWindowRepository(): WindowRepository {
        if (this::windowRepository.isInitialized) return windowRepository
        windowRepository = WindowRepository(host.lifecycleScope)
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        return windowRepository
    }

    // ——— §2.3 row 3: the first-run notices ———————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.onCreate:566-581` -- the whole once-per-process gated block, minus
     * its `lifecycleScope.launch(Dispatchers.Main)` wrapper, which stays at the call site so the
     * host keeps deciding when in its own `onCreate` this runs.
     */
    suspend fun showFirstRunNotices() {
        if(!initialized) {
            requestSdcardPermission()
            ErrorReportControl.checkCrash(host)
            if(!CommonUtils.checkPoorTranslations(host)) exitProcess(2)
            showBetaNotice()
            showStableNotice()
            showNewSyncTargetsNotice()
            if(!CommonUtils.isDiscrete) {
                UserMessages.toast(windowRepository.name)
            }
            checkDocBackupDBInSync()
        }
        initialized = true
    }

    /**
     * Checks if the list of documents installed matches the list of
     * books in the backup database.
     *
     * Backup database is used to allow user to quickly reinstall all
     * available books if moving to a new device.
     */
    private fun checkDocBackupDBInSync() {
        val docs = SwordDocumentFacade.documents
        val knownInstalled = docDao.getKnownInstalled()
        if (knownInstalled.isEmpty()) {
            Log.i(TAG, "There is at least one Bible, but Bible Backup DB is empty, populate with first time books");
            val allDocs = docs.map {
                SwordDocumentInfo(it.initials, it.name, it.abbreviation, it.language.name, it.getProperty(DownloadManager.REPOSITORY_KEY) ?: "")
            }
            docDao.insert(allDocs)
        } else {
            knownInstalled.forEach {
                Log.i(TAG, "The ${it.name} is installed")
            }
        }
    }

    /**
     * `internal`, not `private`: its only production caller is `showFirstRunNotices()`, which also
     * runs `checkCrash`/`checkPoorTranslations` (can `exitProcess`) and two other notices ahead of
     * it -- `ReadingAppBootstrapSyncNoticeTest` calls this directly instead of reconstructing that
     * whole sequential chain.
     */
    internal fun showNewSyncTargetsNotice() {
        val displayedVer = preferences.getInt("new-sync-targets-notice-displayed", 0)
        if (displayedVer >= NEW_SYNC_TARGETS_ANNOUNCE_VERSION) return

        // The notice is only relevant to users who already use device sync. Suppress
        // it for everyone else so that users who enable sync later don't see a stale
        // announcement about targets that aren't new to them.
        if (!CommonUtils.isCloudSyncEnabled) {
            preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
            return
        }

        appDialogs.post(
            AppDialogRequest.Confirm(
                title = host.getString(R.string.new_sync_targets_notice_title),
                message = host.getString(R.string.new_sync_targets_notice_message),
                confirmText = host.getString(R.string.open_settings),
                dismissText = host.getString(R.string.dismiss),
                cancellable = false,
            ),
        ) { result ->
            preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
            if (result == AppDialogResult.Ok) {
                ScreenLauncher.open(host, Screen.SyncSettings)
            }
        }
    }

    /**
     * Ported from a `suspendCoroutine`-wrapped `AlertDialog.Builder` to [AppDialogController.await]
     * (Task 28) -- same resume values (`true` only on "dismiss until update", which also persists
     * `ver`; `false` on plain dismiss/back) and the same texts/links, now structured as
     * [AppDialogRequest.Notice] blocks instead of one hand-assembled `Spanned` string. The big
     * centred logo (`imageStr`/`biggerLogoDrawable`) is [AppDialogRequest.NoticeBlock.Logo], hidden
     * in discrete mode. Fix batch 6 A6: that now follows [CommonUtils.isDiscrete] (flavor OR the
     * `discrete_mode` preference), the body names the calculator ([stableNoticeAppName]), and the
     * SEPARATE title-bar logo is the calculator's ([noticeLogoRes], resolved in `AppDialogOverlay`).
     */
    internal suspend fun showStableNotice(): Boolean {
        if (CommonUtils.isBeta) return false

        val ver = CommonUtils.mainVersion
        val displayedVer = preferences.getString("stable-notice-displayed", "")
        Log.i(TAG, "showStableNotice: $displayedVer $ver")
        if (displayedVer == ver) return false

        val videoMessage = host.getString(R.string.upgrade_video_message, CommonUtils.mainVersion)
        val appName = host.getString(stableNoticeAppName(CommonUtils.isDiscrete))
        val par1 = host.getString(R.string.stable_notice_par1, CommonUtils.mainVersion, appName)
        val buy = host.getString(R.string.buy_development)
        val support = host.getString(R.string.buy_development2)

        val result = appDialogs.await(
            AppDialogRequest.Notice(
                title = host.getString(R.string.stable_notice_title),
                showTitleLogo = true,
                blocks = listOfNotNull(
                    AppDialogRequest.NoticeBlock.Html(par1),
                    if (CommonUtils.isDiscrete) null else AppDialogRequest.NoticeBlock.Logo,
                    AppDialogRequest.NoticeBlock.Html("<big><a href=\"$newFeaturesIntroVideo\"><b>$videoMessage</b></a></big>"),
                    AppDialogRequest.NoticeBlock.IconLine(
                        AppDialogRequest.NoticeIcon.Money,
                        "&nbsp;<small><a href=\"$buyDevelopmentLink\">$support ($buy)</a></small>",
                    ),
                ),
                confirmText = host.getString(R.string.beta_notice_dismiss_until_update),
                neutralText = host.getString(R.string.dismiss),
            ),
        )
        return if (result == AppDialogResult.Ok) {
            Log.i(TAG, "showStableNotice: saving $ver")
            preferences.setString("stable-notice-displayed", ver)
            true
        } else {
            false
        }
    }

    /** Ported the same way as [showStableNotice] -- see its kdoc. */
    internal suspend fun showBetaNotice(): Boolean {
        if (!shouldShowBetaNotice(CommonUtils.isBeta, CommonUtils.isDiscrete)) return false

        val announceVersion = 3
        val displayedVer = preferences.getInt("beta-notice-displayed2", 0)
        if (displayedVer >= announceVersion) return false

        val videoMessage = host.getString(R.string.upgrade_video_message, CommonUtils.mainVersion)
        val videoMessageLink = "<a href=\"${betaIntroVideo}\"><b>$videoMessage</b></a>"

        val par1 = host.getString(R.string.beta_notice_content_1)
        val par2 = host.getString(R.string.beta_notice_content_2,
             " <a href=\"https://github.com/AndBible/and-bible/issues\">"
                + "${host.getString(R.string.beta_notice_github_issues)}</a>"
        )
        val par3 = host.getString(R.string.beta_notice_content_3,
            " <a href=\"https://github.com/AndBible/and-bible\">"
                + "${host.getString(R.string.beta_notice_github)}</a>"

        )
        val extraMessage = """
            |<b>DEVELOPER'S SPECIAL NOTICE FOR BETA TESTERS (April 2026)</b><br><br>
            |Welcome to the 5.1 beta! Many new features have landed, including:<br>
            |<br>
            |• <b>AI assistant</b> with tool calling and support for multiple
            | providers (Claude, Grok, OpenRouter, OpenAI-compatible and more).<br>
            |• <b>Reading &amp; memorization progress tracking</b> with multiple
            | modes: Word Scramble, Word Order, Word Blur and Type It.<br>
            |• <b>My Documents</b>: your own editable, syncable pages.<br>
            |• <b>Multi-translation search</b> across several Bibles at once.<br>
            |• Many more improvements &mdash; see the "What's new" video above.<br>
            |<br>
            |Please test and report any bugs via
            |<a href="https://github.com/AndBible/and-bible/issues/new/choose">GitHub</a>
            | or Main Menu &rarr; Report a bug.<br>
            |<br>
            |Best regards, Tuomas<br><br>
            |P.S. You can support AndBible development financially by
            |<a href="$buyDevelopmentLink">sponsoring development hours</a>.
            | <br><br>
            | (Standard beta notice below)
        """.trimMargin()
        val htmlMessage = "$extraMessage<br><br>$videoMessageLink<br><br>$par1<br><br> $par2<br><br> $par3 <br><br> <i>${host.getString(R.string.version_text, CommonUtils.applicationVersionName)}</i>"

        val result = appDialogs.await(
            AppDialogRequest.Notice(
                title = host.getString(R.string.beta_notice_title),
                showTitleLogo = true,
                blocks = listOf(AppDialogRequest.NoticeBlock.Html(htmlMessage)),
                confirmText = host.getString(R.string.beta_notice_dismiss_until_update),
                neutralText = host.getString(R.string.dismiss),
            ),
        )
        return if (result == AppDialogResult.Ok) {
            preferences.setInt("beta-notice-displayed2", announceVersion)
            true
        } else {
            false
        }
    }

    // ——— §2.3 row 4: the deep link ———————————————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.openLink` (`:2318-2356` at the batch's baseline), reached from the
     * `intent.hasExtra("openLink")` branch `StartupActivity.gotoMainBibleActivity` populates on an
     * `ACTION_VIEW` launch. Renamed from `openLink` on the way out: `CommonUtils.openLink` and
     * `BibleView.openLink` are two different, unrelated functions of that name already.
     */
    fun openDeepLink(uri: Uri) {
        when (uri.host) {
            "read.andbible.org" -> {
                val key = CommonUtils.parseAndBibleReference(uri) ?: return
                windowControl.showLink(key.document, key)
            }
            "stepbible.org" -> {
                val qParam = uri.getQueryParameter("q") ?: return

                val docRegex = Regex("""version=([^&|]+)""")
                val refRegex = Regex("""reference=([^&|]+)""")

                val versionMatch = docRegex.find(qParam)
                val version = if (versionMatch != null) versionMatch.groups[1]?.value else null
                val doc = if (version != null) Books.installed().getBook(version) else null

                val defV11n = if (doc is SwordBook) doc.versification else KJVA
                val v11nStr = uri.getQueryParameter("v11n")
                val v11n = if (v11nStr == null) defV11n else Versifications.instance().getVersification(v11nStr) ?: defV11n

                val refMatch = refRegex.find(qParam) ?: return
                val keyStr = refMatch.groups[1]?.value ?: return

                val key = PassageKeyFactory.instance().getKey(v11n, keyStr)
                windowControl.showLink(doc, key)
            }
            "www.bible.com" -> {
                val urlRegex = Regex("""/(\w+)/bible/(\w+)/([\w\d]+)\.(\d+)\.(\w+)""")
                val match = urlRegex.find(uri.path.toString()) ?: return
                val book = match.groups[3]?.value ?: return
                val chapter = match.groups[4]?.value?.toInt() ?: return
                val docStr = match.groups[5]?.value
                val doc = if (docStr != null) Books.installed().getBook(docStr) else null
                val defV11n = if (doc is SwordBook) doc.versification else KJVA

                val key = VerseFactory.fromString(defV11n, "$book.$chapter")!!
                windowControl.showLink(doc, key)
            }
        }
    }

    // ——— §2.3 row 5: the network callback ————————————————————————————————————————————————————————

    var networkAvailable: Boolean = false

    val networkCallback = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            networkAvailable = true
            if (!hostPaused) {
                syncScope.launch { startSync() }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            networkAvailable = false
            stopPeriodicSync()
        }
    }

    /**
     * Whether [registerNetworkCallback] actually registered, so [unregisterNetworkCallback] can be
     * called unconditionally from a host's `onDestroy`.
     *
     * Not cosmetic: `registerDefaultNetworkCallback` is gated on API >= N, a host may be destroyed
     * without ever having bootstrapped a reading route at all (`NavHostComposeActivity` has ~45
     * routes that never call [registerNetworkCallback]), and `unregisterNetworkCallback` throws
     * `IllegalArgumentException` for a callback that was never registered.
     */
    private var networkCallbackRegistered = false

    /** Classic `MainBibleActivity.onCreate`'s network-callback block, which R7 extracted here (so
     *  the range that citation used to name no longer exists in that file); its surviving CALL site
     *  is `MainBibleActivity.kt:604`. Balanced by [unregisterNetworkCallback]. */
    fun registerNetworkCallback() {
        val connManager = host.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    /**
     * The other end of [registerNetworkCallback] (T8a item 4), called from each host's `onDestroy`.
     *
     * Removes THIS bootstrap's own [networkCallback] object and nothing else, which is what makes it
     * safe while a second host is still alive -- see the class KDoc for the per-host/shared analysis
     * and the test that measures it. Idempotent, so a second `onDestroy` (or a host that never
     * registered) is a no-op rather than an `IllegalArgumentException`.
     */
    fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        networkCallbackRegistered = false
        val connManager = host.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connManager.unregisterNetworkCallback(networkCallback)
    }

    // ——— §2.3 rows 6 and 7: keyboard mode and the sdcard permission ——————————————————————————————

    /**
     * Classic `MainBibleActivity.requestSdcardPermission`.
     *
     * **Answered by `NavHostComposeActivity.onRequestPermissionsResult` since slice 8 (finding M2).**
     * Before that only `MainBibleActivity` implemented the result half, so on the flipped host a DENY
     * never ran `turnOffManualInstallFolderSetting()` and the dialog returned on every Settings exit.
     */
    fun requestSdcardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val requestSdCardPermission = preferences.getBoolean(REQUEST_SDCARD_PERMISSION_PREF, false)
            if (requestSdCardPermission && host.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                host.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), SDCARD_READ_REQUEST)
            }
        }
    }

    fun setSoftKeyboardMode() {
        // F59 (T9 walk, spec §3.1.1): the design's "one mode on every API level" was gated on
        // whether `WindowInsetsCompat.Type.ime()` is visible to Compose under `ADJUST_NOTHING` below
        // API 30 (Task B0's gate). It was measured NEGATIVE on a container API 28 emulator: under
        // `adjustResize` (today) the insets listener reports `ime.bottom=685` and
        // `ReadingInsets.imeHeight` becomes 685; under `adjustNothing` the listener is never even
        // DISPATCHED for the IME and both `ReadingInsets.imeHeight` and Compose's own
        // `WindowInsets.ime` stay 0 forever -- `WindowInsetsCompat` below API 30 synthesises
        // `Type.ime()` from the system-window inset, which only carries the keyboard while the
        // framework is actually resizing the window for it, and `ADJUST_NOTHING` stops it resizing.
        //
        // The threshold below is `host.appOwnsImeInsetFromSdk`. `MainBibleActivity` was deleted in
        // `47dc2f73f`, so there is one host; the value is a member only so this call and the nav
        // host's own window-mode call and insets-listener gate read the same number. See
        // `ReadingHostActivity.appOwnsImeInsetFromSdk`'s kdoc.
        if (Build.VERSION.SDK_INT >= host.appOwnsImeInsetFromSdk) {
            host.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        } else if (windowControl.isMultiWindow) {
            host.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        } else {
            host.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    // ——— §2.3 row 8: the cloud-sync loop —————————————————————————————————————————————————————————

    private var syncJob: Job? = null

    suspend fun startSync() {
        if(CommonUtils.isCloudSyncEnabled) {
            synchronize(true)
            if(syncJob != null) {
                Log.e(TAG, "syncJob already exists")
            } else {
                syncJob = host.lifecycleScope.launch { periodicSync() }
            }
        }
    }

    private suspend fun periodicSync() {
        Log.i(TAG, "Periodic sync starting")
        while (CommonUtils.isCloudSyncEnabled && syncJob?.isCancelled == false) {
            delay(60*1000) // 1 minute
            if(syncJob?.isCancelled == false) synchronize()
        }
    }

    private val lastTouched: Long get() {
        return windowRepository.windowList.mapNotNull { it.bibleView?.lastTouched }.max()
    }

    private val syncInterval get() =
        CommonUtils.settings.getLong("cloud_sync_interval", DEFAULT_SYNC_INTERVAL) * 1000
    private val lastSynchronized get() =
        CommonUtils.settings.getLong("globalLastSynchronized", 0L)

    private val now get() = System.currentTimeMillis()

    suspend fun synchronize(force: Boolean = false) {
        if (DatabaseContainer.replacing) return
        if(CommonUtils.isCloudSyncEnabled && networkAvailable) {
            windowRepository.saveIntoDb(false)
            if (force || (now - max(lastSynchronized, lastTouched) > syncInterval && CloudSync.hasChanges())) {
                Log.i(TAG, "Performing periodic sync")
                if(!CloudSync.signedIn) {
                    CloudSync.signIn(host)
                }
                CloudSync.start()
                CloudSync.waitUntilFinished()
            }
        }
    }

    fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    companion object {
        /**
         * The once-per-process gate on [showFirstRunNotices], classic
         * `MainBibleActivity.Companion.initialized`. Process-wide, not per host: the notices are
         * "have we shown these to the user since the app started", which does not become two
         * questions because there are two Activities.
         */
        var initialized = false
    }
}

/** Fix batch 6 A6 (surface 1): the app name the stable notice's body uses -- the calculator's when [discrete]. */
@StringRes
internal fun stableNoticeAppName(discrete: Boolean): Int =
    if (discrete) R.string.app_name_calculator else R.string.app_name_long

/** Fix batch 6 A6 (surface 2): the logo in the notices' title bar -- the calculator's when [discrete]. */
@DrawableRes
internal fun noticeLogoRes(discrete: Boolean): Int =
    if (discrete) R.drawable.ic_calculator_color else R.drawable.ic_logo

/**
 * Fix batch 6 A6 (surface 4): the beta notice is English-only and links github.com/AndBible, so it is
 * never shown when [discrete] (the maintainer's decision, spec §0).
 */
internal fun shouldShowBetaNotice(isBeta: Boolean, discrete: Boolean): Boolean = isBeta && !discrete
