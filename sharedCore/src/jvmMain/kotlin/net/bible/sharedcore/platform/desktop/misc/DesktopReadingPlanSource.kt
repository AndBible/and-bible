package net.bible.sharedcore.platform.desktop.misc

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.readingplan.ReadingPlanSource

class DesktopReadingPlanSource(private val loader: ClassLoader = DesktopReadingPlanSource::class.java.classLoader) : ReadingPlanSource {
    override fun builtInPlanCodes(): List<String> {
        val index = loader.getResourceAsStream("readingplan/index.txt")
        if (index == null) {
            Log.w("DesktopReadingPlanSource", "Bundled reading plan index is missing")
            return emptyList()
        }
        return index.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.filter(::validCode).distinct().sorted().toList() }
    }

    override fun openBuiltInPlan(code: String): String? {
        if (!validCode(code)) return null
        val input = loader.getResourceAsStream("readingplan/$code.properties")
        if (input == null) {
            Log.w("DesktopReadingPlanSource", "Bundled reading plan not found: $code")
            return null
        }
        return input.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
    }

    private fun validCode(code: String) = code.isNotEmpty() && code != "." && code != ".." && '/' !in code && '\\' !in code
}
