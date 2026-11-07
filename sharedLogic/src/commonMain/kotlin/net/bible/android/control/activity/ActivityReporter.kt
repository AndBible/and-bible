package net.bible.android.control.activity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val alwaysAvailable: StateFlow<Boolean> = MutableStateFlow(true)

fun interface ActivityReporter {
    val available: StateFlow<Boolean> get() = alwaysAvailable
    fun report(item: ActivityItem)
}

class ActivityFailureReport(item: ActivityItem) :
    Exception("${item.kind} failed: ${item.title}: ${item.message}", item.cause)
