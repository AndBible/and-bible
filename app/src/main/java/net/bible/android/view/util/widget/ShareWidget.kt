/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.util.widget

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.AttributeSet
import android.util.LayoutDirection
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ShareVersesBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordContentFacade
import java.util.*

class ShareWidget(context: Context, attributeSet: AttributeSet?, val selection: Selection):
    LinearLayout(context, attributeSet) {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val bindings = ShareVersesBinding.inflate(inflater, this, true)

    init {
        CommonUtils.buildActivityComponent().inject(this)
        bindings.run {
            if(!selection.hasRange) {
                toggleShowSelectionOnly.visibility = View.GONE
                toggleShowEllipsis.visibility = View.GONE
            }

            toggleVersenumbers.isChecked = CommonUtils.settings.getBoolean("share_verse_numbers", true)
            advertise.isChecked = CommonUtils.settings.getBoolean("share_show_add", true)
            toggleShowReference.isChecked = CommonUtils.settings.getBoolean("share_show_reference", true)
            toggleAbbreviateReference.isChecked = CommonUtils.settings.getBoolean("share_abbreviate_reference", true)
            toggleShowVersion.isChecked = CommonUtils.settings.getBoolean("share_show_version", true)
            toggleShowReferenceAtFront.isChecked = CommonUtils.settings.getBoolean("share_show_reference_at_front", true)
            toggleNotes.visibility = if(selection.notes!= null) View.VISIBLE else View.GONE
            toggleNotes.isChecked = CommonUtils.settings.getBoolean("show_notes", true)
            toggleShowSelectionOnly.isChecked = CommonUtils.settings.getBoolean("show_selection_only", true)
            toggleShowEllipsis.isChecked = CommonUtils.settings.getBoolean("show_ellipsis", true)

            toggleVersenumbers.setOnClickListener { updateText()}
            advertise.setOnClickListener { updateText()}
            toggleShowReference.setOnClickListener { updateText()}
            toggleAbbreviateReference.setOnClickListener { updateText()}
            toggleShowVersion.setOnClickListener { updateText()}
            toggleShowReferenceAtFront.setOnClickListener { updateText()}
            toggleNotes.setOnClickListener { updateText()}
            toggleShowSelectionOnly.setOnClickListener { updateText()}
            toggleShowEllipsis.setOnClickListener { updateText()}
        }
        updateText()
    }

    private fun updateText() {
        val text = SwordContentFacade.getSelectionText(selection,
            showVerseNumbers = bindings.toggleVersenumbers.isChecked,
            advertiseApp = bindings.advertise.isChecked,
            abbreviateReference = bindings.toggleAbbreviateReference.isChecked,
            showNotes = bindings.toggleNotes.isChecked,
            showVersion = bindings.toggleShowVersion.isChecked,
            showReference = bindings.toggleShowReference.isChecked,
            showReferenceAtFront = bindings.toggleShowReferenceAtFront.isChecked,
            showSelectionOnly = bindings.toggleShowSelectionOnly.isChecked,
            showEllipsis = bindings.toggleShowEllipsis.isChecked
        )
        val isRtl = TextUtils.getLayoutDirectionFromLocale(Locale(selection.book.language.code)) == LayoutDirection.RTL
        bindings.preview.textDirection = if(isRtl)  View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        bindings.preview.text = text
        CommonUtils.settings.apply {
            setBoolean("share_verse_numbers", bindings.toggleVersenumbers.isChecked)
            setBoolean("share_show_add", bindings.advertise.isChecked)
            setBoolean("share_show_reference", bindings.toggleShowReference.isChecked)
            setBoolean("share_abbreviate_reference", bindings.toggleAbbreviateReference.isChecked)
            setBoolean("share_show_version", bindings.toggleShowVersion.isChecked)
            setBoolean("show_notes", bindings.toggleNotes.isChecked)
            setBoolean("show_selection_only", bindings.toggleShowSelectionOnly.isChecked)
            setBoolean("show_ellipsis", bindings.toggleShowEllipsis.isChecked)
        }
        bindings.toggleAbbreviateReference.isEnabled = bindings.toggleShowReference.isChecked
        bindings.toggleShowVersion.isEnabled = bindings.toggleShowReference.isChecked
        bindings.toggleShowReferenceAtFront.isEnabled = bindings.toggleShowReference.isChecked
        bindings.toggleShowEllipsis.isEnabled = bindings.toggleShowSelectionOnly.isChecked
    }

    companion object {
        fun dialog(context: Context, selection: Selection) {
            AlertDialog.Builder(context).apply {
                val layout = ShareWidget(context, null, selection)
                setView(layout)
                setPositiveButton(R.string.backup_share) {
                    _, _ ->

                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, layout.bindings.preview.text)
                        type = "text/plain"
                    }
                    val chooserIntent = Intent.createChooser(emailIntent, context.getString(R.string.share_verse_menu_title))
                    context.startActivity(chooserIntent)

                }
                setCancelable(true)
                setNeutralButton(R.string.cancel, null)
                setNegativeButton(R.string.verse_action_copy) { _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(selection.verseRange.name, layout.bindings.preview.text)
                    clipboard.setPrimaryClip(clip)
                    ABEventBus.getDefault().post(ToastEvent(context.getString(R.string.text_copied_to_clicpboard)))
                }
                setTitle(R.string.share_verse_widget_title)
                create().show()
            }
        }
        fun dialog(context: Context, bookmark: BookmarkEntities.Bookmark) =
            dialog(context, Selection(bookmark))
    }
}
