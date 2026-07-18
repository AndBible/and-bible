package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalToolPermissionsControllerTest {

    private val readCategory = ToolCategoryVd("READ", "Read")
    private val writeCategory = ToolCategoryVd("WRITE", "Write")
    private val getVerse = ToolVd("getVerse", "Get verse", "Reads a verse", requiresPermission = false, categoryId = "READ")
    private val addBookmark = ToolVd("addBookmark", "Add bookmark", "Adds a bookmark", requiresPermission = true, categoryId = "WRITE")

    private val catalog: List<Pair<ToolCategoryVd, List<ToolVd>>> =
        listOf(readCategory to listOf(getVerse), writeCategory to listOf(addBookmark))

    private class Fake(
        private val catalog: List<Pair<ToolCategoryVd, List<ToolVd>>>,
        seed: Map<String, ToolPermission> = emptyMap(),
    ) : ToolPermissionService {
        val overrides: MutableMap<String, ToolPermission> = seed.toMutableMap()
        var lastSave: Map<String, ToolPermission>? = null
        var saveCount = 0
        var resetCount = 0

        override fun toolsByCategory() = catalog

        override fun permissionFor(toolId: String): ToolPermission =
            overrides[toolId] ?: defaultFor(toolId)

        override fun save(permissions: Map<String, ToolPermission>) {
            lastSave = permissions
            saveCount++
            overrides.clear()
            overrides.putAll(permissions)
        }

        override fun resetAll() {
            resetCount++
            overrides.clear()
        }

        private fun defaultFor(toolId: String): ToolPermission {
            val requiresPermission = catalog.flatMap { it.second }.first { it.id == toolId }.requiresPermission
            return if (requiresPermission) ToolPermission.ASK else ToolPermission.ENABLED
        }
    }

    private fun controller(fake: Fake) = GlobalToolPermissionsController(fake, CoroutineScope(UnconfinedTestDispatcher()))

    @Test fun state_reflectsToolsByCategoryGrouped() = runTest {
        val f = Fake(catalog)
        val c = controller(f)
        assertEquals(
            listOf(ToolPermGroupVd(readCategory, listOf(getVerse)), ToolPermGroupVd(writeCategory, listOf(addBookmark))),
            c.state.value,
        )
    }

    @Test fun permissions_seededFromServicePermissionFor() = runTest {
        val f = Fake(catalog, seed = mapOf("addBookmark" to ToolPermission.ALLOW))
        val c = controller(f)
        assertEquals(
            mapOf("getVerse" to ToolPermission.ENABLED, "addBookmark" to ToolPermission.ALLOW),
            c.permissions.value,
        )
        assertFalse(c.isDirty.value)
    }

    @Test fun setPermission_updatesWorkingMapAndDirty() = runTest {
        val f = Fake(catalog)
        val c = controller(f)
        assertFalse(c.isDirty.value)

        c.setPermission("addBookmark", ToolPermission.DENY)
        assertEquals(ToolPermission.DENY, c.permissions.value["addBookmark"])
        assertTrue(c.isDirty.value)
        // service untouched until save()
        assertNull(f.lastSave)
        assertEquals(0, f.saveCount)

        // flipping back to the original value clears dirty again (equality-based tracking)
        c.setPermission("addBookmark", ToolPermission.ASK)
        assertFalse(c.isDirty.value)
    }

    @Test fun save_routesWorkingMapToServiceAndRebaselines() = runTest {
        val f = Fake(catalog)
        val c = controller(f)
        c.setPermission("addBookmark", ToolPermission.ALLOW)
        c.setPermission("getVerse", ToolPermission.DISABLED)
        assertTrue(c.isDirty.value)

        c.save()

        assertEquals(mapOf("getVerse" to ToolPermission.DISABLED, "addBookmark" to ToolPermission.ALLOW), f.lastSave)
        assertEquals(1, f.saveCount)
        assertFalse(c.isDirty.value)

        // further edits are dirty relative to the new baseline, not the original defaults
        c.setPermission("getVerse", ToolPermission.ENABLED)
        assertTrue(c.isDirty.value)
        c.setPermission("getVerse", ToolPermission.DISABLED)
        assertFalse(c.isDirty.value)
    }

    @Test fun resetAll_callsServiceResetAndReseedsWorkingMapToDefaults() = runTest {
        val f = Fake(catalog)
        val c = controller(f)
        c.setPermission("addBookmark", ToolPermission.DENY)
        c.setPermission("getVerse", ToolPermission.DISABLED)
        assertTrue(c.isDirty.value)

        c.resetAll()

        assertEquals(1, f.resetCount)
        assertEquals(
            mapOf("getVerse" to ToolPermission.ENABLED, "addBookmark" to ToolPermission.ASK),
            c.permissions.value,
        )
        assertFalse(c.isDirty.value)
        // resetAll is an immediate service action, not staged behind save()
        assertEquals(0, f.saveCount)
    }
}
