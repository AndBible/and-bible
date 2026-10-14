/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android

import kotlinx.coroutines.flow.map

import net.bible.android.view.activity.ai.RawLogServiceImpl
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.bind
import net.bible.android.view.activity.ai.LlmModelServiceImpl
import net.bible.android.view.activity.ai.LlmProviderServiceImpl
import net.bible.android.view.activity.ai.AiSettingsServiceImpl
import net.bible.android.view.activity.ai.PromptServiceImpl
import net.bible.android.view.activity.ai.AgentSessionServiceImpl
import net.bible.android.view.activity.ai.ReadingLlmServiceImpl
import net.bible.android.view.activity.ai.DocumentFilterServiceImpl
import net.bible.android.view.activity.ai.ToolPermissionServiceImpl

import net.bible.android.control.document.DocumentChanges
import net.bible.android.view.activity.nav.SystemBarSettingChanges
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.device.ScreenSettings
import android.content.res.Resources
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakSettingsChanges
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.common.CommonUtils
import net.bible.service.common.AndBibleAddons
import net.bible.service.db.DatabaseContainer
import net.bible.service.installzip.InstallZipProgress
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.koin.core.context.GlobalContext

/**
 * Override settings if required
 */

const val TEST_SDK = 33
class TestBibleApplication : BibleApplication() {
    private companion object {
        /** The container detached at the previous test's teardown, closed at this one's (see [onTerminate]). */
        var detachedContainer: DatabaseContainer? = null
    }

    init {
        println("TestBibleApplication BibleApplication subclass being used.")
    }

    private var aiScope = net.bible.sharedcore.platform.AppCoroutineScope()
    private var previousAppScope: net.bible.sharedcore.platform.AppCoroutineScope? = null
    private var previousLauncher: net.bible.sharedcore.platform.OrderedLauncher? = null
    private var aiLauncher = net.bible.sharedcore.platform.OrderedLauncher(aiScope)
    private var overriddenKoin: org.koin.core.Koin? = null
    private var terminated = false
    private var terminationPending = false
    private var startupRegistrationOwner: net.bible.sharedcore.platform.OrderedLauncher? = null
    private var previousManagerLauncher: net.bible.sharedcore.platform.OrderedLauncher? = null
    private var previousSecretStore: net.bible.service.llm.SecretStore? = null
    private var fixtureSecretStore: net.bible.service.llm.SecretStore? = null
    private var presenterReleased = false
    override val isRunningTests: Boolean = true

    override fun getLocalizedResources(language: String): Resources = application.resources

    override fun onCreate() {
        net.bible.test.DatabaseResetter.requireNoRetainedWorkers()
        net.bible.test.ReadingFixtureOwners.requireEmpty()
        check(!terminationPending && startupRegistrationOwner == null) {
            "Previous fixture termination/startup has not completed; retry onTerminate first"
        }
        // The manager may still own work from the preceding graph. Do not replace its launcher,
        // graph or resources until that exact queue has completed, including on failed startup.
        startupRegistrationOwner = runCatching { MyDocumentBookManager.launcher }.getOrNull()
        startupRegistrationOwner?.let { previous ->
            net.bible.test.DatabaseResetter.runResetBlocking { previous.join("mydocument-sync") }
        }
        startupRegistrationOwner = null
        if (terminated) {
            aiScope = net.bible.sharedcore.platform.AppCoroutineScope()
            aiLauncher = net.bible.sharedcore.platform.OrderedLauncher(aiScope)
        }
        terminated = false
        presenterReleased = false
        // At the start, not in onTerminate: a test may terminate the application mid-way with its activity still live.
        CurrentActivityHolder.forgetActivitiesForTest()
        // The production handler is process-global; Robolectric creates an application per test.
        // Never leave its reporting/delegation chain installed in the shared test JVM.
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            super.onCreate()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
        val koin = GlobalContext.get()
        overriddenKoin = koin
        previousAppScope = koin.get()
        previousLauncher = koin.get()
        previousManagerLauncher = MyDocumentBookManager.launcher
        startupRegistrationOwner = previousManagerLauncher
        net.bible.test.DatabaseResetter.runResetBlocking {
            checkNotNull(startupRegistrationOwner).join("mydocument-sync")
        }
        startupRegistrationOwner = null
        previousSecretStore = koin.get()
        fixtureSecretStore = net.bible.android.platform.AndroidSecretStore(CommonUtils.realSharedPreferences)
        koin.loadModules(listOf(org.koin.dsl.module {
            single<net.bible.service.llm.SecretStore> { checkNotNull(fixtureSecretStore) }
            single<net.bible.sharedcore.platform.AppCoroutineScope> { aiScope }
            single<net.bible.sharedcore.platform.OrderedLauncher> { aiLauncher }
            // Singletons keep the launcher they were built with, and the fixture replaces aiScope after a terminate.
            single { net.bible.android.control.page.window.WorkspacePersistence(aiLauncher) { DatabaseContainer.instance.workspaceDb.workspaceDao() } }
            single<net.bible.service.llm.AiSettings> {
                net.bible.service.llm.AiSettingsStore(
                    { DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao() }, aiLauncher, aiScope,
                    net.bible.android.platform.AndroidAppEnvironment(this@TestBibleApplication),
                    DatabaseContainer.databaseRestored, get<CloudSync>().workspaceRefreshRequired, DatabaseContainer.aiLifetime,
                    { DatabaseContainer.instance.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } },
                )
            }
            singleOf(::RawLogServiceImpl) { bind<net.bible.sharedcore.ai.RawLogService>() }
            singleOf(::LlmModelServiceImpl) { bind<net.bible.sharedcore.ai.LlmModelService>() }
            singleOf(::LlmProviderServiceImpl) { bind<net.bible.sharedcore.ai.LlmProviderService>() }
            singleOf(::AiSettingsServiceImpl) { bind<net.bible.sharedcore.ai.AiSettingsService>() }
            singleOf(::PromptServiceImpl) { bind<net.bible.sharedcore.ai.PromptService>() }
            singleOf(::AgentSessionServiceImpl) { bind<net.bible.sharedcore.ai.reading.AgentSessionService>() }
            singleOf(::ReadingLlmServiceImpl) { bind<net.bible.sharedcore.ai.reading.ReadingLlmService>() }
            singleOf(::DocumentFilterServiceImpl) { bind<net.bible.sharedcore.ai.DocumentFilterService>() }
            singleOf(::ToolPermissionServiceImpl) { bind<net.bible.sharedcore.ai.ToolPermissionService>() }
        }))
        MyDocumentBookManager.launcher = aiLauncher
        CommonUtils.initializeApp()
        kotlinx.coroutines.runBlocking { CommonUtils.aiSettings.ensureLoaded() }
        net.bible.test.DatabaseResetter.runResetBlocking {
            MyDocumentBookManager.launcher.join("mydocument-sync")
        }
    }

    /**
     * This is never called in real system (only in tests). See parent documentation.
     */
    suspend fun drainAiSettingsFixture() {
        aiLauncher.join("ai-settings")
        aiLauncher.join("mydocument-sync")
        val children = aiScope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
        children.forEach { it.cancel() }
        children.forEach { it.join() }
    }

    override fun onTerminate() {
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try { action() } catch (e: Throwable) {
                if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e)
            }
        }
        startupRegistrationOwner?.let { previous ->
            terminationPending = true
            var drained = false
            cleanup {
                net.bible.test.DatabaseResetter.runResetBlocking { previous.join("mydocument-sync") }
                drained = true
            }
            if (!drained) {
                if (!presenterReleased) cleanup {
                    super.onTerminate()
                    presenterReleased = true
                }
                throw failure ?: IllegalStateException("Retained startup work has not completed")
            }
            startupRegistrationOwner = null
            terminationPending = false
        }
        if (terminated) return
        terminationPending = true
        // Test factories/controller helpers retain stopped, unactivated and private owners.
        CurrentActivityHolder.liveActivitiesForTest().forEach { activity ->
            if (activity is net.bible.android.view.activity.nav.NavHostComposeActivity)
                net.bible.test.ReadingFixtureOwners.retain(activity)
        }
        var readingDrained = false
        cleanup {
            net.bible.test.DatabaseResetter.runResetBlocking { net.bible.test.ReadingFixtureOwners.drain() }
            readingDrained = true
        }
        if (!readingDrained) throw failure ?: IllegalStateException("Reading fixture ownership incomplete")
        val job = aiScope.coroutineContext[kotlinx.coroutines.Job]!!
        cleanup {
            net.bible.test.DatabaseResetter.runResetBlocking {
                try {
                    aiLauncher.join("ai-settings")
                    aiLauncher.join("mydocument-sync")
                } finally {
                    job.cancel()
                    job.join()
                }
            }
        }
        // The abortable Main pump may return before its cancelled worker finishes joining.
        // Cancellation alone is not quiescence: retain ownership until a retry proves completion.
        cleanup { job.cancel() }
        if (!job.isCompleted) {
            if (!presenterReleased) cleanup {
                super.onTerminate()
                presenterReleased = true
            }
            throw failure ?: IllegalStateException("Fixture owner has not completed termination")
        }
        cleanup {
            val koin = overriddenKoin
            // Never restore an old fixture's bindings into a replacement global graph.
            if (koin != null && GlobalContext.getOrNull() === koin) {
                if (MyDocumentBookManager.launcher === aiLauncher) {
                    MyDocumentBookManager.launcher = checkNotNull(previousManagerLauncher)
                }
                if (koin.getOrNull<net.bible.service.llm.SecretStore>() === fixtureSecretStore) {
                    koin.loadModules(listOf(org.koin.dsl.module {
                        single<net.bible.service.llm.SecretStore> { checkNotNull(previousSecretStore) }
                    }))
                }
                koin.loadModules(listOf(org.koin.dsl.module {
                    single<net.bible.sharedcore.platform.AppCoroutineScope> { checkNotNull(previousAppScope) }
                    single<net.bible.sharedcore.platform.OrderedLauncher> { checkNotNull(previousLauncher) }
                    single { net.bible.android.control.page.window.WorkspacePersistence(checkNotNull(previousLauncher)) { DatabaseContainer.instance.workspaceDb.workspaceDao() } }
                    single<net.bible.service.llm.AiSettings> {
                        net.bible.service.llm.AiSettingsStore(
                            { DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao() }, get(), get(), get(),
                            DatabaseContainer.databaseRestored, get<CloudSync>().workspaceRefreshRequired, DatabaseContainer.aiLifetime,
                            { DatabaseContainer.instance.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } },
                        )
                    }
                    singleOf(::RawLogServiceImpl) { bind<net.bible.sharedcore.ai.RawLogService>() }
                    singleOf(::LlmModelServiceImpl) { bind<net.bible.sharedcore.ai.LlmModelService>() }
                    singleOf(::LlmProviderServiceImpl) { bind<net.bible.sharedcore.ai.LlmProviderService>() }
                    singleOf(::AiSettingsServiceImpl) { bind<net.bible.sharedcore.ai.AiSettingsService>() }
                    singleOf(::PromptServiceImpl) { bind<net.bible.sharedcore.ai.PromptService>() }
                    singleOf(::AgentSessionServiceImpl) { bind<net.bible.sharedcore.ai.reading.AgentSessionService>() }
                    singleOf(::ReadingLlmServiceImpl) { bind<net.bible.sharedcore.ai.reading.ReadingLlmService>() }
                    singleOf(::DocumentFilterServiceImpl) { bind<net.bible.sharedcore.ai.DocumentFilterService>() }
                    singleOf(::ToolPermissionServiceImpl) { bind<net.bible.sharedcore.ai.ToolPermissionService>() }
                }))
            }
            overriddenKoin = null
        }
        cleanup { CommonUtils.destroy() }
        if (!presenterReleased) cleanup {
            super.onTerminate()
            presenterReleased = true
        }
        cleanup { AgentSessionManager.resetSubscribersForTest() }
        cleanup { SharedActivityState.instance.resetSubscribersForTest() }
        cleanup { GlobalContext.getOrNull()?.getOrNull<WindowStateServiceImpl>()?.resetSubscribersForTest() }
        cleanup { WorkspaceChanges.resetSubscribersForTest() }
        cleanup { GlobalContext.getOrNull()?.getOrNull<DocumentSync>()?.resetSubscribersForTest() }
        cleanup { GlobalContext.getOrNull()?.getOrNull<CloudSync>()?.resetSubscribersForTest() }
        cleanup { InstallZipProgress.resetSubscribersForTest() }
        cleanup { SpeakChanges.resetSubscribersForTest() }
        cleanup { SpeakSettingsChanges.resetSubscribersForTest() }
        cleanup { DatabaseContainer.resetBookmarksSyncedForTest() }
        cleanup { MyDocumentBookManager.resetSubscribersForTest() }
        cleanup { PassageChangeMediator.resetSubscribersForTest() }
        cleanup { net.bible.service.history.HistoryManager.resetInstanceForTest() }
        cleanup { ScreenSettings.resetSubscribersForTest() }
        cleanup { CurrentActivityHolder.resetSubscribersForTest() }
        cleanup { SystemBarSettingChanges.resetSubscribersForTest() }
        cleanup { DocumentChanges.resetSubscribersForTest() }
        cleanup { DatabaseContainer.resetPhase8StreamsForTest() }
        cleanup { AndBibleAddons.resetSubscribersForTest() }
        // D1 final review M9: the one-JVM suite's safety net. A test that touched the settings or a DAO without
        // DatabaseContainer.reset() would otherwise leave its container (files in this test's now dead Robolectric
        // data dir) for the next class. The container is detached now (never built) but closed only at the NEXT
        // test's teardown: closing it here broke background work of the finished test still using it (e.g. a
        // BibleView render reading ProgressDb on Dispatchers.Default -> "Database is closed", reported by
        // kotlinx-coroutines-test as UncaughtExceptionsBeforeTest in a later runTest). Last, so nothing above
        // reopens it.
        cleanup {
            val previous = detachedContainer
            detachedContainer = DatabaseContainer.detachInstanceForTest()
            previous?.closeAll()
        }
        terminationPending = false
        terminated = true
        failure?.let { throw it }
    }
}
