package net.bible.sharedcore.platform

/** Strings domain code needs. One member per resource; grows per batch (spec L1a section 3). */
interface CoreStrings {
    val labelAll: String // R.string.all
    val errorOccurred: String // R.string.error_occurred
    fun somethingWithParenthesis(a: String, b: String): String // R.string.something_with_parenthesis: "%1$s (%2$s)"
    fun readingPlanDay(day: String): String // R.string.rdg_plan_day: "Day %s"
}
