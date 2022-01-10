package net.bible.android.view.activity.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import android.widget.ListView
import android.widget.Toast
import androidx.lifecycle.*
import net.bible.android.activity.databinding.SearchResultsVerseFragmentBinding
import net.bible.android.view.activity.search.SearchResultsAdapter
import net.bible.android.view.activity.search.SearchResultsData
import net.bible.android.activity.R
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.MainBibleActivity
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.service.sword.SwordDocumentFacade

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
/**
 * A placeholder fragment containing a simple view.
 */

class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    val text: LiveData<String> = Transformations.map(_index) {
        "Hello world from section: $it"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }
}

class SearchResultsFragment : Fragment() {

    private lateinit var pageViewModel: PageViewModel
    private var _binding: SearchResultsVerseFragmentBinding? = null
    private lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var mCurrentlyDisplayedSearchResults: List<Key> = java.util.ArrayList()
    private lateinit var intent: Intent

    var searchControl: SearchControl? = null

    fun setEmployee(myEmp: SearchControl?) {
        this.searchControl = myEmp
    }
    fun setCurrentlyDisplayedSearchResults(results: List<Key>) {
        this.mCurrentlyDisplayedSearchResults = results
    }
    fun setActiveWindowPageManagerProvider(x: ActiveWindowPageManagerProvider) {
        this.activeWindowPageManagerProvider = x
    }
    fun setIntent(x: Intent){
        this.intent = x
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(
            this,
            ViewModelProvider.NewInstanceFactory()
        ).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = SearchResultsVerseFragmentBinding.inflate(inflater, container, false)
        val root = binding.root

        pageViewModel.text.observe(viewLifecycleOwner, Observer {
            val strtext = requireArguments().getString("edttext")
            val arr: ArrayList<SearchResultsData> = requireArguments().getParcelableArrayList("mylist")!!

            val customAdapter = SearchResultsAdapter(activity, arr, searchControl)

            val list: ListView = binding.searchResultsList
            list.adapter = customAdapter
            list.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS;
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            list.isVerticalScrollBarEnabled = false

            list.setOnItemClickListener { parent, view, position, id ->
                val element = customAdapter.getItemAtPosition(position) // The item that was clicked
                Toast.makeText(list.context, "Hi",Toast.LENGTH_LONG)
                try { // no need to call HistoryManager.addHistoryItem() here because PassageChangeMediator will tell HistoryManager a change is about to occur
                    verseSelected(mCurrentlyDisplayedSearchResults[position])
                } catch (e: Exception) {
                    Log.e("blah", "Selection error", e)
                    Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
                }
//                val intent = Intent(this, BookDetailActivity::class.java)
//                startActivity(intent)
            }





        })
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

class SearchStatisticsFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.search_results_statistics_fragment, container, false)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SearchStatisticsFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            SearchStatisticsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
class PlaceholderFragment : Fragment() {

    private lateinit var pageViewModel: PageViewModel
    private var _binding: SearchResultsVerseFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(
            this,
            ViewModelProvider.NewInstanceFactory()
        ).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
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
