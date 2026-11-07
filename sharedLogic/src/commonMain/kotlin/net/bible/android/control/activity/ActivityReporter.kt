package net.bible.android.control.activity

fun interface ActivityReporter {
    fun report(item: ActivityItem)
}

class ActivityFailureReport(item: ActivityItem) :
    Exception("${item.kind} failed: ${item.title}: ${item.message}", item.cause)
