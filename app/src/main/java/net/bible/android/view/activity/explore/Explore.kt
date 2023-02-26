package net.bible.android.view.activity.explore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.windowControl
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Key
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("ViewConstructor")
class Explore (val currentActivity: ActivityBase,
               val windowControl: WindowControl,
               val selection: Selection?
)
{
    var x = 1

    fun initialise(){
        val verseRange = selection?.verseRange ?: return
        val currentPage = windowControl?.activeWindowPageManager?.currentPage


        val bottomSheet = BottomSheet()
//        bottomSheet.behavior.isDraggable = true
//        bottomSheet.behavior.peekHeight = 1500
//        bottomSheet.behavior.maxWidth = 1000
        bottomSheet.show(currentActivity.supportFragmentManager,"TAG1")



    }

    class BottomSheet : BottomSheetDialogFragment() {

        private lateinit var bottomSheet: ViewGroup
        private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
        private lateinit var viewPager: ViewPager
        private lateinit var appBarLayout: AppBarLayout

        override fun onStart() {
            super.onStart()
            bottomSheet =
                dialog!!.findViewById(com.google.android.material.R.id.design_bottom_sheet) as ViewGroup // notice the R root package
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            // SETUP YOUR BEHAVIOR HERE
            bottomSheetBehavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(view: View, i: Int) {
                    if (BottomSheetBehavior.STATE_EXPANDED == i) {
                        showView(appBarLayout, getActionBarSize()) // show app bar when expanded completely
                    }
                    if (BottomSheetBehavior.STATE_COLLAPSED == i) {
                        hideAppBar(appBarLayout) // hide app bar when collapsed
                    }
                    if (BottomSheetBehavior.STATE_HIDDEN == i) {
                        dismiss() // destroy the instance
                    }
                }

                override fun onSlide(view: View, v: Float) {}
            })

            hideAppBar(appBarLayout)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        private fun hideAppBar(view: View) {
            val params = view.layoutParams
            params.height = 0
            view.layoutParams = params
        }

        private fun showView(view: View, size: Int) {
            val params = view.layoutParams
            params.height = size
            view.layoutParams = params
        }

        private fun getActionBarSize(): Int {
            val styledAttributes =
                requireContext().theme.obtainStyledAttributes(intArrayOf(R.attr.actionBarSize))
            return styledAttributes.getDimension(0, 0f).toInt()
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val myview: View = inflater.inflate(R.layout.explore, container, false)

            // SETUP THE VIEWPAGER AND THE TABLAYOUT HERE

            val tabLayout = myview.findViewById<TabLayout>(R.id.myTabLayout)
            appBarLayout = myview.findViewById(R.id.appBarLayout)
            viewPager = myview.findViewById(R.id.myViewPager)
            tabLayout.addTab(tabLayout.newTab().setText("Verse"))
            tabLayout.addTab(tabLayout.newTab().setText("Chapter"))
            tabLayout.addTab(tabLayout.newTab().setText("Book"))
            tabLayout.addTab(tabLayout.newTab().setText("Devotional"))
            tabLayout.addTab(tabLayout.newTab().setText("Headings"))
            tabLayout.tabGravity = TabLayout.GRAVITY_START

            // USE childFragmentManager
            val adapter = MyFragmentAdapter(childFragmentManager)
            viewPager.adapter = adapter
            viewPager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    viewPager.setCurrentItem(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
            return myview
        }
    }

    class MyFragmentAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        companion object {
            const val NUM_ITEMS = 4
        }

        override fun getItem(position: Int): Fragment {
            when (position) {
                0 -> return VerseFragment()
                1 -> return TwoFragment()
                2 -> return BookFragment()
                3 -> return UrlFragment("https://odb.org/2023/02/26/is-it-a-sign")
            }
            return ChapterFragment()
        }

        override fun getCount(): Int {
            return NUM_ITEMS
        }
    }
    class VerseFragment() : Fragment() {

        lateinit var runnable: Runnable
        lateinit var progressBar: ProgressBar

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.explore_chapter, container, false)

            progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
            val wv = view.findViewById<WebView>(R.id.explore_chapter_webview)
//            wv.getSettings().setLoadWithOverviewMode(true)
//            wv.getSettings().setUseWideViewPort(true)
//            wv.setWebChromeClient(WebChromeClient())
//            wv.getSettings().setUserAgentString("Android WebView")
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true;


            wv.loadUrl("https://odb.org/${SimpleDateFormat("yyyy/MM/dd/").format(Date())}")
//            wv.loadUrl("https://www.esv.org/Exodus+20.20/")
//            wv.loadUrl("https://www.blueletterbible.org/kjv/gen/1/5/s_1005")

            wv.setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return false
                }
            })
            return view

        }
        // Overriding WebViewClient functions
        open inner class WebViewClient : android.webkit.WebViewClient() {

            // Load the URL
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return false
            }

            // ProgressBar will disappear once page is loaded
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }
    }

    class ChapterFragment() : Fragment() {

        lateinit var runnable: Runnable
        lateinit var progressBar: ProgressBar

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.explore_chapter, container, false)
            val textView = view.findViewById<TextView>(R.id.targettext)

            var looper = 2000
            val handler = Handler()
            runnable = Runnable {
                textView.text = looper.toString()
                looper -= 1
                handler.postDelayed(runnable, 2000)
            }
            handler.post(runnable)

            progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
            val wv = view.findViewById<WebView>(R.id.explore_chapter_webview)
            wv.loadUrl("https://www.blueletterbible.org/kjv/gen/1/5/s_1005")
            wv.settings.javaScriptEnabled = true
            wv.setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return false
                }
            })
            return view

        }
        // Overriding WebViewClient functions
        open inner class WebViewClient : android.webkit.WebViewClient() {

            // Load the URL
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return false
            }

            // ProgressBar will disappear once page is loaded
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }
    }

    class BookFragment : Fragment() {

        lateinit var runnable: Runnable
        lateinit var progressBar: ProgressBar

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.explore_book, container, false)

            progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

            // Lookup document in dictionary
            val currentPage = windowControl?.activeWindowPageManager?.currentPage

            var currentDocument:Book = Books.installed().getBook("ISBE")
            var key:Key = currentDocument.getKey("AARON")

            val frag = SwordContentFacade.readOsisFragment(currentDocument, key)
            val x = OsisFragment(frag, key, currentDocument)

            // Put it into the webview

            val wv = view.findViewById<WebView>(R.id.explore_book_webview)


//            wv.loadUrl("https://www.esv.org/Exodus+20.20/")
//            wv.loadUrl("https://www.biblegateway.com/passage/?search=Exodus%2020&version=NIV")

            wv.loadDataWithBaseURL(null,x.xmlStr, null, null,null)

            wv.settings.javaScriptEnabled = true
            wv.setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return false
                }
            })
            return view

        }
        // Overriding WebViewClient functions
        open inner class WebViewClient : android.webkit.WebViewClient() {

            // Load the URL
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return false
            }

            // ProgressBar will disappear once page is loaded
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }
    }

    class TwoFragment : Fragment() {
        lateinit var runnable: Runnable

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.explore_chapter, container, false)
            val textView = view.findViewById<TextView>(R.id.targettext)

            var looper = 0
            val handler = Handler()
            runnable = Runnable {
                textView.text = looper.toString()
                looper += 1
                handler.postDelayed(runnable, 2000)
            }

            handler.post(runnable)


            return view
        }
    }
    class UrlFragment(url:String) : Fragment() {
        lateinit var runnable: Runnable
        lateinit var progressBar: ProgressBar
        var url = url
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.explore_url, container, false)
            val wv = view.findViewById<WebView>(R.id.explore_url_webview)

            progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
            wv.loadUrl("https://www.esv.org/Exodus+20.20/")
//            wv.loadUrl("https://odb.org/")
//            wv.loadUrl(url)

            wv.settings.javaScriptEnabled = true
            wv.setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return false
                }
            })


            return view
        }
        // Overriding WebViewClient functions
        open inner class WebViewClient : android.webkit.WebViewClient() {

            // Load the URL
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return false
            }

            // ProgressBar will disappear once page is loaded
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }
    }
}

// A custom ViewPager is required to manage scrolling issues when tabs are used on a bottomsheet.
// See https://stackoverflow.com/questions/37715822/android-viewpager-with-recyclerview-works-incorrectly-inside-bottomsheet/38281457#38281457
class BottomSheetViewPager(context: Context, attrs: AttributeSet?) : ViewPager(context, attrs) {
    constructor(context: Context) : this(context, null)
    private val positionField: Field =
        ViewPager.LayoutParams::class.java.getDeclaredField("position").also {
            it.isAccessible = true
        }

    init {
        addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                requestLayout()
            }
        })
    }

    override fun getChildAt(index: Int): View {
        val stackTrace = Throwable().stackTrace
        val calledFromFindScrollingChild = stackTrace.getOrNull(1)?.let {
            it.className == "com.google.android.material.bottomsheet.BottomSheetBehavior" &&
                it.methodName == "findScrollingChild"
        }
        if (calledFromFindScrollingChild != true) {
            return super.getChildAt(index)
        }

        val currentView = getCurrentView() ?: return super.getChildAt(index)
        return if (index == 0) {
            currentView
        } else {
            var view = super.getChildAt(index)
            if (view == currentView) {
                view = super.getChildAt(0)
            }
            return view
        }
    }

    private fun getCurrentView(): View? {
        for (i in 0 until childCount) {
            val child = super.getChildAt(i)
            val lp = child.layoutParams as? ViewPager.LayoutParams
            if (lp != null) {
                val position = positionField.getInt(lp)
                if (!lp.isDecor && currentItem == position) {
                    return child
                }
            }
        }
        return null
    }
}



