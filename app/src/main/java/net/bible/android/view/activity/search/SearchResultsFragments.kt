package net.bible.android.view.activity.search

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import net.bible.android.activity.databinding.SearchResultsStatisticsFragmentVerseBinding
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SearchResultsStatisticsFragmentByBinding
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.MainBibleActivity
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordDocumentFacade

private lateinit var displayedResultsArray: ArrayList<SearchResultsData>
private var isSearchResultsFiltered = false

class PlaceholderFragment: Fragment() {

    private var _binding: SearchResultsStatisticsFragmentVerseBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = SearchResultsStatisticsFragmentVerseBinding.inflate(inflater, container, false)
        val root = binding.root

        return root
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private fun setResultsAdapter(resultsArray:ArrayList<SearchResultsData>, activity: Activity): SearchResultsAdapter {
    // This function is called from both the SearchResultsFragment and the SearchWordStatistics (for filtering)
    val tabhost = activity.findViewById<View>(R.id.tabs) as TabLayout
    val verseCount = if(resultsArray.count()==0) "..." else resultsArray.count().toString()
    tabhost.getTabAt(verseTabPosition)!!.text = CommonUtils.resources.getString(R.string.verse_count, verseCount)  // The count needs to be set here because it can be changed when filtering the list by word

    displayedResultsArray = resultsArray
    return SearchResultsAdapter(activity, android.R.layout.simple_list_item_2,
        displayedResultsArray as java.util.ArrayList<SearchResultsData>?
    )
}
class SearchResultsFragment(val mSearchResultsArray:ArrayList<SearchResultsData>) : Fragment() {
    private var _binding: SearchResultsStatisticsFragmentVerseBinding? = null
    private val binding get() = _binding!!
    lateinit var arrayAdapter: ArrayAdapter<SearchResultsData>

    var searchControl: SearchControl? = null
    lateinit var intent: Intent
    lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = SearchResultsStatisticsFragmentVerseBinding.inflate(inflater, container, false)
        val root = binding.root

        val resultList: ListView = binding.searchResultsList

        arrayAdapter = setResultsAdapter(mSearchResultsArray, requireActivity())
        resultList.adapter = arrayAdapter
        resultList.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS;
        resultList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        resultList.isVerticalScrollBarEnabled = false

        resultList.setOnItemClickListener { parent, view, position, id ->
            Toast.makeText(resultList.context, "Hi",Toast.LENGTH_LONG)
            try { // no need to call HistoryManager.addHistoryItem() here because PassageChangeMediator will tell HistoryManager a change is about to occur
                verseSelected(mCurrentlyDisplayedSearchResults[displayedResultsArray[position].id!!])
            } catch (e: Exception) {
                Log.e("SearchResults", "Selection error", e)
                Dialogs.showErrorMsg(R.string.error_occurred, e)
            }
        }
        return root
    }

    private fun verseSelected(key: Key?) {
        Log.i("SearchResults.TAG", "chose:$key")
        if (key != null) { // which doc do we show

            var targetDocInitials = intent.extras!!.getString(SearchControl.TARGET_DOCUMENT)
            if (StringUtils.isEmpty(targetDocInitials)) {
                targetDocInitials = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument!!.initials
            }
            val swordDocumentFacade = SwordDocumentFacade()
            val targetBook = swordDocumentFacade.getDocumentByInitials(targetDocInitials)
            activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocumentAndKey(targetBook, key)
            // this also calls finish() on this Activity.  If a user re-selects from HistoryList then a new Activity is created
            val intent = Intent(this.context, MainBibleActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SearchBookStatisticsFragment : Fragment() {
    private var _binding: SearchResultsStatisticsFragmentByBinding? = null

    private val binding get() = _binding!!
    var bookStatistics = mutableListOf<BookStat>()
     lateinit var searchResultsArray: ArrayList<SearchResultsData>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = SearchResultsStatisticsFragmentByBinding.inflate(inflater, container, false)
        val root = binding.root

        val statisticsLayout = binding.statisticsLayout
        binding.showKeyWordOnly.visibility = View.GONE

        val maxCount: Int = bookStatistics.maxOfOrNull { it.count } ?: 0
        bookStatistics.map {

            val statsRow: View = inflater.inflate(
                R.layout.search_results_statistics_row_by_book,
                statisticsLayout, false
            )
            var button = statsRow.findViewById<Button>(R.id.searchStatisticsBookButton)
            button.text = it.book
            var text = statsRow.findViewById<TextView>(R.id.searchStatisticsBookCount)
            text.text = "${it.count}"
            var progressBar = statsRow.findViewById<ProgressBar>(R.id.searchStatisticsBookCountProgress)
            progressBar.max = maxCount
            progressBar.progress = it.count
            progressBar.progressTintList = ColorStateList.valueOf(it.color)
            statsRow.visibility = View.VISIBLE

            statisticsLayout.addView(statsRow,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 90)
            )

            button.tag = it.listIndex
            button.setOnClickListener {

                val tabhost = requireActivity().findViewById<View>(R.id.tabs) as TabLayout
                tabhost.getTabAt(0)!!.select()
                val resultList = requireActivity().findViewById<View>(R.id.searchResultsList) as ListView
                if (isSearchResultsFiltered) {
                    resultList.adapter = setResultsAdapter(searchResultsArray, requireActivity())
                    isSearchResultsFiltered = false
                }
                resultList.setSelection(it.tag as Int);
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SearchWordStatisticsFragment() : Fragment() {
    var wordStatistics = mutableListOf<WordStat>()
    var keyWordStatistics = mutableListOf<WordStat>()
    var searchResultsArray = arrayListOf<SearchResultsData>()

    private var _binding: SearchResultsStatisticsFragmentByBinding? = null
    private val binding get() = _binding!!
    private lateinit var inflater: LayoutInflater
    private lateinit var statsRow: View

    class CustomOnClickListener(val resultIndexes: IntArray, val activity:Activity, val searchResultsArray:ArrayList<SearchResultsData>) : View.OnClickListener {
        override fun onClick(v: View?) {
            isSearchResultsFiltered = true
            val tabhost = activity.findViewById<View>(R.id.tabs) as TabLayout
            tabhost.getTabAt(0)!!.select()

            val filteredSearchResults = searchResultsArray.filter { resultIndexes.contains(it.id!!) } as ArrayList<SearchResultsData>
            val resultList = activity.findViewById<View>(R.id.searchResultsList) as ListView
            resultList.adapter = setResultsAdapter(filteredSearchResults, activity)
        }
    }
    private fun constructButtonList(keyWordsOnly: Boolean = false) {

        val statisticsLayout = binding.statisticsLayout
        val sortedWordStatistics = if(keyWordsOnly) keyWordStatistics.sortedBy { it.word } else wordStatistics.sortedBy { it.word }
        val maxCount: Int = sortedWordStatistics.maxOfOrNull { it.verseIndexes.count() } ?: 0
        statisticsLayout.removeAllViews()

        // TODO: This works but it kills the progress bars for ALL buttons. Why??????
//        val statsRow: View = inflater.inflate(
//            R.layout.search_results_statistics_row_by_word,
//            statisticsLayout, false
//        )
//        val button = statsRow.findViewById<Button>(R.id.searchStatisticsWordButton)
//        button.text = "ALL"
//        val text = statsRow.findViewById<TextView>(R.id.searchStatisticsWordCount)
//        text.text = "${sortedWordStatistics.sumOf {it.verseIndexes.count()}}"
//        val progressBar = statsRow.findViewById<ProgressBar>(R.id.searchStatisticsWordCountProgress)
//        progressBar.visibility = View.GONE
//        statsRow.requestLayout()
//        statisticsLayout.addView(statsRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
//        button.setOnClickListener(CustomOnClickListener(sortedWordStatistics.flatMap { it.verseIndexes.toList() }.toIntArray(), requireActivity(), searchResultsArray))

        sortedWordStatistics.map {
            val statsRow: View = inflater.inflate(
                R.layout.search_results_statistics_row_by_word,
                statisticsLayout, false
            )
            val button = statsRow.findViewById<Button>(R.id.searchStatisticsWordButton)
            button.text = it.originalWord
            val text = statsRow.findViewById<TextView>(R.id.searchStatisticsWordCount)
            text.text = "${it.verseIndexes.count()}"
            val progressBar = statsRow.findViewById<ProgressBar>(R.id.searchStatisticsWordCountProgress)
            progressBar.max = maxCount
            progressBar.progress = it.verseIndexes.count()
            statsRow.requestLayout()
            statisticsLayout.addView(statsRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            button.setOnClickListener(CustomOnClickListener(it.verseIndexes, requireActivity(), searchResultsArray))
        }
    }
    override fun onCreateView(
        theInflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        inflater = theInflater
        _binding = SearchResultsStatisticsFragmentByBinding.inflate(inflater, container, false)
        var showKeyWordsCheckBox = binding.showKeyWordOnly

        constructButtonList(showKeyWordsCheckBox.isChecked)
        showKeyWordsCheckBox.setOnClickListener {
            constructButtonList(showKeyWordsCheckBox.isChecked)
        }
        return binding.root
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

