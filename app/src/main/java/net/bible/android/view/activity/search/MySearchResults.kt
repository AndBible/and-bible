package net.bible.android.view.activity.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import net.bible.android.activity.databinding.SearchResultsLayoutActivityBinding
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
    resources.getString(R.string.results_count),
    resources.getString(R.string.by_book),
    resources.getString(R.string.by_word)
)
private val mSearchArrayAdapter = ArrayList<SearchResultsData>()
private var mCurrentlyDisplayedSearchResults: List<Key> = ArrayList()
private val bookStatistics = mutableListOf<BookStat>()

class BookStat(val book: String, var count: Int,
               val bookInitials: String,
               val bookOrdinal:Int,
               val listIndex:Int,
                val color: Int) {
    override fun toString(): String = "$book: $count"
}

class SearchResultsData : Parcelable {
    @JvmField
    var id: Int?
    @JvmField
    var osisKey: String?
    @JvmField
    var reference: String?
    @JvmField
    var translation: String?
    @JvmField
    var verse: String?
    /* What I really want to do is make the 'Key' parcelable but I don't know how to do that. So instead I have to send the properties I need and get the key later on */

    constructor(Id: Int?, OsisKey: String?, Reference: String?, Translation: String?, Verse: String?) {
        id = Id;
        osisKey = OsisKey
        reference = Reference
        translation = Translation
        verse = Verse
    }

    protected constructor(`in`: Parcel) {
        id = `in`.readInt()
        osisKey = `in`.readString()
        reference = `in`.readString()
        translation = `in`.readString()
        verse = `in`.readString()
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
//    private lateinit var binding: ListBinding
    private lateinit var binding: SearchResultsLayoutActivityBinding
    private var mSearchResultsHolder: SearchResultsDto? = null

    /*  mKeyArrayAdapter is replaced by mSearchArrayAdapter */
//    private var mKeyArrayAdapter: ArrayAdapter<Key>? = null

    @Inject lateinit var navigationControl: NavigationControl
    private var isScriptureResultsCurrentlyShown = true
    @Inject lateinit var searchResultsActionBarManager: SearchResultsActionBarManager
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    /** Called when the activity is first created.  */

    private val versification: Versification
        get() = navigationControl.versification

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState, true)
        Log.i(TAG, "Displaying Search results view")

        binding = SearchResultsLayoutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildActivityComponent().inject(this)

        val bundle = Bundle()
        bundle.putString("edttext", "From Activity")

        val fragobj = SearchResultsFragment()
        fragobj.setArguments(bundle)

        val sectionsPagerAdapter = SearchResultsPagerAdapter(this, supportFragmentManager, searchControl, activeWindowPageManagerProvider, intent)
        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

        searchResultsActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener)
        setActionBarManager(searchResultsActionBarManager)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isScriptureResultsCurrentlyShown = searchControl.isCurrentlyShowingScripture

/*      I don't  have a close button so I don't see why this is necessary */
//        binding.closeButton.setOnClickListener {
//            finish()
//        }

/*      The GlobalScope code almost works fine. Only trouble is that the search results are delayed by one search.
        That is, the first time the search is done no results are shown. The second time, the results from the previous
        search are shown and so on. I believe it is because the Fragment manager is not hooked into the scope so it is
        processed immediately and doesn't  wait for the code inside the scope to complete. But i don't know how to fix
        that problem.
*/
//        GlobalScope.launch {
            prepareResults()
//        }
    }

//    private suspend fun prepareResults() {
    private fun prepareResults() {
//        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.VISIBLE
            binding.empty.visibility = View.GONE
//        }
        if (fetchSearchResults()) { // initialise adapters before result population - easier when updating due to later Scripture toggle
//            withContext(Dispatchers.Main) {
//                mKeyArrayAdapter = SearchItemAdapter(this@MySearchResults, SearchResults.LIST_ITEM_TYPE, mCurrentlyDisplayedSearchResults, searchControl)
//                listAdapter = mKeyArrayAdapter as ListAdapter
                populateViewResultsAdapter()
//            }
        }
//        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.GONE
//            if(listAdapter?.isEmpty == true) {
//                binding.empty.visibility = View.VISIBLE
//            }
//        }
    }

//    private suspend fun fetchSearchResults(): Boolean = withContext(Dispatchers.IO) {
    private fun fetchSearchResults(): Boolean {
        Log.i(TAG, "Preparing search results")
        var isOk: Boolean
        try { // get search string - passed in using extras so extras cannot be null
            val extras = intent.extras
            val searchText = extras!!.getString(SearchControl.SEARCH_TEXT)
            var searchDocument = extras.getString(SearchControl.SEARCH_DOCUMENT)
            if (StringUtils.isEmpty(searchDocument)) {
                searchDocument = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument!!.initials
            }
            mSearchResultsHolder = searchControl.getSearchResults(searchDocument, searchText)
            // tell user how many results were returned
            val msg: String
            msg = if (mCurrentlyDisplayedSearchResults.size >= SearchControl.MAX_SEARCH_RESULTS) {
                getString(R.string.search_showing_first, SearchControl.MAX_SEARCH_RESULTS)
            } else {
                getString(R.string.search_result_count, mSearchResultsHolder!!.size())
            }
//            withContext(Dispatchers.Main) {
                Toast.makeText(this@MySearchResults, msg, Toast.LENGTH_SHORT).show()
//            }
            isOk = true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing search query", e)
            isOk = false
            Dialogs.instance.showErrorMsg(R.string.error_executing_search) { onBackPressed() }
        }
//    return@withContext isOk
    return isOk
    }

    /**
     * Move search results into view Adapter
     */
    private fun populateViewResultsAdapter() {

        mCurrentlyDisplayedSearchResults = if (isScriptureResultsCurrentlyShown) {
            mSearchResultsHolder!!.mainSearchResults
        } else {
            mSearchResultsHolder!!.otherSearchResults
        }
        val extras = intent.extras
        var searchDocument = extras!!.getString(SearchControl.SEARCH_DOCUMENT)

        mSearchArrayAdapter!!.clear()
        bookStatistics.clear()
        var listIndex = 0
        for (key in mCurrentlyDisplayedSearchResults) {
            var text = searchControl.getSearchResultVerseText(key)
            mSearchArrayAdapter.add(SearchResultsData(1, key.osisID.toString(), key.name,searchDocument, text))

            // Get the text of the verse
            val bookOrdinal = ((key as Verse).book as BibleBook).ordinal
            var mBibleBook = BibleBook.values()[bookOrdinal]
            var bookNameLong = versification.getLongName(mBibleBook)  // key.rootName
            val bookStat = bookStatistics.firstOrNull{it.book == bookNameLong}
            if (bookStat == null) {
                bookStatistics.add(BookStat(bookNameLong, 1, bookNameLong, bookOrdinal, listIndex, getBookTextColor(bookOrdinal) ))
            } else {
                bookStatistics.first{it.book == bookNameLong}.count += 1
            }
            listIndex += 1
        }
        TAB_TITLES[0] = resources.getString(R.string.results_count, listIndex)
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
                                intent: Intent
) :
    FragmentPagerAdapter(fm) {
    val searchControl = searchControl
    val activeWindowPageManagerProvider = activeWindowPageManagerProvider
    val intent = intent

    override fun getItem(position: Int): Fragment {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).
        var frag: Fragment
        when (position) {
            0 -> {
                frag = SearchResultsFragment.newInstance(1)
                val bundle = Bundle()
                bundle.putString("edttext", "From Activity")
                bundle.putParcelableArrayList("mylist", mSearchArrayAdapter)
                frag.setArguments(bundle)
                frag.searchControl = searchControl
                frag.mCurrentlyDisplayedSearchResults = mCurrentlyDisplayedSearchResults
                frag.activeWindowPageManagerProvider = activeWindowPageManagerProvider
                frag.intent = intent
            }
            1-> {
                frag = SearchStatisticsFragment()
                frag.bookStatistics = bookStatistics
            }
            else -> frag = PlaceholderFragment.newInstance(1)
        }

        return frag
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return TAB_TITLES[position]
    }

    override fun getCount(): Int {
        // Show 2 total pages.
        return 2
    }
}
