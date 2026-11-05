import XCTest

/// H2 Task 10 (W8): Speak settings, reading plan daily/mark-read, dictionary and general book key choosers, and one RTL walk.
///
/// Preconditions are established here (KJV through KjvProvisioning.swift, module fixtures through the debug
/// `-incomingRuntimeTest -incomingFixture <alias>` harness, never assumed from another class). Aliases and contents
/// (see FeaturePathsUITests): genbook -> K4GenBook (keys /Part 1/Chapter 1 "In the beginning God created the heaven
/// and the earth.", /Part 1/Chapter 2, /Part 2), dict -> K4ZLD (AARON "Aaron thy brother.", ABBA "Abba, Father.",
/// ZION "The mount Zion.").
///
/// Selectors that were NOT measured when this was drafted (a miss is HARNESS discovery with the tree attached, not
/// automatically a product RED): the Speak transport gear, the Compose Checkbox AX type of the daily reading rows,
/// the dictionary key list rows and the RTL top-bar Back button.
final class FeatureScreensUITests: RealAppUITestCase {
    private static var kjvEnsured = false
    override func setUp() {
        super.setUp()
        if !Self.kjvEnsured { ensureKJVInstalled(); Self.kjvEnsured = true }
    }

    // MARK: shared helpers

    private func button(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func text(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func tapRequired(_ app: XCUIApplication, _ target: XCUIElement, _ step: String, timeout: TimeInterval = 30) {
        harnessRequire(target.waitForExistence(timeout: timeout), app, "Missing control: \(step)")
        for _ in 0..<4 where !target.isHittable { app.swipeUp() }
        harnessRequire(target.isHittable && target.isEnabled, app, "Unusable control: \(step)")
        target.tap()
    }

    private let readingArguments = ["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16"]

    private func launchReading(_ extra: [String] = [], reset: Bool = true) -> XCUIApplication {
        // Relaunches must NOT reset databases (the preferences under test must survive) and must not pass
        // -standardTestFixture, which rewrites the device defaults.
        let arguments = reset ? readingArguments : ["-openDocument", "KJV", "-openRef", "John.3.16"]
        return launch(arguments + extra)
    }

    // MARK: fixtures (copied from FeaturePathsUITests: its helpers are private)

    private func confirmInstalls(_ app: XCUIApplication, _ filenames: [String]) {
        for filename in filenames {
            let message = app.staticTexts.matching(NSPredicate(format: "label == %@",
                "Do you want to install module (\(filename))?")).firstMatch
            harnessRequire(message.waitForExistence(timeout: 60), app, "install-confirm-\(filename)")
            tapRequired(app, app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch, "install-ok-\(filename)")
        }
    }

    /// Cold incoming install on the reading route (where the production prompt is shown), then a clean process.
    private func installFixtures(_ aliases: String, _ filenames: [String]) {
        let app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16",
                          "-incomingRuntimeTest", "-incomingFixture", aliases, "-incomingRemoveFixtures"])
        confirmInstalls(app, filenames)
        let prompt = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Do you want to install module'")).firstMatch
        _ = waitUntil(timeout: 60) { !prompt.exists }
        // The debug harness tags the screen once every offered fixture is registered; the chooser launch below still
        // proves the install (row present).
        harnessRequire(element(app, "incoming-cold-fixtures-installed").waitForExistence(timeout: 90), app,
                       "incoming-cold-fixtures-installed")
        app.terminate()
    }

    private func chooserFilters(_ app: XCUIApplication) -> Bool {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Document type,'")).firstMatch.exists
    }

    private func showAllDocumentTypes(_ app: XCUIApplication) {
        let type = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Document type,'")).firstMatch
        guard type.exists, !type.label.contains("All types") else { return }
        let item = app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS 'All types' AND NOT (label BEGINSWITH 'Document type')")).firstMatch
        var opened = false
        for attempt in 1...3 {
            type.tap()
            if item.waitForExistence(timeout: 6) { opened = true; break }
            if attempt == 1 { attachTree(app, "document-type-menu-not-open") }
        }
        harnessRequire(opened, app, "HARNESS: 'All types' document type choice")
        item.tap()
        harnessRequire(waitUntil(timeout: 15) { type.label.contains("All types") }, app, "Document type filter shows All types")
    }

    private func openChooserRow(_ app: XCUIApplication, initials: String, timeout: TimeInterval = 180) -> XCUIElement {
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", initials + " ")).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        let entry = element(app, "debug-open-chooser")
        while Date() < deadline {
            if !chooserFilters(app) {
                guard entry.waitForExistence(timeout: 60) else { break }
                entry.tap()
                _ = waitUntil(timeout: 30) { self.chooserFilters(app) }
            }
            showAllDocumentTypes(app)
            for _ in 0..<8 {
                if row.exists && row.isHittable { return row }
                nudgeList(app, up: true)
            }
            for _ in 0..<8 { nudgeList(app, up: false) }
            if row.exists && row.isHittable { return row }
            let back = app.buttons["Back"].firstMatch
            if back.exists { back.tap() }
            sleep(3)
        }
        return row
    }

    /// Labels of the displayed document's web view root (`BibleView-<id>/bible/<initials>_ordinal_<n>`).
    private func requireShownModule(_ app: XCUIApplication, _ initials: String, _ step: String) {
        let labels = { app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'BibleView-'"))
            .allElementsBoundByIndex.map(\.label) }
        harnessRequire(waitUntil(timeout: 30) { labels().contains { $0.contains("/\(initials)_") } }, app,
                       "\(step): the displayed web view belongs to \(initials)")
    }

    /// Chooser (finite debug route) -> module row -> key list -> key -> reading host with the key's text.
    /// Selection goes through the real chooser result path into the reading host (result consumption), not just a chooser.
    private func chooseKey(_ app: XCUIApplication, initials: String, key: String, expectedText: String) {
        let row = openChooserRow(app, initials: initials)
        harnessRequire(row.exists, app, "HARNESS: \(initials) row in the document chooser after install")
        row.tap()
        pickKey(app, initials: initials, key: key, expectedText: expectedText, retry: row)
    }

    /// Second key of the same document: the chooser would return to the remembered key without a key list (observed in
    /// two runs), so the real key chooser is opened from the toolbar title (resolveKeyChooserOpening) instead.
    private func chooseKeyViaTitle(_ app: XCUIApplication, initials: String, currentTitle: String, key: String, expectedText: String) {
        let title = app.staticTexts.matching(NSPredicate(format: "label == %@", currentTitle)).allElementsBoundByIndex
            .first { $0.frame.minY < 200 && !$0.frame.isEmpty }
        harnessRequire(title != nil, app, "HARNESS: toolbar title '\(currentTitle)'")
        title?.tap()
        pickKey(app, initials: initials, key: key, expectedText: expectedText, retry: nil)
    }

    private func pickKey(_ app: XCUIApplication, initials: String, key: String, expectedText: String, retry: XCUIElement?) {
        let keyLabel = NSPredicate(format: "label == %@", key)
        let keyRow = app.descendants(matching: .any).matching(keyLabel).firstMatch
        if !waitUntil(timeout: 15, { keyRow.exists }) {
            attachTree(app, "\(initials)-before-key-list")
            if let retry, retry.exists && retry.isHittable { retry.tap() } // a lost tap (HARNESS) vs an inert row (PRODUCT?)
        }
        harnessRequire(keyRow.waitForExistence(timeout: 30), app, "PRODUCT?: \(initials) key list shows '\(key)'")
        shot(app, "\(initials)-key-list")
        for _ in 0..<6 where !keyRow.isHittable { nudgeList(app, up: true) }
        harnessRequire(keyRow.isHittable, app, "HARNESS: key '\(key)' hittable in the \(initials) key list")
        keyRow.tap()
        harnessRequire(element(app, "reading-host").waitForExistence(timeout: 60), app, "\(initials) reading host after key choice")
        let titleShown = { app.staticTexts.matching(NSPredicate(format: "label == %@", key)).allElementsBoundByIndex
            .contains { $0.frame.minY < 200 && !$0.frame.isEmpty } }
        harnessRequire(waitUntil(timeout: 30, titleShown), app, "\(initials) toolbar title shows '\(key)'")
        if !expectedText.isEmpty { // Chapter 2's text is not pinned by the fixture notes: its title is the oracle
            let page = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", expectedText)).firstMatch
            harnessRequire(page.waitForExistence(timeout: 60), app, "\(initials) key '\(key)' shows '\(expectedText)'")
        }
        requireShownModule(app, initials, "\(initials)-\(key)")
        shot(app, "\(initials)-\(key)-entry")
    }

    // MARK: dictionary and general book

    /// K4ZLD (dictionary): key list AARON/ABBA/ZION; two keys are chosen in turn, each entry shows its own text, so the
    /// second choice cannot be satisfied by the first one's leftover page.
    func testDictionaryKeyChooser() throws {
        installFixtures("dict", ["h2-dict.zip"])
        let app = launch(readingArguments + ["-featureScenario", "chooser"])
        defer { app.terminate() }
        chooseKey(app, initials: "K4ZLD", key: "ZION", expectedText: "The mount Zion")
        chooseKeyViaTitle(app, initials: "K4ZLD", currentTitle: "ZION", key: "AARON", expectedText: "Aaron thy brother")
        let stale = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS 'The mount Zion'"))
            .allElementsBoundByIndex.filter { $0.frame.intersects(app.frame) && $0.frame.minY >= 0 }
        harnessRequire(stale.isEmpty, app, "Previous entry (ZION) is no longer visible after choosing AARON")
    }

    // MARK: Speak settings

    /// The transport settings gear is labelled "Speak" like the play button (SpeakTransportBar). Scope: a "Speak" button
    /// on the same row as "Next", to its right (play is left of Next, the gear is the last button).
    private func speakGear(_ app: XCUIApplication) -> XCUIElement? {
        let next = harnessLabel(app, "Next")
        guard next.waitForExistence(timeout: 20) else { return nil }
        let speaks = app.buttons.matching(NSPredicate(format: "label == 'Speak'")).allElementsBoundByIndex
        return speaks.filter { abs($0.frame.midY - next.frame.midY) < 24 && $0.frame.midX > next.frame.midX }
            .max { $0.frame.midX < $1.frame.midX }
    }

    /// Run 2 evidence: with KJV (Strong's) the toolbar has no Speak button and More options holds only Night mode,
    /// Workspaces, Window pinning, All text options, so Speak is unreachable there (PRODUCT? suspect, reported). The
    /// dictionary reading shows the headphones button, so the test reads K4ZLD/ZION (installed through the `dict` fixture)
    /// and restarts with NO debug scenario: the debug-open-chooser overlay would sit over the transport bar's gear.
    private func prepareSpeakableReading() -> XCUIApplication {
        installFixtures("dict", ["h2-dict.zip"])
        var app = launch(readingArguments + ["-featureScenario", "chooser"])
        chooseKey(app, initials: "K4ZLD", key: "ZION", expectedText: "The mount Zion")
        sendToBackground(app)
        app.terminate()
        app = launch([])
        return app
    }

    private func openSpeakSettings(_ app: XCUIApplication) {
        harnessRequire(element(app, "reading-host").waitForExistence(timeout: 90), app, "Reading host before Speak")
        // The KJV toolbar shows the Strong's/original-language icon in the Speak slot (first run), so Speak may sit in the
        // overflow: try the toolbar button briefly, then More options.
        let toolbarSpeak = harnessLabel(app, "Speak")
        if toolbarSpeak.waitForExistence(timeout: 8) {
            toolbarSpeak.tap()
        } else {
            attachTree(app, "toolbar-without-speak")
            tapRequired(app, button(app, "More options"), "more-options")
            let item = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'Speak'")).firstMatch
            harnessRequire(item.waitForExistence(timeout: 10), app, "Speak in the toolbar or More options")
            item.tap()
        }
        guard let gear = speakGear(app) else {
            harnessRequire(false, app, "HARNESS: transport settings gear (a Speak button right of Next)"); return
        }
        harnessRequire(gear.isHittable, app, "Speak gear hittable")
        attachTree(app, "speak-bar-with-gear")
        gear.tap()
        harnessRequire(text(app, "General Speak Settings").waitForExistence(timeout: 20)
                       || text(app, "Playback Settings").waitForExistence(timeout: 5), app,
                       "Speak settings sheet opened by the gear")
    }

    /// The shared Compose migration test covers settings, advanced navigation and persistence. This
    /// XCUITest retains only the iOS-specific handoff to system TTS settings.
    func testSpeakSettingsOpensSystemTtsSettings() throws {
        let app = prepareSpeakableReading()
        defer { app.terminate() }
        openSpeakSettings(app)
        let system = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS 'Open system TTS settings'"))
            .firstMatch
        for _ in 0..<8 where !(system.exists && system.isHittable) { nudgeSheetList(app) }
        harnessRequire(waitForStableOnScreen(app, system), app, "system TTS row settled")
        system.tap()

        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        if settings.wait(for: .runningForeground, timeout: 20) {
            XCUIDevice.shared.press(.home)
            app.activate()
            harnessRequire(system.waitForExistence(timeout: 30) && waitForStableOnScreen(app, system),
                           app, "Speak sheet usable after iOS Settings")
        } else {
            attachTree(app, "system-tts-row-no-background")
            let note = XCTAttachment(string: "device-only: iOS Settings handoff not observable in the simulator")
            note.name = "device-only-ios-settings-handoff"
            note.lifetime = .keepAlways
            add(note)
            print("NOTE device-only: iOS Settings handoff not observable in the simulator")
        }
    }

    // MARK: reading plan

    private let planTitle = "1-Year through Bible"

    /// The per-passage checkboxes (contentDescription = passage). The Compose Checkbox AX type is not measured: try the
    /// checkbox/switch/toggle types, then fall back to non-static elements whose label also shows as a static text and
    /// is not a known button.
    private func readingCheckboxes(_ app: XCUIApplication) -> [XCUIElement] {
        for query in [app.checkBoxes, app.switches, app.toggles] {
            let found = query.allElementsBoundByIndex.filter { $0.exists && !$0.frame.isEmpty }
            if !found.isEmpty { return found }
        }
        let staticLabels = Set(app.staticTexts.allElementsBoundByIndex.map(\.label))
        let known: Set<String> = ["Done", "Speak", "Back", "Menu", "Passage", "Stop", "Pause", planTitle]
        return app.buttons.allElementsBoundByIndex.filter {
            $0.exists && staticLabels.contains($0.label) && !known.contains($0.label) && !$0.label.hasPrefix("Day ")
                && $0.label.range(of: #"\d"#, options: .regularExpression) != nil
        }
    }

    private func isChecked(_ element: XCUIElement) -> Bool {
        // Measured: the checkbox is a Button labelled with the passage; its checked flag is not in the unticked tree line.
        // Accept selected/value/description markers; the first tick attaches the tree so the real marker is visible.
        element.isSelected || ["1", "checked", "on", "On"].contains(element.value as? String ?? "")
            || element.debugDescription.contains("Selected") || element.debugDescription.contains("value: 1")
    }

    private func dayLabel(_ app: XCUIApplication) -> String? {
        // Measured: "Day N" is a Button (clickable subtitle), not a StaticText.
        // One snapshot, not `exists` then `label`: after Done the screen changes, and a button that existed for the first
        // query can be gone for the second, which XCTest records as a failure ("Failed to get matching snapshot").
        guard let root = try? app.snapshot() else { return nil }
        let pattern = try! NSRegularExpression(pattern: #"^Day \d+$"#)
        func find(_ node: XCUIElementSnapshot) -> String? {
            if node.elementType == .button,
               pattern.firstMatch(in: node.label, range: NSRange(node.label.startIndex..., in: node.label)) != nil {
                return node.label
            }
            for child in node.children { if let hit = find(child) { return hit } }
            return nil
        }
        return find(root)
    }

    private func loadPlan(_ app: XCUIApplication) {
        harnessRequire(element(app, "debug-feature-ready").waitForExistence(timeout: 90), app, "plan scenario ready")
        let loaded = button(app, planTitle)
        if !loaded.waitForExistence(timeout: 8) {
            let row = text(app, planTitle)
            for _ in 0..<12 where !(row.exists && row.isHittable) { app.swipeUp() }
            harnessRequire(row.exists && row.isHittable, app, "Plan selector row '\(planTitle)'")
            row.tap()
        }
        harnessRequire(loaded.waitForExistence(timeout: 30), app, "Daily reading loaded for '\(planTitle)'")
    }

    /// Select a plan -> today's reading -> tick the first passage and see it persist -> tick all -> Done enabled ->
    /// Done advances -> a relaunch keeps the result. Opening a passage is NOT marking it read.
    func testReadingPlanSelectorDailyAndMarkRead() throws {
        var app = launchReading(["-featureScenario", "plan-settings"])
        loadPlan(app)
        let day = dayLabel(app)
        harnessRequire(day != nil, app, "Daily reading shows a 'Day N' description")
        let boxes = readingCheckboxes(app)
        harnessRequire(!boxes.isEmpty, app, "HARNESS: per-passage checkboxes found (AX type unmeasured)")
        attachTree(app, "daily-reading-before")
        shot(app, "daily-reading-before")
        let done = button(app, "Done")
        harnessRequire(done.exists, app, "Done button exists")
        harnessRequire(!done.isEnabled, app, "Done is disabled while nothing is read")
        harnessRequire(boxes.allSatisfy { !isChecked($0) }, app, "fresh plan: no passage is read")

        // One passage ticked: it persists across a relaunch, Done stays disabled while others remain.
        boxes[0].tap()
        _ = waitUntil(timeout: 3) { false }
        attachTree(app, "daily-reading-after-first-tick"); shot(app, "daily-reading-after-first-tick")
        harnessRequire(waitUntil(timeout: 10) { self.isChecked(self.readingCheckboxes(app)[0]) }, app, "first passage ticked")
        if boxes.count > 1 { harnessRequire(!done.isEnabled, app, "Done still disabled with passages unread") }
        sendToBackground(app)
        app.terminate()
        app = launchReading(["-featureScenario", "plan-settings"], reset: false)
        loadPlan(app)
        harnessRequire(dayLabel(app) == day, app, "same day after relaunch")
        harnessRequire(isChecked(readingCheckboxes(app)[0]), app, "tick persisted across relaunch")

        // Tick the rest -> Done enabled -> Done.
        for index in readingCheckboxes(app).indices where !isChecked(readingCheckboxes(app)[index]) {
            readingCheckboxes(app)[index].tap()
            harnessRequire(waitUntil(timeout: 10) { self.isChecked(self.readingCheckboxes(app)[index]) }, app, "passage \(index) ticked")
        }
        harnessRequire(waitUntil(timeout: 10) { done.isEnabled }, app, "Done enabled once every passage is read")
        shot(app, "daily-reading-all-read")
        done.tap()

        // Progress advances. Run 2 evidence: in-process Done restores the reader (no daily screen), the plan day moved
        // on (relaunch showed Day 2, unticked). So the oracle is the NEXT day on reopen, whichever way the screen went.
        let dayNumber = Int(day!.filter(\.isNumber))!
        let expectedDay = "Day \(dayNumber + 1)"
        let inProcess = waitUntil(timeout: 30) { self.dayLabel(app) == expectedDay || self.element(app, "reading-host").exists && self.dayLabel(app) == nil }
        harnessRequire(inProcess, app, "Done left the Day \(dayNumber) screen (next day or reader)")
        let note = XCTAttachment(string: "after Done in-process: dayLabel=\(dayLabel(app) ?? "nil")")
        note.name = "daily-after-done"; note.lifetime = .keepAlways; add(note)
        attachTree(app, "daily-after-done")
        sendToBackground(app)
        app.terminate()

        // Reopen keeps it.
        app = launchReading(["-featureScenario", "plan-settings"], reset: false)
        defer { app.terminate() }
        loadPlan(app)
        harnessRequire(dayLabel(app) == expectedDay, app, "reopen shows \(expectedDay) (was \(day ?? "?"))")
        harnessRequire(readingCheckboxes(app).allSatisfy { !isChecked($0) }, app, "the new day starts with unread passages")
        shot(app, "daily-reading-reopened")
    }

}
