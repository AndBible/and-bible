package net.bible.sharedcore.platform.desktop.dbus

import java.lang.reflect.Type
import org.freedesktop.dbus.Marshalling
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the actual wire signatures: Kotlin variance otherwise emits an invalid dictionary value. */
class PortalSignatureTest {
    private fun signature(type: Class<*>, name: String) =
        Marshalling.getDBusType(type.declaredMethods.single { it.name == name }.genericParameterTypes)

    @Test fun openFileHasVariantDictionary() {
        assertEquals("ssa{sv}", signature(PortalChooser::class.java, "OpenFile"))
    }

    @Test fun saveFileHasVariantDictionary() {
        assertEquals("ssa{sv}", signature(PortalChooser::class.java, "SaveFile"))
    }

    @Test fun openUriHasVariantDictionary() {
        assertEquals("ssa{sv}", signature(PortalOpenUriInterface::class.java, "OpenURI"))
    }

    @Test fun chooserFilterStructsMatchPortalProtocol() {
        assertEquals("(sa(us))", Marshalling.getDBusType(PortalFilter::class.java as Type).joinToString(""))
        assertEquals("(us)", Marshalling.getDBusType(PortalFilterEntry::class.java as Type).joinToString(""))
    }
}
