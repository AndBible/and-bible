package net.bible.android.activity

import android.content.Intent
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.io.StringReader
import org.crosswire.jsword.book.BookData
import org.crosswire.common.xml.XMLUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import net.bible.android.BibleApplication
import net.bible.service.sword.SwordDocumentFacade

class RandomVerseWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_VERSE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, javaClass.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        // TODO: get the actual active Bible from the main app somehow
        val activeBible = SwordDocumentFacade.bibles.firstOrNull()
        if(activeBible==null){
            return
        }
        val allKeys = activeBible.globalKeyList
        var randomKey: org.crosswire.jsword.passage.Key
        do {
          val randomNumber = (0..allKeys.cardinality).random()
          randomKey = allKeys.get(randomNumber)
        } while(randomKey.name.split(':')[1] == "0") //idk how this was happenable
        val bookData = BookData(activeBible, randomKey)
        val verseTextXml = XMLUtil.writeToString(bookData.saxEventProvider)
        val verseText = processXml(verseTextXml, false, true, false, false, true)
        val verseRef = randomKey.name

        val views = RemoteViews(context.packageName, R.layout.random_verse_widget)
        views.setTextViewText(R.id.verse_text, verseText)
        views.setTextViewText(R.id.verse_reference, verseRef)

        val intent = Intent(context, RandomVerseWidget::class.java).apply {
            action = ACTION_REFRESH_VERSE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root_layout, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun processXml(
        xmlInput: String,
        linebreaks: Boolean,
        pilcrows: Boolean,
        verseNumbers: Boolean,
        chevrons: Boolean,
        brackets: Boolean
    ): String {
        //Log.d(TAG, "processXml called with xmlInput: $xmlInput, linebreaks: $linebreaks, pilcrows: $pilcrows, verseNumbers: $verseNumbers, chevrons: $chevrons")
        val factory = XmlPullParserFactory.newInstance()
        val xpp = factory.newPullParser()
        xpp.setInput(StringReader("<root>$xmlInput</root>")) // Wrapped in root for valid XML
        var eventType = xpp.eventType
        var verseNumber: String? = null
        var pilcrow = false
        var dropText = false
        var spaceBeforeNextText = false
        var para = StringBuilder()
        val paras = mutableListOf(para)

        val addText = { text: String ->
            if(!dropText) {
                if(spaceBeforeNextText){
                    if(para.isNotEmpty()){
                        para.append(' ')
                    }
                    spaceBeforeNextText = false
                }
                if(verseNumber != null){
                    if(verseNumbers) {
                        para.append(verseNumber)
                    }
                    verseNumber = null
                }
                if(pilcrow && pilcrows){
                    para.append("¶")
                    pilcrow = false
                }
                para.append(text)
            }
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (xpp.name) {
                        "verse" -> {
                            // Extract the verse number from osisID (e.g., "John.1.6" -> "6")
                            val osisId = xpp.getAttributeValue(null, "osisID")
                            osisId?.split('.')?.lastOrNull()?.let {
                                verseNumber = it
                            }
                        }
                        "milestone" -> {
                            // Check for the paragraph marker
                            if (xpp.getAttributeValue(null, "marker") == "¶") {
                                para = StringBuilder()
                                paras.add(para)
                                pilcrow = true
                            }
                        }
                        "title" -> {
                            dropText = true
                        }
                        "chapter" -> {
                            dropText = true
                        } // this was never part of the original text
                        "note" -> {
                            dropText = true
                        }
                        "transChange" -> {
                            if (brackets) {
                                addText("[")
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (xpp.name) {
                        "verse" -> {
                            if(para.length != 0){
                                spaceBeforeNextText = true
                            }
                        }
                        "title" -> {
                            dropText = false
                        }
                        "chapter" -> {
                            dropText = false
                        }
                        "note" -> {
                            dropText = false
                        }
                        "transChange" -> {
                            if (brackets) {
                                addText("]")
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    addText(xpp.text)
                }
            }
            eventType = xpp.next()
        }

        return paras.joinToString(if(linebreaks) "\n" else " ") {
            (if (chevrons) "> " else "") + it.toString()
        }
    }

    companion object {
        private const val ACTION_REFRESH_VERSE = "net.bible.android.activity.action.REFRESH_VERSE"
    }
}
class VerseWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return VerseRemoteViewsFactory(this.applicationContext, intent)
    }
}

class VerseRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var verseText: String = "Loading..."

    override fun onCreate() {
        // In onCreate, we can do some initial setup.
        // The data will be loaded in onDataSetChanged.
    }

    override fun onDataSetChanged() {
        // This is called when notifyAppWidgetViewDataChanged is called.
        // We fetch the verse text here.
        val bibleApplication = context.applicationContext as BibleApplication
        net.bible.service.db.DatabaseContainer.initializeDatabase()

        val documentControl = bibleApplication.applicationComponent.documentControl()
        val bible = documentControl.currentBible.currentDocument ?: SwordDocumentFacade.bibles.firstOrNull()

        verseText = if (bible != null) {
            val randomVerseKey = bible.getRandomVerse()
            // We also update the reference in the main widget from here
            val views = RemoteViews(context.packageName, R.layout.random_verse_widget)
            views.setTextViewText(R.id.verse_reference, randomVerseKey.name)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)

            bible.getPlainText(randomVerseKey, 1, false)
        } else {
            context.getString(R.string.no_bibles_installed)
        }
    }

    override fun onDestroy() {
        // Clean up any resources
    }

    override fun getCount(): Int {
        // We only have one item: the scrollable text view
        return 1
    }

    override fun getViewAt(position: Int): RemoteViews {
        // Create a RemoteViews object for the list item.
        return RemoteViews(context.packageName, R.layout.random_verse_widget_view_item).apply {
            setTextViewText(R.id.verse_text_item, verseText)
        }
    }

    override fun getLoadingView(): RemoteViews? {
        // You can return a custom loading view, or null to use the default.
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}
