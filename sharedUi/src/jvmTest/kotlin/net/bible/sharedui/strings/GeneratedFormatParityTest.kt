package net.bible.sharedui.strings

import java.io.File
import java.lang.reflect.Modifier
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.stringsgen.MemberKind
import net.bible.stringsgen.parseAndroidStringsMapping
import net.bible.stringsgen.parseStringsInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Generated/keyed parity, not Android equivalence for Android's two preformatted Float arguments. */
class GeneratedFormatParityTest {
    @Test fun generatedAndTableFormattersAgree() {
        val root = File(checkNotNull(System.getProperty("repoRoot")))
        val api = parseStringsInterface(File(root, "sharedUi/src/commonMain/kotlin/net/bible/sharedui/strings/Strings.kt").readText())
        val mappings = parseAndroidStringsMapping(File(root, "app/src/main/java/net/bible/sharedui/strings/AndroidStrings.kt").readText())
        assertEquals(api.size, api.map { it.name }.toSet().size, "duplicate interface names")
        assertEquals(mappings.size, mappings.map { it.name }.toSet().size, "duplicate mappings")
        assertEquals(api.map { it.name }.toSet(), mappings.map { it.name }.toSet(), "mapping coverage")
        val formatted = mappings.filter { it.kind == MemberKind.FORMAT_FUN || it.kind == MemberKind.PLURAL_FUN }
        val methods = Strings::class.java.declaredMethods.filter {
            !it.isSynthetic && !Modifier.isStatic(it.modifiers) &&
                it.returnType == String::class.java && it.parameterCount > 0
        }
        assertEquals(formatted.map { it.name }.toSet(), methods.map { it.name }.toSet(), "real parameterized String API coverage")
        assertEquals(methods.size, methods.map { it.name }.toSet().size, "overloads need explicit handling")
        assertTrue(formatted.isNotEmpty())
        for (tag in listOf("en", "fi")) {
            val strings = iosStrings(tag)
            val table = StringTable.of(tag, { lookup(tag, it) }, { n, c -> lookupPlural(tag, n, c) }, { lookupArray(tag, it) })
            val handled = mutableSetOf<String>()
            for (mapping in formatted) {
                val args = mapping.params.mapIndexed<String, Any> { index, decl ->
                    when (val type = decl.substringAfter(':').trim()) {
                        "String" -> "sample-$index"
                        "Int" -> 7 + index * 4
                        "Double" -> 1.5 + index
                        "Float" -> 1.25f + index
                        else -> error("Unhandled parameter type $type in ${mapping.name}")
                    }
                }.toTypedArray()
                val values = mapping.params.mapIndexed { index, decl -> decl.substringBefore(':').trim() to args[index] }.toMap()
                assertTrue(lookup(tag, mapping.key) != mapping.key || mapping.kind == MemberKind.PLURAL_FUN, "missing key ${mapping.key}")
                val expected = when (mapping.kind) {
                    MemberKind.FORMAT_FUN -> table.fmt(mapping.key, *args)
                    MemberKind.PLURAL_FUN -> {
                        val count = values.getValue(mapping.pluralCountArg) as Int
                        assertTrue(lookupPlural(tag, mapping.key, count) != mapping.key, "missing plural ${mapping.key}")
                        table.pluralFormat(mapping.key, count, *mapping.pluralFmtArgs.map { values.getValue(it) }.toTypedArray())
                    }
                    else -> error("Unhandled formatted mapping ${mapping.kind}")
                }
                val method = methods.single { it.name == mapping.name }
                assertEquals(args.size, method.parameterCount, mapping.name)
                assertEquals(expected, method.invoke(strings, *args), "$tag ${mapping.name}")
                assertTrue(handled.add(mapping.name), "duplicate invocation")
            }
            assertEquals(methods.map { it.name }.toSet(), handled, "invoked API coverage for $tag")
        }
    }
}
