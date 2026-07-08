package net.bible.stringsgen

enum class MemberKind { VAL, FORMAT_FUN }

/** One member of the Strings interface as recovered from AndroidStrings.kt.
 *  - VAL: [key] is its single resource key; [params] empty.
 *  - FORMAT_FUN: [key] is the format string's key; [params] are the fun's param decls
 *    (e.g. "price: String", "channel: Int") in declaration order. */
data class StringMember(
    val name: String,
    val kind: MemberKind,
    val key: String = "",
    val params: List<String> = emptyList(),
)

/** Android values-qualifiers that are NOT locales (screen-size / density / UI-mode / API-version
 *  configuration variants). These carry no strings.xml in the AndBible tree today, but skipping
 *  them explicitly keeps the generator deterministic if one is ever added. */
private val NON_LOCALE_QUALIFIERS = setOf(
    "values-land",
    "values-large",
    "values-large-land",
    "values-large-port",
    "values-night",
    "values-sw600dp",
    "values-v9",
    "values-v14",
    "values-v27",
    "values-xlarge",
    "values-xlarge-land",
    "values-xlarge-port",
)

/** True if [qualifier] is a known non-locale configuration qualifier that must be skipped. */
fun isNonLocaleQualifier(qualifier: String): Boolean = qualifier in NON_LOCALE_QUALIFIERS

/** Android values-qualifier → BCP-47/iOS tag. Base "values" → "en". */
fun qualifierToTag(qualifier: String): String = when (qualifier) {
    "values" -> "en"
    "values-iw" -> "he"
    "values-in" -> "id"
    "values-fil" -> "fil"
    "values-yue" -> "yue"
    "values-b+sr" -> "sr"
    "values-b+sr+Latn" -> "sr-Latn"
    "values-b+sr+RS" -> "sr-RS"
    "values-pt-rBR" -> "pt-BR"
    "values-zh-rCN" -> "zh-Hans"
    "values-zh-rTW" -> "zh-Hant"
    else -> qualifier.removePrefix("values-")
}
