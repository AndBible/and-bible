/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.ai

import android.app.AlertDialog
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.preference.PreferenceFragmentCompat
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.db.DatabaseContainer

abstract class AiSettingsFragmentBase : PreferenceFragmentCompat() {

    internal val settings get() = CommonUtils.aiSettings
    internal val dao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
    internal val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    internal open fun refreshAll() {}

    internal fun buildDisclaimerHtml(): String {
        val intro = getString(R.string.ai_disclaimer_intro)
        val approach = getString(R.string.ai_disclaimer_approach)
        val responsibility = getString(R.string.ai_disclaimer_responsibility)
        val p1 = getString(R.string.ai_disclaimer_point1)
        val p2 = getString(R.string.ai_disclaimer_point2)
        val p3 = getString(R.string.ai_disclaimer_point3)
        val p4 = getString(R.string.ai_disclaimer_point4)
        val p5 = getString(R.string.ai_disclaimer_point5)
        val p6 = getString(R.string.ai_disclaimer_point6)
        val p7 = getString(R.string.ai_disclaimer_point7)
        val p8 = getString(R.string.ai_disclaimer_point8)
        val p9 = getString(R.string.ai_disclaimer_point9)
        return "$intro $approach $responsibility<br><br>• $p1<br><br>• $p2<br><br>• $p3<br><br>• $p4<br><br>$p6<br><br>$p7 $p8<br><br>$p9<br><br><i>$p5</i>"
    }

    internal fun showDisclaimerInfoDialog() {
        val spanned = htmlToSpan(buildDisclaimerHtml())
        val d = AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.ai_disclaimer_dialog_title)
            setMessage(spanned)
            setPositiveButton(R.string.okay, null)
            setCancelable(true)
        }.create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.apply {
            movementMethod = LinkMovementMethod.getInstance()
            setTextIsSelectable(true)
        }
    }

    /**
     * Gate that ensures the user has accepted the AI disclaimer before proceeding.
     * If already accepted, calls [onAccepted] immediately.
     * Otherwise shows the acceptance dialog first.
     */
    internal fun ensureDisclaimerAccepted(onAccepted: () -> Unit) {
        if (settings.aiDisclaimerAccepted) {
            onAccepted()
            return
        }
        val context = requireContext()
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val textView = TextView(context).apply {
            text = htmlToSpan(buildDisclaimerHtml())
            movementMethod = LinkMovementMethod.getInstance()
            setTextIsSelectable(true)
        }

        val acceptButton = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.ai_disclaimer_accept_button)
            isAllCaps = false
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (16 * density).toInt()
            layoutParams = params
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(textView)
            addView(acceptButton)
        }

        val scrollView = ScrollView(context).apply { addView(layout) }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.ai_disclaimer_accept_title)
            .setView(scrollView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        acceptButton.setOnClickListener {
            settings.aiDisclaimerAccepted = true
            dialog.dismiss()
            onAccepted()
        }

        dialog.show()
    }

    /** Create a ScrollView containing a padded vertical LinearLayout for dialog content. */
    internal fun createDialogLayout(): Pair<ScrollView, LinearLayout> {
        val context = requireContext()
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        scrollView.addView(layout)
        return scrollView to layout
    }

    internal fun addLabeledField(layout: LinearLayout, label: String, field: View) {
        val density = resources.displayMetrics.density
        val labelView = TextView(requireContext()).apply {
            text = label
            setTextAppearance(android.R.style.TextAppearance_Small)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (8 * density).toInt()
            layoutParams = params
        }
        layout.addView(labelView)

        val fieldParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        fieldParams.bottomMargin = (4 * density).toInt()
        field.layoutParams = fieldParams
        layout.addView(field)
    }
}
