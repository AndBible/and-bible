package net.bible.android.view.activity.readingplan.model

import java.util.Date

data class DayBarItem (
    val dayNumber: Int,
    val date: Date,
    /** This day's readings are being shown in daily reading */
    var dayActive: Boolean,
    var dayReadPartial: Boolean,
    var dayReadComplete: Boolean,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DayBarItem

        if (dayNumber != other.dayNumber) return false
        if (date != other.date) return false
        if (dayActive != other.dayActive) return false
        if (dayReadPartial != other.dayReadPartial) return false
        if (dayReadComplete != other.dayReadComplete) return false

        return true
    }
    override fun hashCode(): Int {
        var result = dayNumber
        result = 31 * result + date.hashCode()
        result = 31 * result + dayActive.hashCode()
        result = 31 * result + dayReadPartial.hashCode()
        result = 31 * result + dayReadComplete.hashCode()
        return result
    }
}
