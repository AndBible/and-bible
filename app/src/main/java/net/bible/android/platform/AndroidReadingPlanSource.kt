package net.bible.android.platform

import net.bible.android.SharedConstants
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.readingplan.ReadingPlanSource
import java.io.File
import java.io.IOException

/** The bundled reading plans in `assets/readingplan`, read exactly as the reading plan DAO always did. */
class AndroidReadingPlanSource : ReadingPlanSource {
    private val assets get() = CommonUtils.resources.assets

    override fun builtInPlanCodes(): List<String> =
        (assets.list(FOLDER) ?: emptyArray()).filter { it.endsWith(DOT_PROPERTIES) }.map { it.replace(DOT_PROPERTIES, "") }

    override fun openBuiltInPlan(code: String): String? =
        try {
            assets.open(FOLDER + File.separator + code + DOT_PROPERTIES).use { String(it.readBytes(), Charsets.ISO_8859_1) }
        } catch (e: IOException) {
            null
        }

    private companion object {
        const val FOLDER = SharedConstants.READINGPLAN_DIR_NAME
        const val DOT_PROPERTIES = ".properties"
    }
}
