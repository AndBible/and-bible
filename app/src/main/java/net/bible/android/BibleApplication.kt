/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.widget.Toast

import androidx.annotation.RequiresApi
import androidx.multidex.MultiDexApplication

import net.bible.android.activity.SpeakWidgetManager
import net.bible.android.control.ApplicationComponent
import net.bible.android.control.DaggerApplicationComponent
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.service.common.CommonUtils
import net.bible.service.device.ProgressNotificationManager
import net.bible.service.device.ScreenSettings
import net.bible.service.device.speak.TextToSpeechNotificationManager
import net.bible.service.sword.SwordEnvironmentInitialisation

import org.crosswire.common.util.Language
import org.crosswire.jsword.bridge.BookIndexer
import java.util.Locale


/** Main And Bible application singleton object
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class BibleApplication : MultiDexApplication() {
    lateinit var applicationComponent: ApplicationComponent
        private set

    var localeOverrideAtStartUp: String? = null
        private set
    private var ttsNotificationManager: TextToSpeechNotificationManager? = null
    private var ttsWidgetManager: SpeakWidgetManager? = null

    val appStateSharedPreferences: SharedPreferences
        get() = getSharedPreferences(saveStateTag, 0)

    override fun onCreate() {
        super.onCreate()
        ABEventBus.getDefault().register(this)

        // save to a singleton to allow easy access from anywhere
        application = this

        Log.i(TAG, "OS:" + System.getProperty("os.name") + " ver " + System.getProperty("os.version"))
        Log.i(TAG, "Java:" + System.getProperty("java.vendor") + " ver " + System.getProperty("java.version"))
        Log.i(TAG, "Java home:" + System.getProperty("java.home")!!)
        Log.i(TAG, "User dir:" + System.getProperty("user.dir") + " Timezone:" + System.getProperty("user.timezone"))

        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().contextClassLoader = javaClass.classLoader

        // This must be done before accessing JSword to prevent default folders being used
        SwordEnvironmentInitialisation.initialiseJSwordFolders()

        // Initialize the Dagger injector ApplicationScope objects
        applicationComponent = DaggerApplicationComponent.builder().build()

        // ideally this would be installed before initialiseJSwordFolders but the listener depends on applicationComponent
        SwordEnvironmentInitialisation.installJSwordErrorReportListener()

        // some changes may be required for different versions
        upgradePersistentData()

        // initialise link to Android progress control display in Notification bar
        ProgressNotificationManager.instance.initialise()

        // various initialisations required every time at app startup
        applicationComponent.warmUp().warmUpSwordEventually()

        localeOverrideAtStartUp = LocaleHelper.getOverrideLanguage(this)

        ttsNotificationManager = TextToSpeechNotificationManager()
        ttsWidgetManager = SpeakWidgetManager()
    }

    /**
     * Override locale.  If user has selected a different ui language to the devices default language
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun upgradePersistentData() {
        val prefs = CommonUtils.getSharedPreferences()
        val prevInstalledVersion = prefs.getInt("version", -1)
        if (prevInstalledVersion < CommonUtils.getApplicationVersionNumber() && prevInstalledVersion > -1) {
            val editor = prefs.edit()

            // ver 16 and 17 needed text size pref to be changed to int from string
            if (prevInstalledVersion < 16) {
                Log.d(TAG, "Upgrading preference")
                var textSize: String? = "16"
                if (prefs.contains(TEXT_SIZE_PREF)) {
                    Log.d(TAG, "text size pref exists")
                    textSize = try {
                        prefs.getString(TEXT_SIZE_PREF, "16")
                    } catch (e: Exception) {
                        // maybe the conversion has already taken place e.g. in debug environment
                        Integer.toString(prefs.getInt(TEXT_SIZE_PREF, 16))
                    }

                    Log.d(TAG, "existing value:" + textSize!!)
                    editor.remove(TEXT_SIZE_PREF)
                }

                val textSizeInt = Integer.parseInt(textSize!!)
                editor.putInt(TEXT_SIZE_PREF, textSizeInt)

                Log.d(TAG, "Finished Upgrading preference")
            }

            // there was a problematic Chinese index architecture before ver 24 so delete any old indexes
            if (prevInstalledVersion < 24) {
                Log.d(TAG, "Deleting old Chinese indexes")
                val chineseLanguage = Language("zh")

                val books = applicationComponent.swordDocumentFacade().documents
                for (book in books) {
                    if (chineseLanguage == book.language) {
                        try {
                            val bookIndexer = BookIndexer(book)
                            // Delete the book, if present
                            if (bookIndexer.isIndexed) {
                                Log.d(TAG, "Deleting index for " + book.initials)
                                bookIndexer.deleteIndex()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting index", e)
                        }

                    }
                }
            }
            // add new
            if (prevInstalledVersion < 61) {
                if (prefs.contains(ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR)) {
                    val pref2Val = if (prefs.getBoolean(ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR, false)) "true" else "false"
                    Log.d(TAG, "Setting new night mode pref list value:$pref2Val")
                    editor.putString(ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR, pref2Val)
                }
            }

            // clear old split screen config because it has changed a lot
            if (prevInstalledVersion < 154) {
                editor.remove("screen1_weight")
                editor.remove("screen2_minimized")
                editor.remove("split_screen_pref")
            }

            // clear setting temporarily used for window state
            if (prevInstalledVersion < 157) {
                val appPrefs = appStateSharedPreferences
                if (appPrefs.contains("screenStateArray")) {
                    Log.d(TAG, "Removing screenStateArray")
                    appPrefs.edit()
                            .remove("screenStateArray")
                            .apply()
                }
            }



            editor.putInt("version", CommonUtils.getApplicationVersionNumber())
            editor.apply()
            Log.d(TAG, "Finished all Upgrading")
        }
    }

    /**
     * This is never called in real system (only in tests). See parent documentation.
     */
    override fun onTerminate() {
        Log.i(TAG, "onTerminate")
        ttsNotificationManager!!.destroy()
        ttsWidgetManager!!.destroy()
        super.onTerminate()
        ABEventBus.getDefault().unregisterAll()
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    open fun getLocalizedResources(language: String): Resources {
        val app = application
        val oldConf = app.resources.configuration
        val newConf = Configuration(oldConf)
        newConf.setLocale(Locale(language))
        return app.createConfigurationContext(newConf).resources
    }

    fun onEventMainThread(ev: ToastEvent) {
        val duration = ev.duration ?: Toast.LENGTH_SHORT
        val message = if(ev.messageId != null) getString(ev.messageId) else ev.message
        Toast.makeText(this, message, duration).show()
    }

    companion object {

        private const val TEXT_SIZE_PREF = "text_size_pref"

        // this was moved from the MainBibleActivity and has always been called this
        private const val saveStateTag = "MainBibleActivity"

        lateinit var application: BibleApplication
            private set

        private const val TAG = "BibleApplication"
    }
}
