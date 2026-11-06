package net.bible.desktop.wintoast

import net.bible.sharedcore.platform.NativeNotification
import java.math.BigDecimal
import java.security.MessageDigest

const val TOAST_GROUP = "andbible"
const val ANDBIBLE_AUMID = "org.andbible.AndBible"

fun toastXml(n: NativeNotification): String = buildString {
    append("<toast><visual><binding template=\"ToastGeneric\">")
    append("<text>").append(xmlEscape(n.title)).append("</text>")
    append("<text>").append(xmlEscape(n.body)).append("</text>")
    if (n.progress != null || n.indeterminate) {
        append("<progress value=\"{progressValue}\" valueStringOverride=\"{progressText}\" status=\"{status}\"/>")
    }
    append("</binding></visual>")
    if (!n.urgent) append("<audio silent=\"true\"/>")
    append("</toast>")
}

fun toastData(n: NativeNotification): Map<String, String> {
    val progress = n.progress
    return mapOf(
        "progressValue" to when {
            n.indeterminate -> "indeterminate"
            progress != null -> BigDecimal.valueOf(progress.toLong()).divide(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
            else -> ""
        },
        "progressText" to if (progress != null && !n.indeterminate) "$progress %" else "",
        "status" to n.body,
    )
}

fun toastTag(id: String): String = if (id.length <= 64) id else
    MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }.take(64)

private fun xmlEscape(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                append(char).append(value[index + 1])
                index++
            }
            char.isSurrogate() -> Unit
            char == '\t' || char == '\n' || char == '\r' || char.code in 0x20..0xD7FF ||
                char.code in 0xE000..0xFFFD -> append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '\"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char
                },
            )
        }
        index++
    }
}
