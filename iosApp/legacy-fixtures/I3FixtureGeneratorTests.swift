// I3FixtureGeneratorTests.swift
//
// CI-only fixture generator for the I3 legacy migration (Kotlin reads the Swift app's SwiftData
// stores). It is NOT part of the shipped app. The workflow .github/workflows/ios-i3-legacy-fixtures.yml
// copies this file into the legacy Swift checkout at
//   Sources/BibleCore/Tests/BibleCoreTests/
// and runs it with `-only-testing:BibleCoreTests/I3FixtureGeneratorTests`.
//
// The same file must compile against the 1.0.1 release and the pinned 1.0.2 commit. Every API it
// calls has an identical signature in both revisions (see README.md in this directory for the
// differences that exist and why none of them is touched here).
//
// Output (one directory per scenario) under the `I3_FIXTURE_OUT` environment variable:
//   <scenario>/AndBible.store*        CloudKit-capable store (WAL/SHM files included)
//   <scenario>/LocalStore.store*      device-local store
//   <scenario>/sidecar.json           UserDefaults dictionary + Keychain items (test values only)
//   <scenario>/PreservedDatabases/ai_settings.sqlite3   (scenario `preserved-ai` only)

import Foundation
import SwiftData
import XCTest
@testable import BibleCore

final class I3FixtureGeneratorTests: XCTestCase {
    // MARK: Scenarios

    /// Empty container plus the default and system labels the app seeds on first launch.
    func testScenarioFresh() throws {
        try generate(scenario: "fresh") { container, directory in
            let context = ModelContext(container)
            I3Seed.seedDefaultLabels(context)
            return I3Sidecar(userDefaults: [:], keychain: [])
        }
    }

    /// Value-rich data covering every entity, composite settings and settings rows.
    func testScenarioRich() throws {
        try generate(scenario: "rich") { container, directory in
            let context = ModelContext(container)
            try I3Seed.seedRich(context)
            return I3Sidecar.rich
        }
    }

    /// Android bookmark Room v12 fixture restored through the Swift restore path (fidelity rows).
    func testScenarioRestoredAndroid() throws {
        try generate(scenario: "restored-android") { container, directory in
            let context = ModelContext(container)
            try I3Seed.seedRestoredAndroid(context)
            return I3Sidecar(userDefaults: [:], keychain: [])
        }
    }

    /// `rich` plus a preserved Android `ai_settings.sqlite3` whose GlobalAISettings row has the same
    /// fixed id as the Swift row (merge rule U11).
    func testScenarioPreservedAI() throws {
        try generate(scenario: "preserved-ai") { container, directory in
            let context = ModelContext(container)
            try I3Seed.seedRich(context)
            let preserved = directory.appendingPathComponent("PreservedDatabases", isDirectory: true)
            try FileManager.default.createDirectory(at: preserved, withIntermediateDirectories: true)
            try I3Seed.writePreservedAndroidAISettings(
                to: preserved.appendingPathComponent("ai_settings.sqlite3")
            )
            return I3Sidecar.rich
        }
    }

    // MARK: Driver

    /// Creates the two stores for `scenario`, lets `seed` fill them, writes the sidecar, and
    /// verifies that the store files exist.
    private func generate(
        scenario: String,
        seed: (ModelContainer, URL) throws -> I3Sidecar
    ) throws {
        let directory = I3Output.directory(for: scenario)
        try? FileManager.default.removeItem(at: directory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        let sidecar: I3Sidecar
        do {
            // The container is scoped to this block so SwiftData closes both stores before the
            // files are inspected.
            let container = try I3Output.makeContainer(in: directory)
            sidecar = try seed(container, directory)
            try ModelContext(container).save()
        }
        try sidecar.write(to: directory.appendingPathComponent("sidecar.json"))

        let names = try FileManager.default.contentsOfDirectory(atPath: directory.path)
        XCTAssertTrue(names.contains("AndBible.store"), "\(scenario): AndBible.store missing in \(names)")
        XCTAssertTrue(names.contains("LocalStore.store"), "\(scenario): LocalStore.store missing in \(names)")
        print("[I3Fixture] \(scenario) -> \(directory.path)")
    }
}

// MARK: - Output location and container

enum I3Output {
    /// Root directory taken from `I3_FIXTURE_OUT`, falling back to the test's temporary directory.
    static var root: URL {
        if let path = ProcessInfo.processInfo.environment["I3_FIXTURE_OUT"], !path.isEmpty {
            return URL(fileURLWithPath: path, isDirectory: true)
        }
        return URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("i3-fixtures", isDirectory: true)
    }

    static func directory(for scenario: String) -> URL {
        root.appendingPathComponent(scenario, isDirectory: true)
    }

    /// Same schemas and store names as `AndBibleApp.loadModelContainer` with `cloudKitDatabase: .none`;
    /// only the store location differs (an explicit URL under `directory` instead of Application Support).
    static func makeContainer(in directory: URL) throws -> ModelContainer {
        let cloudModels = BibleCoreBaseModelRegistration.cloudModels
            + AIModelRegistration.cloudSyncableModels
        let localModels = BibleCoreBaseModelRegistration.localModels
            + AIModelRegistration.localOnlyModels
        let schema = Schema(cloudModels + localModels)
        let cloudConfig = ModelConfiguration(
            "AndBible",
            schema: Schema(cloudModels),
            url: directory.appendingPathComponent("AndBible.store"),
            cloudKitDatabase: .none
        )
        let localConfig = ModelConfiguration(
            "LocalStore",
            schema: Schema(localModels),
            url: directory.appendingPathComponent("LocalStore.store"),
            cloudKitDatabase: .none
        )
        return try ModelContainer(for: schema, configurations: [cloudConfig, localConfig])
    }
}

// MARK: - Sidecar

/// UserDefaults and Keychain state that is not part of the SwiftData stores. Test values only.
struct I3Sidecar {
    struct KeychainItem {
        let service: String
        let account: String
        let value: String
    }

    var userDefaults: [String: Any]
    var keychain: [KeychainItem]

    static let aiProviderID = UUID(uuidString: "a2000000-0000-0000-0000-000000000001")!

    static var rich: I3Sidecar {
        I3Sidecar(
            userDefaults: [
                "icloud_sync_enabled": true,
                "discrete_mode": true,
                "show_calculator": true,
                "calculator_pin": "4321",
                "locale_pref": "fi",
            ],
            keychain: [
                KeychainItem(
                    service: "org.andbible.BibleCore.AICredentials",
                    account: "llm_api_key_\(aiProviderID.uuidString.lowercased())",
                    value: "test-ai-key-not-a-secret"
                ),
                KeychainItem(
                    service: "org.andbible.remote-sync",
                    account: "gdrive_password",
                    value: "test-gdrive-password-not-a-secret"
                ),
            ]
        )
    }

    func write(to url: URL) throws {
        let object: [String: Any] = [
            "userDefaults": userDefaults,
            "keychain": keychain.map { ["service": $0.service, "account": $0.account, "value": $0.value] },
        ]
        let data = try JSONSerialization.data(
            withJSONObject: object,
            options: [.prettyPrinted, .sortedKeys]
        )
        try data.write(to: url, options: .atomic)
    }
}

// MARK: - Seeding

enum I3Seed {
    // Stable identities, shared with the Kotlin tests through the committed fixtures.
    static let workspaceA = UUID(uuidString: "13000000-0000-0000-0000-0000000000a1")!
    static let workspaceB = UUID(uuidString: "13000000-0000-0000-0000-0000000000b1")!
    static let windowA1 = UUID(uuidString: "13000000-0000-0000-0000-0000000000a2")!
    static let windowA2 = UUID(uuidString: "13000000-0000-0000-0000-0000000000a3")!
    static let windowB1 = UUID(uuidString: "13000000-0000-0000-0000-0000000000b2")!
    static let labelPrayer = UUID(uuidString: "13000000-0000-0000-0000-000000000101")!
    static let labelStudy = UUID(uuidString: "13000000-0000-0000-0000-000000000102")!
    static let bibleBookmark1 = UUID(uuidString: "13000000-0000-0000-0000-000000000201")!
    static let bibleBookmark2 = UUID(uuidString: "13000000-0000-0000-0000-000000000202")!
    static let bibleBookmark3 = UUID(uuidString: "13000000-0000-0000-0000-000000000203")!
    static let genericBookmark = UUID(uuidString: "13000000-0000-0000-0000-000000000211")!
    static let studyPadEntry1 = UUID(uuidString: "13000000-0000-0000-0000-000000000301")!
    static let studyPadEntry2 = UUID(uuidString: "13000000-0000-0000-0000-000000000302")!
    static let studyPadEntry3 = UUID(uuidString: "13000000-0000-0000-0000-000000000303")!
    static let myDocument = UUID(uuidString: "13000000-0000-0000-0000-000000000401")!
    static let myDocumentPage = UUID(uuidString: "13000000-0000-0000-0000-000000000402")!
    static let readingPlan = UUID(uuidString: "13000000-0000-0000-0000-000000000501")!

    /// Default and system labels, as `AndBibleApp.prepareContainerForUse` seeds them.
    static func seedDefaultLabels(_ context: ModelContext) {
        let bookmarkService = BookmarkService(store: BookmarkStore(modelContext: context))
        bookmarkService.prepareDefaultLabels()
        bookmarkService.ensureSystemLabels()
    }

    // MARK: Rich

    static func seedRich(_ context: ModelContext) throws {
        seedDefaultLabels(context)
        let settingsStore = SettingsStore(modelContext: context)

        // Labels: every style flag differs from the defaults.
        let prayer = Label(
            id: labelPrayer, name: "Prayer \u{1F64F}", color: 0x10203040,
            markerStyle: true, markerStyleWholeVerse: true,
            underlineStyle: true, underlineStyleWholeVerse: false,
            hideStyle: true, hideStyleWholeVerse: true, favourite: true
        )
        prayer.type = "HIGHLIGHT"
        prayer.customIcon = "heart"
        let study = Label(id: labelStudy, name: "Study", color: Int(Int32(bitPattern: 0xFF336699)))
        context.insert(prayer)
        context.insert(study)

        try seedWorkspaces(context, labelID: labelPrayer)
        try seedBookmarks(context, prayer: prayer, study: study)
        try seedStudyPad(context, label: study)
        try seedMyDocument(context)
        try seedReadingPlan(context)
        try context.save()

        try seedSettingsRows(settingsStore)
        try seedProgress(settingsStore)
        try seedAI(context)
        try context.save()
    }

    // MARK: Workspaces

    /// Distinct, non-default values on every leaf of the composite value types, so a column mixed
    /// up by the Kotlin schema map (suffixed duplicate leaf names) shows up as a wrong value.
    /// Integer leaves are unique per `seed`; Bool leaves alternate with `seed`.
    static func sentinelTextDisplay(seed: Int) -> TextDisplaySettings {
        func flag(_ k: Int) -> Bool { (seed + k) % 2 == 0 }
        var s = TextDisplaySettings()
        s.fontSize = 20 + seed
        s.fontFamily = "family-\(seed)"
        s.lineSpacing = 30 + seed
        s.marginLeft = 40 + seed
        s.marginRight = 50 + seed
        s.maxWidth = 200 + seed
        s.topMargin = 60 + seed
        s.strongsMode = seed % 3 + 1
        s.showMorphology = flag(1)
        s.showFootNotes = flag(2)
        s.showFootNotesInline = flag(3)
        s.expandXrefs = flag(4)
        s.showXrefs = flag(5)
        s.showRedLetters = flag(6)
        s.showSectionTitles = flag(7)
        s.showVerseNumbers = flag(8)
        s.showVersePerLine = flag(9)
        s.showBookmarks = flag(10)
        s.showMyNotes = flag(11)
        s.justifyText = flag(12)
        s.hyphenation = flag(13)
        s.showPageNumber = flag(14)
        s.infiniteScroll = flag(15)
        s.nonStrongsWordItalic = flag(16)
        s.showMarkAsReadButton = flag(17)
        s.showTitleScrollButton = flag(18)
        s.showMemorizationIndicators = flag(19)
        s.showAiDocMarkers = flag(20)
        s.pageScrollAmount = TextDisplaySettings.pageScrollAmountValues[seed % 5]
        s.showOrdinals = flag(21)
        s.dayTextColor = -1_000_000 - seed
        s.dayBackground = -2_000_000 - seed
        s.dayNoise = 70 + seed
        s.nightTextColor = -3_000_000 - seed
        s.nightBackground = -4_000_000 - seed
        s.nightNoise = 80 + seed
        s.bookmarksHideLabels = [labelPrayer, labelStudy]
        s.enableVerseSelection = flag(22)
        return s
    }

    static func sentinelSpeak(seed: Int) -> SpeakSettings {
        var playback = PlaybackSettings(
            speakChapterChanges: seed % 2 == 0,
            speakTitles: seed % 2 == 1,
            speakFootnotes: seed % 2 == 0,
            speed: 110 + seed,
            bookId: "SPK\(seed)",
            bookmarkWasCreated: seed % 2 == 0
        )
        playback.verseRange = SpeakVerseRange(versification: "KJV", osisRef: "Gen.1.1-Gen.1.3")
        return SpeakSettings(
            playbackSettings: playback,
            sleepTimer: 20 + seed,
            lastSleepTimer: 30 + seed,
            queue: seed % 2 == 0,
            repeatPlayback: seed % 2 == 1,
            numPagesToSpeakId: 5 + seed
        )
    }

    static func sentinelWorkspaceSettings(seed: Int, labelID: UUID) -> WorkspaceSettings {
        WorkspaceSettings(
            enableTiltToScroll: seed % 2 == 1,
            enableReverseSplitMode: seed % 2 == 0,
            autoPin: seed % 2 == 0,
            restoreButtonsVisible: seed % 2 == 1,
            speakSettings: sentinelSpeak(seed: seed),
            recentLabels: [
                RecentLabel(
                    labelId: labelID,
                    lastAccess: Date(timeIntervalSince1970: 1_700_000_000 + Double(seed))
                ),
            ],
            autoAssignLabels: [labelID],
            autoAssignPrimaryLabel: labelID,
            studyPadCursors: [labelID: 7 + seed],
            hideCompareDocuments: ["NET\(seed)", "ESV\(seed)"],
            limitAmbiguousModalSize: seed % 2 == 0
        )
    }

    static func seedWorkspaces(_ context: ModelContext, labelID: UUID) throws {
        struct WindowPlan {
            let id: UUID
            let order: Int
            let category: String
            let seed: Int
        }
        let plans: [(UUID, String, Int, [WindowPlan])] = [
            (workspaceA, "Study A \u{2728}", 0, [
                WindowPlan(id: windowA1, order: 0, category: "bible", seed: 3),
                WindowPlan(id: windowA2, order: 1, category: "commentary", seed: 4),
            ]),
            (workspaceB, "\u{05DE}\u{05E8}\u{05D7}\u{05D1} \u{05E2}\u{05D1}\u{05D5}\u{05D3}\u{05D4} B", 1, [
                WindowPlan(id: windowB1, order: 0, category: "dictionary", seed: 5),
            ]),
        ]
        for (index, plan) in plans.enumerated() {
            let seed = index + 1
            let workspace = Workspace(id: plan.0, name: plan.1, orderNumber: plan.2)
            workspace.contentsText = "Contents \(seed)"
            workspace.textDisplaySettings = sentinelTextDisplay(seed: seed)
            workspace.workspaceSettings = sentinelWorkspaceSettings(seed: seed, labelID: labelID)
            workspace.unPinnedWeight = 0.25 * Float(seed)
            workspace.workspaceColor = Int(Int32(bitPattern: 0xFF102030)) + seed
            context.insert(workspace)

            for windowPlan in plan.3 {
                let window = Window(
                    id: windowPlan.id,
                    isSynchronized: windowPlan.seed % 2 == 0,
                    isPinMode: windowPlan.seed % 2 == 1,
                    isLinksWindow: windowPlan.order == 1,
                    orderNumber: windowPlan.order,
                    syncGroup: windowPlan.seed,
                    layoutWeight: 0.5 + Float(windowPlan.seed) / 10,
                    layoutState: windowPlan.order == 0 ? "split" : "minimized"
                )
                let pageManager = PageManager(
                    id: windowPlan.id,
                    currentCategoryName: windowPlan.category
                )
                pageManager.bibleDocument = "KJV"
                pageManager.bibleVersification = "KJVA"
                pageManager.bibleBibleBook = 39 + windowPlan.seed // distinct per window
                pageManager.bibleChapterNo = 1 + windowPlan.seed
                pageManager.bibleVerseNo = 2 + windowPlan.seed
                pageManager.commentaryDocument = "MHC"
                pageManager.commentaryAnchorOrdinal = 1000 + windowPlan.seed
                pageManager.dictionaryDocument = "StrongsGreek"
                pageManager.dictionaryKey = "G\(windowPlan.seed)"
                pageManager.generalBookDocument = "ISBE"
                pageManager.generalBookKey = "Key \(windowPlan.seed)"
                pageManager.mapDocument = "Maps"
                pageManager.mapKey = "Map \(windowPlan.seed)"
                pageManager.epubIdentifier = "epub-\(windowPlan.seed)"
                pageManager.epubHref = "chapter\(windowPlan.seed).xhtml"
                pageManager.textDisplaySettings = sentinelTextDisplay(seed: 10 + windowPlan.seed)
                pageManager.jsState = "{\"scroll\":\(windowPlan.seed)}"
                context.insert(window)
                context.insert(pageManager)
                window.workspace = workspace
                window.pageManager = pageManager
                pageManager.window = window

                for historyIndex in 0..<3 {
                    let item = HistoryItem(
                        createdAt: Date(timeIntervalSince1970: 1_700_000_000 + Double(historyIndex * 60 + windowPlan.seed)),
                        document: historyIndex % 2 == 0 ? "KJV" : "MHC",
                        key: "Gen.\(historyIndex + 1).1"
                    )
                    item.anchorOrdinal = 100 * (historyIndex + 1) + windowPlan.seed
                    context.insert(item)
                    item.window = window
                }
                if windowPlan.order == 0 {
                    workspace.maximizedWindowId = windowPlan.id
                }
            }
            workspace.primaryTargetLinksWindowId = plan.3.last?.id
        }
    }

    // MARK: Bookmarks

    static func seedBookmarks(_ context: ModelContext, prayer: Label, study: Label) throws {
        let created = Date(timeIntervalSince1970: 1_700_100_000)
        let updated = Date(timeIntervalSince1970: 1_700_100_100)
        func trust(_ start: Int, _ end: Int) -> PersistedOrdinalTrustMetadata {
            PersistedOrdinalTrustMetadata(
                state: .verifiedAndroid,
                mappingVersion: 1,
                provenance: .androidImport,
                sourceBookInitials: "KJV",
                sourceVersification: "KJVA",
                sourceOrdinalStart: start,
                sourceOrdinalEnd: end
            )
        }
        let longBody = "\u{1F64F} \u{05E9}\u{05DC}\u{05D5}\u{05DD} \u{05E2}\u{05D5}\u{05DC}\u{05DD}\n"
            + String(repeating: "abcdefghij", count: 20_000) // about 200 kB of note text
        let bodies: [(UUID, String, String?)] = [
            (bibleBookmark1, "Emoji note \u{1F600}\u{1F468}\u{200D}\u{1F469}\u{200D}\u{1F467} and accents \u{00E5}\u{00E4}\u{00F6}", "MARKDOWN"),
            (bibleBookmark2, "\u{0627}\u{0644}\u{0633}\u{0644}\u{0627}\u{0645} \u{0639}\u{0644}\u{064A}\u{0643}\u{0645} (RTL) mixed with latin", "HTML"),
            (bibleBookmark3, longBody, nil),
        ]
        for (index, entry) in bodies.enumerated() {
            let start = 10 + index * 10
            let bookmark = BibleBookmark(
                id: entry.0,
                kjvOrdinalStart: start,
                kjvOrdinalEnd: start + 1,
                ordinalStart: start,
                ordinalEnd: start + 1,
                v11n: "KJVA",
                bookInitials: "KJV",
                createdAt: created.addingTimeInterval(Double(index)),
                lastUpdatedOn: updated.addingTimeInterval(Double(index)),
                wholeVerse: index != 1,
                ordinalTrustMetadata: trust(start, start + 1)
            )
            bookmark.startOffset = index == 1 ? 3 : nil
            bookmark.endOffset = index == 1 ? 9 : nil
            bookmark.primaryLabelId = index == 0 ? prayer.id : study.id
            bookmark.type = index == 0 ? "EXAMPLE" : nil
            bookmark.customIcon = index == 2 ? "star" : nil
            bookmark.editAction = index == 0 ? EditAction(mode: .append, content: "Amen") : nil
            let note = BibleBookmarkNotes(bookmarkId: entry.0, notes: entry.1, contentType: entry.2)
            let toPrayer = BibleBookmarkToLabel(orderNumber: 8 + index, indentLevel: 2, expandContent: index != 1)
            let toStudy = BibleBookmarkToLabel(orderNumber: 3 + index, indentLevel: 1, expandContent: true)
            context.insert(bookmark)
            context.insert(note)
            context.insert(toPrayer)
            context.insert(toStudy)
            note.bookmark = bookmark
            toPrayer.bookmark = bookmark
            toPrayer.label = prayer
            toStudy.bookmark = bookmark
            toStudy.label = study
        }

        let generic = GenericBookmark(
            id: genericBookmark,
            key: "Entry.1",
            bookInitials: "MHC",
            createdAt: created,
            ordinalStart: 12,
            ordinalEnd: 13,
            lastUpdatedOn: updated,
            wholeVerse: false
        )
        generic.startOffset = 1
        generic.endOffset = 5
        generic.primaryLabelId = study.id
        generic.customIcon = "bookmark"
        generic.playbackSettings = PlaybackSettings(
            speakChapterChanges: false, speakTitles: false, speakFootnotes: true,
            speed: 135, bookId: "MHC", bookmarkWasCreated: true
        )
        let genericNote = GenericBookmarkNotes(
            bookmarkId: genericBookmark, notes: "Generic note \u{1F4D6}", contentType: "HTML"
        )
        let genericLink = GenericBookmarkToLabel(orderNumber: 4, indentLevel: 3, expandContent: false)
        context.insert(generic)
        context.insert(genericNote)
        context.insert(genericLink)
        genericNote.bookmark = generic
        genericLink.bookmark = generic
        genericLink.label = prayer
    }

    // MARK: StudyPad, documents, plans

    static func seedStudyPad(_ context: ModelContext, label: Label) throws {
        let rows: [(UUID, Int, Int, String?, String)] = [
            (studyPadEntry1, 0, 0, "MARKDOWN", "# Heading \u{1F4DD}"),
            (studyPadEntry2, 1, 2, "HTML", "<p>Indented two levels</p>"),
            (studyPadEntry3, 2, 1, nil, "\u{05E9}\u{05DC}\u{05D5}\u{05DD} indented once"),
        ]
        for row in rows {
            let entry = StudyPadTextEntry(id: row.0, orderNumber: row.1, indentLevel: row.2, contentType: row.3)
            let text = StudyPadTextEntryText(studyPadTextEntryId: row.0, text: row.4)
            context.insert(entry)
            context.insert(text)
            entry.label = label
            text.entry = entry
        }
    }

    static func seedMyDocument(_ context: ModelContext) throws {
        let document = MyDocument(
            id: myDocument,
            name: "Sermon Notes",
            documentDescription: "Fixture document",
            initials: "I3DOC",
            orderNumber: 2,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            updatedAt: Date(timeIntervalSince1970: 1_700_000_100)
        )
        let page = MyDocumentPage(
            id: myDocumentPage,
            title: "Outline",
            pageKey: "outline",
            orderNumber: 1,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            updatedAt: Date(timeIntervalSince1970: 1_700_000_100),
            languageCode: "fi"
        )
        let content = MyDocumentPageContent(pageId: myDocumentPage, content: "# Outline\n\n- one \u{1F600}\n- two")
        context.insert(document)
        context.insert(page)
        context.insert(content)
        page.document = document
        page.pageContent = content
    }

    static func seedReadingPlan(_ context: ModelContext) throws {
        let plan = ReadingPlan(
            id: readingPlan,
            planCode: "i3-plan",
            planName: "I3 Plan",
            startDate: Date(timeIntervalSince1970: 1_700_000_000),
            currentDay: 2,
            totalDays: 3
        )
        context.insert(plan)
        for day in 1...3 {
            let row = ReadingPlanDay(
                id: UUID(uuidString: "13000000-0000-0000-0000-00000000050\(day)")!,
                dayNumber: day,
                isCompleted: day < 3,
                readings: "Gen.\(day)"
            )
            row.completedDate = day < 3 ? Date(timeIntervalSince1970: 1_700_000_000 + Double(day * 86_400)) : nil
            context.insert(row)
            row.plan = plan
        }
    }

    // MARK: Settings rows and progress

    static func seedSettingsRows(_ settingsStore: SettingsStore) throws {
        settingsStore.setString("active_workspace_id", value: workspaceB.uuidString)
        // Nextcloud configuration (password lives in the Keychain, see sidecar.json).
        settingsStore.setString("sync_adapter", value: "NEXT_CLOUD")
        settingsStore.setString("gdrive_server_url", value: "https://cloud.example.invalid/remote.php/dav/files/fixture")
        settingsStore.setString("gdrive_username", value: "fixture-user")
        settingsStore.setString("gdrive_folder_path", value: "AndBibleFixture")
        settingsStore.setBool("sync_enable_bookmarks", value: true)
        // remote_sync.* rows
        settingsStore.setString("remote_sync_device_identifier", value: "i3-fixture-device")
        settingsStore.setString("remote_sync.accepted_baseline.bookmarks", value: "{\"fixture\":\"bookmarks\"}")
        settingsStore.setString("remote_sync.workspaces.accepted_baseline", value: "{\"fixture\":\"workspaces\"}")
        RemoteSyncBookmarkAndroidBookStore(settingsStore: settingsStore)
            .setRawBook("KJV", for: bibleBookmark1)
        RemoteSyncBookmarkPlaybackSettingsStore(settingsStore: settingsStore).setPlaybackSettingsJSON(
            "{\"bookId\":\"KJV\",\"speed\":122}", for: bibleBookmark1, kind: .bible
        )
        // Speak settings, as SpeakService persists them (key `SpeakSettings`).
        let speakJSON = String(
            data: try JSONEncoder().encode(sentinelSpeak(seed: 7)),
            encoding: .utf8
        )!
        settingsStore.setString("SpeakSettings", value: speakJSON)
    }

    static func seedProgress(_ settingsStore: SettingsStore) throws {
        _ = try ReadingProgressStore(settingsStore: settingsStore).recordChapterRead(
            bookInitials: "KJV",
            identity: try XCTUnwrap(ReadingProgressKJVAIdentity(androidKJVBookOrdinal: 2, chapter: 1)),
            source: .manual,
            readAt: 1_700_000_200
        )
        let memorization = MemorizationProgressStore(settingsStore: settingsStore)
        try memorization.markAsMemorized(try kjvaRange(start: 15, end: 15))
        try memorization.addMemorizationTarget(try kjvaRange(start: 20, end: 21))
    }

    static func kjvaRange(start: Int, end: Int) throws -> VerifiedKJVAOrdinalRange {
        try XCTUnwrap(
            VerifiedKJVAOrdinalRange(
                resolvingSourceBookInitials: "KJVA",
                sourceVersification: "KJVA",
                sourceOrdinalStart: start,
                sourceOrdinalEnd: end
            )
        )
    }

    // MARK: AI

    /// Copy of `insertCompleteAISettingsFixture` (RemoteSyncAISettingsTests.swift), with the provider
    /// id exposed through `I3Sidecar.aiProviderID` for the Keychain item.
    static func seedAI(_ modelContext: ModelContext) throws {
        let provider = I3Sidecar.aiProviderID
        let configuredModel = UUID(uuidString: "a3000000-0000-0000-0000-000000000001")!
        let agentPrompt = UUID(uuidString: "a4000000-0000-0000-0000-000000000001")!
        let usageRecord = UUID(uuidString: "a6000000-0000-0000-0000-000000000001")!
        let promptCategory = UUID(uuidString: "a5000000-0000-0000-0000-000000000001")!
        let builtinOverride = UUID(uuidString: "a7000000-0000-0000-0000-000000000001")!
        let rawLog = UUID(uuidString: "a8000000-0000-0000-0000-000000000001")!

        modelContext.insert(
            LLMProviderConfig(
                id: provider, provider: .custom, displayName: "Private compatible provider",
                endpoint: "https://llm.example.invalid/v1", apiFormat: .anthropic, orderNumber: 4
            )
        )
        modelContext.insert(
            LLMConfiguredModel(
                id: configuredModel, providerConfigId: provider, modelId: "model/with unicode \u{03BB}",
                orderNumber: 3, inputPricePerMillion: 1.25, outputPricePerMillion: 5.5,
                cacheCreationPricePerMillion: 0.75, cacheReadPricePerMillion: 0.125
            )
        )
        modelContext.insert(
            PromptCategory(id: promptCategory, name: "Study, custom", orderNumber: 8, hidden: true)
        )
        modelContext.insert(
            AgentPrompt(
                id: agentPrompt,
                name: "Compare translations",
                description: "Preserve Android converter values",
                promptTemplate: "Compare {{selection}}\nwithout truncation.",
                showIn: [.verseSelection, .workspaceMenu],
                orderNumber: 6,
                createdAtMilliseconds: 1_725_000_000_123,
                strictContextMatching: false,
                permissionMode: .askOncePerRun,
                allowedTools: [.getVerseContent, .searchBible],
                deniedTools: [.createBookmark],
                configuredModelId: configuredModel,
                specifyBeforeRun: true,
                noDocumentCreation: true,
                maxIterations: 7,
                autoIncludeDocuments: true,
                autoIncludeCommentaries: true,
                bibleOnly: true,
                isTextTransformation: false,
                categoryId: promptCategory
            )
        )
        let globalSettings = GlobalAISettings()
        globalSettings.agentPermissionModeRawValue = AIPermissionMode.denyAll.rawValue
        globalSettings.permanentlyAllowedToolsRawValue = #"["GET_VERSE_CONTENT"]"#
        globalSettings.permanentlyDeniedToolsRawValue = #"["CREATE_BOOKMARK"]"#
        globalSettings.aiExcludedDocumentsRawValue = #"["KJV","NET"]"#
        globalSettings.commentaryMaxResponseTokens = 12_345
        globalSettings.hiddenBuiltInPromptsRawValue = #"["11111111-1111-1111-1111-111111111111"]"#
        globalSettings.maxIterations = 9
        globalSettings.commentaryDeselectedRawValue = #"["MHCC"]"#
        globalSettings.defaultModelId = configuredModel
        globalSettings.aiLanguage = "fi-FI"
        globalSettings.askModelBeforeRun = true
        globalSettings.aiDisclaimerAccepted = true
        globalSettings.hiddenBuiltInCategoriesRawValue = #"["22222222-2222-2222-2222-222222222222"]"#
        globalSettings.customAgentSystemPrompt = "Use installed documents first."
        globalSettings.customTextTransformationSystemPrompt = "Return only transformed text."
        globalSettings.favoritePromptsRawValue = #"["33333333-3333-3333-3333-333333333333"]"#
        globalSettings.rawLogRetentionDays = 14
        globalSettings.autoHideAgentLogOnCompletion = true
        modelContext.insert(globalSettings)

        let usage = LLMUsageRecord(id: usageRecord, configuredModelId: configuredModel, deviceId: "ios-test-device")
        usage.inputTokens = 101
        usage.outputTokens = 202
        usage.cacheCreationTokens = 303
        usage.cacheReadTokens = 404
        usage.estimatedCostUSD = 1.2345
        modelContext.insert(usage)
        modelContext.insert(BuiltInPromptOverride(id: builtinOverride, configuredModelId: configuredModel))
        modelContext.insert(
            LLMRawLogRecord(
                id: rawLog, promptId: agentPrompt, promptName: "Device-local run",
                promptDescription: "Must not leave this device", configuredModelId: configuredModel,
                modelName: "model/with unicode \u{03BB}", providerType: "CUSTOM",
                timestampMilliseconds: 1_725_000_000_999, totalInputTokens: 10, totalOutputTokens: 20,
                estimatedCostUSD: 0.001, logData: Data("source-device-local-raw-log".utf8),
                iterationCount: 2, wasError: false
            )
        )
    }

    // MARK: Restored Android

    /// Restores the Android bookmark Room v12 fixture (`Fixtures/android-bookmark-room-v12.sql`) through
    /// `RemoteSyncBookmarkRestoreService`, mirroring `testIOSPatchMatchesAndAppliesToAndroidRoomV12Fixture`
    /// (AndroidBookmarkRoomV12ContractTests.swift), so the fidelity rows exist.
    static func seedRestoredAndroid(_ context: ModelContext) throws {
        let settingsStore = SettingsStore(modelContext: context)
        let fixtureURL = try makeAndroidBookmarkRoomV12Fixture()
        defer { try? FileManager.default.removeItem(at: fixtureURL) }
        let restoreService = RemoteSyncBookmarkRestoreService()
        let snapshot = try restoreService.readSnapshot(from: fixtureURL)
        _ = try restoreService.replaceLocalBookmarks(
            from: snapshot,
            modelContext: context,
            settingsStore: settingsStore
        )
        RemoteSyncLogEntryStore(settingsStore: settingsStore)
            .replaceEntries(snapshot.logEntries, for: .bookmarks)
        try context.save()
    }

    // MARK: Preserved Android AI settings

    /// Writes an Android-format `ai_settings.sqlite3` (schema v23) with one provider that exists only
    /// on the Android side and a `GlobalAiSettings` row that has the same fixed id as the Swift row
    /// but different values, to exercise merge rule U11.
    static func writePreservedAndroidAISettings(to url: URL) throws {
        let schema = Schema(
            AIModelRegistration.cloudSyncableModels
                + AIModelRegistration.localOnlyModels
                + [Setting.self]
        )
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(for: schema, configurations: [configuration])
        let context = ModelContext(container)
        context.insert(
            LLMProviderConfig(
                id: UUID(uuidString: "a2000000-0000-0000-0000-0000000000aa")!,
                provider: .openAI,
                displayName: "Android-only provider",
                orderNumber: 1
            )
        )
        let global = GlobalAISettings()
        global.aiLanguage = "sv-SE"
        global.maxIterations = 3
        global.commentaryMaxResponseTokens = 4_321
        global.aiDisclaimerAccepted = false
        context.insert(global)
        try context.save()
        let snapshot = try RemoteSyncAISettingsSnapshotService(credentialForProvider: { _ in nil })
            .snapshotCurrentStateStrict(modelContext: context, settingsStore: SettingsStore(modelContext: context))
        _ = try RemoteSyncAISettingsDatabaseWriter().writeFullDatabase(at: url, snapshot: snapshot)
    }
}
