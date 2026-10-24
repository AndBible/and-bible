package net.bible.sharedcore.util

fun formatHoursMinsSecs(secs: Long): String {
    val h = (secs / 3600).toInt()
    val m = (secs / 60 % 60).toInt()
    val s = (secs % 60).toInt()

    val hms = StringBuilder()
    if (h > 0) {
        hms.append(h).append(":")
    }

    // add padding for 1 digit mins
    if (m < 10) {
        hms.append(0)
    }
    hms.append(m).append(":")

    // add padding for 1 digit secs
    if (s < 10) {
        hms.append(0)
    }
    hms.append(s)
    return hms.toString()
}
