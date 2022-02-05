package net.bible.android.view.activity.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.text.toHtml
import com.google.android.material.tabs.TabLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.databinding.SearchResultsStatisticsBinding
import java.util.ArrayList
import net.bible.android.activity.R
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchResultsDto
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import javax.inject.Inject
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import net.bible.android.view.activity.navigation.GridChoosePassageBook.Companion.getBookTextColor
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.resources


private var TAB_TITLES = arrayOf(
    resources.getString(R.string.add_bookmark_whole_verse1),
    resources.getString(R.string.by_book),
    resources.getString(R.string.by_word)
)
var mCurrentlyDisplayedSearchResults: List<Key> = ArrayList()  // I tried to make this a non-global but i had the same problem as before where the values in the variable was one behind the search. I don't know why!!
const val verseTabPosition = 0
private const val bookTabPosition = 1
private const val wordTabPosition = 2

class MyTimer(val name:String){
    var startTime = System.nanoTime()
    var totalTime:Long = 0

    fun start() {startTime = System.nanoTime()}
    fun stop(addToLog: Boolean = false) {
        totalTime += System.nanoTime() - startTime
        if (addToLog) log()
    }
    fun reset() {totalTime = 0}
    fun log() {
        Log.e("MyTimer", name + ": " + (totalTime/1000000) + "mS\n")
    }
}

class BookStat(val book: String, var count: Int,
               val bookInitials: String,
               val bookOrdinal:Int,
               val listIndex:Int,
                val color: Int) {
    override fun toString(): String = "$book: $count"
}
class WordStat(val word: String,
               var verseIndexes: IntArray,
               val originalWord: String) {
    override fun toString(): String = "$originalWord: ${verseIndexes.count()}"
}

private var wordHits: ArrayList<Pair<String, ArrayList<Int>>> = arrayListOf()

class SearchResultsData : Parcelable {
    @JvmField
    var id: Int?  // Holds the original index in the results. Used for selection and filtering.
    @JvmField
    var osisKey: String?
    @JvmField
    var reference: String?
    @JvmField
    var translation: String?
    @JvmField
    var verse: String?
    @JvmField
    var verseHtml: String?  // I would prefer to pass a spannable string but i dont know how to make it parcelable

    /* What I really want to do is make the 'Key' parcelable but I don't know how to do that. So instead I have to send the properties I need and get the key later on */

    constructor(Id: Int?, OsisKey: String?, Reference: String?, Translation: String?, Verse: String?, VerseHtml: String?) {
        id = Id;
        osisKey = OsisKey
        reference = Reference
        translation = Translation
        verse = Verse
        verseHtml = VerseHtml
    }

    protected constructor(`in`: Parcel) {
        id = `in`.readInt()
        osisKey = `in`.readString()
        reference = `in`.readString()
        translation = `in`.readString()
        verse = `in`.readString()
        verseHtml = `in`.readString()
    }

    override fun describeContents(): Int {
        return this.hashCode()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(id!!)
        dest.writeString(osisKey)
        dest.writeString(reference)
        dest.writeString(translation)
        dest.writeString(verse)
        dest.writeString(verseHtml)

    }

    companion object CREATOR : Parcelable.Creator<SearchResultsData> {
        override fun createFromParcel(parcel: Parcel): SearchResultsData {
            return SearchResultsData(parcel)
        }

        override fun newArray(size: Int): Array<SearchResultsData?> {
            return arrayOfNulls(size)
        }
    }
}

class MySearchResults : CustomTitlebarActivityBase() {
    private lateinit var binding: SearchResultsStatisticsBinding
    private var mSearchResultsHolder: SearchResultsDto? = null
    private lateinit var sectionsPagerAdapter: SearchResultsPagerAdapter
    val mSearchResultsArray = ArrayList<SearchResultsData>()
    private val bookStatistics = mutableListOf<BookStat>()
    private val wordStatistics = mutableListOf<WordStat>()

    @Inject lateinit var navigationControl: NavigationControl
    private var isScriptureResultsCurrentlyShown = true
    @Inject lateinit var searchResultsActionBarManager: SearchResultsActionBarManager
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    /** Called when the activity is first created.  */

    private val totalTime = MyTimer("Total Time")

    private val versification: Versification
        get() = navigationControl.versification

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState, true)
        Log.i(TAG, "Displaying Search results view")
        totalTime.reset()

        binding = SearchResultsStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildActivityComponent().inject(this)

        val bundle = Bundle()
        bundle.putString("edttext", "From Activity")

        sectionsPagerAdapter = SearchResultsPagerAdapter(this,
                                                                supportFragmentManager, searchControl, activeWindowPageManagerProvider, intent,
                                                                mSearchResultsArray, bookStatistics, wordStatistics)
        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        viewPager.offscreenPageLimit = 2    // The progressbar on the 3rd tab goes to zero when the view is lost. So just keep it in memory and all is fine. It is not a big view so i think it is ok.
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

        searchResultsActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener)
        setActionBarManager(searchResultsActionBarManager)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isScriptureResultsCurrentlyShown = searchControl.isCurrentlyShowingScripture

        GlobalScope.launch {
            prepareResults()
        }
    }

    private suspend fun prepareResults() {
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.VISIBLE
            binding.empty.visibility = View.GONE
        }
        if (fetchSearchResults()) { // initialise adapters before result population - easier when updating due to later Scripture toggle
            withContext(Dispatchers.Main) {
                populateViewResultsAdapter()
            }
        }
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.GONE
//            if(listAdapter?.isEmpty == true) {
//                binding.empty.visibility = View.VISIBLE
//            }
        }
    }

    private suspend fun fetchSearchResults(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Preparing search results")
        var isOk: Boolean
        try { // get search string - passed in using extras so extras cannot be null
            val fetchResultsTimer = MyTimer("fetchResultsTimer")
            val extras = intent.extras
            val searchText = extras!!.getString(SearchControl.SEARCH_TEXT)
            var searchDocument = extras.getString(SearchControl.SEARCH_DOCUMENT)
            if (StringUtils.isEmpty(searchDocument)) {
                searchDocument = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument!!.initials
            }
            mSearchResultsHolder = searchControl.getSearchResults(searchDocument, searchText)
            // tell user how many results were returned
            val msg:String = if (mCurrentlyDisplayedSearchResults.size >= SearchControl.MAX_SEARCH_RESULTS) {
                getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS)
            } else {
                getString(R.string.search_result_count, mSearchResultsHolder!!.size())
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MySearchResults, msg, Toast.LENGTH_SHORT).show()
            }
            isOk = true
            fetchResultsTimer.stop(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing search query", e)
            isOk = false
            Dialogs.instance.showErrorMsg(R.string.error_executing_search) { onBackPressed() }
        }
    return@withContext isOk
    }

    /**
     * Move search results into view Adapter
     */

    private fun populateViewResultsAdapter() {

        val populateViewResultsAdapterTimer = MyTimer("populateViewResultsAdapterTimer")
        val getSearchResultVerseElementTimer = MyTimer("getSearchResultVerseElement")
        val verseTextSpannableTimer = MyTimer("SearchHighlight")
        val bookStatsTimer = MyTimer("Book Stats")
        val wordStatsTimer = MyTimer("Word Stats")

        mCurrentlyDisplayedSearchResults = if (isScriptureResultsCurrentlyShown) {
            mSearchResultsHolder!!.mainSearchResults
        } else {
            mSearchResultsHolder!!.otherSearchResults
        }
        val extras = intent.extras
        var searchDocument = extras!!.getString(SearchControl.SEARCH_DOCUMENT)

        mSearchResultsArray!!.clear()
        bookStatistics.clear()
        wordStatistics.clear()

        var listIndex = 0
        var totalWords = 0
        val searchHighlight = SearchHighlight(SearchControl.originalSearchString)
        for (key in mCurrentlyDisplayedSearchResults) {
            // Add verse to results array

            var verseTextSpannable: SpannableString? = null
            getSearchResultVerseElementTimer.start()
            val verseTextElement = searchControl.getSearchResultVerseElement(key)
            getSearchResultVerseElementTimer.stop()

            verseTextSpannableTimer.start()
            verseTextSpannable = searchHighlight.generateSpannableFromVerseElement(verseTextElement)
            verseTextSpannableTimer.stop()

            mSearchResultsArray.add(SearchResultsData(listIndex, key.osisID.toString(), key.name,searchDocument, "text", verseTextSpannable!!.toHtml()))

            // Add book to the book statistics array
            bookStatsTimer.start()
            val bookOrdinal = ((key as Verse).book as BibleBook).ordinal
            var mBibleBook = BibleBook.values()[bookOrdinal]
            var bookNameLong = versification.getLongName(mBibleBook)  // key.rootName
            val bookStat = bookStatistics.firstOrNull{it.book == bookNameLong}
            if (bookStat == null) {
                bookStatistics.add(BookStat(bookNameLong, 1, bookNameLong, bookOrdinal, listIndex, getBookTextColor(bookOrdinal) ))
            } else {
                bookStatistics.first{it.book == bookNameLong}.count += 1
            }
            bookStatsTimer.stop()

            // Add words in this verse to word statistics array
            wordStatsTimer.start()
            var verseSpans: Array<StyleSpan> = verseTextSpannable!!.getSpans(0, verseTextSpannable?.length, StyleSpan::class.java)
            for (i in verseSpans.indices) {
                if (verseSpans[i].getStyle() === Typeface.BOLD) {
                    totalWords += 1
                    val start = verseTextSpannable.getSpanStart(verseSpans[i])
                    val end = verseTextSpannable.getSpanEnd(verseSpans[i])

                    var wordArray = CharArray(end-start)
                    verseTextSpannable.getChars(start, end, wordArray, 0)
                    val originalWord = wordArray.joinToString("")
                    val word = originalWord.lowercase()
                    val wordStat = wordStatistics.firstOrNull{it.word == word}
                    if (wordStat == null) {
                        wordStatistics.add(WordStat(word, intArrayOf(listIndex), originalWord))
                    } else {
                        wordStatistics.first{it.word == word}.verseIndexes += listIndex
                    }
                }
            }
            wordStatsTimer.stop()
            listIndex += 1
        }
        sectionsPagerAdapter.verseListFrag.arrayAdapter.notifyDataSetChanged()
        sectionsPagerAdapter.getItem(bookTabPosition)
        sectionsPagerAdapter.getItem(wordTabPosition)
        sectionsPagerAdapter.notifyDataSetChanged()

        val tabHost = this.findViewById<View>(R.id.tabs) as TabLayout
        tabHost.getTabAt(verseTabPosition)!!.text = CommonUtils.resources.getString(R.string.verse_count, mSearchResultsArray.count().toString())  // For some reason the value set in 'setResultsAdapter' get's cleared so I need to do it here as well.
        tabHost.getTabAt(bookTabPosition)!!.text = CommonUtils.resources.getString(R.string.book_count, bookStatistics.count().toString())
        tabHost.getTabAt(wordTabPosition)!!.text = CommonUtils.resources.getString(R.string.word_count, totalWords.toString())

        populateViewResultsAdapterTimer.stop()
        populateViewResultsAdapterTimer.log()
        getSearchResultVerseElementTimer.log()
        verseTextSpannableTimer.log()
        bookStatsTimer.log()
        wordStatsTimer.log()
        totalTime.stop(true)
    }
    /**
     * Handle scripture/Appendix toggle
     */
    private val scriptureToggleClickListener = View.OnClickListener {
        isScriptureResultsCurrentlyShown = !isScriptureResultsCurrentlyShown
        populateViewResultsAdapter()
//        mKeyArrayAdapter!!.notifyDataSetChanged()
        searchResultsActionBarManager.setScriptureShown(isScriptureResultsCurrentlyShown)
    }

    companion object {
        private const val TAG = " MySearchResults"
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_2
    }
}

class SearchResultsPagerAdapter(private val context: Context, fm: FragmentManager,
                                searchControl: SearchControl,
                                activeWindowPageManagerProvider: ActiveWindowPageManagerProvider,
                                intent: Intent,
                                val mSearchResultsArray:ArrayList<SearchResultsData>,
                                val bookStatistics: MutableList<BookStat>,
                                val wordStatistics: MutableList<WordStat>
) :
    FragmentPagerAdapter(fm) {
    val searchControl = searchControl
    val activeWindowPageManagerProvider = activeWindowPageManagerProvider
    val intent = intent
    lateinit var verseListFrag: SearchResultsFragment

    override fun getItem(position: Int): Fragment {
        // getItem is called to instantiate the fragment for the given page.
        // This initialises the fragment but only if POSITION_NONE is returned from getItemPosition.
        // This happens naturally when pages are moving in and out of view but not when we try to refresh.
        // The individual fragment code gets called twice - first with no data to display and then with the correct data.
        // This is because the page is shown in the GlobalScope in order to allow us to show a progress indicator.
        var frag: Fragment
        when (position) {
            verseTabPosition -> {
                frag = SearchResultsFragment(mSearchResultsArray)
//                val bundle = Bundle()
//                bundle.putString("edttext", "From Activity")
//                bundle.putParcelableArrayList("VerseResultList", mSearchResultsArray)
//                frag.setArguments(bundle)
                frag.searchControl = searchControl
                frag.activeWindowPageManagerProvider = activeWindowPageManagerProvider
                frag.intent = intent
                verseListFrag = frag
            }
            bookTabPosition-> {
                frag = SearchBookStatisticsFragment()
                frag.bookStatistics = bookStatistics
                frag.searchResultsArray = mSearchResultsArray
//                bookStatisticsFrag = frag
            }
            2-> {
                frag = SearchWordStatisticsFragment()
                frag.searchResultsArray = mSearchResultsArray
                frag.wordStatistics = wordStatistics
            }
            else -> frag = PlaceholderFragment.newInstance(1)
        }
        return frag
    }
    override fun getItemPosition(`object`: Any): Int {
        // This line is needed to force a refresh of the fragment.
        // It doesn't seem to affect anything adversely, I think because all tabs remain in memory the whole time.
        return POSITION_NONE
    }
    override fun getCount(): Int {
        return 3
    }
}
