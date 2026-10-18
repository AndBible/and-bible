/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedui.strings.keyed

import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.llm.AgentTool
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayString
import net.bible.sharedui.strings.StringsData
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The `Keyed*` classes read strings by resource key; a misspelt key answers the key itself, so every member is called
 * against the generated tables and must answer real, fully filled text.
 */
class KeyedStringsTest {
    /**
     * A table over the generated tables of [tag] that fails, naming the key, when a key (or plural/array name) is not
     * in the English table. A missing key would otherwise answer itself, which no text check can tell from real text
     * for a one-word key such as `cancel`.
     */
    private fun tableFor(tag: String, raw: ((String) -> String)? = null): StringTable {
        fun known(key: String) = key.also { check(it in StringsData.data.getValue("en")) { "Unknown string key: $it" } }
        val text = { key: String -> lookup(tag, known(key)) }
        val plural = { name: String, count: Int ->
            check(name in StringsData.plurals.getValue("en")) { "Unknown plural: $name" }
            lookupPlural(tag, name, count)
        }
        val array = { name: String ->
            check(name in StringsData.arrays.getValue("en")) { "Unknown array: $name" }
            lookupArray(tag, name)
        }
        return if (raw == null) StringTable.of(tag, text, plural, array) else StringTable.of(tag, text, plural, array, raw)
    }

    private val table = tableFor("en")
    private val placeholder = Regex("%(\\d+\\$)?[0-9.]*[sdfxX]")
    private val resourceKey = Regex("[a-z][A-Za-z0-9]*(_[A-Za-z0-9]+)+")

    /** [filled] false for no-argument members: Android answers their raw template too (e.g. sqliteInvalidFile keeps its "%s"). */
    private fun assertText(what: String, value: String, filled: Boolean = true) {
        assertTrue(value.isNotBlank(), "$what is blank")
        assertFalse(resourceKey.matches(value), "$what answered a resource key: $value")
        if (filled) assertFalse(placeholder.containsMatchIn(value), "$what has an unfilled placeholder: $value")
    }

    private fun assertList(what: String, value: List<String>) {
        assertTrue(value.isNotEmpty(), "$what is empty")
        value.forEach {
            assertText("$what item", it)
            assertFalse(it.startsWith("@string/"), "$what item is an unresolved reference: $it")
        }
    }

    @Test
    fun coreStringsAnswerRealText() {
        val core = KeyedCoreStrings(table)
        assertText("namelessTitle", core.namelessTitle, filled = false)
        assertText("processingEpub", core.processingEpub("x"))
        assertText("creatingIndexFor", core.creatingIndexFor("x"))
        assertText("syncIntroduction", core.syncIntroduction, filled = false)
        assertText("syncDriveIntroduction", core.syncDriveIntroduction(false))
        assertText("syncAdapterSummary", core.syncAdapterSummary("x"))
        assertText("syncLastUpdated", core.syncLastUpdated("x"))
        assertText("syncCloudInfo", core.syncCloudInfo("x"))
        assertText("documentSyncNothing", core.documentSyncNothing, filled = false)
        assertText("documentSyncUpload", core.documentSyncUpload(3))
        assertText("documentSyncUploadSize", core.documentSyncUploadSize(3, "x"))
        assertText("documentSyncDownload", core.documentSyncDownload(3))
        assertText("documentSyncDownloadSize", core.documentSyncDownloadSize(3, "x"))
        assertText("documentSyncWifiWaiting", core.documentSyncWifiWaiting, filled = false)
        assertText("storageSpaceWarning", core.storageSpaceWarning, filled = false)
        assertText("errorDownloading", core.errorDownloading, filled = false)
        assertText("journalDescription", core.journalDescription, filled = false)
        assertText("studypads", core.studypads, filled = false)
        assertText("compareDescription", core.compareDescription, filled = false)
        assertText("compareAbbreviation", core.compareAbbreviation, filled = false)
        assertText("memorizeDescription", core.memorizeDescription, filled = false)
        assertText("memorizeAbbreviation", core.memorizeAbbreviation, filled = false)
        assertText("multiDescription", core.multiDescription, filled = false)
        assertText("multiAbbreviation", core.multiAbbreviation, filled = false)
        assertText("myNotesDescription", core.myNotesDescription, filled = false)
        assertText("myNotesAbbreviation", core.myNotesAbbreviation, filled = false)
        assertText("errorDownloadingStatus", core.errorDownloadingStatus("x", "x", 3))
        assertText("labelAll", core.labelAll, filled = false)
        assertText("installZipSuccessful", core.installZipSuccessful, filled = false)
        assertText("installZipCanceled", core.installZipCanceled, filled = false)
        assertText("invalidModule", core.invalidModule, filled = false)
        assertText("sqliteInvalidFile", core.sqliteInvalidFile, filled = false)
        assertText("aiUnknownPrompt", core.aiUnknownPrompt, filled = false)
        assertText("errorPageTooLarge", core.errorPageTooLarge, filled = false)
        assertText("workspaceNumber", core.workspaceNumber(3))
        assertText("errorOccurred", core.errorOccurred, filled = false)
        assertText("errorNoContent", core.errorNoContent, filled = false)
        assertText("errorKeyNotInDocument", core.errorKeyNotInDocument("x", "x"))
        assertText("verseShareAdvertise", core.verseShareAdvertise, filled = false)
        assertText("somethingWithParenthesis", core.somethingWithParenthesis("x", "x"))
        assertText("readingPlanDay", core.readingPlanDay("x"))
        assertText("sortByAlphabetical", core.sortByAlphabetical, filled = false)
        assertText("sortByBibleBook", core.sortByBibleBook, filled = false)
        assertText("documentNotInstalled", core.documentNotInstalled("x"))
        assertText("noIndexedBibleWithStrongsRef", core.noIndexedBibleWithStrongsRef, filled = false)
        assertText("wordNotFoundInDictionaries", core.wordNotFoundInDictionaries, filled = false)
        assertText("speakNoBooksAvailable", core.speakNoBooksAvailable, filled = false)
        assertText("speakGeneralError", core.speakGeneralError, filled = false)
        assertText("verseRangeModeDisabled", core.verseRangeModeDisabled, filled = false)
        assertText("rewind", core.rewind, filled = false)
        assertText("forward", core.forward, filled = false)
        assertText("pause", core.pause, filled = false)
        assertText("stop", core.stop, filled = false)
        assertText("speakStatusStopped", core.speakStatusStopped, filled = false)
        assertText("sleepTimerStarted", core.sleepTimerStarted(3))
        assertText("signInFailed", core.signInFailed, filled = false)
        assertText("cloudSyncTitle", core.cloudSyncTitle, filled = false)
        assertText("cloudFetchAndRestoreInitial", core.cloudFetchAndRestoreInitial, filled = false)
        assertText("cloudCreateNew", core.cloudCreateNew, filled = false)
        assertText("cloudDisableSync", core.cloudDisableSync, filled = false)
        assertText("syncCantFetch", core.syncCantFetch, filled = false)
        assertText("syncUpdateApp", core.syncUpdateApp, filled = false)
        assertText("syncError", core.syncError, filled = false)
        assertText("okay", core.okay, filled = false)
        assertText("cancel", core.cancel, filled = false)
        assertText("areYouSure", core.areYouSure, filled = false)
        assertText("overrideBackup", core.overrideBackup("x"))
        assertText("areYouSureResetLocal", core.areYouSureResetLocal("x"))
        assertText("areYouSureResetCloud", core.areYouSureResetCloud("x"))
        assertText("syncDisabling", core.syncDisabling("x"))
        assertText("share", core.share, filled = false)
        assertText("backupPhoneStorage", core.backupPhoneStorage, filled = false)
        assertText("sendBackupFile", core.sendBackupFile, filled = false)
        assertText("backupBackupTitle", core.backupBackupTitle, filled = false)
        assertText("backupBackupMessage", core.backupBackupMessage, filled = false)
        assertText("backupSuccess2", core.backupSuccess2, filled = false)
        assertText("pleaseWait", core.pleaseWait, filled = false)
        assertText("restoreBackupSections", core.restoreBackupSections, filled = false)
        assertText("backupModulesTitle", core.backupModulesTitle, filled = false)
        assertText("backupModulesSuccess", core.backupModulesSuccess, filled = false)
        assertText("apkFile", core.apkFile, filled = false)
        assertText("backupApp2", core.backupApp2, filled = false)
        assertText("disablingSync", core.disablingSync, filled = false)
        assertText("downloadingBackup", core.downloadingBackup, filled = false)
        assertText("loadingBackup", core.loadingBackup, filled = false)
        assertText("restoreUnsuccessfull", core.restoreUnsuccessfull, filled = false)
        assertText("restoreSuccess", core.restoreSuccess, filled = false)
        assertText("restoreConfirmation", core.restoreConfirmation, filled = false)
        assertText("restore", core.restore, filled = false)
        assertText("import2", core.import2, filled = false)
        assertText("resetDatabaseSuccess", core.resetDatabaseSuccess, filled = false)
        assertText("selectAll", core.selectAll, filled = false)
        assertText("selectNone", core.selectNone, filled = false)
        assertText("backupEmailSubject2", core.backupEmailSubject2("x"))
        assertText("backupEmailMessage2", core.backupEmailMessage2("x"))
        assertText("backupModulesEmailSubject2", core.backupModulesEmailSubject2("x"))
        assertText("backupModulesEmailMessage2", core.backupModulesEmailMessage2("x", "x"))
        assertText("overwriteSomething", core.overwriteSomething("x"))
        assertText("askRestoreOrImport", core.askRestoreOrImport("x"))
        assertText("resetDatabaseConfirm", core.resetDatabaseConfirm("x"))
        assertText("backupFileInfo", core.backupFileInfo(3, "x"))
        assertText("bookmarksDbStats", core.bookmarksDbStats("x", "x", "x"))
        assertText("exportedStudypadsSubject", core.exportedStudypadsSubject, filled = false)
        assertText("exportedStudypadsMessage", core.exportedStudypadsMessage("x"))
        assertText("sendExportFile", core.sendExportFile, filled = false)
        assertText("exportDestinationTitle", core.exportDestinationTitle, filled = false)
        assertText("exportDestinationMessage", core.exportDestinationMessage, filled = false)
        assertText("searchUnsupportedSyntax", core.searchUnsupportedSyntax("x"))
        assertList("memorizeWordVisibilityEntries", core.memorizeWordVisibilityEntries)
        assertList("memorizeWordVisibilityValues", core.memorizeWordVisibilityValues)
        listOf("READINGPLANS", "BOOKMARKS", "WORKSPACES", "MYDOCUMENTS", "AI_SETTINGS", "PROGRESS").forEach {
            assertText("syncCategoryContents($it)", core.syncCategoryContents(it))
        }
        assertFailsWith<IllegalArgumentException> { core.syncCategoryContents("NOPE") }
        listOf(403, 404, 500).forEach { assertText("httpStatusText($it)", core.httpStatusText(it)) }
        listOf("bookmarks.sqlite3", "readingplans.sqlite3", "workspaces.sqlite3", "repositories.sqlite3", "settings.sqlite3",
            "mydocuments.sqlite3", "ai_settings.sqlite3", "progress.sqlite3").forEach { assertText("databaseTitle($it)", core.databaseTitle(it)) }
        assertFailsWith<IllegalStateException> { core.databaseTitle("other.sqlite3") }
        assertText("syncDriveIntroduction(true)", core.syncDriveIntroduction(true))
    }

    @Test
    fun coreStringsFillArguments() {
        val core = KeyedCoreStrings(table)
        assertEquals("Day 7", core.readingPlanDay("7"))
        assertEquals("Genesis (KJV)", core.somethingWithParenthesis("Genesis", "KJV"))
        assertEquals("Please download 'KJV'", core.documentNotInstalled("KJV"))
        assertTrue("AndBible: Bible Study" in core.verseShareAdvertise, core.verseShareAdvertise)
    }

    @Test
    fun discreteDriveIntroductionHidesTheBibleAppName() {
        val core = KeyedCoreStrings(table)
        assertTrue("Simple Calculator" in core.syncDriveIntroduction(true), core.syncDriveIntroduction(true))
        assertFalse("Bible Study" in core.syncDriveIntroduction(true), core.syncDriveIntroduction(true))
        assertTrue("Bible Study" in core.syncDriveIntroduction(false), core.syncDriveIntroduction(false))
    }

    @Test
    fun memorizeWordVisibilityArraysPairUp() {
        listOf("en", "fi", "de").forEach { tag ->
            val core = KeyedCoreStrings(tableFor(tag))
            assertList("$tag entries", core.memorizeWordVisibilityEntries)
            assertTrue(core.memorizeWordVisibilityValues.isNotEmpty(), "$tag values")
            assertEquals(core.memorizeWordVisibilityEntries.size, core.memorizeWordVisibilityValues.size, tag)
            assertTrue(core.memorizeWordVisibilityValues.none { it.isBlank() }, tag)
        }
    }

    @Test
    fun llmStringsAnswerRealText() {
        val llm = KeyedLlmStrings(table)
        assertText("actionActiveWindow", llm.actionActiveWindow, filled = false)
        assertText("actionAddEntryToStudypad", llm.actionAddEntryToStudypad("x"))
        assertText("actionAddLabelToBookmark", llm.actionAddLabelToBookmark("x", "x"))
        assertText("actionAddMyDocumentPage", llm.actionAddMyDocumentPage("x", "x"))
        assertText("actionAddNoteToBookmarkAt", llm.actionAddNoteToBookmarkAt("x"))
        assertText("actionCloseWindow", llm.actionCloseWindow("x"))
        assertText("actionCreateBookmarkAt", llm.actionCreateBookmarkAt("x"))
        assertText("actionCreateBookmarkAtWithExtras", llm.actionCreateBookmarkAtWithExtras("x", "x"))
        assertText("actionCreateLabel", llm.actionCreateLabel("x"))
        assertText("actionCreateMyDocument", llm.actionCreateMyDocument("x"))
        assertText("actionCreateStudypad", llm.actionCreateStudypad("x", 3))
        assertText("actionCreateWindow", llm.actionCreateWindow, filled = false)
        assertText("actionCreateWindowWithDoc", llm.actionCreateWindowWithDoc("x"))
        assertText("actionCreateWindowWithDocAndKey", llm.actionCreateWindowWithDocAndKey("x", "x"))
        assertText("actionDeleteBookmark", llm.actionDeleteBookmark("x"))
        assertText("actionDeleteLabel", llm.actionDeleteLabel("x"))
        assertText("actionDeleteMyDocumentPage", llm.actionDeleteMyDocumentPage("x"))
        assertText("actionEditMyDocumentPage", llm.actionEditMyDocumentPage("x"))
        assertText("actionFinish", llm.actionFinish("x"))
        assertText("actionLabels", llm.actionLabels("x"))
        assertText("actionMinimizeWindow", llm.actionMinimizeWindow("x"))
        assertText("actionOpenMyDocumentPage", llm.actionOpenMyDocumentPage("x"))
        assertText("actionOpenStudypad", llm.actionOpenStudypad("x"))
        assertText("actionRemoveLabelFromBookmark", llm.actionRemoveLabelFromBookmark("x", "x"))
        assertText("actionRestoreWindow", llm.actionRestoreWindow("x"))
        assertText("actionSetDocumentTitle", llm.actionSetDocumentTitle("x"))
        assertText("actionSetWindowDocument", llm.actionSetWindowDocument("x", "x"))
        assertText("actionSetWindowDocumentWithKey", llm.actionSetWindowDocumentWithKey("x", "x", "x"))
        assertText("actionUpdateNoteOnBookmarkAt", llm.actionUpdateNoteOnBookmarkAt("x"))
        assertText("actionUpdateStudypadTextEntry", llm.actionUpdateStudypadTextEntry("x"))
        assertText("actionWithNote", llm.actionWithNote, filled = false)
        assertText("agentAlreadyRunning", llm.agentAlreadyRunning, filled = false)
        assertText("agentLogCancelled", llm.agentLogCancelled, filled = false)
        assertText("agentLogCompleted", llm.agentLogCompleted, filled = false)
        assertText("agentLogDone", llm.agentLogDone("x"))
        assertText("agentLogExecuting", llm.agentLogExecuting("x"))
        assertText("agentLogIteration", llm.agentLogIteration(3))
        assertText("agentLogResponseReceived", llm.agentLogResponseReceived, filled = false)
        assertText("agentLogSaved", llm.agentLogSaved("x"))
        assertText("agentLogTool", llm.agentLogTool("x"))
        assertText("agentLogToolFailed", llm.agentLogToolFailed("x"))
        assertText("agentLogViewRaw", llm.agentLogViewRaw, filled = false)
        assertText("aiAgentNotificationCancel", llm.aiAgentNotificationCancel, filled = false)
        assertText("aiAgentNotificationCompleted", llm.aiAgentNotificationCompleted, filled = false)
        assertText("aiAgentNotificationCompletedTap", llm.aiAgentNotificationCompletedTap, filled = false)
        assertText("aiAgentNotificationError", llm.aiAgentNotificationError, filled = false)
        assertText("aiAgentNotificationPermissionNeeded", llm.aiAgentNotificationPermissionNeeded, filled = false)
        assertText("aiAgentNotificationPermissionTap", llm.aiAgentNotificationPermissionTap, filled = false)
        assertText("aiAgentNotificationRunning", llm.aiAgentNotificationRunning, filled = false)
        assertText("aiLanguageAppDefault", llm.aiLanguageAppDefault("x"))
        assertText("aiRegeneratePromptNotFound", llm.aiRegeneratePromptNotFound, filled = false)
        assertText("cancel", llm.cancel, filled = false)
        assertText("commentaryFilterDialogMessage", llm.commentaryFilterDialogMessage("x"))
        assertText("commentaryFilterDialogTitle", llm.commentaryFilterDialogTitle, filled = false)
        assertText("commentaryFilterItem", llm.commentaryFilterItem("x", "x", "x"))
        assertText("commentaryFilterTotalTokens", llm.commentaryFilterTotalTokens("x", "x"))
        assertText("defaultPromptAskQuestion", llm.defaultPromptAskQuestion, filled = false)
        assertText("defaultPromptAskQuestionDesc", llm.defaultPromptAskQuestionDesc, filled = false)
        assertText("defaultPromptBookmarkAnnotate", llm.defaultPromptBookmarkAnnotate, filled = false)
        assertText("defaultPromptBookmarkAnnotateDesc", llm.defaultPromptBookmarkAnnotateDesc, filled = false)
        assertText("defaultPromptCompareTranslations", llm.defaultPromptCompareTranslations, filled = false)
        assertText("defaultPromptCompareTranslationsDesc", llm.defaultPromptCompareTranslationsDesc, filled = false)
        assertText("defaultPromptCrossReferences", llm.defaultPromptCrossReferences, filled = false)
        assertText("defaultPromptCrossReferencesDesc", llm.defaultPromptCrossReferencesDesc, filled = false)
        assertText("defaultPromptCustom", llm.defaultPromptCustom, filled = false)
        assertText("defaultPromptCustomDesc", llm.defaultPromptCustomDesc, filled = false)
        assertText("defaultPromptCustomTemplate", llm.defaultPromptCustomTemplate, filled = false)
        assertText("defaultPromptEnhanceNote", llm.defaultPromptEnhanceNote, filled = false)
        assertText("defaultPromptEnhanceNoteDesc", llm.defaultPromptEnhanceNoteDesc, filled = false)
        assertText("defaultPromptExplainVerses", llm.defaultPromptExplainVerses, filled = false)
        assertText("defaultPromptExplainVersesDesc", llm.defaultPromptExplainVersesDesc, filled = false)
        assertText("defaultPromptExplainVersesStudypad", llm.defaultPromptExplainVersesStudypad, filled = false)
        assertText("defaultPromptExplainVersesStudypadDesc", llm.defaultPromptExplainVersesStudypadDesc, filled = false)
        assertText("defaultPromptStudyLayout", llm.defaultPromptStudyLayout, filled = false)
        assertText("defaultPromptStudyLayoutDesc", llm.defaultPromptStudyLayoutDesc, filled = false)
        assertText("defaultPromptSummary", llm.defaultPromptSummary, filled = false)
        assertText("defaultPromptSummaryDesc", llm.defaultPromptSummaryDesc, filled = false)
        assertText("defaultPromptThematicStudy", llm.defaultPromptThematicStudy, filled = false)
        assertText("defaultPromptThematicStudyDesc", llm.defaultPromptThematicStudyDesc, filled = false)
        assertText("defaultPromptTranslateToLanguage", llm.defaultPromptTranslateToLanguage("x"))
        assertText("defaultPromptTranslateToUiLanguageDesc", llm.defaultPromptTranslateToUiLanguageDesc, filled = false)
        assertText("defaultPromptWordStudy", llm.defaultPromptWordStudy, filled = false)
        assertText("defaultPromptWordStudyDesc", llm.defaultPromptWordStudyDesc, filled = false)
        assertText("defaultPromptWorkspaceAssistant", llm.defaultPromptWorkspaceAssistant, filled = false)
        assertText("defaultPromptWorkspaceAssistantDesc", llm.defaultPromptWorkspaceAssistantDesc, filled = false)
        assertText("easySetupAnthropicDesc", llm.easySetupAnthropicDesc, filled = false)
        assertText("easySetupFreeTier", llm.easySetupFreeTier, filled = false)
        assertText("easySetupGeminiDesc", llm.easySetupGeminiDesc, filled = false)
        assertText("easySetupOpenaiDesc", llm.easySetupOpenaiDesc, filled = false)
        assertText("llmContinueIterationsMessage", llm.llmContinueIterationsMessage(3, 3))
        assertText("llmContinueIterationsTitle", llm.llmContinueIterationsTitle, filled = false)
        assertText("llmCostTotal", llm.llmCostTotal("x"))
        assertText("llmDefaultDocumentTitle", llm.llmDefaultDocumentTitle, filled = false)
        assertText("llmDefaultStudypadOpened", llm.llmDefaultStudypadOpened, filled = false)
        assertText("llmDefaultTaskCompleted", llm.llmDefaultTaskCompleted, filled = false)
        assertText("llmErrorMaxIterations", llm.llmErrorMaxIterations(3))
        assertText("llmErrorUnknown", llm.llmErrorUnknown, filled = false)
        assertText("llmNoDocumentCreationIntercepted", llm.llmNoDocumentCreationIntercepted, filled = false)
        assertText("llmNoteUpdated", llm.llmNoteUpdated, filled = false)
        assertText("llmParseError", llm.llmParseError("x"))
        assertText("llmParseErrorAnthropic", llm.llmParseErrorAnthropic("x"))
        assertText("llmParseErrorNoChoices", llm.llmParseErrorNoChoices, filled = false)
        assertText("llmParseErrorOpenai", llm.llmParseErrorOpenai("x"))
        assertText("llmProviderCustom", llm.llmProviderCustom, filled = false)
        assertText("llmRateLimitedRetrying", llm.llmRateLimitedRetrying("x"))
        assertText("llmUsageSummaryDefault", llm.llmUsageSummaryDefault, filled = false)
        assertText("llmUsageSummaryFormat", llm.llmUsageSummaryFormat(3L, 3L, "x"))
        assertText("okay", llm.okay, filled = false)
        assertText("permissionAlwaysAllowConfirm", llm.permissionAlwaysAllowConfirm("x"))
        assertText("permissionAlwaysAllowConfirmResetHint", llm.permissionAlwaysAllowConfirmResetHint, filled = false)
        assertText("permissionAlwaysAllowConfirmTitle", llm.permissionAlwaysAllowConfirmTitle, filled = false)
        assertText("promptCategoryFavorites", llm.promptCategoryFavorites, filled = false)
        assertText("promptCategoryGeneral", llm.promptCategoryGeneral, filled = false)
        assertText("promptCategoryNotes", llm.promptCategoryNotes, filled = false)
        assertText("promptCategoryStudy", llm.promptCategoryStudy, filled = false)
        assertText("promptCategoryTest", llm.promptCategoryTest, filled = false)
        assertText("promptCategoryUncategorized", llm.promptCategoryUncategorized, filled = false)
        assertText("promptModelDefault", llm.promptModelDefault, filled = false)
        assertText("rawLlmLogEntryApiResponse", llm.rawLlmLogEntryApiResponse(3))
        assertText("rawLlmLogEntryAssistant", llm.rawLlmLogEntryAssistant, filled = false)
        assertText("rawLlmLogEntryEmpty", llm.rawLlmLogEntryEmpty, filled = false)
        assertText("rawLlmLogEntrySystem", llm.rawLlmLogEntrySystem, filled = false)
        assertText("rawLlmLogEntryTokens", llm.rawLlmLogEntryTokens("x"))
        assertText("rawLlmLogEntryToolCall", llm.rawLlmLogEntryToolCall("x"))
        assertText("rawLlmLogEntryToolDefinitions", llm.rawLlmLogEntryToolDefinitions(3))
        assertText("rawLlmLogEntryToolDesc", llm.rawLlmLogEntryToolDesc("x"))
        assertText("rawLlmLogEntryToolParams", llm.rawLlmLogEntryToolParams("x"))
        assertText("rawLlmLogEntryToolResult", llm.rawLlmLogEntryToolResult("x"))
        assertText("rawLlmLogEntryUsage", llm.rawLlmLogEntryUsage("x", "x", "x"))
        assertText("rawLlmLogEntryUser", llm.rawLlmLogEntryUser, filled = false)
        assertText("rawLlmLogTotal", llm.rawLlmLogTotal("x", "x", "x"))
        assertText("rawLogItemTokens", llm.rawLogItemTokens("x", "x"))
        assertText("selectAll", llm.selectAll, filled = false)
        assertText("selectNone", llm.selectNone, filled = false)
        assertText("toolCategoryBibleSearch", llm.toolCategoryBibleSearch, filled = false)
        assertText("toolCategoryBookmarks", llm.toolCategoryBookmarks, filled = false)
        assertText("toolCategoryGeneralBooks", llm.toolCategoryGeneralBooks, filled = false)
        assertText("toolCategoryLabels", llm.toolCategoryLabels, filled = false)
        assertText("toolCategoryMyDocuments", llm.toolCategoryMyDocuments, filled = false)
        assertText("toolCategoryStudyPads", llm.toolCategoryStudyPads, filled = false)
        assertText("toolCategoryWindows", llm.toolCategoryWindows, filled = false)
        assertText("toolLogBookmarkCount", llm.toolLogBookmarkCount(3))
        assertText("toolLogCommentaryCount", llm.toolLogCommentaryCount(3))
        assertText("toolLogDocumentCount", llm.toolLogDocumentCount(3))
        assertText("toolLogGenbookKeyCount", llm.toolLogGenbookKeyCount(3, 3))
        assertText("toolLogLabelCount", llm.toolLogLabelCount(3))
        assertText("toolLogMyDocumentCount", llm.toolLogMyDocumentCount(3))
        assertText("toolLogMyDocumentPageCount", llm.toolLogMyDocumentPageCount(3))
        assertText("toolLogSearchResults", llm.toolLogSearchResults(3, 3))
        assertText("toolLogStudyPadCount", llm.toolLogStudyPadCount(3))
        assertText("toolLogWindowCopy", llm.toolLogWindowCopy, filled = false)
        assertText("toolLogWindowCount", llm.toolLogWindowCount(3))
        assertText("toolLogWindowMinimized", llm.toolLogWindowMinimized, filled = false)
        assertText("toolLogWindowRemoved", llm.toolLogWindowRemoved, filled = false)
        AgentTool.entries.forEach { assertText("toolDisplayName($it)", llm.toolDisplayName(it)) }
    }

    @Test
    fun llmPromptsFailLoudlyWithoutARawResource() {
        assertFailsWith<IllegalStateException> { KeyedLlmStrings(table).defaultAgentSystemPrompt }
        val withRaw = KeyedLlmStrings(tableFor("en") { "raw:$it" })
        assertEquals("raw:llm_agent_system_prompt", withRaw.defaultAgentSystemPrompt)
        assertEquals("raw:llm_text_transformation_system_prompt", withRaw.defaultTextTransformationSystemPrompt)
    }

    @Test
    fun labelNamesAnswerRealText() {
        val labels = KeyedLabelNameStrings(table)
        assertText("speak", labels.speak)
        assertText("unlabelled", labels.unlabelled)
        assertText("paragraphBreak", labels.paragraphBreak)
        assertText("ai", labels.ai)
    }

    @Test
    fun everyTextDisplaySettingHasATitle() {
        val labels = KeyedSettingLabels(table)
        TextDisplaySettings.Types.entries.forEach { assertText("label($it)", labels.label(SettingsBundle(), it)) }
        assertEquals(
            TextDisplaySettings.Types.entries.size,
            TextDisplaySettings.Types.entries.map { KeyedSettingLabels.titleKey(it) }.toSet().size,
        )
    }

    @Test
    fun textDisplayStringKeysFormatLikeAndroid() {
        fun text(key: TextDisplayString, vararg args: Any) = table.fmt(textDisplayStringKey(key), *args)
        assertEquals("14 pt", text(TextDisplayString.FONT_SIZE_PT, 14))
        assertEquals("9 mm", text(TextDisplayString.VALUE_MM, 9))
        assertEquals("Line spacing 1.5x", text(TextDisplayString.LINE_SPACING_PT, 1.5f))
        assertEquals("Margin size (4/5/6 mm)", text(TextDisplayString.MARGIN_SIZE_MM_TITLE, 4, 5, 6))
        assertEquals("Text options - My ws", text(TextDisplayString.WORKSPACE_TITLE, "My ws"))
        assertEquals("Text options - Window 2", text(TextDisplayString.WINDOW_TITLE, 2))
        TextDisplayString.entries.forEach { key ->
            val args = arrayOf<Any>(1, 2, 3)
            val result = table.fmt(textDisplayStringKey(key), *args)
            assertText("$key", result)
            assertNotEquals(textDisplayStringKey(key), result)
        }
        TextDisplayArray.entries.forEach { assertList("array $it", table.array(textDisplayArrayName(it))) }
    }

    @Test
    fun formatTemplateHandlesTheSpecsAndroidStringsUse() {
        assertEquals("Day 7", formatTemplate("Day %s", arrayOf("7")))
        assertEquals("3 of 5", formatTemplate("%d of %d", arrayOf(3, 5)))
        assertEquals("5 of 3", formatTemplate("%2\$d of %1\$d", arrayOf(3, 5)))
        assertEquals("1.5 pt", formatTemplate("%.1f pt", arrayOf(1.5f)))
        assertEquals("1.2", formatTemplate("%.1f", arrayOf(1.2f)))
        assertEquals("0.5", formatTemplate("%.1f", arrayOf(0.45)))
        assertEquals("-2.0", formatTemplate("%.1f", arrayOf(-2.0)))
        assertEquals("12", formatTemplate("%.0f", arrayOf(12.4)))
        assertEquals("x1.5x", formatTemplate("x%1.1fx", arrayOf(1.5f)))
        assertEquals("100%", formatTemplate("%d%%", arrayOf(100)))
        assertEquals("0A", formatTemplate("%02X", arrayOf(10)))
        assertEquals("a (b)", formatTemplate("%1\$s (%2\$s)", arrayOf("a", "b")))
        assertEquals("x ", formatTemplate("x %s", arrayOf()))
        assertEquals("50 %", formatTemplate("50 %", arrayOf()))
        assertEquals("a%q", formatTemplate("a%q", arrayOf()))
    }

    @Test
    fun allSevenPlansAreNamed() {
        val plans = KeyedDistributedPlanCatalog(table).plans()
        assertEquals(7, plans.size)
        assertEquals(7, plans.map { it.planCode }.toSet().size)
        plans.forEach {
            assertText("name ${it.planCode}", it.planName)
            assertText("description ${it.planCode}", it.planDescription)
        }
    }

    @Test
    fun speakPhrasesAreCompleteAndDivineNameArraysPairUp() {
        listOf("en", "fi", "de", "zh-Hans").forEach { tag ->
            val p = keyedSpeakPhrases(tableFor(tag))
            listOf(p.first, p.second, p.third, p.fourth, p.fifth, p.bookChanged, p.chapterChanged).forEach { assertText("$tag phrase", it) }
            assertTrue(p.divineNameOriginal.isNotEmpty(), "$tag divine names")
            assertEquals(p.divineNameOriginal.size, p.divineNameReplace.size, tag)
        }
    }

    @Test
    fun aFinnishTableAnswersFinnish() {
        val fi = tableFor("fi")
        assertNotEquals(KeyedLabelNameStrings(table).speak, KeyedLabelNameStrings(fi).speak)
        assertText("fi memorize", KeyedCoreStrings(fi).memorizeDescription)
    }
}
