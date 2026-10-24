package net.bible.android.control.link

import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.SystemKJVA
import org.crosswire.jsword.versification.system.Versifications

/** A link target coming from the Bible text (osis ref, content ref, strong, morph, sword) and its canonical URL form. */
class BibleLink(val type: String, val target: String, private val v11nName: String? = null, val forceDoc: Boolean = false) {
    val versification: Versification get() =
        Versifications.instance().getVersification(v11nName ?: SystemKJVA.V11N_NAME) ?: KJVA
    val url: String get() {
        return when(type) {
            "content" -> "$type:$target"
            "strong" -> "$type:$target"
            "robinson" -> "$type:$target"
            "strongMorph" -> "$type:$target"
            else -> {
                if(target.startsWith("sword://") || target.startsWith("osis:"))
                    target
                else {
                    var protocol = "osis:"
                    var ref = target
                    if (target.split(":").size > 1) {
                        protocol = "sword://"
                        ref = target.replace(":", "/")
                    }
                    "$protocol$ref"
                }
            }
        }
    }
}
