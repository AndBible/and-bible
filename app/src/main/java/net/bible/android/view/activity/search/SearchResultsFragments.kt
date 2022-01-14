package net.bible.android.view.activity.search

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import net.bible.android.activity.databinding.SearchResultsVerseFragmentBinding
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SearchResultsStatisticsFragmentBinding
import net.bible.android.activity.databinding.SearchResultsStatisticsRowBinding
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.MainBibleActivity
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.service.sword.SwordDocumentFacade

class PlaceholderFragment: Fragment() {

    //    private lateinit var pageViewModel: PageViewModel
    private var _binding: SearchResultsVerseFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = SearchResultsVerseFragmentBinding.inflate(inflater, container, false)
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

class SearchResultsFragment : Fragment() {

    private var _binding: SearchResultsVerseFragmentBinding? = null
    private val binding get() = _binding!!

    var mCurrentlyDisplayedSearchResults: List<Key> = java.util.ArrayList()
    var searchControl: SearchControl? = null
    lateinit var intent: Intent
    lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = SearchResultsVerseFragmentBinding.inflate(inflater, container, false)
        val root = binding.root

        val strtext = requireArguments().getString("edttext")
        val arr: ArrayList<SearchResultsData> = requireArguments().getParcelableArrayList("mylist")!!

        val customAdapter = SearchResultsAdapter(activity, android.R.layout.simple_list_item_2, arr, searchControl)

        val resultList: ListView = binding.searchResultsList
        resultList.adapter = customAdapter
        resultList.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS;
        resultList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        resultList.isVerticalScrollBarEnabled = false

        resultList.setOnItemClickListener { parent, view, position, id ->
            Toast.makeText(resultList.context, "Hi",Toast.LENGTH_LONG)
            try { // no need to call HistoryManager.addHistoryItem() here because PassageChangeMediator will tell HistoryManager a change is about to occur
                verseSelected(mCurrentlyDisplayedSearchResults[position])
            } catch (e: Exception) {
                Log.e("blah", "Selection error", e)
                Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
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
        fun newInstance(sectionNumber: Int): SearchResultsFragment {
            return SearchResultsFragment().apply {
//                arguments = Bundle().apply {
//                    putInt(ARG_SECTION_NUMBER, sectionNumber)
//                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SearchBookStatisticsFragment : Fragment() {
    private var _binding: SearchResultsStatisticsFragmentBinding? = null

    private val binding get() = _binding!!
    var bookStatistics = mutableListOf<BookStat>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = SearchResultsStatisticsFragmentBinding.inflate(inflater, container, false)
        var rowBinding = SearchResultsStatisticsRowBinding.inflate(inflater, container, false)
        val root = binding.root

        val statisticsLayout = binding.statisticsLayout

        var buttonIds = intArrayOf()

        val maxCount: Int = bookStatistics.maxOfOrNull { it.count } ?: 0
        bookStatistics.map {

            val statsRow: View = inflater.inflate(
                R.layout.search_results_statistics_row,
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
                resultList.setSelection(it.tag as Int);
            }
        }

        return root
    }

//    companion object {
//        /**
//         * Use this factory method to create a new instance of
//         * this fragment using the provided parameters.
//         *
//         * @param param1 Parameter 1.
//         * @param param2 Parameter 2.
//         * @return A new instance of fragment SearchStatisticsFragment.
//         */
//        // TODO: Rename and change types and number of parameters
//        @JvmStatic
//        fun newInstance() =
//            SearchStatisticsFragment().apply {
//                arguments = Bundle().apply {
////                    putString(ARG_PARAM1, param1)
////                    putString(ARG_PARAM2, param2)
//                }
//            }
//    }
}

class SearchWordStatisticsFragment : Fragment() {
    private var _binding: SearchResultsStatisticsFragmentBinding? = null

    private val binding get() = _binding!!
    var wordStatistics = mutableListOf<WordStat>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = SearchResultsStatisticsFragmentBinding.inflate(inflater, container, false)
        val root = binding.root

        val statisticsLayout = binding.statisticsLayout
        val sortedWordStatistics = wordStatistics.sortedBy { it.word }
        val maxCount: Int = sortedWordStatistics.maxOfOrNull { it.verseIndexes.count() } ?: 0
        sortedWordStatistics.map {

            val statsRow: View = inflater.inflate(
                R.layout.search_results_word_statistics_row,
                statisticsLayout, false
            )
            var button = statsRow.findViewById<Button>(R.id.searchStatisticsWordButton)
            button.setText(it.originalWord)
            var text = statsRow.findViewById<TextView>(R.id.searchStatisticsWordCount)
            text.setText("${it.verseIndexes.count()}")
            var progressBar = statsRow.findViewById<ProgressBar>(R.id.searchStatisticsWordCountProgress)
            progressBar.max = maxCount
            progressBar.progress = it.verseIndexes.count()
            statsRow.visibility = View.VISIBLE
            statsRow.invalidate()
            statsRow.requestLayout()
            statisticsLayout.addView(statsRow,
//                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 90)
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            )
        }
        return root
    }
}

