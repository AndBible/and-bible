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

    /**
     * Real `ZWORKSPACE.ZAUTOASSIGNLABELS` blob (Core Data transformable NSSet) from the 1.0.2 / iOS 26 `rich`
     * fixture store. All 16 fixture stores hold this same one-element set; none holds an empty one.
     */
    private val realNSSetHex =
            "62706C6973743030D4010203040506070A582476657273696F6E592461726368697665725424746F7058246F626A6563" +
            "747312000186A05F100F4E534B657965644172636869766572D1080954726F6F748001A50B0C12161C55246E756C6CD2" +
            "0D0E0F115A4E532E6F626A656374735624636C617373A11080028004D2130E14155C4E532E7575696462797465734F10" +
            "10130000000000000000000000000001018003D21718191A5A24636C6173736E616D655824636C6173736573564E5355" +
            "554944A2191B584E534F626A656374D217181D1E554E53536574A21D1B08111A24293237494C5153595F646F76787A7C" +
            "818EA1A3A8B3BCC3C6CFD4DA0000000000000101000000000000001F000000000000000000000000000000DD"

    @Test fun realCoreDataNSSetBlobDecodes() {
        val bytes = realNSSetHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(setOf<Any?>(LegacyUuid("13000000-0000-0000-0000-000000000101")), KeyedArchive.decode(bytes))
    }

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

    @Test fun missingTopRootThrows() {
        val bytes = BplistBuilder.build(PlistDict(mapOf(
            "\$archiver" to PlistString("NSKeyedArchiver"),
            "\$top" to PlistDict(emptyMap()),
            "\$objects" to PlistArray(listOf(PlistString("\$null"))),
        )))
        assertFailsWith<LegacyFormatException> { KeyedArchive.decode(bytes) }
    }

    @Test fun outOfRangeObjectIndexThrows() {
        assertFailsWith<LegacyFormatException> { KeyedArchive.decode(archive(rootUid = 9)) }
    }
}
