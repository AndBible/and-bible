package net.bible.android.view.activity.search

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.appcompat.app.AppCompatActivity
import ir.smartdevelop.eram.showcaseview.PlaceholderFragment
import ir.smartdevelop.eram.showcaseview.SearchResultsFragment
import ir.smartdevelop.eram.showcaseview.SearchStatisticsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.databinding.SearchResultsLayoutActivityBinding
import java.util.ArrayList
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ListBinding
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchResultsDto
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager
import net.bible.service.common.CommonUtils.buildActivityComponent
import net.bible.service.sword.SwordContentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import javax.inject.Inject

private val TAB_TITLES = arrayOf(
    R.string.results,
    R.string.statistics
)
private val arrayList = ArrayList<SearchResultsData>()


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

class MySearchResults : AppCompatActivity() {
//    private lateinit var binding: ListBinding
    private lateinit var binding: SearchResultsLayoutActivityBinding
    private var mSearchResultsHolder: SearchResultsDto? = null
    private var mCurrentlyDisplayedSearchResults: List<Key> = ArrayList()
    private var mKeyArrayAdapter: ArrayAdapter<Key>? = null
    private var isScriptureResultsCurrentlyShown = true
    @Inject lateinit var searchResultsActionBarManager: SearchResultsActionBarManager
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Search results view")

        binding = SearchResultsLayoutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildActivityComponent().inject(this)

        val bundle = Bundle()
        bundle.putString("edttext", "From Activity")

        val fragobj = SearchResultsFragment()
        fragobj.setArguments(bundle)

        val sectionsPagerAdapter = SearchResultsPagerAdapter(this, supportFragmentManager, searchControl)
        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

//        searchResultsActionBarManager.registerScriptureToggleClickListener(scriptureToggleClickListener)
//        setActionBarManager(searchResultsActionBarManager)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isScriptureResultsCurrentlyShown = searchControl.isCurrentlyShowingScripture
//        binding.closeButton.setOnClickListener {
//            finish()
//        }
//        GlobalScope.launch {
//            prepareResults()
//        }
        prepareResults()

    }

    private fun prepareResults() {
//        withContext(Dispatchers.Main) {
//            binding.loadingIndicator.visibility = View.VISIBLE
//            binding.empty.visibility = View.GONE
//        }
        if (fetchSearchResults()) { // initialise adapters before result population - easier when updating due to later Scripture toggle
//            withContext(Dispatchers.Main) {
//                mKeyArrayAdapter = SearchItemAdapter(this@MySearchResults, SearchResults.LIST_ITEM_TYPE, mCurrentlyDisplayedSearchResults, searchControl)
//                listAdapter = mKeyArrayAdapter as ListAdapter
                populateViewResultsAdapter()
//            }
        }
//        withContext(Dispatchers.Main) {
//            binding.loadingIndicator.visibility = View.GONE
//            if(listAdapter?.isEmpty == true) {
//                binding.empty.visibility = View.VISIBLE
//            }
//        }
    }

    private  fun fetchSearchResults(): Boolean {
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
        // addAll is only supported in Api 11+
//        mKeyArrayAdapter!!.clear()
//        for (key in mCurrentlyDisplayedSearchResults) {
//            mKeyArrayAdapter!!.add(key)
//        }

        arrayList!!.clear()
        for (key in mCurrentlyDisplayedSearchResults) {
            var text = searchControl.getSearchResultVerseText(key)
            arrayList.add(SearchResultsData(1,key.osisRef.toString() , key.osisID.toString(),"KJV",text))
        }
    }
    companion object {
        private const val TAG = " MySearchResults"
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_2
    }

}

//class SearchResultsListActivity : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        val arrayList = ArrayList<SearchResultsData>()
//        val customAdapter = SearchResultsAdapter(this, arrayList, searchControl)
//        val list = findViewById<ListView>(R.id.list)
//        list.adapter = customAdapter
//    }
//}

class SearchResultsPagerAdapter(private val context: Context, fm: FragmentManager, searchControl: SearchControl) :
    FragmentPagerAdapter(fm) {
    val searchControl = searchControl

    override fun getItem(position: Int): Fragment {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).
        var frag: Fragment
        when (position) {
            0 -> {
                frag = SearchResultsFragment.newInstance(1)
                val bundle = Bundle()
                bundle.putString("edttext", "From Activity")
                bundle.putParcelableArrayList("mylist", arrayList)
//                val fragobj = SearchResultsFragment()
                frag.setArguments(bundle)
                frag.setEmployee(searchControl)

            }
            1-> frag = SearchStatisticsFragment.newInstance("a","b")
            else -> frag = PlaceholderFragment.newInstance(1)
        }

        return frag
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        // Show 2 total pages.
        return 2
    }
}
