package net.bible.android.view.activity.search

import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import android.widget.ListView
import android.widget.TabHost
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import net.bible.android.activity.databinding.SearchResultsVerseFragmentBinding
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SearchResultsLayoutActivityBinding
import net.bible.android.activity.databinding.SearchResultsStatisticsFragmentBinding
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.MainBibleActivity
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.navigation.GridChoosePassageBook.Companion.getBookTextColor
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.common.CommonUtils

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
/**
 * A placeholder fragment containing a simple view.
 */

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

class SearchStatisticsFragment : Fragment() {
    private var _binding: SearchResultsStatisticsFragmentBinding? = null

    private val binding get() = _binding!!
    var bookStatistics = mutableListOf<BookStat>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = SearchResultsStatisticsFragmentBinding.inflate(inflater, container, false)
        val root = binding.root

        val buttonLayout: ConstraintLayout = _binding!!.buttonLayout
        val flowContainer: Flow = _binding!!.flowContainer

        var buttonIds = intArrayOf()

        bookStatistics.map {
            val newButton = Button(flowContainer.context)
            newButton.setLayoutParams(LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            newButton.setText("${it.book} = ${it.count}")
            newButton.id = View.generateViewId()
            newButton.isAllCaps = false
            newButton.tag = it.listIndex
//            newButton.setPadding(20)
//            val drawable = resources.getDrawable(R.drawable.search_result_statistics_button)
//            val gd = GradientDrawable(
//                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(R.color.accent_day, R.color.blue_100, R.color.red)
//            )
//            gd.setCornerRadius(0f)
////            newButton.setBackgroundResource(R.drawable.search_result_statistics_button)
//            newButton.setBackground(gd)

//            newButton.setBackgroundResource(R.drawable.search_result_statistics_button)

            newButton.setOnClickListener {
                val tabhost = requireActivity().findViewById<View>(R.id.tabs) as TabLayout
                tabhost.getTabAt(0)!!.select()
//                tabs.getTabAt(1)?.select()
////                viewPager.currentItem = 0
                val resultLis = requireActivity().findViewById<View>(R.id.searchResultsList) as ListView
//                resultLis.smoothScrollToPosition(it.tag as Int);
//                val position: Int = resultLis.getPositionForView(tabhost)
//                resultLis.setSelection(position+1)
                resultLis.smoothScrollToPosition(it.tag as Int);
                Toast.makeText(buttonLayout.context, newButton.text, Toast.LENGTH_LONG)
            }
            buttonLayout.addView(newButton)
            buttonIds += newButton.id
        }
        flowContainer.referencedIds = buttonIds

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

class PlaceholderFragment : Fragment() {

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
