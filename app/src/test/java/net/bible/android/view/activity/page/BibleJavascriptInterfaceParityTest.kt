package net.bible.android.view.activity.page

import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.sharedcore.bibleview.BibleBridgeDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * L2a spec §5.1: the TS `BibleJavascriptInterface` type, the decoder table and the Kotlin
 * [BibleBridge] contract list the same methods with the same arity, and the deferred sets agree.
 */
class BibleJavascriptInterfaceParityTest {
    private val androidTs = File("bibleview-js/src/composables/android.ts").readText()

    /** name → arity, parsed from `export type BibleJavascriptInterface = { ... }`. */
    private fun tsMembers(): Map<String, Int> {
        val body = androidTs.substringAfter("export type BibleJavascriptInterface = {").substringBefore("\n}")
        return Regex("""^\s+(\w+):\s*(.+?),?\s*$""", RegexOption.MULTILINE).findAll(body).associate { m ->
            val name = m.groupValues[1]
            val type = m.groupValues[2]
            name to if (type.startsWith("AsyncFunc")) 1 else topLevelParams(type)
        }
    }

    private fun topLevelParams(type: String): Int {
        val inner = type.substringAfter("(").let { s ->
            var depth = 1; var i = 0
            while (depth > 0) { when (s[i]) { '(', '<', '{', '[' -> depth++; ')', '>', '}', ']' -> depth-- }; i++ }
            s.substring(0, i - 1)
        }
        if (inner.isBlank()) return 0
        var depth = 0; var count = 1
        for (c in inner) when (c) { '(', '<', '{', '[' -> depth++; ')', '>', '}', ']' -> depth--; ',' -> if (depth == 0) count++ }
        return count
    }

    @Test fun tsTypeDecoderAndContractAgree() {
        val ts = tsMembers() - "getActiveLanguages"
        val decoder = BibleBridgeDecoder.entries.mapValues { it.value.kinds.length }
        val kotlin = BibleBridge::class.java.methods.associate { it.name to it.parameterCount }
        assertEquals(ts.toSortedMap(), decoder.toSortedMap())
        assertEquals(decoder.toSortedMap(), kotlin.toSortedMap())
    }

    @Test fun deferredSetsAgree() {
        val viaDeferredCall = Regex("""deferredCall\(\(callId\)\s*=>\s*window\.android\.(\w+)\(""")
            .findAll(androidTs).map { it.groupValues[1] }.toSet()
        assertEquals(BibleBridgeDecoder.deferredMethods.toSortedSet(), viaDeferredCall.toSortedSet())
    }

    @Test fun parserSeesTheWholeType() {
        assertEquals(88, tsMembers().size)
    }
}
