/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.activity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.service.common.CommonUtils
import net.bible.service.common.firstBibleDoc
import net.bible.service.history.KeyHistoryItem
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookData
import org.crosswire.jsword.passage.Verse
import org.crosswire.common.xml.XMLUtil
import org.crosswire.jsword.book.sword.SwordBook
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class RandomVerseWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive action: ${intent.action}")
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_VERSE) {
            val pendingResult = goAsync()
            // Create a short-lived scope for this specific broadcast
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope.launch {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, RandomVerseWidget::class.java)
                    )
                    appWidgetIds.forEach { appWidgetId ->
                        refreshWidgetData(context, appWidgetManager, appWidgetId)
                    }
                } finally {
                    pendingResult.finish()
                    scope.cancel() // Clean up scope when work is done
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i(TAG, "onUpdate")
        val pendingResult = goAsync()
        // Create a short-lived scope for this specific update broadcast
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    val verseInfo = calculateVerseData(context)
                    saveVerseData(context, appWidgetId, verseInfo)
                    updateAppWidget(context, appWidgetManager, appWidgetId, verseInfo)
                }
            } finally {
                pendingResult.finish()
                scope.cancel() // Clean up scope when work is done
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, verseInfo: VerseInfo) {
        Log.i(TAG, "updateAppWidget for id: $appWidgetId")
        val intent = Intent(context, VerseWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }

        val views = RemoteViews(context.packageName, R.layout.random_verse_widget)
        views.setRemoteAdapter(R.id.verse_list, intent)
        views.setEmptyView(R.id.verse_list, R.id.widget_empty_view)
        views.setTextViewText(R.id.verse_reference, verseInfo.reference)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setScrollPosition(R.id.verse_list, verseInfo.mainVerseIndex)
        }

        val refreshIntent = Intent(context, RandomVerseWidget::class.java).apply {
            action = ACTION_REFRESH_VERSE
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root_layout, refreshPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun refreshWidgetData(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "Refreshing data for widget: $appWidgetId")
        val verseInfo = calculateVerseData(context)
        saveVerseData(context, appWidgetId, verseInfo)

        val updateViews = RemoteViews(context.packageName, R.layout.random_verse_widget)
        updateViews.setTextViewText(R.id.verse_reference, verseInfo.reference)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateViews.setScrollPosition(R.id.verse_list, verseInfo.mainVerseIndex)
        }

        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, updateViews)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.verse_list)

        Log.i(TAG, "Refreshed widget $appWidgetId with verse: ${verseInfo.reference}")
    }

    data class VerseInfo(val reference: String, val verses: List<String>, val mainVerseIndex: Int)

    private suspend fun calculateVerseData(context: Context): VerseInfo = withContext(Dispatchers.IO) {
        var verseRef = ""
        val versesToStore = mutableListOf<String>()
        var mainVerseIndex = 0

        try {
            // Use initializeAppCoroutine() which handles switching to Main thread for sensitive parts
            CommonUtils.initializeAppCoroutine()
            val bibleApplication = context.applicationContext as BibleApplication
            net.bible.service.db.DatabaseContainer.initializeDatabase()
            
            val appComponent = bibleApplication.applicationComponent
            val historyManager = appComponent.historyManager()
            val windowControl = appComponent.windowControl()

            // 1. Try to find most recent Bible from History across all windows
            val allWindows = windowControl.windowRepository.windowList
            val mostRecentBibleFromHistory = allWindows
                .flatMap { historyManager.getHistory(it.id) }
                .filterIsInstance<KeyHistoryItem>()
                .filter { it.document.bookCategory == BookCategory.BIBLE }
                .sortedByDescending { it.createdAt }
                .mapNotNull { it.document as? SwordBook }
                .firstOrNull()

            // 2. Fallbacks
            val activeBible = mostRecentBibleFromHistory 
                ?: try { windowControl.defaultBibleDoc() } catch (e: Exception) { null }
                ?: firstBibleDoc 
                ?: throw Exception("no Bible installed")

            val v11n = activeBible.versification
            val maxOrdinal = v11n.maximumOrdinal()
            if (maxOrdinal <= 0) throw Exception("no verses found")

            var mainVerse: Verse
            var randomOrdinal: Int
            do {
                randomOrdinal = (1..maxOrdinal).random()
                mainVerse = v11n.decodeOrdinal(randomOrdinal)
            } while (mainVerse.verse == 0)

            val startOrdinal = (randomOrdinal - 2).coerceAtLeast(1)
            val endOrdinal = (randomOrdinal + 2).coerceAtMost(maxOrdinal)

            for (i in startOrdinal..endOrdinal) {
                val v = v11n.decodeOrdinal(i)
                val rawXml = XMLUtil.writeToString(BookData(activeBible, v).saxEventProvider)
                val text = processXml(rawXml, verseNumbers = true, verseNumberSuperscripts = true)
                
                if (i == randomOrdinal) {
                    mainVerseIndex = versesToStore.size
                    versesToStore.add("<b>&nbsp;&nbsp;$text</b>")
                } else {
                    versesToStore.add(text)
                }
            }

            verseRef = mainVerse.name

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating verse data", e)
            versesToStore.clear()
            val errorMsg = context.getString(R.string.no_bibles_installed)
            versesToStore.add(errorMsg)
            verseRef = errorMsg
        }
        VerseInfo(verseRef, versesToStore, mainVerseIndex)
    }

    private suspend fun saveVerseData(context: Context, appWidgetId: Int, verseInfo: VerseInfo) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = prefs.edit()
        editor.putInt("${PREF_PREFIX_KEY}${appWidgetId}_count", verseInfo.verses.size)
        verseInfo.verses.forEachIndexed { index, s ->
            editor.putString("${PREF_PREFIX_KEY}${appWidgetId}_$index", s)
        }
        editor.commit()
    }

    companion object {
        private const val ACTION_REFRESH_VERSE = "net.bible.android.activity.action.REFRESH_VERSE"
        private const val TAG = "RandomVerseWidget"
        const val PREFS_NAME = "RandomVerseWidgetPrefs"
        const val PREF_PREFIX_KEY = "verse_text_"
    }
}

class VerseWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return VerseRemoteViewsFactory(this.applicationContext, intent)
    }
}

class VerseRemoteViewsFactory(private val context: Context, private val intent: Intent) : RemoteViewsService.RemoteViewsFactory {

    private val verses = mutableListOf<String>()
    private val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        Log.i("VerseRemoteViewsFactory", "onDataSetChanged for widget: $appWidgetId")
        val prefs = context.getSharedPreferences(RandomVerseWidget.PREFS_NAME, 0)
        verses.clear()
        
        val count = prefs.getInt("${RandomVerseWidget.PREF_PREFIX_KEY}${appWidgetId}_count", 0)
        for (i in 0 until count) {
            val v = prefs.getString("${RandomVerseWidget.PREF_PREFIX_KEY}${appWidgetId}_$i", "")
            if (!v.isNullOrEmpty()) {
                verses.add(v)
            }
        }
        
        if (verses.isEmpty()) {
            verses.add(context.getString(R.string.loading_text))
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = verses.size

    override fun getViewAt(position: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.random_verse_widget_view_item).apply {
            val styledText = HtmlCompat.fromHtml(verses[position], HtmlCompat.FROM_HTML_MODE_LEGACY)
            setTextViewText(R.id.verse_text_item, styledText)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}

private fun processXml(
    xmlInput: String,
    linebreaks: Boolean = false,
    pilcrows: Boolean = true,
    verseNumbers: Boolean = false,
    verseNumberSuperscripts: Boolean = false,
    chevrons: Boolean = false,
    brackets: Boolean = true,
    rawOsis: Boolean = false
): String {
    if (rawOsis) return xmlInput

    val factory = XmlPullParserFactory.newInstance()
    val xpp = factory.newPullParser()
    xpp.setInput(StringReader("<root>$xmlInput</root>"))
    var eventType = xpp.eventType
    var verseNumber: String? = null
    var pilcrow = false
    var dropText = false
    var spaceBeforeNextText = false
    var para = StringBuilder()
    val paras = mutableListOf(para)

    val addText = { text: String ->
        if (!dropText) {
            if (spaceBeforeNextText) {
                if (para.isNotEmpty()) para.append(' ')
                spaceBeforeNextText = false
            }
            if (verseNumber != null) {
                var displayVerseNumber = verseNumber!!
                if (verseNumberSuperscripts) {
                    val superscripts = "⁰¹²³⁴⁵⁶⁷⁸⁹"
                    displayVerseNumber = displayVerseNumber.map { c ->
                        if (c in '0'..'9') superscripts[c - '0'] else c
                    }.joinToString("")
                }
                if (verseNumbers) para.append(displayVerseNumber)
                verseNumber = null
            }
            if (pilcrow && pilcrows) {
                para.append("¶")
                pilcrow = false
            }
            para.append(text)
        }
    }

    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (xpp.name) {
                "verse" -> xpp.getAttributeValue(null, "osisID")?.split('.')?.lastOrNull()?.let { verseNumber = it }
                "milestone" -> if (xpp.getAttributeValue(null, "marker") == "¶") {
                    para = StringBuilder()
                    paras.add(para)
                    pilcrow = true
                }
                "title", "chapter", "note" -> dropText = true
                "transChange" -> if (brackets) addText("[")
            }
            XmlPullParser.END_TAG -> when (xpp.name) {
                "verse" -> if (para.isNotEmpty()) spaceBeforeNextText = true
                "title", "chapter", "note" -> dropText = false
                "transChange" -> if (brackets) addText("]")
            }
            XmlPullParser.TEXT -> addText(xpp.text)
        }
        eventType = xpp.next()
    }

    return paras.joinToString(if (linebreaks) "\n" else " ") { (if (chevrons) "> " else "") + it.toString() }
}
