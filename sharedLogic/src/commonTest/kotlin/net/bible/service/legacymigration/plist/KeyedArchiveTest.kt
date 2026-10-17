package net.bible.service.legacymigration.plist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyedArchiveTest {
    private fun archive(vararg objects: PlistValue, rootUid: Int = 1): ByteArray = BplistBuilder.build(
        PlistDict(mapOf(
            "\$archiver" to PlistString("NSKeyedArchiver"),
            "\$version" to PlistInt(100000),
            "\$top" to PlistDict(mapOf("root" to PlistUid(rootUid))),
            "\$objects" to PlistArray(listOf<PlistValue>(PlistString("\$null")) + objects),
        )),
    )

    private fun cls(name: String, vararg supers: String) = PlistDict(mapOf(
        "\$classname" to PlistString(name),
        "\$classes" to PlistArray((listOf(name) + supers).map { PlistString(it) }),
    ))

    @Test fun emptyNSSetDecodesToEmptySet() {
        // Mirrors the shape Core Data stores for an empty transformable NSSet attribute.
        val bytes = archive(
            PlistDict(mapOf("NS.objects" to PlistArray(emptyList()), "\$class" to PlistUid(2))),
            cls("NSSet", "NSObject"),
        )
        assertEquals(emptySet<Any?>(), KeyedArchive.decode(bytes))
    }

    @Test fun nsSetOfTwoUuids() {
        val u1 = ByteArray(16) { it.toByte() }
        val u2 = ByteArray(16) { (0xF0 + it).toByte() }
        val bytes = archive(
            PlistDict(mapOf("NS.objects" to PlistArray(listOf(PlistUid(3), PlistUid(4))), "\$class" to PlistUid(2))),
            cls("NSSet", "NSObject"),
            PlistDict(mapOf("NS.uuidbytes" to PlistData(u1), "\$class" to PlistUid(5))),
            PlistDict(mapOf("NS.uuidbytes" to PlistData(u2), "\$class" to PlistUid(5))),
            cls("NSUUID", "NSObject"),
        )
        assertEquals(
            setOf<Any?>(
                LegacyUuid("00010203-0405-0607-0809-0a0b0c0d0e0f"),
                LegacyUuid("f0f1f2f3-f4f5-f6f7-f8f9-fafbfcfdfeff"),
            ),
            KeyedArchive.decode(bytes),
        )
    }

    @Test fun nsArrayAndPrimitives() {
        val bytes = archive(
            PlistDict(mapOf("NS.objects" to PlistArray(listOf(PlistUid(3), PlistUid(4), PlistUid(0), PlistUid(5), PlistUid(6))), "\$class" to PlistUid(2))),
            cls("NSMutableArray", "NSArray"),
            PlistString("hi"),
            PlistInt(42),
            PlistReal(2.5),
            PlistBool(true),
        )
        assertEquals(listOf<Any?>("hi", 42L, null, 2.5, true), KeyedArchive.decode(bytes))
    }

    @Test fun nullRootIsNull() {
        assertEquals(null, KeyedArchive.decode(archive(rootUid = 0)))
    }

    @Test fun unsupportedClassThrows() {
        val bytes = archive(
            PlistDict(mapOf("NS.keys" to PlistArray(emptyList()), "\$class" to PlistUid(2))),
            cls("NSDictionary", "NSObject"),
        )
        val e = assertFailsWith<LegacyFormatException> { KeyedArchive.decode(bytes) }
        assertEquals("unsupported archived class NSDictionary", e.message)
    }

    @Test fun nonArchiveThrows() {
        assertFailsWith<LegacyFormatException> { KeyedArchive.decode(BplistBuilder.build(PlistString("x"))) }
    }
}
