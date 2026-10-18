/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.common

import net.bible.sharedcore.platform.files.BackupFileSniffer
import okio.Path.Companion.toOkioPath
import okio.source
import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.URLSpan
import android.util.LayoutDirection
import net.bible.sharedcore.log.Log
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import net.bible.android.BibleApplication
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.BuildConfig.BUILD_TYPE
import net.bible.android.activity.BuildConfig.CommitDate
import net.bible.android.activity.BuildConfig.FLAVOR_appearance
import net.bible.android.activity.BuildConfig.FLAVOR_distchannel
import net.bible.android.activity.BuildConfig.GitHash
import net.bible.android.activity.R
import net.bible.android.activity.SpeakWidgetManager
import net.bible.android.common.toV11n
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.android.control.event.UserMessages
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.GlobalTextDisplaySettings
import net.bible.android.database.InheritedFrom
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.database.bookmarks.BookmarkType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.LabelType
import net.bible.android.database.json
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.getEnumSet
import net.bible.sharedcore.platform.setEnumSet
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.koin.java.KoinJavaComponent
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.buyDevelopmentLink
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.blockingAtEdge
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DataBaseNotReady
import net.bible.service.device.speak.TextToSpeechNotificationManager
import net.bible.service.download.DownloadManager
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordEnvironmentInitialisation
import net.bible.service.sword.epub.addManuallyInstalledEpubBooks
import net.bible.service.sword.esword.addManuallyInstalledESwordBooks
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.sword.mybible.addManuallyInstalledMyBibleBooks
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mysword.addManuallyInstalledMySwordBooks
import net.bible.service.sword.csvprompt.addManuallyInstalledCsvPromptBooks
import net.bible.service.sword.ttf.addManuallyInstalledTtfBooks
import org.apache.commons.lang3.StringUtils
import org.crosswire.common.util.IOUtil
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.GenBookBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseKey
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import fi.sykero.kdom.input.build
import org.spongycastle.util.io.pem.PemReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.IndexOutOfBoundsException
import java.security.KeyFactory
import java.security.Signature
import java.util.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle

@Suppress("DEPRECATION")
fun htmlToSpan(html: String?): Spanned {
    val spanned = SpannableString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    } else {
        Html.fromHtml(html)
    })

    class MyURLSpan(url: String): URLSpan(url) {
        override fun onClick(widget: View) {
            CommonUtils.openLink(url)
        }
    }

    val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
    for (s in urlSpans) {
        val start = spanned.getSpanStart(s)
        val end = spanned.getSpanEnd(s)
        spanned.removeSpan(s)
        val newSpan = MyURLSpan(s.url)
        spanned.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return spanned
}

const val promoAndNewFeaturesPlaylistAutostart = "https://www.youtube.com/watch?v=f2cf6-7liMo&list=PLD-W_Iw-N2MlOXgRTLQqoXZpQxkqf119a&index=1"
// "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MlOXgRTLQqoXZpQxkqf119a" // What's new 4.0 playlist

const val windowsAndWorkspacesPlaylist = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ" // 4.0 (Window's and workspaces playlist)
const val labelsAndBookmarksPlaylist = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mnv8aYRK3QbZBjE3ZMmrJZ7" // 4.0 (Labels and Bookmarks playlist)
const val notesAndStudyPadsPlayList= "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m" // 4.0 (playlist for notes & study pads)
const val speakPlayList = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Ml4arSb_fDBYqgiYtVPmjFo" // playlist for speak related tutorials

const val windowPinningVideo = windowsAndWorkspacesPlaylist
const val studyPadsVideo = notesAndStudyPadsPlayList
const val workspacesVideo = windowsAndWorkspacesPlaylist

const val betaIntroVideo = "https://youtu.be/EKnyBIti9Fk" //promoAndNewFeaturesPlaylistAutostart
const val newFeaturesIntroVideo = "https://youtu.be/Gm-JLot1uf4" // What's new in 5.1

const val speakHelpVideo = speakPlayList
const val automaticSpeakBookmarkingVideo = speakPlayList


open class CommonUtilsBase : KoinComponent {
    val windowControl: WindowControl by inject()
    val speakControl: SpeakControl by inject()
    val bibleTraverser: BibleTraverser by inject()
}

enum class BibleViewSwipeMode {CHAPTER, PAGE, NONE}

class Ref<T>(var value: T? = null)

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */

/**
 * Fix batch 1 2.5b: the explicit state [CommonUtils.changeAppIconAndName] must write for one launcher
 * component, or null when the current state already matches. `COMPONENT_ENABLED_STATE_DEFAULT`
 * means "whatever the manifest says" -- a fresh install reports it for both aliases, and reading it
 * as a mismatch force-stopped every fresh install on its first Settings visit.
 */
internal fun componentStateToWrite(current: Int, manifestEnabled: Boolean, wantEnabled: Boolean): Int? {
    val effective = when (current) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestEnabled
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        else -> false
    }
    if (effective == wantEnabled) return null
    return if (wantEnabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}

object CommonUtils : CommonUtilsBase() {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private const val DEFAULT_MAX_TEXT_LENGTH = 250
    private const val ELLIPSIS = "..."

	val json get() = AppJson

    private const val TAG = "CommonUtils"
    var isAndroid = true
        private set

    // Backup of old databases will be stored here for 30 days
    val dbBackupPath get() = File(SharedConstants.internalFilesDir, "database_backups").apply { mkdirs() }

    val applicationNameMedium get() = application.getString(R.string.app_name_medium)

    val applicationVersionName: String
        get() {
            var versionName: String
            try {
                val manager = application.packageManager
                val info = manager.getPackageInfo(application.packageName, 0)
                versionName = info.versionName ?: throw NameNotFoundException()
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Error getting package name.", e)
                versionName = "Error"
            }

            return "$versionName#$GitHash $FLAVOR_distchannel $FLAVOR_appearance $BUILD_TYPE ($CommitDate)"
        }

    val mainVersion: String get() {
        val verFull = applicationVersionName
        val numbers = verFull.split(".")
        return "${numbers[0]}.${numbers[1]}"
    }

    val isRtl get(): Boolean {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == LayoutDirection.RTL
    }

    val mainVersionFloat: Float get() {
        return mainVersion.toFloat()
    }

    val isBeta: Boolean get() {
        val verFull = applicationVersionName
        val ver = verFull.split("#")[0]
        return ver.endsWith("-beta") || ver.endsWith("-alpha") || application.packageName.endsWith(".next") || isDebugMode
    }

    val applicationVersionNumber: Int
        get() {
            // TODO we have to change this to Long if we one day will have very long version numbers.
            var versionNumber: Int
            try {
                val manager = application.packageManager
                val info = manager.getPackageInfo(application.packageName, 0)
                versionNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else info.versionCode
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Error getting package name.", e)
                versionNumber = -1
            }

            return versionNumber
        }

    private val packageInfo: PackageInfo
        get () {
            val manager = application.packageManager
            return manager.getPackageInfo(application.packageName, 0)
        }

    val isFirstInstall get() = packageInfo.firstInstallTime == packageInfo.lastUpdateTime


    val isPortrait: Boolean get() {
        val res = CurrentActivityHolder.currentActivity?.resources?: BibleApplication.application.resources
        return res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    val megabytesFree: Long
        get() {
            val bytesAvailable = getFreeSpace(Environment.getExternalStorageDirectory().path)
            val megAvailable = bytesAvailable / 1048576
            Log.i(TAG, "Megs available on internal memory :$megAvailable")
            return megAvailable
        }

    private val store get() = DatabaseContainer.instance.settingsStore

    class AndBibleSettings : AppSettings {
        override fun getString(key: String, default: String?) = orDefaultIfDbNotReady(default) { store.getString(key, default) }
        override fun getLong(key: String, default: Long) = orDefaultIfDbNotReady(default) { store.getLong(key, default) }
        override fun getInt(key: String, default: Int) = orDefaultIfDbNotReady(default) { store.getLong(key, default.toLong()).toInt() }
        override fun getBoolean(key: String, default: Boolean) = if(initialized) store.getBoolean(key, default) else default
        override fun getDouble(key: String, default: Double) = orDefaultIfDbNotReady(default) { store.getDouble(key, default) }
        override fun getFloat(key: String, default: Float): Float = orDefaultIfDbNotReady(default) { store.getDouble(key, default.toDouble()).toFloat() }

        /**
         * Nav-graph slice 8 C3 (Review Focus #1): a read before the database exists answers [default]
         * instead of crashing. "Backup & restore" from `ErrorReportControl.checkCrash` runs before
         * `StartupActivity.initializeDatabase()`, and its host's theme reads `getDisplayColorMode`
         * through [getString]; `DatabaseContainer.instance` throws [DataBaseNotReady] there.
         *
         * The guard is on exactly that throw, NOT on [initialized] like [getBoolean]: the database is
         * already readable while `initialized` is still false, and those reads must keep their stored values.
         *  - [migrateOldSettingsKeys] runs inside the first `DatabaseContainer.instance`, before
         *    `initializeApp` sets `initialized`. Its secret migration reads [getString]; an
         *    `initialized` guard would return null and never migrate the old cloud-sync credentials.
         *  - `StartupActivity` sets `DatabaseContainer.ready` in `initializeDatabase()` long before
         *    `initializeAppCoroutine()`. The calculator, Welcome and crash-check screens in between
         *    read real settings, such as the theme's colour mode.
         * Setters stay unguarded: writing before the database exists is still a crash.
         */
        private inline fun <T> orDefaultIfDbNotReady(default: T, read: () -> T): T =
            try { read() } catch (e: DataBaseNotReady) { default }

        override fun setString(key: String, value: String?) = store.setString(key, value)
        override fun setLong(key: String, value: Long?) = store.setLong(key, value)
        override fun setInt(key: String, value: Int?) = store.setLong(key, value?.toLong())
        override fun setBoolean(key: String, value: Boolean?) = store.setBoolean(key, value)
        override fun setDouble(key: String, value: Double?) = store.setDouble(key, value)
        override fun setFloat(key: String, value: Float?) = store.setDouble(key, value?.toDouble())

        val displayColorMode: DisplayColorMode get() =
            DisplayColorMode.fromValue(getString("display_color_mode", null))
                ?: DisplayColorMode.defaultFor(isOnyxDevice)
        val monochromeMode: Boolean get() = displayColorMode != DisplayColorMode.NORMAL
        val colorEinkMode: Boolean get() = displayColorMode == DisplayColorMode.COLOR_EINK
        /** True only for MONOCHROME: pure black & white, stricter than [monochromeMode]. */
        val pureMonochromeMode: Boolean get() = displayColorMode == DisplayColorMode.MONOCHROME
        val themePaletteStyle: ThemePaletteStyle get() =
            ThemePaletteStyle.fromValue(getString(THEME_PALETTE_STYLE_KEY, null)) ?: ThemePaletteStyle.DEFAULT
        val einkMode: Boolean get() = getBoolean("eink_mode", false)
        val disableAnimations: Boolean get() = getBoolean("disable_animations", isOnyxDevice)
        val disableClickToEdit: Boolean get() = getBoolean("disable_click_to_edit", false)
        val hideStatusBar: Boolean get() = getBoolean("hide_status_bar", false)
        val notesContentType: String get() = getString("notes_content_type", "HTML") ?: "HTML"
        val fontSizeMultiplier: Int get() = getInt("font_size_multiplier", 100)
        val fontSizeMultiplierFloat: Float get() = getInt("font_size_multiplier", 100) / 100F
        val bibleViewSwipeMode: BibleViewSwipeMode get() = BibleViewSwipeMode.valueOf(getString("bible_view_swipe_mode", "CHAPTER")!!)
        
        // Experimental features - all default to disabled
        val enabledExperimentalFeatures: Set<String> get() = getStringSet("experimental_features", emptySet())
        fun isExperimentalFeatureEnabled(feature: String): Boolean = enabledExperimentalFeatures.contains(feature)
        val bookmarkEditActionsEnabled: Boolean get() = isExperimentalFeatureEnabled("bookmark_edit_actions")
        val addParagraphBreakEnabled: Boolean get() = isExperimentalFeatureEnabled("add_paragraph_break")


        /**
         * True when any LlmProviderConfig exists. Read on the UI thread, so it reads the app-wide
         * [net.bible.sharedcore.ai.PromptService.configured] snapshot (refreshed when the AI configuration changes)
         * instead of the database; [initializeAppCoroutine] awaits its first refresh.
         */
        val llmConfigured: Boolean
            get() = promptService.configured.value

    }

    private var _settings: AndBibleSettings? = null
    val settings: AndBibleSettings get() {
        val s = _settings
        if(s != null) return s
        return AndBibleSettings().apply { _settings = this }
    }

    /**
     * Global AI settings stored in the syncable AiSettingsDatabase.
     * Use this for agent permissions, excluded documents, commentary token limit, etc.
     */
    val aiSettings: net.bible.service.llm.AiSettings get() = org.koin.java.KoinJavaComponent.get(net.bible.service.llm.AiSettings::class.java)

    private val promptService: net.bible.sharedcore.ai.PromptService
        get() = org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.ai.PromptService::class.java)

    private val llmConfiguredSource = net.bible.sharedcore.event.EventSource<Unit>()
    private var llmBridgeJob: kotlinx.coroutines.Job? = null
    private var llmBridgeFor: net.bible.sharedcore.ai.PromptService? = null

    /**
     * Fires after [net.bible.sharedcore.ai.PromptService.configured] changed value, so a listener reading
     * [AndBibleSettings.llmConfigured] sees the new value (unlike `aiSettings.configChanged`, which fires before the
     * asynchronous refresh). Reading it starts the bridge; it restarts when the service or its scope was replaced.
     */
    val llmConfiguredChanges: net.bible.sharedcore.event.Events<Unit> get() {
        val service = promptService
        synchronized(llmConfiguredSource) {
            if (llmBridgeJob?.isActive != true || llmBridgeFor !== service) {
                llmBridgeJob?.cancel()
                llmBridgeFor = service
                var last = service.configured.value
                val scope = org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.AppCoroutineScope>(net.bible.sharedcore.platform.AppCoroutineScope::class.java)
                llmBridgeJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    service.configured.collect { if (it != last) { last = it; llmConfiguredSource.emit(Unit) } }
                }
            }
        }
        return llmConfiguredSource
    }

    internal suspend fun runEachCatching(vararg steps: suspend () -> Unit) {
        for (step in steps) {
            try { step() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Snapshot store step failed", e) }
        }
    }

    private val readingProgressSettingsStore: ReadingProgressSettingsStore
        get() = org.koin.java.KoinJavaComponent.get(ReadingProgressSettingsStore::class.java)

    /** The snapshot held by [GlobalTextDisplaySettingsStore]; loaded in [initializeAppCoroutine]. */
    var globalTextDisplaySettings: WorkspaceEntities.TextDisplaySettings
        get() = globalTextDisplaySettingsStore.current
        set(value) = globalTextDisplaySettingsStore.set(value)

    private val globalTextDisplaySettingsStore: GlobalTextDisplaySettingsStore
        get() = org.koin.java.KoinJavaComponent.get(GlobalTextDisplaySettingsStore::class.java)

    val localePref: String?
        get() = realSharedPreferences.getString("locale_pref", null)

    // Note: use AndBibleSettings always if possible to save preferences. They are persisted in DB.
    //
    // This `PreferenceManager` call is, since the Z-late epilogue, the only androidx.preference
    // reference left in CODE anywhere in the tree -- so it alone is what keeps the two
    // `androidx.preference` gradle dependencies (app/build.gradle.kts) earning their place. It is
    // not dead weight: the default SharedPreferences file it names is the one every pre-DB setting
    // was written to.
    //
    // Stated as "in code" on purpose, because the wider claim is false: four res/xml files
    // (settings, sync_settings, reading_progress_settings, prompt_advanced_settings) still use
    // androidx.preference element names as tags -- including three `MultiSelectListPreference`
    // tags the epilogue itself introduced, retagging the rows of the deleted
    // `InverseMultiSelectListPreference`. They are not runtime uses: nothing inflates those files
    // any more, and `SettingsIconParityTest` reads them with a raw `XmlResourceParser`, so no
    // androidx.preference class is ever loaded from them (spec 10.5, S12 decision D6). Deleting the
    // dependency would still not be safe, and not because of them -- because of the call below.
    val realSharedPreferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(application.applicationContext)

    val deviceIdentifier: String get() =
        Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
            ?: realSharedPreferences.getString("android_id", null).let {
                if(it == null) {
                    val id = UUID.randomUUID().toString()
                    realSharedPreferences.edit().putString("android_id", id).apply()
                    id
                } else { it}
            }

    init {
        try {
            if (Build.ID != null) {
                isAndroid = true
            }
        } catch (cnfe: Exception) {
            isAndroid = false
        }

        println("isAndroid:$isAndroid")
    }

    fun getShareableDocumentText(selection: net.bible.service.sword.ShareSelection, facade: SwordContentFacade): String = facade.getSelectionText(
        selection,
        showVerseNumbers = settings.getBoolean("share_verse_numbers", true),
        advertiseApp = settings.getBoolean("share_show_add", true),
        abbreviateReference = settings.getBoolean("share_abbreviate_reference", true),
        showNotes = settings.getBoolean("show_notes", true),
        showVersion = settings.getBoolean("share_show_version", true),
        showReference = settings.getBoolean("share_show_reference", true),
        showReferenceAtFront = settings.getBoolean("share_show_reference_at_front", true),
        showSelectionOnly = settings.getBoolean("show_selection_only", true),
        showEllipsis = settings.getBoolean("show_ellipsis", true),
        showQuotes = settings.getBoolean("share_show_quotes", false),
        separateVersesWithNewlines = settings.getBoolean("share_separate_verses_newlines", false)
    )

    fun getFreeSpace(path: String): Long {
        val stat = StatFs(path)
        val bytesAvailable = stat.availableBytes
        Log.i(TAG, "Free space :$bytesAvailable")
        return bytesAvailable
    }

    fun limitTextLength(text: String?, maxLength: Int = DEFAULT_MAX_TEXT_LENGTH, singleLine: Boolean = false): String? {
        var text = text
        if (text != null) {
            val origLength = text.length

            if (singleLine) {
                // get first line but limit length in case there are no line breaks
                text = StringUtils.substringBefore(text, "\n")
            }

            if (text!!.length > maxLength) {
                // break on a space rather than mid-word
                val cutPoint = text.indexOf(" ", maxLength)
                if (cutPoint >= maxLength) {
                    text = text.substring(0, cutPoint + 1)
                }
            }

            if (text.length != origLength) {
                text += ELLIPSIS
            }
        }
        return text
    }

    fun ensureDirExists(dir: File) {
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }
    }

    fun deleteDirectory(path: File): Boolean {
        Log.i(TAG, "Deleting directory:" + path.absolutePath)
        if (path.exists()) {
            if (path.isDirectory) {
                path.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                        Log.i(TAG, "Deleted " + file)
                    }
                }
            }
            val deleted = path.delete()
            if (!deleted) {
                Log.w(TAG, "Failed to delete:" + path.absolutePath)
            }
            return deleted
        }
        return false
    }

    fun loadProperties(propertiesFile: File): Properties {
        val properties = Properties()
        if (propertiesFile.exists()) {
            var `in`: FileInputStream? = null
            try {
                `in` = FileInputStream(propertiesFile)
                properties.load(`in`)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading properties", e)
            } finally {
                IOUtil.close(`in`)
            }
        }
        return properties
    }

    fun pause(seconds: Int) {
        pauseMillis(seconds * 1000)
    }

    fun pauseMillis(millis: Int) {
        try {
            Thread.sleep(millis.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Error sleeping", e)
        }

    }

    fun getSharedPreference(key: String, defaultValue: String): String? = settings.getString(key, defaultValue)
    fun saveSharedPreference(key: String, value: String) = settings.setString(key, value)

    fun getResourceString(resourceId: Int, vararg formatArgs: Any): String {
        return resources.getString(resourceId, *formatArgs)
    }

    fun getResourceInteger(resourceId: Int): Int {
        return resources.getInteger(resourceId)
    }

    fun getResourceBoolean(resourceId: Int): Boolean {
        return resources.getBoolean(resourceId)
    }

    val resources: Resources get() =
        CurrentActivityHolder.currentActivity?.resources?: application.resources


    fun getResourceColor(resourceId: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val theme = CurrentActivityHolder.currentActivity?.theme?: resources.newTheme().apply {
                applyStyle(R.style.AppTheme, true)
            }

            resources.getColor(resourceId, theme)
        } else {
            resources.getColor(resourceId)
        }

    fun getResourceDrawable(resourceId: Int, context: Context? = null): Drawable? {
        val theme = CurrentActivityHolder.currentActivity?.theme?: resources.newTheme().apply {
            applyStyle(R.style.AppTheme, true)
        }

        return ResourcesCompat.getDrawable(context?.resources?:resources, resourceId, theme)
    }

    fun getTintedDrawable(res: Int, color: Int = R.color.grey_500): Drawable {
        val d = getResourceDrawable(res)!!
        d.mutate().setTint(getResourceColor(color))
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        return d
    }

    /**
     * convert dip measurements to pixels
     */
    fun convertDipsToPx(dips: Float): Int {
        // Converts 14 dip into its equivalent px
        val scale = resources.displayMetrics.density
        return (dips * scale + 0.5f).toInt()
    }

    fun convertDipsToPx(dips: Int): Int = convertDipsToPx(dips.toFloat())

    /**
     * convert dip measurements to pixels
     */
    fun convertPxToDips(px: Int): Int {
        val scale = resources.displayMetrics.density
        return Math.round(px / scale)
    }

    /**
     * StringUtils methods only compare with a single char and hence create lots
     * of temporary Strings This method compares with all chars and just creates
     * one new string for each original string. This is to minimise memory
     * overhead & gc.
     *
     * @param str
     * @param removeChars
     * @return
     */
    fun remove(str: String, removeChars: CharArray): String? {
        if (StringUtils.isEmpty(str) || !StringUtils.containsAny(str, *removeChars)) {
            return str
        }

        val r = StringBuilder(str.length)
        // for all chars in string
        for (i in 0 until str.length) {
            val strCur = str[i]

            // compare with all chars to be removed
            var matched = false
            var j = 0
            while (j < removeChars.size && !matched) {
                if (removeChars[j] == strCur) {
                    matched = true
                }
                j++
            }
            // if current char does not match any in the list then add it to the
            if (!matched) {
                r.append(strCur)
            }
        }
        return r.toString()
    }

    /** format seconds duration as h:m:s
     *
     * @param secs duration
     * @return h:m:s
     */
    fun getHoursMinsSecs(secs: Long): String = net.bible.sharedcore.util.formatHoursMinsSecs(secs)

    fun getKeyDescription(key: Key): String = net.bible.service.history.keyDescription(key)

    fun getWholeChapter(currentVerse: Verse, showIntros: Boolean = true): VerseRange = net.bible.android.control.versification.wholeChapter(currentVerse, showIntros)

    fun getWholeChapters(v11n: Versification, book: BibleBook, chapter1: Int, chapter2: Int): VerseRange = net.bible.android.control.versification.wholeChapters(v11n, book, chapter1, chapter2)

    private val scope = CoroutineScope(Dispatchers.Default)

    private fun forceStopApp() {
        Log.i(TAG, "forceStopApp!")
        scope.launch {
            org.koin.java.KoinJavaComponent.get<CloudSync>(CloudSync::class.java).waitUntilFinished()
            DatabaseContainer.flushSettingsBeforeExit()
            exitProcess(2)
        }
    }

    private val lastDisplaySettings: List<WorkspaceEntities.TextDisplaySettings.Types> get() {
        val lastDisplaySettingsString = settings.getString("lastDisplaySettings", null)
        var lastTypes = mutableListOf<WorkspaceEntities.TextDisplaySettings.Types>()
        if(lastDisplaySettingsString!= null) {
            try {
                lastTypes = LastTypesSerializer.fromJson(lastDisplaySettingsString).types
            } catch (e: SerializationException) {
                Log.e(TAG, "Could not deserialize $lastDisplaySettingsString")
            }
        }
        return lastTypes
    }

    val lastDisplaySettingsSorted get() = lastDisplaySettings.sortedBy { it.name }

    fun displaySettingChanged(type: WorkspaceEntities.TextDisplaySettings.Types) {
        val lastTypes = lastDisplaySettings.toMutableList()
        lastTypes.remove(type)
        while (lastTypes.size >= 5) {
            lastTypes.removeAt(lastTypes.size - 1)
        }
        lastTypes.add(0, type)
        settings.setString("lastDisplaySettings", LastTypesSerializer(lastTypes).toJson())
    }

    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()

    suspend fun unlockDocument(context: AppCompatActivity, book: Book): Boolean {
        var repeat = true
        while (repeat) {
            val answer = dialogs.await(
                AppDialogRequest.TextInput(
                    title = application.getString(R.string.give_passphrase_for_module, book.initials),
                    message = null,
                    initial = book.unlockKey ?: "",
                    confirmText = application.getString(R.string.okay),
                    dismissText = application.getString(R.string.cancel),
                    neutralText = application.getString(R.string.show_unlock_info),
                    cancellable = false,
                    draftKey = "unlock:${book.initials}",
                ),
            )
            val passphrase: String? = when (answer) {
                is AppDialogResult.Text -> answer.value
                AppDialogResult.Neutral -> {
                    showAbout(context, book)
                    continue
                }
                else -> null
            }
            if (passphrase != null) {
                val success = book.unlock(passphrase)
                if (success) {
                    docDao.getBook(book.initials)?.apply {
                        cipherKey = passphrase
                        docDao.update(this)
                    }
                    return true
                }
            }
            repeat = dialogs.await(
                AppDialogRequest.Confirm(
                    title = application.getString(R.string.try_again_passphrase),
                    message = null,
                    confirmText = application.getString(R.string.yes),
                    dismissText = application.getString(R.string.no),
                    cancellable = false,
                ),
            ) == AppDialogResult.Ok
        }
        return false
    }

    /** about display is generic so handle it here
     */
    suspend fun showAbout(context: Context, document: Book) {
        var about = "<b>${document.name}</b>\n\n"
        val badDocument = document.bookMetaData.getProperty("BadDocument")
        if(badDocument != null) {
            about += "<b>${application.getString(R.string.warn_bad_document)}</b>\n\n"
        }
        about += document.bookMetaData.getProperty("About") ?: ""
        // either process the odd formatting chars in about
        about = about.replace("\\pard", "")
        about = about.replace("\\par", "\n")

        val shortPromo = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_PROMO)

        if(shortPromo != null) {
            about += "\n\n${shortPromo}"
        }

        // Copyright and distribution information
        val shortCopyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_COPYRIGHT)
        val copyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_COPYRIGHT)
        val distributionLicense = document.bookMetaData.getProperty(SwordBookMetaData.KEY_DISTRIBUTION_LICENSE)
        val unlockInfo = document.bookMetaData.getProperty(SwordBookMetaData.KEY_UNLOCK_INFO)
        var copyrightMerged = ""
        if (StringUtils.isNotBlank(shortCopyright)) {
            copyrightMerged += shortCopyright
        } else if (StringUtils.isNotBlank(copyright)) {
            copyrightMerged += "\n\n" + copyright
        }
        if (StringUtils.isNotBlank(distributionLicense)) {
            copyrightMerged += "\n\n" +distributionLicense
        }
        if (StringUtils.isNotBlank(copyrightMerged)) {
            val copyrightMsg = application.getString(R.string.module_about_copyright, copyrightMerged)
            about += "\n\n" + copyrightMsg
        }
        if(unlockInfo != null) {
            about += "\n\n<b>${application.getString(R.string.unlock_info)}</b>\n\n$unlockInfo"
        }

        // add version
        val existingDocument = Books.installed().getBook(document.initials)
        val existingVersion = existingDocument?.bookMetaData?.getProperty("Version")
        val existingVersionDate = existingDocument?.bookMetaData?.getProperty("SwordVersionDate") ?: "-"

        val versionLatest = document.bookMetaData.getProperty("Version")
        val versionLatestDate = document.bookMetaData.getProperty("SwordVersionDate") ?: "-"

        val versionMessageInstalled = if(existingVersion != null)
            application.getString(R.string.module_about_installed_version,
                try {
                    Version(existingVersion).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing version $existingVersion", e)
                    existingVersion
                }, existingVersionDate)
        else null

        val versionMessageLatest = if(versionLatest != null)
            application.getString((
                if (existingDocument != null)
                    R.string.module_about_latest_version
                else
                    R.string.module_about_installed_version),
                try {Version(versionLatest).toString()} catch(e: Exception) {versionLatest}, versionLatestDate)
        else null

        if(versionMessageLatest != null) {
            about += "\n\n" + versionMessageLatest
        }

        val history = document.bookMetaData.getValues("History")
        if(history != null) {
            about += "\n\n" + application.getString(R.string.about_version_history, "\n" +
                history.reversed().joinToString("\n"))
        }

        // add versification
        if (document is SwordBook) {
            val versification = document.versification
            val versificationMsg = application.getString(R.string.module_about_versification, versification.name)
            about += "\n\n" + versificationMsg
        }

        // add id
        if (document is SwordBook) {
            val repoName = document.getProperty(DownloadManager.REPOSITORY_KEY)
            val repoMessage = if(repoName != null) application.getString(R.string.module_about_repository, repoName) else ""
            val osisIdMessage = application.getString(R.string.module_about_osisId, document.initials)
            about += """


                $osisIdMessage
                
                $repoMessage
                """.trimIndent()
        }
        about = about.replace("\n", "<br>")
        dialogs.await(
            AppDialogRequest.Message(
                title = null,
                message = about,
                confirmText = application.getString(R.string.okay),
                cancellable = false,
            ),
        )
    }

    /**
     * Ported from a platform `AlertDialog.Builder` to [AppDialogController.post] (Task 28) -- a
     * fire-and-forget notice (no caller waits on its single OK button), same texts/links, now
     * structured as [AppDialogRequest.Notice] blocks: the per-item help text (`Html`), the full-docs
     * link (`Html`), the sponsor line (`IconLine`, the money icon that used to be an `ImageSpan`),
     * and the version line (`Html`, only when [showVersion]) -- matching the brief's block order.
     */
    fun showHelp(callingActivity: ActivityBase, filterItems: List<Int>? = null, showVersion: Boolean = false) {
        val app = application
        val versionMsg = app.getString(R.string.version_text, applicationVersionName)

        data class HelpItem(val title: Int, val text: Int, val videoLink: String? = null, val docUrl: String? = null)

        val help = listOf(
            HelpItem(R.string.help_nav_title, R.string.help_nav_text, docUrl = DocsLinks.page("navigation")),
            HelpItem(R.string.help_contextmenus_title, R.string.help_contextmenus_text),
            HelpItem(R.string.help_window_pinning_title, R.string.help_window_pinning_text, windowPinningVideo, docUrl = DocsLinks.page("windows")),
            HelpItem(R.string.help_bookmarks_title, R.string.help_bookmarks_text, bookmarksMyNotesPlaylist, docUrl = DocsLinks.page("bookmarks")), // beta video
            HelpItem(R.string.studypads, R.string.help_studypads_text, studyPadsVideo, docUrl = DocsLinks.page("study_pads")), // beta video
            HelpItem(R.string.help_search_title, R.string.help_search_text2, docUrl = DocsLinks.page("search")),
            HelpItem(R.string.help_workspaces_title, R.string.help_workspaces_text, workspacesVideo, docUrl = DocsLinks.page("workspaces")),
            HelpItem(R.string.help_hidden_features_title, R.string.help_hidden_features_text)
        ).run {
            if(filterItems != null) {
                filter { filterItems.contains(it.title) }
            } else this
        }

        val buy = app.getString(R.string.buy_development)
        val support = app.getString(R.string.buy_development2)
        val buyMessage = "<b>$support</b>: <a href=\"$buyDevelopmentLink\">$buy</a>"

        var htmlMessage = ""

        for(helpItem in help) {
            val helpText = app.getString(helpItem.text).replace("\n", "<br>")

            val links = mutableListOf<String>()
            if(helpItem.videoLink != null) {
                links.add("&bull;&nbsp;<i><a href=\"${helpItem.videoLink}\">${app.getString(R.string.watch_tutorial_video)}</a></i>")
            }
            if(helpItem.docUrl != null) {
                links.add("&bull;&nbsp;<i><a href=\"${helpItem.docUrl}\">${app.getString(R.string.help_read_more_link)}</a></i>")
            }
            val linksHtml = if(links.isNotEmpty()) "<br>${links.joinToString("<br>")}<br>" else ""

            htmlMessage += "<b>${app.getString(helpItem.title)}</b><br>$helpText<br>$linksHtml<br>"
        }

        val fullDocsLink = app.getString(R.string.help_full_documentation_link)
        val fullDocsMessage = "<a href=\"${DocsLinks.BASE_URL}\">$fullDocsLink</a>"

        dialogs.post(
            AppDialogRequest.Notice(
                title = app.getString(R.string.help),
                showTitleLogo = true,
                blocks = listOfNotNull(
                    AppDialogRequest.NoticeBlock.Html(htmlMessage),
                    AppDialogRequest.NoticeBlock.Html(fullDocsMessage),
                    AppDialogRequest.NoticeBlock.IconLine(AppDialogRequest.NoticeIcon.Money, "&nbsp;$buyMessage"),
                    if (showVersion) AppDialogRequest.NoticeBlock.Html("<i>$versionMsg</i>") else null,
                ),
                confirmText = app.getString(android.R.string.ok),
            ),
        )
    }

    /**
     * Show a help dialog with a short blurb and a clickable "Read more in the manual" link.
     *
     * The link opens [helpUrl], normally built with [DocsLinks.page].
     *
     * @param activity Activity used to launch the dialog.
     * @param titleResId String resource for the dialog title.
     * @param messageResId String resource for the short blurb shown above the link.
     * @param helpUrl Full docs URL, e.g. `DocsLinks.page("ai", "setting-permissions")`.
     */
    fun showHelpDialog(
        activity: Activity,
        titleResId: Int,
        messageResId: Int,
        helpUrl: String,
    ) {
        val readMore = activity.getString(R.string.help_read_more_link)
        val messageHtml = activity.getString(messageResId) +
            "<br><br><i><a href=\"$helpUrl\">$readMore</a></i>"
        dialogs.post(
            AppDialogRequest.Message(
                title = activity.getString(titleResId),
                message = messageHtml,
                confirmText = activity.getString(R.string.okay),
                cancellable = true,
            ),
        )
    }

    /**
     * Opens [link] in a browser immediately, no question asked -- classic `openLink`'s own `else`
     * branch, extracted (C1 fix) so a Compose caller that has ALREADY asked its own "open external
     * link?" question (`AbLinkRouting`, driven by [isDiscrete]) does not ask a SECOND time through
     * [openLink]'s `Dialogs.simpleQuestion` FIFO -- which would also queue behind, and so be hidden
     * by, whatever dialog contains the link.
     */
    fun openLinkNow(link: String) {
        val activity = CurrentActivityHolder.currentActivity!!
        try {
            activity.startActivityForResult(Intent(Intent.ACTION_VIEW, Uri.parse(link)),
                ActivityBase.STD_REQUEST_CODE
            )
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "No activity found to handle link: $link", e)
            UserMessages.toast(application.getString(R.string.error_opening_link, link))
        }
    }

    /** Non-Compose callers only (a WebView link, …): asks first in discrete mode through the
     *  platform `Dialogs.simpleQuestion` FIFO, same as before C1. A Compose dialog body's link goes
     *  through `AbLinkRouting`/[openLinkNow] instead -- see [openLinkNow]'s kdoc for why. */
    fun openLink(link: String, forceAsk: Boolean = false) {
        val activity = CurrentActivityHolder.currentActivity!!
        if (isDiscrete || forceAsk) {
            activity.lifecycleScope.launch(Dispatchers.Main) {
                if(Dialogs.simpleQuestion(activity,
                        message = net.bible.android.view.activity.page.application.getString(R.string.external_link_question, link),
                        title = net.bible.android.view.activity.page.application.getString(R.string.external_link),
                    )
                ) {
                    openLinkNow(link)
                }
            }
        } else {
            openLinkNow(link)
        }
    }

    fun verifySignature(file: File, signatureFile: File): Boolean {
        // Adapted from https://stackoverflow.com/questions/34066949/verify-digital-signature-on-android
        if(!signatureFile.canRead()) return false
        val reader = PemReader(InputStreamReader(application.resources.openRawResource(R.raw.publickey)))
        val data = file.inputStream()
        val signatureData = signatureFile.inputStream()

        val publicKeyPem = reader.readPemObject()
        val publicKeyBytes: ByteArray = publicKeyPem.content
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec) as RSAPublicKey
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initVerify(publicKey)
        val buffy = ByteArray(16 * 1024)
        var read = -1
        while (data.read(buffy).also { read = it } != -1) {
            signature.update(buffy, 0, read)
        }
        val signatureBytes = ByteArray(publicKey.modulus.bitLength() / 8)
        signatureData.read(signatureBytes)
        return signature.verify(signatureBytes)
    }
    
    private var ttsNotificationManager: TextToSpeechNotificationManager? = null
    private var ttsWidgetManager: SpeakWidgetManager? = null

    internal var myDocumentRegistration: kotlinx.coroutines.Job? = null
        private set

    var initialized = false
    private var booksInitialized = false
    val isOnyxDevice get() = Build.BRAND.lowercase() == "onyx"

    fun initializeApp() {
        if(!initialized) {
            try {
                val pid = android.os.Process.myPid()
                Runtime.getRuntime().exec("logcat -P '$pid'").waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Logcat could not be run")
            }

            DatabaseContainer.ready = true
            DatabaseContainer.instance
            loadSnapshotStoresInBackground()
            if (ttsNotificationManager == null) {
                ttsNotificationManager = TextToSpeechNotificationManager()
            }
            if(!BuildVariant.Appearance.isDiscrete && ttsWidgetManager == null) {
                ttsWidgetManager = SpeakWidgetManager()
            }

            addManuallyInstalledMyBibleBooks()
            addManuallyInstalledMySwordBooks()
            addManuallyInstalledESwordBooks()
            addManuallyInstalledEpubBooks(net.bible.android.platform.AndroidCoreStrings(application), net.bible.android.platform.AndroidAppEnvironment(application))
            addManuallyInstalledTtfBooks()
            addManuallyInstalledCsvPromptBooks()
            // Synchronous framework entry points cannot block Main on registration.
            // Reading startup awaits this queue in initializeAppCoroutine before restoring windows.
            myDocumentRegistration = MyDocumentBookManager.launcher.launch("mydocument-sync") {
                MyDocumentBookManager.registerAllDocuments()
                // every book is registered now: safe to sweep legacy search indexes of uninstalled books
                SwordEnvironmentInitialisation.deleteOrphanLegacyIndexesInBackground()
            }

            // IN practice we don't need to restore this data, because it is stored by JSword in book
            // metadata (persisted by JSWORD to files) too.
            //docDao.getAll().forEach {
            //    Books.installed().getBook(it.initials)?.putProperty(REPOSITORY_KEY, it.repository)
            //}

            initialized = true
        }

        if(!booksInitialized && Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }.isNotEmpty()) {
            if(!application.isRunningTests) {
                for (it in blockingAtEdge { docDao.getUnlocked() }) { // L1-edge: synchronous app bootstrap (widget/media-button entry before any coroutine)
                    val book = Books.installed().getBook(it.initials)
                    book!!.unlock(it.cipherKey)
                }
            }
            booksInitialized = true
        }
    }

    /**
     * A host recreated directly (process death) only runs [initializeApp], never [initializeAppCoroutine]. Start the
     * snapshot loads here without blocking; writes that arrive before they finish are applied to the loaded rows.
     * The job is owned by [DatabaseContainer.aiLifetime]: every database reset, close or restore cancels and joins it
     * before closing (a Room 3 close under its in-flight read is a native crash). A cancelled load leaves the store
     * unloaded (factory defaults) until a later [initializeAppCoroutine] loads it; the host bootstrap joins this job
     * through [awaitSnapshotLoadsAtEdge].
     */
    @androidx.annotation.VisibleForTesting
    internal fun loadSnapshotStoresInBackground(): kotlinx.coroutines.Job {
        val scope = org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.AppCoroutineScope>(net.bible.sharedcore.platform.AppCoroutineScope::class.java)
        return scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            runEachCatching(
                { globalTextDisplaySettingsStore.load() },
                { readingProgressSettingsStore.load() },
                { promptService.refresh() },
            )
        }.also {
            snapshotLoadJob = it
            DatabaseContainer.aiLifetime.ownUntilReplace(it)
            it.start()
        }
    }

    @Volatile private var snapshotLoadJob: kotlinx.coroutines.Job? = null

    /**
     * The cold-start barrier for the snapshot stores: the process-death path never runs [initializeAppCoroutine], so
     * the host bootstrap joins the background load here before anything reads a store (otherwise a first render reads
     * factory defaults and nothing re-renders when the load lands). Bounded by one row read per store. A cancelled
     * load (a reset or restore took ownership) completes the join at once instead of hanging; the stores then keep
     * their defaults until a later load.
     */
    internal fun awaitSnapshotLoadsAtEdge() {
        val job = snapshotLoadJob ?: return
        if (job.isCompleted) return
        blockingAtEdge { job.join() } // L1-edge: cold-start barrier for the snapshot stores (host bootstrap, process-death path)
    }

    suspend fun initializeAppCoroutine() {
        DatabaseContainer.ready = true
        aiSettings.ensureLoaded()
        // Per-step isolation, as in the background variant: an unreadable AI/settings row must not abort startup.
        runEachCatching(
            { globalTextDisplaySettingsStore.load() },
            { readingProgressSettingsStore.load() },
            { promptService.refresh() },
        )
        MyDocumentBookManager.launcher.join("mydocument-sync")
        if(!initialized) {
            try {
                val pid = android.os.Process.myPid()
                Runtime.getRuntime().exec("logcat -P '$pid'").waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Logcat could not be run")
            }

            DatabaseContainer.ready = true
            DatabaseContainer.instance
            org.koin.java.KoinJavaComponent.get<net.bible.service.cloudsync.documents.DocumentSyncSettings>(
                net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java
            ).ensureLoaded()
            withContext(Dispatchers.Main) {
                if (ttsNotificationManager == null) {
                    ttsNotificationManager = TextToSpeechNotificationManager()
                }
                if(!BuildVariant.Appearance.isDiscrete && ttsWidgetManager == null) {
                    ttsWidgetManager = SpeakWidgetManager()
                }
            }
            withContext(Dispatchers.IO) {
                addManuallyInstalledMyBibleBooks()
                addManuallyInstalledMySwordBooks()
                addManuallyInstalledESwordBooks()
                addManuallyInstalledEpubBooks(net.bible.android.platform.AndroidCoreStrings(application), net.bible.android.platform.AndroidAppEnvironment(application))
                addManuallyInstalledTtfBooks()
                addManuallyInstalledCsvPromptBooks()
            }
            MyDocumentBookManager.registerAllDocuments()
            SwordEnvironmentInitialisation.deleteOrphanLegacyIndexesInBackground()

            // IN practice we don't need to restore this data, because it is stored by JSword in book
            // metadata (persisted by JSWORD to files) too.
            //docDao.getAll().forEach {
            //    Books.installed().getBook(it.initials)?.putProperty(REPOSITORY_KEY, it.repository)
            //}

            initialized = true
        }

        if(!booksInitialized && Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }.isNotEmpty()) {
            if(!application.isRunningTests) {
                for (it in docDao.getUnlocked()) {
                    Books.installed().getBook(it.initials)?.unlock(it.cipherKey)
                }
            }
            booksInitialized = true
        }
    }

    fun prepareForDestruction() {
        if(!initialized) return
        windowControl.windowRepository.saveIntoDb(false)
    }

    fun destroy() {
        ttsNotificationManager?.destroy()
        ttsWidgetManager?.destroy()
        ttsNotificationManager = null
        ttsWidgetManager = null
        initialized = false
    }

    fun prepareData() {
        val dataVersionNow = 1L
        val dataVersion = settings.getLong("data-version", 0)
        if(dataVersion < 1) {
            prepareExampleBookmarksAndWorkspaces()
        }
        if(dataVersion != dataVersionNow) {
            settings.setLong("data-version", dataVersionNow)
        }
    }

    const val lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

    val defaultBible get() = net.bible.android.control.page.defaultBible()
    val defaultVerse: VerseRange get() = net.bible.android.control.page.defaultVerse()

    private fun prepareExampleBookmarksAndWorkspaces() {
        blockingAtEdge { prepareExampleBookmarks() } // L1-edge: synchronous first-run bootstrap (prepareData runs on the host UI thread before the window repository exists)
        prepareExampleWorkspaces()
    }

    /** First-run example bookmarks and highlight labels; one bridge at the caller covers every DAO call here. */
    private suspend fun prepareExampleBookmarks() {
        val bookmarkDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val hasExistingBookmarks = bookmarkDao.allBookmarks(BookmarkSortOrder.ORDER_NUMBER).isNotEmpty()

        val migratedNotesName = application.getString(R.string.migrated_my_notes)
        var highlightLabels = emptyList<BookmarkEntities.Label>()

        if(bookmarkDao.allLabelsSortedByName().none { !it.name.startsWith("__") && it.name != migratedNotesName }) {
            val redLabel = BookmarkEntities.Label(name = application.getString(R.string.label_red), type = LabelType.HIGHLIGHT, color = Color.argb(255, 255, 0, 0), displayStyleWholeVerse = null, favourite = true)
            val greenLabel = BookmarkEntities.Label(name = application.getString(R.string.label_green), type = LabelType.HIGHLIGHT, color = Color.argb(255, 0, 255, 0), displayStyleWholeVerse = null, favourite = true)
            val blueLabel = BookmarkEntities.Label(name = application.getString(R.string.label_blue), type = LabelType.HIGHLIGHT, color = Color.argb(255, 0, 0, 255), displayStyleWholeVerse = null, favourite = true)
            val underlineLabel = BookmarkEntities.Label(name = application.getString(R.string.label_underline), type = LabelType.HIGHLIGHT, color = Color.argb(255, 255, 0, 255), displayStyle = BookmarkDisplayStyle.UNDERLINE, displayStyleWholeVerse = null, favourite = true)

            highlightLabels = listOf(
                redLabel,
                greenLabel,
                underlineLabel,
                blueLabel,
            )

            val salvationLabel = BookmarkEntities.Label(name = application.getString(R.string.label_salvation), type = LabelType.EXAMPLE, color = Color.argb(255,  100, 0, 150))
            bookmarkDao.insertLabels(highlightLabels)

            fun getBookmark(verseRange: VerseRange, start: Double, end: Double): BookmarkEntities.BibleBookmarkWithNotes {
                val v1 = verseRange.toVerseArray()[start.toInt()]
                val v2 = verseRange.toVerseArray()[end.toInt()]
                val l1 = SwordContentFacade.getCanonicalText(defaultBible, v1, true, strings = org.koin.core.context.GlobalContext.get().get<net.bible.sharedcore.platform.CoreStrings>(), saxTextPolicy = net.bible.android.platform.androidSaxTextPolicy()).length
                val l2 = SwordContentFacade.getCanonicalText(defaultBible, v2, true, strings = org.koin.core.context.GlobalContext.get().get<net.bible.sharedcore.platform.CoreStrings>(), saxTextPolicy = net.bible.android.platform.androidSaxTextPolicy()).length
                val tr = BookmarkEntities.TextRange(((start - start.toInt())*l1).roundToInt(), ((end-end.toInt()) * l2).roundToInt())
                val v = VerseRange(v1.versification, v1, v2)

                return BookmarkEntities.BibleBookmarkWithNotes(v, textRange = tr, wholeVerse = false, book = defaultBible)
            }

            if(!hasExistingBookmarks) {
                bookmarkDao.insert(salvationLabel)

                // first bookmark, full verses, with underline
                var b = BookmarkEntities.BibleBookmarkWithNotes(defaultVerse, textRange = null, wholeVerse = true, book = defaultBible).apply { primaryLabelId = underlineLabel.id }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, underlineLabel.id))
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, salvationLabel.id))

                // second bookmark, red
                b = getBookmark(defaultVerse, 1.0, 1.5).apply { primaryLabelId = redLabel.id }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, redLabel.id))

                // third bookmark, green
                b = getBookmark(defaultVerse, 1.2, 1.4).apply {
                    primaryLabelId = greenLabel.id
                    notes = lorem
                }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(b.noteEntity!!)

                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, greenLabel.id))

                val salvationVerses = listOf("Joh.3.3", "Tit.3.3-Tit.3.7", "Rom.3.23-Rom.3.24", "Rom.4.3", "1Tim.1.15", "Eph.2.8-Eph.2.9", "Isa.6.3", "Rev.4.8", "Exo.20.2-Exo.20.17")
                    .mapNotNull { try {VerseRangeFactory.fromString(KJVA, it)} catch (e: NoSuchVerseException) {
                        Log.e("CommonUtils", "NoSuchVerseException for ${it}??!", e)
                        null 
                    }}

                salvationVerses
                    .map {
                        BookmarkEntities.BibleBookmarkWithNotes(it, textRange = null, wholeVerse = true, book = null).apply { type = BookmarkType.EXAMPLE }
                    }.forEach {
                        bookmarkDao.insert(it.bookmarkEntity)
                        bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(it.id, salvationLabel.id))
                    }
            }
        }
    }

    private fun prepareExampleWorkspaces() {
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        val ws = blockingAtEdge { workspaceDao.allWorkspaces() } // L1-edge: synchronous first-run bootstrap (prepareData)
        if(ws.isNotEmpty()) {
            blockingAtEdge { workspaceDao.updateWorkspaces(ws) } // L1-edge: synchronous first-run bootstrap (prepareData)
            settings.setBoolean("first-time", false)
        } else {
            val workspaceSettings = WorkspaceEntities.WorkspaceSettings()
            val newWorkspaces = listOf(
                WorkspaceEntities.Workspace(name = application.getString(R.string.workspace_number, 1), workspaceSettings = workspaceSettings),
                WorkspaceEntities.Workspace(name = application.getString(R.string.workspace_number, 2), workspaceSettings = workspaceSettings),
            )
            blockingAtEdge { newWorkspaces.forEach { workspaceDao.insertWorkspace(it) } } // L1-edge: synchronous first-run bootstrap (prepareData)
            val workspaceIds = newWorkspaces.map { it.id }
            settings.setString("current_workspace_id", workspaceIds[0].toString())
        }
    }

    fun makeLarger(icon: Drawable, sizeMultiplier: Float = 1.0f): Drawable = LayerDrawable(arrayOf(icon)).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val m = sizeMultiplier
            setLayerSize(0, (icon.intrinsicWidth*m).toInt(), (icon.intrinsicHeight*m).toInt())
        }
    }

    fun iconWithSync(icon: Int, syncOn: Boolean, sizeMultiplier: Float? = null): Drawable {
        var syncColor = R.color.sync_on_green
        var circleColor = R.color.background_color
        if (!syncOn) {
            circleColor = R.color.transparent
            syncColor = R.color.transparent
        }
        val iconDrawable = getTintedDrawable(icon)
        val d1 = getTintedDrawable(R.drawable.ic_workspace_overlay_24dp, syncColor)
        val circleDrawable = getTintedDrawable(R.drawable.ic_baseline_circle_24, circleColor)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            LayerDrawable(arrayOf(iconDrawable, circleDrawable, d1)).apply {
                val s = sizeMultiplier?:1.0F
                val size = (d1.intrinsicWidth * s).toInt()
                val s1 = (d1.intrinsicWidth * 0.7 * s).toInt()
                val s2 = (d1.intrinsicWidth * 0.6 * s).toInt()
                val d = (s1-s2) / 2
                setLayerSize(0, size, size)
                setLayerSize(1, s1, s1)
                setLayerSize(2, s2, s2)
                setLayerGravity(1, Gravity.BOTTOM or Gravity.END)
                setLayerGravity(2, Gravity.BOTTOM or Gravity.END)
                setLayerInsetEnd(1, -d)
                setLayerInsetBottom(1, -d)
                setLayerInsetEnd(2, 0)
                setLayerInsetBottom(2, 0)
            }
        else
            d1
    }

    fun combineIcons(icon: Int, icon2: Int, sizeMultiplier: Float? = null): Drawable {
        val d1 = getTintedDrawable(icon)
        val d2 = getTintedDrawable(icon2)
        val circleDrawable = getTintedDrawable(R.drawable.ic_baseline_circle_24, R.color.background_color)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            LayerDrawable(arrayOf(d1, circleDrawable, d2)).apply {
                val s = sizeMultiplier ?: 1.0F
                val size = (d1.intrinsicWidth * s).toInt()
                val s1 = (d1.intrinsicWidth * 0.7 * s).toInt()
                val s2 = (d1.intrinsicWidth * 0.6 * s).toInt()
                val d = (s1 - s2) / 2
                setLayerSize(0, size, size)
                setLayerSize(1, s1, s1)
                setLayerSize(2, s2, s2)
                setLayerGravity(1, Gravity.BOTTOM or Gravity.END)
                setLayerGravity(2, Gravity.BOTTOM or Gravity.END)
                setLayerInsetEnd(1, -d)
                setLayerInsetBottom(1, -d)
                setLayerInsetEnd(2, 0)
                setLayerInsetBottom(2, 0)
            }
        else
            d1
    }

    fun iconWithInheritance(icon: Int, inheritedFrom: InheritedFrom, level: SettingsLevel, sizeMultiplier: Float? = null): Drawable {
        return when {
            level == SettingsLevel.GLOBAL ->
                combineIcons(icon, R.drawable.ic_settings_black_24dp, sizeMultiplier)
            inheritedFrom == InheritedFrom.WORKSPACE ->
                iconWithSync(icon, true, sizeMultiplier)
            inheritedFrom == InheritedFrom.GLOBAL ->
                combineIcons(icon, R.drawable.ic_settings_black_24dp, sizeMultiplier)
            level == SettingsLevel.WORKSPACE ->
                combineIcons(icon, R.drawable.ic_workspace_overlay_24dp, sizeMultiplier)
            else ->
                getTintedDrawable(icon).let { if (sizeMultiplier != null) makeLarger(it, sizeMultiplier) else it }
        }
    }

    suspend fun checkPoorTranslations(activity: ActivityBase): Boolean {
        val languageTag = Locale.getDefault().toLanguageTag()
        val languageCode = Locale.getDefault().language

        Log.i(TAG, "Language tag $languageTag, code $languageCode")

        // Transifex as of 6.12.2024 + ai-translated languages (10.4.2026)
        val goodLanguages = (
            "en,af,fi,fr,de,it,pt-BR,ro,sk,sl,tr,kk,uk,lt,yue,zh-Hans-CN,zh-Hant-TW," +
            "es,ta,cs,hu,nl,sr,te,pl,hr,bn,ko,ru,bg,ar,he,hi,et,nb,ja,th,vi,da,ca,in,eo,ms,sw,ur,ne,fil").split(",")

        // 4.0 list:

        //    listOf(
        //
        //    "af", "cs", "de", "en", "eo", "es", "fi", "fr", "hi", "hu", "it", "kk", "lt", "my", "nl", "pl", "pt", "ro", "ru",
        //    "sk", "sl", "te", "uk", "zh-Hans-CN", "zh-Hant-TW", "he", "iw"
        //    // almost: "ko", "he", "ar" (hebrew, check...)
        //)

        fun checkLanguage(lang: String): Boolean =
            if(lang.length == 2)
                lang == languageCode
            else
                lang == languageTag


        val languageOK = goodLanguages.any {checkLanguage(it)}

        if(languageOK || (
                settings.getString("poor-translations-dismissed", "") == languageTag
                    && settings.getString("poor-translations-dismissed-version", "") == mainVersion))
        {
            return true
        }

        val lang = Locale.getDefault().displayLanguage
        val instr = application.getString(R.string.instructions_for_translators)
        val instructionsUrl = "https://github.com/AndBible/and-bible/wiki/Translating-User-Interface"
        val instructionsLink = "<a href=\"$instructionsUrl\">$instr</a>"
        val msg = application.getString(R.string.incomplete_translation, lang, application.getString(incompleteTranslationAppName(isDiscrete)), instructionsLink)

        val proceedValue = "proceed"
        val dismissValue = "dismiss"
        val closeValue = "close"
        val result = dialogs.await(
            AppDialogRequest.Options(
                title = null,
                message = msg,
                options = listOf(
                    SettingsItem.Choice(proceedValue, application.getString(R.string.proceed_anyway)),
                    SettingsItem.Choice(dismissValue, application.getString(R.string.beta_notice_dismiss_until_update)),
                    SettingsItem.Choice(closeValue, application.getString(R.string.close)),
                ),
                dismissText = null,
                asActionSheet = false,
                cancellable = false,
            ),
        )
        val selected = (result as? AppDialogResult.Selected)?.value
        return when (selected) {
            proceedValue -> true
            dismissValue -> {
                settings.setString("poor-translations-dismissed-version", mainVersion)
                settings.setString("poor-translations-dismissed", languageTag)
                true
            }
            closeValue -> {
                activity.finish()
                false
            }
            else -> false
        }
    }

    suspend fun requestNotificationPermission(activity_: ActivityBase? = null) = withContext(Dispatchers.Main) {
        val activity = activity_ ?: CurrentActivityHolder.currentActivity ?: return@withContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
                var request = true
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    // dialogs.await(...) is thread-safe on its own, but this whole function runs on
                    // Main (below) because checkSelfPermission/shouldShowRequestPermissionRationale/
                    // requestPermissions are Activity calls that must not run off the main thread --
                    // GlobalScope.launch(Dispatchers.Default) callers exist (SpeakControl.prepareForSpeaking).
                    val answer = dialogs.await(
                        AppDialogRequest.Confirm(
                            title = activity.getString(R.string.permission_required),
                            message = activity.getString(R.string.progress_status_permission),
                            confirmText = activity.getString(R.string.okay),
                            dismissText = activity.getString(R.string.cancel),
                        ),
                    )
                    request = answer == AppDialogResult.Ok
                }
                if(request) {
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 999)
                }
            }
        }
    }

    fun changeAppIconAndName() {
        // There's issue on Android 5 that icon simply disappears and calculator does not appear.
        // See https://github.com/AndBible/and-bible/issues/2310
        if (BuildVariant.Appearance.isDiscrete || Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) return
        val discrete = realSharedPreferences.getBoolean("discrete_mode", false)
        val packageName = BuildConfig.APPLICATION_ID
        val allNames = listOf(
            "net.bible.android.activity.StartupActivity",
            "net.bible.android.view.activity.Calculator"
        )

        val activeName = allNames[if(discrete) 1 else 0]
        var settingsChanged = false
        Log.d(TAG, "Changing app icon / name to $activeName")
        for (name in allNames) {
            val value = name == activeName
            Log.d(TAG, "changing $name to $value")
            val component = ComponentName(packageName, name)
            // minSdk is 23 (and this function returns early on <= 22), so use the API-23 flag; it has the
            // same value (512) as MATCH_DISABLED_COMPONENTS (API 24+).
            @Suppress("DEPRECATION")
            val manifestEnabled = application.packageManager
                .getActivityInfo(component, PackageManager.GET_DISABLED_COMPONENTS).enabled
            val toWrite = componentStateToWrite(
                current = application.packageManager.getComponentEnabledSetting(component),
                manifestEnabled = manifestEnabled,
                wantEnabled = value,
            ) ?: continue
            application.packageManager.setComponentEnabledSetting(
                component,
                toWrite,
                PackageManager.DONT_KILL_APP
            )
            settingsChanged = true
        }
        if(settingsChanged) {
            forceStopApp()
        }
    }

    fun createDiscreteNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = application.getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CALC_NOTIFICATION_CHANNEL,
                application.getString(R.string.app_name_calculator), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    val isMeteredNetwork: Boolean get() {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.isActiveNetworkMetered
    }

    val isCloudSyncAvailable get() = Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1
    val isCloudSyncEnabled: Boolean get () =
        if(!isCloudSyncAvailable) false
        else SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(CommonUtils.settings) }
    val isDiscrete get() = BuildVariant.Appearance.isDiscrete || realSharedPreferences.getBoolean("discrete_mode", false)
    val showCalculator get() = BuildVariant.Appearance.isDiscrete || realSharedPreferences.getBoolean("show_calculator", false)

    /**
     * One-time migration: renames gdrive_* settings keys to cloud_sync_* / sync_enable_* prefixes.
     * Moves secrets from SettingsDatabase to realSharedPreferences.
     * Idempotent — safe to call on every app start.
     */
    fun migrateOldSettingsKeys() {
        val sharedPrefs = realSharedPreferences
        // Use the SettingsStore directly to bypass the `initialized` guard in AndBibleSettings.getBoolean.
        // This function runs during DatabaseContainer.init(), before `initialized` is set to true,
        // so AndBibleSettings.getBoolean would always return the default value, losing the old data.
        val st = store

        val secretMigrations = mapOf(
            "gdrive_password" to "cloud_sync_password",
            "gdrive_username" to "cloud_sync_username",
            "gdrive_server_url" to "cloud_sync_server_url",
            "gdrive_folder_path" to "cloud_sync_folder_path",
        )
        for ((oldKey, newKey) in secretMigrations) {
            if (sharedPrefs.getString(newKey, null) != null) continue
            val value = st.getString(oldKey, null) ?: sharedPrefs.getString(oldKey, null)
            if (value != null) {
                Log.i(TAG, "Migrating setting '$oldKey' → '$newKey'")
                sharedPrefs.edit().putString(newKey, value).apply()
                st.setString(oldKey, null)
                sharedPrefs.edit().remove(oldKey).apply()
            }
        }

        if (sharedPrefs.getString("cloud_sync_last_account", null) == null) {
            val lastAccount = sharedPrefs.getString("lastAccount", null)
            if (lastAccount != null) {
                Log.i(TAG, "Migrating 'lastAccount' → 'cloud_sync_last_account'")
                sharedPrefs.edit().putString("cloud_sync_last_account", lastAccount).apply()
                sharedPrefs.edit().remove("lastAccount").apply()
            }
        }

        val boolRenames = mapOf(
            "gdrive_bookmarks" to "sync_enable_bookmarks",
            "gdrive_workspaces" to "sync_enable_workspaces",
            "gdrive_readingplans" to "sync_enable_readingplans",
            "gdrive_mydocuments" to "sync_enable_mydocuments",
            "gdrive_llmprocessing" to "sync_enable_ai_settings",
        )
        for ((oldKey, newKey) in boolRenames) {
            val value = st.getBoolean(oldKey, false)
            if (value) {
                Log.i(TAG, "Renaming boolean setting '$oldKey' → '$newKey'")
                st.setBoolean(newKey, true)
            }
            st.setBoolean(oldKey, null)
        }

        val oldInterval = st.getLong("gdrive_sync_interval", Long.MIN_VALUE)
        if (oldInterval != Long.MIN_VALUE) {
            Log.i(TAG, "Renaming long setting 'gdrive_sync_interval' → 'cloud_sync_interval'")
            st.setLong("cloud_sync_interval", oldInterval)
            st.setLong("gdrive_sync_interval", null)
        }

        // Migrate boolean monochrome_mode → tri-state display_color_mode
        if (st.getString("display_color_mode", null) == null) {
            // The store has no "contains": a key is absent iff both defaults come back.
            val monoPresent = st.getBoolean("monochrome_mode", false) || !st.getBoolean("monochrome_mode", true)
            if (monoPresent) {
                val oldMono = st.getBoolean("monochrome_mode", false)
                val newValue = if (oldMono) DisplayColorMode.BW.value else DisplayColorMode.NORMAL.value
                Log.i(TAG, "Migrating 'monochrome_mode'=$oldMono → 'display_color_mode'=$newValue")
                st.setString("display_color_mode", newValue)
                st.setBoolean("monochrome_mode", null)
            }
        }
    }

    fun md5Hash(str: String): String = ArchiveUtils.md5Hash(str)

    fun appendUrl(u: String, filename: String): String = ArchiveUtils.appendUrl(u, filename)

    fun unzipFile(zipFile: File, destinationDir: File, filePrefix: String = "") =
        ArchiveUtils.unzip(zipFile.toOkioPath(), destinationDir.toOkioPath(), filePrefix)

    fun unzipInputStream(inputStream: InputStream, destinationDir: File, filePrefix: String = "") =
        ArchiveUtils.unzip(inputStream.source(), destinationDir.toOkioPath(), filePrefix)

    val isDebugMode get() = (0 != application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)

    val tmpDir: File get() {
        val file = File(application.filesDir, "tmp")
        file.mkdirs()
        return file
    }

    val tmpFile: File get() = File.createTempFile("andbible-", ".tmp", tmpDir)

    suspend fun determineFileType(uri: Uri): BackupFileType = withContext(Dispatchers.IO) {
        BackupFileSniffer.sniff {
            application.contentResolver.openInputStream(uri)?.source() ?: throw okio.IOException("no stream")
        }
    }

    fun makeAndBibleUrl(
        keyStr: String,
        docInitials: String? = null,
        v11n: String? = null,
        ordinal: Int? = null
    ): String {
        var url = "https://read.andbible.org/$keyStr"
        val queryParameters = mutableListOf<String>()
        if(docInitials != null) {
            queryParameters.add("document=$docInitials")
        }
        if(v11n != null) {
            queryParameters.add("v11n=$v11n")
        }
        if(ordinal != null) {
            queryParameters.add("ordinal=${ordinal}")
        }
        if(queryParameters.isNotEmpty()) {
            url += "?${queryParameters.joinToString("&")}"
        }
        return url
    }

    fun copyToClipboard(clip: ClipData, toastMessage: Int = R.string.text_copied_to_clicpboard) {
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clip)
        UserMessages.toast(application.getString(toastMessage))
    }

    private val calcPinRegex = Regex("""^(0+)(\d+)$""")

    fun removeLeadingZeroes(s: String): String {
        val match = calcPinRegex.matchEntire(s)
        if(match != null) {
            return match.groupValues[2]
        }
        return s
    }

    suspend fun documentUpgradeConfirmation(context: Activity): Boolean = context.run {
        val warningTitle = getString(R.string.bookmark_warning)
        val warningMessage = getString(R.string.bookmark_warning2)
        val warningRecommendation = getString(R.string.bookmark_warning4)
        val warningQuestion = getString(R.string.bookmark_warning3)
        val warningMsg = "$warningMessage<br><br>$warningRecommendation<br><br>$warningQuestion"
        dialogs.await(
            AppDialogRequest.Confirm(
                title = warningTitle,
                message = warningMsg,
                confirmText = getString(R.string.yes),
                dismissText = getString(R.string.cancel),
                cancellable = false,
            ),
        ) == AppDialogResult.Ok
    }
    fun prependDictionaryKeyWithZeros(keyStr: String): String {
        val lengthDiff = 5 - keyStr.length
        if(lengthDiff <= 0) return keyStr

        return "0".repeat(lengthDiff) + keyStr

    }
    fun parseAndBibleReference(uri: Uri): BookAndKey? {
        var docStr = uri.getQueryParameter("document")
        val path = uri.path?.removePrefix("/") ?: return null
        var keyStr = path

        // Handle certain JSword-style form, e.g. "StrongsGreek:5548"
        if (path.contains(":") && docStr.isNullOrBlank()) {
            val parts = path.split(":", limit = 2)
            docStr = parts[0] // derive document if missing
            keyStr = parts[1]
        }

        // Try to load the document (SwordBook, Dictionary, etc.)
        val doc = docStr?.let { Books.installed().getBook(it) }

        val defV11n = if (doc is SwordBook) doc.versification else KJVA
        val v11nStr = uri.getQueryParameter("v11n")
        val v11n = if (v11nStr == null) defV11n else Versifications.instance().getVersification(v11nStr) ?: defV11n

        if (doc is SwordDictionary) {
            val key = doc.getKey(prependDictionaryKeyWithZeros(keyStr))
            return BookAndKey(key, doc)
        }

        return try {
            val key = PassageKeyFactory.instance().getKey(v11n, keyStr)
            val ordinalStr = uri.getQueryParameter("ordinal")
            if (ordinalStr != null) {
                val ord = ordinalStr.toInt()
                return BookAndKey(key, doc, ordinal = OrdinalRange(ord))
            }
            return BookAndKey(key, doc)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception when parsing URI", e)
            null
        }
    }

    fun parseAndBibleReference(uri: String): BookAndKey?
        = parseAndBibleReference(Uri.parse(uri))
}

const val CALC_NOTIFICATION_CHANNEL = "calc-notifications"

@Serializable
data class LastTypesSerializer(val types: MutableList<WorkspaceEntities.TextDisplaySettings.Types>) {
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }

    companion object {
        fun fromJson(jsonString: String): LastTypesSerializer {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}

/** Fix batch 6 A6 (surface 8): the app name the incomplete-translation dialog uses. */
@androidx.annotation.StringRes
internal fun incompleteTranslationAppName(discrete: Boolean): Int =
    if (discrete) R.string.app_name_calculator else R.string.app_name_long
