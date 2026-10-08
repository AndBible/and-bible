package net.bible.android.control.event

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WorkspaceChange
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.ai.LlmModelServiceImpl
import net.bible.android.view.activity.ai.LlmProviderServiceImpl
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.service.common.AiSettings
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ABEventBus phase 8, Task 2: the settings and add-on owner streams fire from their real posters. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SettingsOwnerStreamsTest {
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    @Test fun recentLabelUpdateEmitsSettingsEdited() {
        val got = mutableListOf<WorkspaceChange>()
        val sub = WorkspaceChanges.changes.subscribe { got += it }
        try {
            CommonUtils.windowControl.windowRepository.updateRecentLabels(listOf(IdType()))
            assertEquals(listOf<WorkspaceChange>(WorkspaceChange.SettingsEdited), got)
        } finally { sub.cancel() }
    }

    @Test fun manageLabelsResultEmitsSettingsEdited() {
        val got = mutableListOf<WorkspaceChange>()
        val sub = WorkspaceChanges.changes.subscribe { got += it }
        try {
            WorkspaceEntities.WorkspaceSettings().updateFrom(
                ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.WORKSPACE)
            )
            assertEquals(listOf<WorkspaceChange>(WorkspaceChange.SettingsEdited), got)
        } finally { sub.cancel() }
    }

    private val providers get() = GlobalContext.get().get<LlmProviderService>() as LlmProviderServiceImpl
    private val models get() = GlobalContext.get().get<LlmModelService>() as LlmModelServiceImpl

    private fun countConfigChanges(block: suspend () -> Unit): Int {
        var n = 0
        val sub = AiSettings.configChanged.subscribe { n++ }
        try { runBlocking { block() } } finally { sub.cancel() }
        return n
    }

    private fun createProvider(): String {
        runBlocking { providers.saveProvider(null, providers.providerTypes().first().id, "T", "k", "", "") }
        return models.providersForPicker().last().id
    }

    @Test fun creatingAProviderEmitsConfigChanged() {
        assertEquals(1, countConfigChanges {
            providers.saveProvider(null, providers.providerTypes().first().id, "T", "k", "", "")
        })
    }

    @Test fun editingAProviderDoesNotEmit() {
        val id = createProvider()
        assertEquals(0, countConfigChanges {
            providers.saveProvider(id, providers.providerTypes().first().id, "T2", "k", "", "")
        }) // as today: only a create notifies
    }

    @Test fun deletingAProviderEmitsConfigChanged() {
        val id = createProvider()
        assertEquals(1, countConfigChanges { providers.deleteProvider(id) })
    }

    @Test fun savingAndDeletingAModelEmitConfigChanged() {
        val providerId = createProvider()
        assertEquals(1, countConfigChanges { models.saveModel(null, providerId, "m1", null, null, false) })
        val modelId = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao().all()
            .single { it.modelId == "m1" }.id.toString()
        assertEquals(1, countConfigChanges { models.deleteModel(modelId) })
    }

    @Test fun clearingAddonCachesEmitsReloaded() {
        var n = 0
        val sub = AndBibleAddons.reloaded.subscribe { n++ }
        try { AndBibleAddons.clearCaches(); assertEquals(1, n) } finally { sub.cancel() }
    }
}
