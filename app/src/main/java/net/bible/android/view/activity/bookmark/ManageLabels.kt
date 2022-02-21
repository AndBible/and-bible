/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.view.activity.bookmark

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextUtils.concat
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ManageLabelsBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.getTintedDrawable
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.htmlToSpan
import net.bible.service.common.labelsAndBookmarksPlaylist
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random.Default.nextInt
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.room.ColumnInfo
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.common.CommonUtils.getResourceColor
import kotlin.collections.ArrayList

val json = Json {
    allowStructuredMapKeys = true
    encodeDefaults = true
}
fun WorkspaceEntities.WorkspaceSettings.updateFrom(resultData: ManageLabels.ManageLabelsData) {
    Log.i("ManageLabels", "WorkspaceEntities.updateRecentLabels")
    autoAssignLabels = resultData.autoAssignLabels
    favouriteLabels = resultData.favouriteLabels
    autoAssignPrimaryLabel = resultData.autoAssignPrimaryLabel
    ABEventBus.getDefault().post(AppSettingsUpdated())
}

private var lastButtonSelected: Button? = null
private var showTextSearch = false
private var searchInsideTextButtonActive = false

/**
 *
 * ManageLabels serves for bookmark label management, selections (in several contexts) and StudyPad selection.
 */
class ManageLabels : ListActivityBase() {
    private lateinit var binding: ManageLabelsBinding
    private val allLabels: MutableList<BookmarkEntities.Label> = ArrayList()
    private val shownLabels: MutableList<Any> = ArrayList()

    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var activeWindowPageManagerProvider: ActiveWindowPageManagerProvider

    enum class Mode {STUDYPAD, WORKSPACE, ASSIGN, HIDELABELS, MANAGELABELS} // TODO: MANAGELABELS deprecated

    lateinit var data: ManageLabelsData

    override fun onBackPressed() {
        saveAndExit()
    }

    var highlightLabel: BookmarkEntities.Label? = null
//    lateinit var filterButtonsCollection: ArrayList<Button>

    fun DisplayTextSearchControls(){
        // Highlights the search button as required
        if (showTextSearch) {
            binding.textSearchLayout.visibility = View.VISIBLE
            (binding.searchRevealButton.getBackground() as GradientDrawable).setColor(getResourceColor(R.color.grey_500)) // set solid color
            // Show the keyboard.
            binding.editSearchText.requestFocus()
            val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.editSearchText, InputMethodManager.SHOW_IMPLICIT)

        } else {
            closeKeyboard()
            binding.textSearchLayout.visibility = View.GONE
            (binding.searchRevealButton.getBackground() as GradientDrawable).setColor(getResourceColor(R.color.transparent)) // set solid color
        }
    }
    fun filterButtonSelected(button:Button, clearEditText:Boolean = true) {
        // Set the display properties of buttons
        if (clearEditText && binding.editSearchText.text.toString() != "") binding.editSearchText.setText("")
        SearchTextOptions.setFilterButtonBackground(lastButtonSelected!!, false)
        SearchTextOptions.setFilterButtonBackground(button, true)
        setSearchInsideTextButtonBackground(button=button)
        closeKeyboard()
    }

    fun setSearchInsideTextButtonBackground(isSearchInsideText:Boolean? = null, button:Button? = null) {
        // Set the background color of the 'Filter Inside Text' button (to the left of the Search text field)
        if (isSearchInsideText != null) searchInsideTextButtonActive = isSearchInsideText
        if ((button != null) && (button!!.tag!=null)) {
            searchInsideTextButtonActive = SearchTextOptions.listItem(button.tag.toString())!!.isSearchInsideText
        }
        if (searchInsideTextButtonActive) {
            binding.searchInsideTextButton.text = getString(R.string.match_any_text)
            (binding.searchInsideTextButton.getBackground() as GradientDrawable).setColor(getResourceColor(R.color.blue_200)) // set solid color
        } else {
            binding.searchInsideTextButton.text = getString(R.string.match_start_of_text)
            (binding.searchInsideTextButton.getBackground() as GradientDrawable).setColor(getResourceColor(R.color.transparent)) // set solid color
        }
    }
    fun applyTextFilter(searchText: String, searchInsideTextButtonActive:Boolean){
        val regexString = if (searchInsideTextButtonActive) searchText else "^$searchText"
        updateLabelList(true,false, regexString)
    }

    @Serializable
    object SearchTextOptions {

        @Serializable
        class Project(
            var name: String // Property with a backing field; allowed
        ) {
            var stars: Int = 0 // property with a backing field; allowed

            val path: String // no backing field; ignored by the serializer
                get() = "kotlin/$name"

            var id by ::name // delegated property; ignored by the serializer
        }

        @Serializable
        class SearchOption(var text:String,
                           var isSearchInsideText:Boolean,
                           var id:String = "",
                           var regex:String=""){
            init {
                text = text.trim()
                id = text + if (isSearchInsideText) "1" else "0"  // The ID stores both the text to search for and an indicator in the last char showing whether it is an 'in text' or 'start of text' search
                regex = if (isSearchInsideText) text else "^${text}"
            }
            fun toJson():String {
                return json.encodeToString(serializer(), this)
            }
        }
        val list: ArrayList<SearchOption> = ArrayList()

        fun toJson(): String {
            return Json.encodeToString(ListSerializer(SearchOption.serializer()),list)
        }
        fun fromJson(jsonString: String): SearchTextOptions {
            // ToDO: https://www.raywenderlich.com/26883403-android-data-serialization-tutorial-with-the-kotlin-serialization-library
            // TODO: https://medium.com/@gurpreetsk/getting-started-with-kotlin-serialization-3315c59bafb2
            return net.bible.android.database.json.decodeFromString(serializer(), jsonString)
        }

        fun initialise (options:String) {
            list.clear()
            val x = options.split(",")
            x.map { if (it.length > 1) list.add(SearchOption( it.take(it.length-1), (it.takeLast(1) == "1" ))) }
        }
        override fun toString(): String {
            var x = list.map {it.id.trim()}.joinToString()
            return x
        }
        fun listItem (id:String): SearchOption? {
            return list.find {it.id == id}
        }
        fun add(option:String, isSearchInsideText: Boolean) {
            val newOption = SearchOption(option.trim(),isSearchInsideText)
            if (!list.any{ (it.id == newOption.id)}){list.add(newOption)}
//            val json_string = json.encodeToString(SearchOption.serializer(), newOption)
            Log.i("AGR","4: " + newOption.toJson())
            Log.i("AGR", "5: " + this.toJson())
        }
        fun remove(button:Button, binding:ManageLabelsBinding) {
            list.myRemoveIf { it.id == button.tag }
            button.visibility = View.GONE
            button.tag ="removed"
            // Had troubles trying to delete this button directly. Kept messing with the flow and other things. This way doesn't work either.
            // So I just rebuild all buttons next time a button gets added so hiding it will be fine.
//            for (i in 0 until binding.buttonLayout.childCount) {
//                val child: View = binding.buttonLayout.getChildAt(i)
//                if (child.id==button.id) {
//                    binding.buttonLayout.removeView(child)
//                    break
//                }
//            }
        }
        fun setFilterButtonBackground(button:Button?, isSelected:Boolean) {
            // Set the background color of a selected button and clears the background color of the previously selected button
            if (button != null)  {
                if ((button!!.tag !=null) && (SearchTextOptions.listItem(button.tag.toString())!!.isSearchInsideText)) {
                    val backgroundColor = if (isSelected) R.color.blue_200 else R.color.transparent
                    (button.getBackground() as GradientDrawable).setColor(getResourceColor(backgroundColor)) // set solid color
                    (button.getBackground() as GradientDrawable).setStroke(4,getResourceColor(R.color.blue_200)) // set solid color
                } else {
                    val backgroundColor = if (isSelected) R.color.grey_500 else R.color.transparent
                    (button.getBackground() as GradientDrawable).setColor(getResourceColor(backgroundColor)) // set solid color
                    (button.getBackground() as GradientDrawable).setStroke(4,getResourceColor(R.color.grey_500)) // set solid color
                }
            }
        }
        fun buildButtonList(binding:ManageLabelsBinding, context: Context,
                            filterButtonSelect:(button:Button)->Unit,
                            updateLabelList:(fromDb: Boolean, reOrder: Boolean, filterText:String)->Unit,
                            DisplayTextSearchControls:()->Unit
        ) {
            for (i in binding.buttonLayout.childCount-1 downTo 0) {
                val child: View = binding.buttonLayout.getChildAt(i)
                if (!arrayListOf(R.id.flowContainer,R.id.all_button, R.id.searchRevealButton).contains(child.id)) {
                    try {
                        binding.buttonLayout.removeView(child)
                    } catch (e:java.lang.Exception){
                        Log.e(TAG, "Error removing button view", e)
                    }
                }
            }

            val allButton = binding.allButton
            var flowButtonIds = intArrayOf(allButton.id)

            lastButtonSelected = allButton
            filterButtonSelect(allButton)

            allButton.setOnClickListener {
                filterButtonSelect(allButton)
                updateLabelList(true, false, "")
                lastButtonSelected = allButton
            }

            // Setup the text filter layout.
            flowButtonIds += binding.searchRevealButton.id
            binding.textSearchLayout.visibility = if (showTextSearch) View.VISIBLE else View.GONE
            binding.searchRevealButton.setOnClickListener {
                showTextSearch = !showTextSearch
                DisplayTextSearchControls()
            }
            DisplayTextSearchControls() // Initialise the display of the search reveal button.

            var orderedList = list
            orderedList.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.id }))
            for (searchOption in orderedList) {
                var newButton = Button(context)
                newButton.id = View.generateViewId()
                newButton.text = searchOption.text
                newButton.tag = searchOption.id
                newButton.setBackgroundResource(R.drawable.button_filter)
                newButton.layoutParams = ViewGroup.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 60)
                newButton.minWidth = 80
                newButton.minimumWidth = 80  // Both these are required to get the minwidth property to work
                newButton.setPadding(5,0,5,0)
                binding.buttonLayout.addView(newButton)
                setFilterButtonBackground(newButton, false)
                flowButtonIds += newButton.id

                newButton.setOnClickListener {
                    filterButtonSelect(newButton)
                    updateLabelList(true,false, listItem(newButton.tag.toString())!!.regex)
                    lastButtonSelected = newButton
                }
                newButton.setOnLongClickListener {
                    remove(newButton, binding)
                    true}
            }
            binding.flowContainer.referencedIds = flowButtonIds
        }
    }
    @Serializable
    data class ManageLabelsData(
        val mode: Mode,
        val selectedLabels: MutableSet<Long> = mutableSetOf(),
        val autoAssignLabels: MutableSet<Long> = mutableSetOf(),
        val favouriteLabels: MutableSet<Long> = mutableSetOf(),
        val deletedLabels: MutableSet<Long> = mutableSetOf(),
        val changedLabels: MutableSet<Long> = mutableSetOf(),

        var autoAssignPrimaryLabel: Long? = null,
        var bookmarkPrimaryLabel: Long? = null,

        val isWindow: Boolean = false,

        var reset: Boolean = false,
    ) {
        val showUnassigned: Boolean get() = setOf(Mode.HIDELABELS, Mode.WORKSPACE, Mode.MANAGELABELS).contains(mode)
        val showCheckboxes: Boolean get() = setOf(Mode.HIDELABELS, Mode.ASSIGN).contains(mode)
        val hasResetButton: Boolean get() = setOf(Mode.WORKSPACE, Mode.HIDELABELS).contains(mode)
        val hasReOrderButton: Boolean get() = setOf(Mode.HIDELABELS, Mode.ASSIGN, Mode.WORKSPACE).contains(mode)
        val workspaceEdits: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val primaryShown: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN).contains(mode)
        val showActiveCategory: Boolean get() = setOf(Mode.WORKSPACE, Mode.ASSIGN, Mode.HIDELABELS).contains(mode)
        val hideCategories: Boolean get() = setOf(Mode.STUDYPAD).contains(mode)

        val contextSelectedItems: MutableSet<Long> get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignLabels
                else -> selectedLabels
            }

        var contextPrimaryLabel: Long? get() =
            when (mode) {
                Mode.WORKSPACE -> autoAssignPrimaryLabel
                Mode.ASSIGN -> bookmarkPrimaryLabel
                else -> null
            }
            set(value) =
                when (mode) {
                    Mode.WORKSPACE -> autoAssignPrimaryLabel = value
                    Mode.ASSIGN -> bookmarkPrimaryLabel = value
                    else -> {}
                }

        val titleId: Int get() {
            return when(mode) {
                Mode.ASSIGN -> R.string.assign_labels
                Mode.STUDYPAD -> R.string.studypads
                Mode.WORKSPACE -> R.string.auto_assign_labels_title
                Mode.HIDELABELS -> R.string.bookmark_settings_hide_labels_title
                Mode.MANAGELABELS -> R.string.manage_labels
            }
        }

        fun toJSON(): String = json.encodeToString(serializer(), this)
        fun applyFrom(workspaceSettings: WorkspaceEntities.WorkspaceSettings?): ManageLabelsData {
            workspaceSettings?: return this
            favouriteLabels.addAll(workspaceSettings.favouriteLabels)
            autoAssignLabels.addAll(workspaceSettings.autoAssignLabels)
            autoAssignPrimaryLabel = workspaceSettings.autoAssignPrimaryLabel
            return this
        }

        companion object {
            fun fromJSON(str: String): ManageLabelsData = json.decodeFromString(serializer(), str)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        searchInsideTextButtonActive = CommonUtils.settings.getBoolean("labels_list_filter_searchInsideTextButtonActive", false)
        showTextSearch = CommonUtils.settings.getBoolean("labels_list_filter_showTextSearch", false)
        SearchTextOptions.initialise(CommonUtils.settings.getString("labels_list_filter_searchTextOptions", "")!!)

        super.onCreate(savedInstanceState, false)
        binding = ManageLabelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)

        data = ManageLabelsData.fromJSON(intent.getStringExtra("data")!!)

        allLabels.addAll(bookmarkControl.assignableLabels.filter {!it.isUnlabeledLabel})

        if(data.mode == Mode.STUDYPAD) {
            title = getString(R.string.studypads)
        }

        title = getString(data.titleId)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        updateLabelList(fromDb = true)

        val key = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.key
        if(key is StudyPadKey) {
            highlightLabel = key.label
        }

        listAdapter = ManageLabelItemAdapter(this, shownLabels, this)

        highlightLabel?.also {
            val pos = shownLabels.indexOf(it)
            GlobalScope.launch(Dispatchers.Main) {
                delay(100)
                listView.smoothScrollToPosition(pos)
            }
        }
        // Setup listeners for the text filter
        val editSearchText = binding.editSearchText
        val searchInsideTextButton = binding.searchInsideTextButton
        val clearSearchTextButton = binding.clearSearchTextButton
        val saveSearchButton = binding.saveSearchButton

        clearSearchTextButton.setOnClickListener {
            editSearchText.text.clear()
            applyTextFilter("", false)
        }

        saveSearchButton.setOnClickListener {
            val searchText = editSearchText.text.toString()
            if ( (searchText != "") ) {
                // The type of search is appended as either a 1 (in label) or 0 (start of label) search. I am not sure how to serialise a better object for this
                SearchTextOptions.add(searchText, searchInsideTextButtonActive)
                SearchTextOptions.buildButtonList(binding, getApplicationContext(), this::filterButtonSelected, this::updateLabelList, this::DisplayTextSearchControls)
            }
        }
        editSearchText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyTextFilter(editSearchText.text.toString(), searchInsideTextButtonActive)
                binding.saveSearchButton.visibility = if (editSearchText.text.toString()=="") View.GONE else View.VISIBLE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count!=0) filterButtonSelected(binding.allButton,false)
            }
        })

        setSearchInsideTextButtonBackground(searchInsideTextButtonActive)  // Initialise the text for this button
        searchInsideTextButton.setOnClickListener {
            searchInsideTextButtonActive = !searchInsideTextButtonActive
            setSearchInsideTextButtonBackground(searchInsideTextButtonActive)
            applyTextFilter(editSearchText.text.toString(), searchInsideTextButtonActive)
        }
        SearchTextOptions.buildButtonList(binding, getApplicationContext(), this::filterButtonSelected, this::updateLabelList, this::DisplayTextSearchControls)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.manage_labels_options_menu, menu)
        menu.findItem(R.id.resetButton).isVisible = data.hasResetButton
        menu.findItem(R.id.reOrder).isVisible = data.hasReOrderButton
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.help -> help()
            R.id.newLabel -> newLabel()
            R.id.resetButton -> reset()
            R.id.reOrder -> updateLabelList(reOrder = true)
            android.R.id.home -> saveAndExit()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    private fun help() {
        when(data.mode) {
            Mode.STUDYPAD -> CommonUtils.showHelp(this, listOf(R.string.studypads))
            Mode.MANAGELABELS -> CommonUtils.showHelp(this, listOf(R.string.help_bookmarks_title))
            Mode.ASSIGN -> help(HelpMode.ASSIGN)
            Mode.WORKSPACE -> help(HelpMode.WORKSPACE)
            Mode.HIDELABELS -> help(HelpMode.HIDE)
        }
    }

    enum class HelpMode {WORKSPACE, ASSIGN, HIDE}

    private fun getIconString(id: Int, iconId: Int): SpannableString {
        val s = getString(id,"__ICON__")
        val start = s.indexOf("__ICON__")
        val length = 8
        val icon = ImageSpan(getTintedDrawable(iconId))
        val span = SpannableString(s)
        span.setSpan(icon, start, start + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    private fun help(helpMode: HelpMode) {
        val length = 9

        val videoLink = "<i><a href=\"${labelsAndBookmarksPlaylist}\">${getString(R.string.watch_tutorial_video)}</a></i><br><br>"
        val v = htmlToSpan(videoLink)

        val h1 = when(helpMode) {
            HelpMode.WORKSPACE -> getString(R.string.auto_assing_labels_help1)
            HelpMode.ASSIGN -> getString(R.string.assing_labels_help1)
            HelpMode.HIDE -> getString(R.string.bookmark_settings_hide_labels_summary)
        }

        val h11 =  "\n\n" + getString(R.string.setting_scope, getString(
            if(data.isWindow) R.string.setting_scope_window
            else R.string.setting_scope_workspace)
        )

        val h2 = concat("\n\n", getIconString(R.string.assing_labels_help2, R.drawable.ic_baseline_bookmark_24))
        val text = getString(R.string.assing_labels_help3, "__ICON2__ __ICON3__")

        val start2 = text.indexOf("__ICON2__")
        val start3 = text.indexOf("__ICON3__")
        val h3 = concat("\n\n", SpannableString(text).apply {
            val icon2 = ImageSpan(getTintedDrawable(R.drawable.ic_label_24dp))
            val icon3 = ImageSpan(getTintedDrawable(R.drawable.ic_label_circle))
            setSpan(icon2, start2, start2 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(icon3, start3, start3 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        })

        val h4 = concat("\n\n", getIconString(R.string.assing_labels_help4, R.drawable.ic_baseline_favorite_24))
        val h5 = concat("\n\n", getIconString(R.string.assing_labels_help5, R.drawable.ic_baseline_refresh_24))

        val span = concat(
            v,
            h1,
            if(listOf(HelpMode.HIDE, HelpMode.WORKSPACE).contains(helpMode)) h11 else "",
            *if(helpMode != HelpMode.HIDE) arrayOf(h2, h3, h4) else arrayOf(""),
            h5
        )

        val title = getString(when(helpMode) {
            HelpMode.ASSIGN -> R.string.assign_labels
            HelpMode.WORKSPACE -> R.string.auto_assign_labels_title
            HelpMode.HIDE -> R.string.bookmark_settings_hide_labels_title
        })

        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(title)
            .setMessage(span)
            .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun studyPadSelected(journal: BookmarkEntities.Label) {
        Log.i(TAG, "Journal selected:" + journal.name)
        try {
            activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocumentAndKey(FakeBookFactory.journalDocument, StudyPadKey(journal))
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to show journal", e)
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    fun ensureNotAutoAssignPrimaryLabel(label: BookmarkEntities.Label) {
        if (data.autoAssignPrimaryLabel == label.id || data.autoAssignPrimaryLabel == null) {
            data.autoAssignPrimaryLabel = data.autoAssignLabels.toList().firstOrNull()
        }
    }

    fun ensureNotBookmarkPrimaryLabel(label: BookmarkEntities.Label) {
        if (data.bookmarkPrimaryLabel == label.id || data.bookmarkPrimaryLabel == null) {
            data.bookmarkPrimaryLabel = data.selectedLabels.toList().firstOrNull()
        }
    }

    private fun randomColor(): Int = Color.argb(255, nextInt(0, 255), nextInt(0, 255), nextInt(0, 255))

    private var newLabelCount = 1L

    private fun newLabel() {
        Log.i(TAG, "New label clicked")
        val newLabel = BookmarkEntities.Label()
        newLabel.color = randomColor()
        newLabel.id = -(newLabelCount++)
        editLabel(newLabel)
    }

    private fun deleteLabel(label: BookmarkEntities.Label) {
        data.deletedLabels.add(label.id)
        data.selectedLabels.remove(label.id)
        data.autoAssignLabels.remove(label.id)
        data.favouriteLabels.remove(label.id)
        data.changedLabels.remove(label.id)

        ensureNotBookmarkPrimaryLabel(label)
        ensureNotAutoAssignPrimaryLabel(label)
    }

    fun editLabel(label_: BookmarkEntities.Label) {
        var label = label_
        val isNew = label.id < 0
        val intent = Intent(this, LabelEditActivity::class.java)
        val labelData = LabelEditActivity.LabelData(
            isAssigning = data.mode == Mode.ASSIGN,
            label = label,
            isAutoAssign = data.autoAssignLabels.contains(label.id),
            isFavourite = data.favouriteLabels.contains(label.id),
            isAutoAssignPrimary = data.autoAssignPrimaryLabel == label.id,
            isThisBookmarkPrimary = data.bookmarkPrimaryLabel == label.id,
            isThisBookmarkSelected = data.selectedLabels.contains(label.id)
        )
        if(isNew) {
            if(data.mode === Mode.ASSIGN) {
                labelData.isThisBookmarkSelected = true
                labelData.isThisBookmarkPrimary = true
            } else if(data.mode === Mode.WORKSPACE) {
                labelData.isAutoAssignPrimary = true
                labelData.isAutoAssign = true
            }
        }

        intent.putExtra("data", json.encodeToString(serializer(), labelData))

        GlobalScope.launch(Dispatchers.Main) {
            val result = awaitIntent(intent) ?: return@launch
            if (result.resultCode != Activity.RESULT_CANCELED) {
                val newLabelData: LabelEditActivity.LabelData = json.decodeFromString(
                    serializer(), result.resultData.getStringExtra("data")!!)

                if(newLabelData.label.name.isEmpty() && label.id < 0) {
                    return@launch
                }

                if (newLabelData.delete) {
                    deleteLabel(label)

                } else {
                    val idx = shownLabels.indexOf(label)
                    shownLabels.remove(label)
                    allLabels.remove(label)
                    label = newLabelData.label
                    allLabels.add(label)
                    if(idx > 0) {
                        shownLabels.add(idx, label)
                    } else {
                        shownLabels.add(label)
                    }
                    data.changedLabels.add(label.id)

                    if (newLabelData.isAutoAssign) {
                        data.autoAssignLabels.add(label.id)
                    } else {
                        data.autoAssignLabels.remove(label.id)
                    }
                    if (newLabelData.isFavourite) {
                        data.favouriteLabels.add(label.id)
                    } else {
                        data.favouriteLabels.remove(label.id)
                    }
                    if (newLabelData.isAutoAssignPrimary) {
                        data.autoAssignPrimaryLabel = label.id
                    } else {
                        ensureNotAutoAssignPrimaryLabel(label)
                    }
                    if (newLabelData.isThisBookmarkPrimary) {
                        data.bookmarkPrimaryLabel = label.id
                    } else {
                        ensureNotBookmarkPrimaryLabel(label)
                    }
                    if(data.mode == Mode.ASSIGN) {
                        if (newLabelData.isThisBookmarkSelected) {
                            data.selectedLabels.add(label.id)
                        } else {
                            data.selectedLabels.remove(label.id)
                        }
                    }
                }
                updateLabelList(reOrder = isNew)
                if(isNew) {
                    listView.smoothScrollToPosition(shownLabels.indexOf(label))
                }
            }
        }
    }

    // This is called by the listener, which is created and configured by the list adapter
    // It should only be called when the mode is STUDYPAD.  Nonetheless, I retained the mode check from the previous
    // onListItemClick() method as an additional sanity check.
    fun selectStudyPadLabel(selected: Any) {
        if (data.mode == Mode.STUDYPAD) {
            if (selected is BookmarkEntities.Label) { 
                saveAndExit(selected) 
            }
        }
        else {
            Log.e(TAG, "Call to selectStudyPadLabel() is unexpected when mode is not STUDYPAD.  mode=${data.mode}")
        }
    }

    private fun saveAndExit(selected: BookmarkEntities.Label? = null) = GlobalScope.launch(Dispatchers.Main) {
        Log.i(TAG, "Okay clicked")
        CommonUtils.settings.setBoolean("labels_list_filter_searchInsideTextButtonActive", searchInsideTextButtonActive)
        CommonUtils.settings.setBoolean("labels_list_filter_showTextSearch", showTextSearch)
        CommonUtils.settings.setString("labels_list_filter_searchTextOptions", SearchTextOptions.toString())

        val deleteLabelIds = data.deletedLabels.filter{ it > 0 }.toList()
        if(deleteLabelIds.isNotEmpty()) {
            bookmarkControl.deleteLabels(deleteLabelIds)
        }

        val result = Intent()
        val saveLabels = allLabels
            .filterIsInstance<BookmarkEntities.Label>()
            .filter{ data.changedLabels.contains(it.id) && !data.deletedLabels.contains(it.id) }

        val newLabels = saveLabels.filter { it.id < 0 }
        val existingLabels = saveLabels.filter { it.id > 0 }

        for (it in newLabels) {
            val oldLabel = it.id
            it.id = 0
            it.id = bookmarkControl.insertOrUpdateLabel(it).id
            for(list in listOf(data.selectedLabels, data.autoAssignLabels, data.changedLabels, data.favouriteLabels)) {
                if(list.contains(oldLabel)) {
                    list.remove(oldLabel)
                    list.add(it.id)
                }
            }
            if(data.bookmarkPrimaryLabel == oldLabel) {
                data.bookmarkPrimaryLabel = it.id
            }
            if(data.autoAssignPrimaryLabel == oldLabel) {
                data.autoAssignPrimaryLabel = it.id
            }
        }

        for (it in existingLabels) {
            bookmarkControl.insertOrUpdateLabel(it)
        }

        result.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, result)
        if(selected != null) {
            studyPadSelected(selected)
        }
        finish()
    }

    private suspend fun askConfirmation(message: String, yesNo: Boolean = false)  = suspendCoroutine<Boolean> {
        AlertDialog.Builder(this).apply {
            setMessage(message)
            setPositiveButton(R.string.yes) { _, _ ->
                it.resume(true)
            }
            if(yesNo) {
                setNegativeButton(R.string.no) { _, _ ->
                    it.resume(false)
                }
                setCancelable(false)
            } else {
                setCancelable(true)
                setOnCancelListener { _ -> it.resume(false) }
                setNegativeButton(R.string.cancel) { _, _ -> it.resume(false) }
            }
            show()
        }
    }

    fun reset() {
        GlobalScope.launch(Dispatchers.Main) {
            val msgId = when(data.mode) {
                Mode.WORKSPACE -> R.string.reset_workspace_labels
                Mode.HIDELABELS -> R.string.reset_hide_labels
                else -> throw RuntimeException("Illegal value")
            }
            if(askConfirmation(getString(msgId))) {
                val result = Intent()
                data.reset = true

                result.putExtra("data", data.toJSON())

                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }

    }

    fun updateLabelList(fromDb: Boolean = false, reOrder: Boolean = false, _filterText:String = "") {
        var filterText = _filterText
        if(fromDb) {
            shownLabels.clear()
            Log.i(TAG, "Parsing filter: $filterText")
            // Check if it is a special type of filter (ie A-C style)
            val specialRegex = "^\\^.\\-.".toRegex()
            if (specialRegex.containsMatchIn(filterText)) {
                filterText = specialRegex.find(filterText)!!.value
                filterText = "^[" + filterText.takeLast(filterText.length-1) + "]"
            }
            val regex = try {
                    filterText.toRegex(RegexOption.IGNORE_CASE)
                } catch (e:Exception) {
                    "".toRegex()
                }
            // Exclude the 'unlabeled label' but include all selected labels plus those that match the filter clause.
            // We always want to see the labels we have selected even when filtering.
            shownLabels.addAll(allLabels.filter {
                !it.isUnlabeledLabel and ((filterText=="") or (regex.containsMatchIn(it.name)) or (data.selectedLabels.contains(it.id))) })
            if (data.showUnassigned) {
                shownLabels.add(bookmarkControl.labelUnlabelled)
            }
            // Don't show the 'Selected' category in Label Settings (assume count=0)
            if(data.showActiveCategory and (data.selectedLabels.count()>0)) {
                shownLabels.add(LabelCategory.ACTIVE)
            }
            if(!data.hideCategories) {
                shownLabels.add(LabelCategory.RECENT)
                shownLabels.add(LabelCategory.OTHER)
            }
        }

        val recentLabelIds = bookmarkControl.windowControl.windowRepository.workspaceSettings.recentLabels.map { it.labelId }
        shownLabels.myRemoveIf { it is BookmarkEntities.Label && data.deletedLabels.contains(it.id) }
        if(fromDb || reOrder) {
            shownLabels.sortWith(compareBy({
                val inActiveCategory = data.showActiveCategory && (it == LabelCategory.ACTIVE || (it is BookmarkEntities.Label && data.contextSelectedItems.contains(it.id)))
                val inRecentCategory = !data.hideCategories && (it == LabelCategory.RECENT || (it is BookmarkEntities.Label && recentLabelIds.contains(it.id)))
                when {
                    inActiveCategory -> 1
                    inRecentCategory -> 2
                    else -> 3
                }
            }, {
                when (it) {
                    is LabelCategory -> 1
                    else -> 2
                }
            }, {
                when (it) {
                    is BookmarkEntities.Label -> it.name.lowercase(Locale.getDefault())
                    else -> ""
                }
            }))
        }

        val labelIds = shownLabels.filterIsInstance<BookmarkEntities.Label>().map { it.id }.toSet()

        if (filterText == "") {

            // TODO: changes made in the label dialog are lost when the filter is applied
            // Some sanity check. These checks clear the changed state of the labels.
            // AGR: I don't know whether these are really needed. But having them clear these settings when the filter is applied.
//            data.autoAssignLabels.myRemoveIf { !labelIds.contains(it) }
//            data.favouriteLabels.myRemoveIf { !labelIds.contains(it) }
            data.selectedLabels.myRemoveIf { !labelIds.contains(it) }
        }
        notifyDataSetChanged()
    }

    companion object {
        private const val TAG = "BookmarkLabels"
    }
}

enum class LabelCategory {ACTIVE, RECENT, OTHER}

private fun <E> MutableSet<E>.myRemoveIf(function: (it: E) -> Boolean)  = filter { function.invoke(it) }.forEach { remove(it) }
private fun <E> MutableList<E>.myRemoveIf(function: (it: E) -> Boolean)  = filter { function.invoke(it) }.forEach { remove(it) }

