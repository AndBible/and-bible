package net.bible.sharedlogic.text

import kotlin.test.Test
import kotlin.test.assertEquals

class NameBasedUuidTest {
    @Test fun matchesJvmOracleLiteral() = assertEquals(
        "fb526879-5a02-30f1-970c-6ba95307a999",
        NameBasedUuid.v3("andbible-builtin:translate-ui-language".encodeToByteArray()),
    )

    @Test fun emptyInput() = assertEquals(
        "d41d8cd9-8f00-3204-a980-0998ecf8427e",
        NameBasedUuid.v3(ByteArray(0)),
    )
}
