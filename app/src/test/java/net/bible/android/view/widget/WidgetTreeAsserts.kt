package net.bible.android.view.widget

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Helpers for asserting on the view tree a Glance widget inflates to. */
fun View.allViews(): List<View> =
    listOf(this) + ((this as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { g.getChildAt(it).allViews() } } ?: emptyList())

fun View.texts(): List<String> = allViews().filterIsInstance<TextView>().map { it.text.toString() }

fun View.descriptions(): List<String> = allViews().mapNotNull { it.contentDescription?.toString() }

/** The first view with this content description, or null. */
fun View.byDescription(description: String): View? = allViews().firstOrNull { it.contentDescription?.toString() == description }

/** The first view in the tree that carries a click handler: the widget's background tap. */
fun View.rootClickable(): View? = allViews().firstOrNull { it.hasOnClickListeners() }
