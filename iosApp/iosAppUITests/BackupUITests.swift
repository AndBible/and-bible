import XCTest

/// Production backup UI through a debug-only graph entry, never direct database injection.
/// Unknown WebKit AX controls fail as harness boundaries with a tree, not as product regressions.
///
/// Required pre-seeded simulator state (host side, before the run; the tests never seed Files themselves):
///  - Files > On My iPhone > `H2 Fixtures` holding BOTH `h2-kjv.zip` and `db-backup.abdb.zip`
///    (`app/src/test/resources/l1b-g2/old-release/db-backup.abdb.zip`), copied with
///    `iosApp/scripts/seed-files-provider.sh <udid> <file> "H2 Fixtures"` (see iosApp/scripts/README.md).
///  - `H2 Backup` exists (the script's `--clean` recreates it). Exports go to fresh UUID child folders there.
/// Do not run `--clean` between seeding and the run (it deletes `H2 Fixtures`). The three acceptance tests
/// are iPhone-only (see `requirePhone`).
final class BackupUITests: RealAppUITestCase {
    private let databaseFile = "AndBibleDatabaseBackup.abdb.zip"
    private let modulesFile = "AndBibleModulesBackup.abmd.zip"
    private let marker = "H2-BACKUP-NOTE"

    /// The acceptance tests hard-code iPhone 16 geometry (toolbar Menu/Back frames, "Phone storage").
    private func requirePhone() throws {
        try XCTSkipIf(isPad, "iPad backup geometry owed (Task 12/16)")
    }

    /// Phone regression: runtime diagnostics alone must not cover the production drawer hit target.
    func testIncomingRuntimeWithoutControlsLeavesProductionDrawerAccessible() throws {
        guard !isPad else { throw XCTSkip("Toolbar geometry was measured on iPhone only") }
        provisionKJV()
        let app = launch(["-standardTestFixture", "-incomingRuntimeTest",
                          "-openDocument", "KJV", "-openRef", "John.3.16",
                          "-AppleLanguages", "(en)", "-AppleLocale", "en_US"])
        defer { app.terminate() }
        let readingHost = element(app, "reading-host")
        let readingReady = readingHost.waitForExistence(timeout: 90) && john3Shown(app)
        attachTree(app, "h2-runtime-without-controls-before-drawer")
        shot(app, "h2-runtime-without-controls-before-drawer")
        guard readingReady else {
            XCTFail("HARNESS: actual KJV John 3 reading host required before overlay regression")
            return
        }
        assertExactJohn316(app)

        // No -incomingRuntimeControls opt-in: fail before tapping, even if AX claims hittability.
        let overlayTags = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH 'incoming-'"))
        let emittedTags = overlayTags.allElementsBoundByIndex.map { $0.identifier }
        guard emittedTags.isEmpty else {
            XCTFail("Runtime diagnostics without controls opt-in emitted overlay tags: \(emittedTags)")
            return
        }
        let bookmarks = label(app, "Bookmarks")
        guard !bookmarks.exists else {
            XCTFail("HARNESS: drawer must start closed; Bookmarks row already exists")
            return
        }

        // Uncovered phone AX measurement: Menu home at (0, 59, 48, 56), selected uniquely by label and frame.
        let expected = CGRect(x: app.frame.minX, y: app.frame.minY + 59, width: 48, height: 56)
        let homes = app.buttons.allElementsBoundByIndex.filter {
            let frame = $0.frame
            return $0.label == "Menu"
                && abs(frame.minX - expected.minX) <= 1 && abs(frame.minY - expected.minY) <= 1
                && abs(frame.width - expected.width) <= 1 && abs(frame.height - expected.height) <= 1
        }
        guard homes.count == 1 else {
            XCTFail("HARNESS: expected unique measured production toolbar home, found \(homes.count)")
            return
        }
        guard homes[0].isEnabled && homes[0].isHittable else {
            XCTFail("HARNESS: measured production toolbar home is not usable")
            return
        }
        homes[0].tap()
        let opened = bookmarks.waitForExistence(timeout: 15)
        attachTree(app, "h2-runtime-without-controls-drawer-result")
        shot(app, "h2-runtime-without-controls-drawer-result")
        XCTAssertTrue(opened && bookmarks.isHittable,
                      "Production toolbar home must open the real drawer with its Bookmarks row")
    }

    private func require(_ condition: Bool, _ app: XCUIApplication, _ step: String,
                         file: StaticString = #filePath, line: UInt = #line) {
        if !condition { attachTree(app, step); shot(app, step) }
        XCTAssertTrue(condition, step, file: file, line: line)
    }

    private func label(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    private func tapLabel(_ app: XCUIApplication, _ text: String) {
        let target = label(app, text)
        require(target.waitForExistence(timeout: 30), app, "Missing control: \(text)")
        if !target.isHittable { app.swipeUp() }
        require(target.isHittable && target.isEnabled, app, "Unusable control: \(text)")
        target.tap()
    }

    /// Tap a button whose AX label starts with `prefix`. Production labels may carry a trailing ellipsis
    /// ("Workspaces \u{2026}") and a menu row exposes both a Button and a StaticText with the same label.
    private func tapButton(_ app: XCUIApplication, prefix: String) {
        let target = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
        require(target.waitForExistence(timeout: 30), app, "Missing button: \(prefix)")
        require(target.isHittable && target.isEnabled, app, "Unusable button: \(prefix)")
        target.tap()
    }

    private func chooserTitles(_ app: XCUIApplication) -> [XCUIElement] {
        let language = app.buttons["Language, All"].firstMatch
        // The measured chooser reports "Document type, All types" (not "Bible") both before and after a delete.
        let type = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Document type,'")).firstMatch
        guard language.exists && type.exists else { return [] }
        // The debug chooser overlay also contains Document, but sits below the real filters.
        return app.staticTexts.matching(NSPredicate(format: "label == 'Document'"))
            .allElementsBoundByIndex.filter {
                !$0.frame.isEmpty && $0.frame.maxY <= min(language.frame.minY, type.frame.minY)
            }
    }

    private func leaveChooser(_ app: XCUIApplication) {
        let titles = chooserTitles(app)
        require(titles.count == 1, app, "HARNESS: unique actual chooser toolbar title and filters")
        guard titles.count == 1 else { return }
        let title = titles[0].frame
        // Measured toolbar: a 40pt unlabeled up button left of the title, within its 64pt band.
        let candidates = app.buttons.allElementsBoundByIndex.filter {
            let frame = $0.frame
            // Unlabeled while an incoming panel overlaps (measured); the measured plain Up label is 'Back'.
            return (($0.label.isEmpty && $0.identifier.isEmpty) || $0.label == "Back") && !frame.isEmpty
                && frame.minX >= app.frame.minX && frame.minX <= app.frame.minX + 24
                && frame.maxX <= title.minX && frame.width <= 64
                && frame.minY >= title.midY - 32 && frame.maxY <= title.midY + 32
        }
        require(candidates.count == 1, app, "HARNESS: unique measured unlabeled chooser toolbar up")
        guard candidates.count == 1 else { return }
        require(candidates[0].isHittable && candidates[0].isEnabled, app,
                "HARNESS: usable measured chooser toolbar up")
        candidates[0].tap()
    }

    /// Measured production route only: Menu -> Bookmarks -> All labels chip -> Find -> Search notes.
    private func assertResetRemovedNote(_ app: XCUIApplication) {
        guard openBookmarks(app) else { return }
        let chip = app.buttons.matching(NSPredicate(format: "label == 'Filter by label, All'"))
        require(chip.count == 1, app, "Visible All labels chip missing or ambiguous: \(chip.count)")
        let finds = app.buttons.matching(NSPredicate(format: "label == 'Find'"))
        require(finds.count == 1 && finds.firstMatch.isHittable, app, "HARNESS: unique hittable exact Find button")
        guard chip.count == 1, finds.count == 1, finds.firstMatch.isHittable else { return }
        finds.firstMatch.tap()
        let fields = app.textViews.matching(NSPredicate(format: "label == 'Search notes'"))
        require(waitUntil(timeout: 20) { fields.count == 1 && fields.firstMatch.isHittable }, app,
                "HARNESS: unique hittable 'Search notes' text view")
        guard fields.count == 1 else { return }
        fields.firstMatch.tap()
        fields.firstMatch.typeText(marker)
        // Measured: once typed, the TextView loses its label and exposes only its value, so re-read it
        // through the type-only query (exactly one hittable text view), not the label query.
        let typed = app.textViews
        require(waitUntil(timeout: 10) {
            typed.count == 1 && typed.firstMatch.isHittable && typed.firstMatch.value as? String == self.marker
        }, app, "HARNESS: marker query applied to real notes search (single text view value)")
        require(label(app, "No items to display").waitForExistence(timeout: 30), app,
                "HARNESS: loaded marker-specific bookmark search has no results")
        let note = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", marker)).firstMatch
        require(!note.exists, app, "UI-created backup note must be absent before import")
        attachTree(app, "h2-reset-marker-search-empty-before-import")
        shot(app, "h2-reset-marker-search-empty-before-import")
        let close = app.buttons.matching(NSPredicate(format: "label == 'Close search'"))
        require(close.count == 1 && close.firstMatch.isHittable, app, "Exact unique 'Close search' control")
        guard close.count == 1, close.firstMatch.isHittable else { return }
        close.firstMatch.tap()
        usleep(1_000_000)
        navigateUpToReading(app, step: "reset-absence")
    }

    /// EVIDENCE ONLY (no assertion on found/not-found): searches the restored Bookmarks list for the marker
    /// through the measured route to tell a missing note from a stale reading WebView. Route failures still fail.
    private func recordRestoredBookmarksMarker(_ app: XCUIApplication) {
        guard openBookmarks(app) else { return }
        let chip = app.buttons.matching(NSPredicate(format: "label == 'Filter by label, All'"))
        let finds = app.buttons.matching(NSPredicate(format: "label == 'Find'"))
        require(chip.count == 1 && finds.count == 1 && finds.firstMatch.isHittable, app,
                "HARNESS: All labels chip and unique hittable Find on restored Bookmarks")
        guard chip.count == 1, finds.count == 1, finds.firstMatch.isHittable else { return }
        finds.firstMatch.tap()
        let fields = app.textViews.matching(NSPredicate(format: "label == 'Search notes'"))
        require(waitUntil(timeout: 20) { fields.count == 1 && fields.firstMatch.isHittable }, app,
                "HARNESS: unique hittable 'Search notes' text view on restored Bookmarks")
        guard fields.count == 1 else { return }
        fields.firstMatch.tap()
        fields.firstMatch.typeText(marker)
        let typed = app.textViews
        require(waitUntil(timeout: 10) {
            typed.count == 1 && typed.firstMatch.isHittable && typed.firstMatch.value as? String == self.marker
        }, app, "HARNESS: marker typed into restored Bookmarks search (single text view value)")
        // Let the filtered list settle before sampling; either result is evidence, not a verdict.
        usleep(3_000_000)
        let found = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", marker)).firstMatch.exists
        attachTree(app, "h2-restored-bookmarks-list-search")
        shot(app, "h2-restored-bookmarks-list-search")
        let result = XCTAttachment(string: "restored-bookmarks-list-marker-found: \(found)")
        result.name = "restored-bookmarks-list-marker-found"
        result.lifetime = .keepAlways
        add(result)
        let close = app.buttons.matching(NSPredicate(format: "label == 'Close search'"))
        require(close.count == 1 && close.firstMatch.isHittable, app, "Exact unique 'Close search' control")
        guard close.count == 1, close.firstMatch.isHittable else { return }
        close.firstMatch.tap()
        usleep(1_000_000)
        navigateUpToReading(app, step: "restored-bookmarks-evidence")
    }

    // MARK: measured navigation helpers (phone AX measurements, no environment labels)

    /// The unique production toolbar home: Button 'Menu' at (0,59,48,56), present only without an incoming panel.
    private func openDrawer(_ app: XCUIApplication) {
        let host = element(app, "reading-host")
        require(host.waitForExistence(timeout: 30), app, "HARNESS: reading host before drawer")
        let tags = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'incoming-'"))
        require(tags.count == 0, app, "HARNESS: incoming panel must be absent for the production drawer")
        guard tags.count == 0 else { return }
        let expected = CGRect(x: app.frame.minX, y: app.frame.minY + 59, width: 48, height: 56)
        let homes = app.buttons.allElementsBoundByIndex.filter {
            let frame = $0.frame
            return $0.label == "Menu"
                && abs(frame.minX - expected.minX) <= 1 && abs(frame.minY - expected.minY) <= 1
                && abs(frame.width - expected.width) <= 1 && abs(frame.height - expected.height) <= 1
        }
        require(homes.count == 1 && homes[0].isEnabled && homes[0].isHittable, app,
                "HARNESS: unique usable measured toolbar home, found \(homes.count)")
        guard homes.count == 1, homes[0].isEnabled, homes[0].isHittable else { return }
        homes[0].tap()
        let rows = app.buttons.matching(NSPredicate(format: "label == 'Bookmarks'"))
        require(rows.firstMatch.waitForExistence(timeout: 15) && rows.count == 1, app,
                "HARNESS: drawer opened with a unique exact Bookmarks button")
    }

    /// Drawer -> exact Bookmarks button -> Bookmarks screen (reading host no longer hittable).
    private func openBookmarks(_ app: XCUIApplication) -> Bool {
        openDrawer(app)
        let rows = app.buttons.matching(NSPredicate(format: "label == 'Bookmarks'"))
        guard rows.count == 1, rows.firstMatch.isHittable else {
            require(false, app, "HARNESS: usable Bookmarks drawer button")
            return false
        }
        let host = element(app, "reading-host")
        rows.firstMatch.tap()
        let left = waitUntil(timeout: 30) { !host.exists || !host.isHittable }
        usleep(1_500_000)
        require(left, app, "Bookmarks screen did not leave the reading-host hittable state")
        return left
    }

    /// The unique plain Up control: Button 'Back' at (4,67,48,48) on Bookmarks and Backup & Restore.
    private func tapMeasuredBack(_ app: XCUIApplication) -> Bool {
        let expected = CGRect(x: app.frame.minX + 4, y: app.frame.minY + 67, width: 48, height: 48)
        let backs = app.buttons.allElementsBoundByIndex.filter {
            let frame = $0.frame
            return $0.label == "Back"
                && abs(frame.minX - expected.minX) <= 1 && abs(frame.minY - expected.minY) <= 1
                && abs(frame.width - expected.width) <= 1 && abs(frame.height - expected.height) <= 1
        }
        require(backs.count == 1 && backs[0].isEnabled && backs[0].isHittable, app,
                "HARNESS: unique usable measured Back (4,67,48,48), found \(backs.count)")
        guard backs.count == 1, backs[0].isEnabled, backs[0].isHittable else { return false }
        backs[0].tap()
        return true
    }

    /// Back to the reading host. The drawer is sometimes open afterwards (measured, nondeterministic):
    /// tolerate both states, close it with the real button, and record which one occurred.
    private func settleReadingAfterUp(_ app: XCUIApplication, step: String) {
        let host = element(app, "reading-host")
        require(host.waitForExistence(timeout: 15), app, "Reading host did not appear after Up (\(step))")
        let closeDrawer = app.buttons.matching(NSPredicate(format: "label == 'Close navigation menu'"))
        let drawerRows = app.buttons.matching(NSPredicate(format: "label == 'Bookmarks'"))
        if closeDrawer.count == 1 {
            attachTree(app, "h2-\(step)-drawer-open-observed")
            shot(app, "h2-\(step)-drawer-open-observed")
            closeDrawer.firstMatch.tap()
            require(waitUntil(timeout: 15) { drawerRows.count == 0 }, app,
                    "Drawer did not close via Close navigation menu (\(step))")
        } else {
            attachTree(app, "h2-\(step)-drawer-closed-observed")
            shot(app, "h2-\(step)-drawer-closed-observed")
        }
        require(waitUntil(timeout: 30) { host.isHittable && self.john3Shown(app) }, app,
                "Reading host not hittable with John 3 shown after Up (\(step))")
    }

    private func navigateUpToReading(_ app: XCUIApplication, step: String) {
        guard tapMeasuredBack(app) else { return }
        settleReadingAfterUp(app, step: step)
    }

    /// Reading launch. Without `controls` no incoming panel exists (the production drawer stays reachable), so
    /// readiness is the reading host plus John 3; with controls the incoming-ready tag is awaited as well.
    private func reading(reset: Bool, reference: String? = "John.3.16", controls: Bool = false) -> XCUIApplication {
        var args = ["-standardTestFixture", "-incomingRuntimeTest"]
        if controls { args += ["-incomingRuntimeControls"] }
        if reset { args += ["-resetDatabases"] }
        if let reference { args += ["-openDocument", "KJV", "-openRef", reference] }
        let app = launch(args)
        if controls {
            require(element(app, "incoming-runtime-ready").waitForExistence(timeout: 90), app,
                    "HARNESS: incoming entry preflight; verify installed modules/registry")
        }
        require(element(app, "reading-host").waitForExistence(timeout: 90), app,
                "HARNESS: reading host missing after launch")
        require(waitUntil(timeout: 60) { self.john3Shown(app) }, app, "KJV John 3 reading missing")
        return app
    }

    /// A UUID child inside the prescribed owned folder prevents stale exports in unfiltered/repeated suites.
    /// No host shell or deletion of unrelated Files items is needed between XCTest methods.
    private func newExportFolder(_ app: XCUIApplication, _ name: String) {
        local(app)
        folder(app, "H2 Backup")
        attachTree(app, "h2-export-owned-folder-before-create")
        shot(app, "h2-export-owned-folder-before-create")
        let navigation = localRoot(app).navigationBars["FullDocumentManagerViewControllerNavigationBar"]
        if isPad {
            let create = navigation.buttons["New Folder"].firstMatch
            require(create.waitForExistence(timeout: 20) && create.isHittable, app, "HARNESS: iPad New Folder")
            create.tap()
        } else {
            tapLabel(app, "More")
            tapLabel(app, "New Folder")
        }
        let rename = localRoot(app).textViews["DOC.inlineRenameField"].firstMatch
        require(rename.waitForExistence(timeout: 20) && rename.isHittable, app, "HARNESS: inline folder rename")
        rename.typeText(name)
        require(rename.value as? String == name, app, "HARNESS: exact UUID folder name")
        let keyboards = app.keyboards
        require(keyboards.firstMatch.waitForExistence(timeout: 20) && rename.exists
                && rename.value as? String == name, app,
                "HARNESS: inline rename remains active with exact UUID before keyboard completion")
        let doneButtons = keyboards.buttons.matching(identifier: "Done")
        require(doneButtons.firstMatch.waitForExistence(timeout: 20) && doneButtons.count == 1,
                app, "HARNESS: unique keyboard Done identifier")
        guard doneButtons.count == 1 else { return }
        require(doneButtons.firstMatch.isHittable && doneButtons.firstMatch.isEnabled, app,
                "HARNESS: keyboard Done is usable during inline rename")
        doneButtons.firstMatch.tap()
        require(waitUntil(timeout: 20) { !rename.exists }, app, "HARNESS: rename completed")
        let exactRoot = app.otherElements.matching(identifier:
            "DOC.browsingRoot Source: com.apple.FileProvider.LocalStorage, Title: \(name)").firstMatch
        require(exactRoot.waitForExistence(timeout: 20), app, "HARNESS: exact local Files UUID folder root")
        let exactTitle = exactRoot.navigationBars["FullDocumentManagerViewControllerNavigationBar"]
            .buttons["\(name), Actions Menu"].firstMatch
        require(exactTitle.waitForExistence(timeout: 20) && exactTitle.isHittable, app,
                "HARNESS: exact UUID Files folder navigation title")
        attachTree(app, "h2-export-uuid-child-open")
        shot(app, "h2-export-uuid-child-open")
    }

    private func save(_ app: XCUIApplication, filename: String, destination: String) {
        tapLabel(app, "Phone storage")
        require(app.buttons["Save"].firstMatch.waitForExistence(timeout: 30), app, "Actual Files export picker")
        newExportFolder(app, destination)
        let fields = app.textFields.allElementsBoundByIndex.filter { $0.isHittable }
        require(fields.count == 1, app, "HARNESS: unique export filename editor")
        require(fields[0].value as? String == (filename as NSString).deletingPathExtension,
                app, "HARNESS: exact generated export filename")
        tapLabel(app, "Save")
        require(waitUntil(timeout: 60) { !localRoot(app).exists }, app, "Export picker dismissal")
        let success = filename == databaseFile
            ? "My Notes, Bookmarks, Study Pads, Reading Plans and Workspaces backed up successfully."
            : "Selected modules backed up successfully."
        require(label(app, success).waitForExistence(timeout: 60), app, "Exact backup completion payload")
        tapLabel(app, "OK")
    }

    /// Restore categories in the production ALL_DB_FILENAMES order (six prompting files of eight).
    /// `title` is the exact category contents; Bookmarks appends archive statistics, so it matches by prefix.
    private let restoreCategories: [(name: String, title: String, prefixTitle: Bool)] = [
        ("Bookmarks", "Bookmarks, Labels and Study Pads", true),
        ("ReadingPlans", "Reading plans and their statuses", false),
        ("Workspaces", "Workspaces and Windows", false),
        ("AISettings", "AI prompts and provider configurations", false),
        ("MyDocuments", "My Documents and their content", false),
        ("Progress", "Memorized verses and chapter reading records", false),
    ]
    private let sectionsTitle = "Which backup sections do you want to restore?"

    private func textElement(_ app: XCUIApplication, _ text: String, prefix: Bool = false) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(
            format: prefix ? "label BEGINSWITH %@" : "label == %@", text)).firstMatch
    }

    /// A dialog action is scoped by being the ONLY button with this exact label and lying below the dialog title.
    private func dialogButton(_ app: XCUIApplication, _ text: String, below title: XCUIElement) -> XCUIElement? {
        let matches = app.buttons.matching(NSPredicate(format: "label == %@", text))
        guard matches.count == 1 else { return nil }
        let button = matches.firstMatch
        guard button.isHittable, button.isEnabled, button.frame.minY >= title.frame.maxY - 1 else { return nil }
        return button
    }

    /// Bounded database-restore state machine: sections -> six ordered (category, Restore, Overwrite, OK)
    /// pairs -> termination. One 240 s deadline. Every state is recorded with tree and screenshot BEFORE its
    /// action; an unexpected, out-of-order or error state fails with that evidence and nothing is dismissed generically.
    private func restoreDatabase(_ app: XCUIApplication, folderName: String, child: String? = nil,
                                 filename: String) {
        configure(app, restore: true, database: true)
        tapLabel(app, "Restore or Import from\u{2026}")
        let deadline = Date().addingTimeInterval(240)
        pick(app, folderName: folderName, child: child, filename: filename)

        var stateNumber = 0
        func record(_ name: String) {
            stateNumber += 1
            attachTree(app, "h2-restore-state-\(stateNumber)-\(name)")
            shot(app, "h2-restore-state-\(stateNumber)-\(name)")
        }
        func failState(_ message: String) {
            record("FAILED")
            XCTFail("\(message) (restore state machine, state \(stateNumber))")
        }
        let knownTitles: [(String, Bool)] = [(sectionsTitle, false), ("Are you sure?", false)]
            + restoreCategories.map { ($0.title, $0.prefixTitle) }
        /// A known title that is not the expected one, or an error, ends the run with evidence.
        func unexpected(expecting expected: String?, expectedPrefix: Bool) -> String? {
            for failure in ["Restoring database was unsuccessful", "An error has occurred"] {
                if textElement(app, failure, prefix: true).exists { return "Known failure state: \(failure)" }
            }
            for (title, prefix) in knownTitles
            where !(title == expected && prefix == expectedPrefix) && textElement(app, title, prefix: prefix).exists {
                return "Out-of-order dialog: \(title)"
            }
            return nil
        }
        func waitFor(_ condition: () -> Bool) -> Bool {
            while Date() < deadline {
                if condition() { return true }
                usleep(300_000)
            }
            return condition()
        }
        func tapScoped(_ title: XCUIElement, _ button: String) -> Bool {
            guard let target = dialogButton(app, button, below: title) else {
                failState("Cannot scope unique usable '\(button)' action to its dialog")
                return false
            }
            target.tap()
            return true
        }

        // A: archive section selection (only for the multi-file archive).
        let sections = textElement(app, sectionsTitle)
        guard waitFor({ sections.exists || unexpected(expecting: self.sectionsTitle, expectedPrefix: false) != nil }) else {
            failState("Section dialog did not appear before the deadline"); return
        }
        if let problem = unexpected(expecting: sectionsTitle, expectedPrefix: false) { failState(problem); return }
        record("sections")
        guard tapScoped(sections, "OK") else { return }
        guard waitFor({ !sections.exists }) else { failState("Section dialog did not close"); return }

        for (index, category) in restoreCategories.enumerated() {
            // B: category question.
            let title = textElement(app, category.title, prefix: category.prefixTitle)
            guard waitFor({ title.exists || unexpected(expecting: category.title, expectedPrefix: category.prefixTitle) != nil }) else {
                failState("Category \(index + 1) \(category.name) did not appear before the deadline"); return
            }
            if let problem = unexpected(expecting: category.title, expectedPrefix: category.prefixTitle) {
                failState(problem); return
            }
            let message = textElement(app, "Database for \(category.title)", prefix: true)
            let messageSeen = message.exists || app.descendants(matching: .any).matching(NSPredicate(
                format: "label CONTAINS %@", "Database for \(category.title)")).firstMatch.exists
            record("category-\(index + 1)-\(category.name)")
            guard messageSeen else { failState("Category \(category.name) restore/import message not found"); return }
            guard dialogButton(app, "Import", below: title) != nil, dialogButton(app, "Cancel", below: title) != nil else {
                failState("Category \(category.name) is missing scoped Import/Cancel actions"); return
            }
            guard tapScoped(title, "Restore") else { return }

            // C: matching overwrite confirmation.
            let sure = textElement(app, "Are you sure?")
            guard waitFor({
                // The answered category dialog may still be fading out: only another problem ends the wait early.
                sure.exists || (unexpected(expecting: "Are you sure?", expectedPrefix: false)
                    .map { !$0.contains(category.title) } ?? false)
            }) else {
                failState("Overwrite confirmation \(index + 1) did not appear before the deadline"); return
            }
            if let problem = unexpected(expecting: "Are you sure?", expectedPrefix: false) {
                // The answered category may still be fading out: only a different dialog is a failure.
                if !problem.contains(category.title) { failState(problem); return }
            }
            let overwriteText = "Overwrite \(category.title)"
            let overwrite = textElement(app, category.prefixTitle ? overwriteText : overwriteText + "?",
                                        prefix: category.prefixTitle)
            guard waitFor({ overwrite.exists }) else {
                failState("Exact overwrite message for \(category.name) not found"); return
            }
            record("overwrite-\(index + 1)-\(category.name)")
            guard tapScoped(sure, "OK") else { return }
            guard waitFor({ !sure.exists && !overwrite.exists }) else {
                failState("Overwrite confirmation \(index + 1) did not close"); return
            }
        }

        // D: termination only after all six pairs. Progress disappearing is termination, never success.
        let documents = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Documents'"))
        let database = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Database'"))
        let restore = app.buttons.matching(NSPredicate(format: "label == %@", "Restore or Import from\u{2026}"))
        func quiescentBackup() -> Bool {
            !localRoot(app).exists && unexpected(expecting: nil, expectedPrefix: false) == nil
                && !textElement(app, "Please wait", prefix: true).exists
                && documents.count == 2 && database.count == 2 && restore.count == 1
                && restore.firstMatch.isHittable
        }
        var stable = 0
        guard waitFor({
            stable = quiescentBackup() ? stable + 1 : 0
            return stable >= 4 // consecutive samples reject a transient snapshot; not a quiet-time oracle
        }) else { failState("Backup did not return to a usable state without progress/modal"); return }
        record("terminated-usable-backup")
        // This is not the acceptance oracle: the caller must inspect restored reading and exact note.
    }

    /// The pane's floating window-button overlay (Compose, above the WKWebView) sits at the pane's top-right corner and
    /// swallows taps there although XCUITest's isHittable does not see it. When the verse number lies in that corner
    /// box, drag the web view down a little (earlier text scrolls in, the number moves down) until it is clear. Bounded.
    private func clearOfWindowButtonOverlay(_ app: XCUIApplication, _ number: XCUIElement) {
        let web = app.webViews.firstMatch
        for _ in 0..<6 {
            let pane = web.frame, frame = number.frame
            let corner = CGRect(x: pane.maxX - pane.width * 0.2, y: pane.minY,
                                width: pane.width * 0.2, height: 110)
            guard frame.intersects(corner) else { return }
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.4, dy: 0.40))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.4, dy: 0.50))
            from.press(forDuration: 0.1, thenDragTo: to)
            usleep(700_000)
            _ = waitForStableOnScreen(app, number)
        }
    }

    private func createNote(_ app: XCUIApplication) {
        // VerseNumber's click bubbles to Verse.vue, selecting the verse rather than a Strong's word.
        let numbers = app.webViews.staticTexts.matching(NSPredicate(format: "label == '16'"))
        require(numbers.firstMatch.waitForExistence(timeout: 30) && numbers.count == 1,
                app, "HARNESS: unique observed John 3 verse number 16")
        let number = numbers.firstMatch
        require(number.isHittable && waitForStableOnScreen(app, number), app,
                "HARNESS: stable hittable verse number 16")
        clearOfWindowButtonOverlay(app, number)
        require(number.isHittable && waitForStableOnScreen(app, number), app,
                "HARNESS: stable hittable verse number 16 below the window-button overlay")
        number.tap()
        attachTree(app, "h2-after-verse16-tap")
        shot(app, "h2-after-verse16-tap")
        // Native toolbar text cannot satisfy this WebView-only modal-title query.
        // Stop for AX discovery if the title is absent or not unique; never use an app-wide match.
        let titles = app.webViews.staticTexts.matching(NSPredicate(format: "label == 'John 3:16'"))
        require(titles.firstMatch.waitForExistence(timeout: 20) && titles.count == 1, app,
                "HARNESS MODAL AX DISCOVERY: unique WebView John 3:16 selection title required")
        let action = app.webViews.descendants(matching: .any).matching(NSPredicate(format:
            "label == 'Note' OR label == 'Write a note'")).firstMatch
        require(action.waitForExistence(timeout: 20) && action.isHittable, app, "HARNESS: real verse Note action")
        action.tap()
        require(waitUntil(timeout: 30) {
            app.webViews.textViews.allElementsBoundByIndex.filter { $0.isHittable }.count == 1
        }, app, "HARNESS: await unique real bookmark note editor AX")
        let editor = app.webViews.textViews.allElementsBoundByIndex.filter { $0.isHittable }[0]
        require(app.keyboards.firstMatch.waitForExistence(timeout: 20), app,
                "HARNESS: native keyboard from real editor autofocus before typing")
        attachTree(app, "h2-editor-autofocus-before-typing")
        shot(app, "h2-editor-autofocus-before-typing")
        editor.typeText(marker)
        attachTree(app, "h2-editor-after-single-type-before-done")
        shot(app, "h2-editor-after-single-type-before-done")
        require(waitUntil(timeout: 20) { editor.exists && (editor.value as? String)?.contains(self.marker) == true },
                app, "HARNESS: real editor contains exact typed marker before keyboard dismissal")
        let done = app.buttons["Done"].firstMatch
        require(done.waitForExistence(timeout: 20) && done.isHittable, app,
                "HARNESS: observed native keyboard Done")
        done.tap()
        require(waitUntil(timeout: 20) { !app.keyboards.firstMatch.exists }, app,
                "HARNESS: native keyboard dismissed before editor controls")
        require(waitUntil(timeout: 20) { editor.exists && (editor.value as? String)?.contains(self.marker) == true },
                app, "HARNESS: real editor contains exact typed marker after keyboard dismissal")
        require(editor.isHittable && editor.frame.intersects(app.frame), app,
                "HARNESS: marker editor visible after keyboard dismissal")
        let closes = app.webViews.buttons.matching(NSPredicate(format: "label == 'Close'"))
        require(closes.firstMatch.waitForExistence(timeout: 20) && closes.count == 1
                && closes.firstMatch.isHittable, app, "HARNESS: observed unique HTML editor Close")
        // HtmlEditor.close saves then emits close; EditableText exits editMode, not the bookmark modal.
        closes.firstMatch.tap()
        require(waitUntil(timeout: 20) { !editor.exists }, app, "HARNESS: editor Close exits edit mode")
        let saved = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", marker)).firstMatch
        require(saved.waitForExistence(timeout: 30), app, "Saved marker displayed in real bookmark modal")
        attachTree(app, "h2-saved-note-before-modal-close")
        shot(app, "h2-saved-note-before-modal-close")
        closeSavedNote(app)
        assertNote(app, text: marker)
        closeSavedNote(app)
    }

    private func closeSavedNote(_ app: XCUIApplication) {
        let notes = app.webViews.staticTexts.matching(NSPredicate(format: "label == %@", marker))
        let titles = app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Joh 3:16'"))
        require(notes.count == 1 && titles.count == 1, app,
                "HARNESS: exact saved marker and measured bookmark header before close")
        guard notes.count == 1 && titles.count == 1 else { return }
        let title = titles.firstMatch.frame
        let note = notes.firstMatch.frame
        let candidates = app.webViews.buttons.allElementsBoundByIndex.filter {
            let frame = $0.frame
            return $0.label.isEmpty && $0.identifier.isEmpty && !frame.isEmpty
                && frame.minX > title.maxX && frame.maxX <= app.frame.maxX
                && frame.width >= 24 && frame.width <= 40 && frame.height <= 40
                && frame.minY >= title.minY - 20 && frame.maxY <= title.maxY + 8
                && frame.maxY < note.minY
        }
        require(candidates.count == 1, app, "HARNESS: unique measured saved-note header close")
        guard candidates.count == 1 else { return }
        require(candidates[0].isHittable, app, "HARNESS: saved-note header close hittable")
        candidates[0].tap()
        require(waitUntil(timeout: 20) { !notes.firstMatch.exists && !titles.firstMatch.exists }, app,
                "HARNESS: saved bookmark modal closed")
    }

    /// Reopen the existing John 3:16 note the way a user does: tap verse number 16, tap the exact note preview,
    /// and require the bookmark modal header `Joh 3:16`. Used only for the H2 marker note; the legacy Android
    /// fixture is checked by its Bookmarks-list row instead. No unlabeled in-verse icon is needed.
    private func assertNote(_ app: XCUIApplication, text: String) {
        let verse = "16", header = "Joh 3:16"
        do {
            let numbers = app.webViews.staticTexts.matching(NSPredicate(format: "label == %@", verse))
            require(numbers.count == 1 && numbers.firstMatch.isHittable
                    && waitForStableOnScreen(app, numbers.firstMatch), app,
                    "HARNESS: unique stable verse \(verse) for existing note selection")
            clearOfWindowButtonOverlay(app, numbers.firstMatch)
            require(numbers.firstMatch.isHittable && waitForStableOnScreen(app, numbers.firstMatch), app,
                    "HARNESS: stable hittable verse \(verse) below the window-button overlay")
            numbers.firstMatch.tap()
            attachTree(app, "h2-existing-note-selection-preview")
            shot(app, "h2-existing-note-selection-preview")
            let previews = app.webViews.staticTexts.matching(NSPredicate(format: "label == %@", text))
            require(previews.firstMatch.waitForExistence(timeout: 20) && previews.count == 1
                    && previews.firstMatch.isHittable, app,
                    "HARNESS AX DISCOVERY: unique exact existing-note preview, never create another Note")
            previews.firstMatch.tap()
            require(app.webViews.staticTexts[header].firstMatch.waitForExistence(timeout: 20), app,
                    "HARNESS: existing bookmark preview opens measured bookmark header \(header)")
        }
        shot(app, "h2-restored-note-modal")
    }

    /// From Backup & Restore: the unique measured Back, then reading (tolerating an open drawer).
    private func backToReading(_ app: XCUIApplication) {
        navigateUpToReading(app, step: "backup-up")
    }

    private func chooseKJV(_ app: XCUIApplication, delete: Bool) {
        openDrawer(app)
        tapLabel(app, "Choose Document")
        // Each row exposes a Button ("KJV <desc>, English") plus a child StaticText with the same prefix;
        // count only the Button so uniqueness means one document row.
        let rows = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'KJV '"))
        require(rows.firstMatch.waitForExistence(timeout: 30) && rows.count == 1,
                app, "HARNESS: unique production KJV document row")
        if delete {
            rows.firstMatch.press(forDuration: 1.2)
            // The long-press context menu is occasionally not up yet (seen once: "Missing control: Delete"); retry once.
            if !label(app, "Delete").waitForExistence(timeout: 5) { rows.firstMatch.press(forDuration: 1.2) }
            tapLabel(app, "Delete")
            tapLabel(app, "Yes")
            require(waitUntil(timeout: 30) { !rows.firstMatch.exists }, app, "KJV absent after actual uninstall")
            attachTree(app, "h2-kjv-absent-in-chooser")
            shot(app, "h2-kjv-absent-in-chooser")
            require(waitUntil(timeout: 20) { self.chooserTitles(app).count == 1 }, app,
                    "HARNESS: actual chooser context after deletion")
            leaveChooser(app)
            require(element(app, "reading-host").waitForExistence(timeout: 30), app,
                    "HARNESS: chooser closed before opening backup")
        } else {
            rows.firstMatch.tap()
            closeDrawerIfOpen(app, step: "after-choose-kjv")
        }
    }

    /// Selecting a document from the chooser can return to the reading host with the navigation drawer still
    /// open (measured: the WebView is then absent from the AX tree, so John 3 cannot be read). Close it with
    /// the real button, as `settleReadingAfterUp` does; the John 3 oracle stays with the caller.
    private func closeDrawerIfOpen(_ app: XCUIApplication, step: String) {
        require(element(app, "reading-host").waitForExistence(timeout: 30), app,
                "Reading host did not appear (\(step))")
        let closeDrawer = app.buttons.matching(NSPredicate(format: "label == 'Close navigation menu'"))
        if closeDrawer.firstMatch.waitForExistence(timeout: 5) {
            attachTree(app, "h2-\(step)-drawer-open-observed")
            shot(app, "h2-\(step)-drawer-open-observed")
            closeDrawer.firstMatch.tap()
            require(waitUntil(timeout: 15) { closeDrawer.count == 0 }, app,
                    "Drawer did not close via Close navigation menu (\(step))")
        }
    }

    /// The bookmark list is lazy: rows below the fold are not in the AX tree. Scroll from the top until the
    /// note shows. Returns whether the target was found and whether any note text was seen at all
    /// (a "Lorem ipsum" fixture note proves "Show notes" is on).
    private func scanBookmarksForNote(_ app: XCUIApplication, _ text: String) -> (found: Bool, sawNotes: Bool) {
        let target = label(app, text)
        let lorem = app.descendants(matching: .any).matching(NSPredicate(format:
            "label CONTAINS 'Lorem ipsum'")).firstMatch
        for _ in 0..<6 { app.swipeDown() }
        var sawNotes = false
        // A plain swipeUp moves more than one screen of this list (round 7: it skipped the John 3:20 row and ran
        // to the last bookmark), so drag a short, fixed distance and sample after each step.
        let frame = app.frame
        for _ in 0..<60 {
            if lorem.exists { sawNotes = true }
            if target.exists && target.isHittable { return (true, sawNotes) }
            let from = app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: frame.midX, dy: frame.height * 0.70))
            let to = app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: frame.midX, dy: frame.height * 0.45))
            from.press(forDuration: 0.1, thenDragTo: to)
        }
        return (target.exists && target.isHittable, sawNotes || lorem.exists)
    }

    func testDatabaseBackupRoundTripThroughFiles() throws {
        try requirePhone()
        let destination = "Database-\(UUID().uuidString)"
        provisionKJV()
        var app = reading(reset: true)
        createNote(app)
        // Controls only for the Backup entry: persisted-state relaunch, no reset/document/reference.
        app = relaunchWithControls(app)
        openBackup(app)
        configure(app, restore: false, database: true)
        tapLabel(app, "Backup to\u{2026}")
        save(app, filename: databaseFile, destination: destination)
        sendToBackground(app)
        app.terminate()
        app = reading(reset: true)
        assertResetRemovedNote(app)
        app = relaunchWithControls(app)
        openBackup(app)
        restoreDatabase(app, folderName: "H2 Backup", child: destination, filename: databaseFile)
        // Same process from here: no relaunch between restore completion and the restored-state oracles.
        backToReading(app)
        // Highlight is a visual oracle: controller must inspect this screenshot on both idioms.
        // The John 3:16 highlight is NOT assertable through accessibility (it is WebView paint). It is evidenced
        // manually from this screenshot; the automated oracle for the restore is the note modal text below.
        shot(app, "h2-john316-restored-highlight-NOT-ASSERTED-manual-evidence-only")
        recordRestoredBookmarksMarker(app)
        assertNote(app, text: marker)
        closeSavedNote(app)
        sendToBackground(app)
        app.terminate()
        app = reading(reset: false, reference: nil)
        defer { app.terminate() }
        require(john3Shown(app), app, "Persisted restored active reading without openRef override")
        assertNote(app, text: marker)
    }

    func testDocumentsBackupExportAndReimport() throws {
        try requirePhone()
        let destination = "Documents-\(UUID().uuidString)"
        provisionKJV()
        var app = reading(reset: true)
        app = relaunchWithControls(app)
        openBackup(app)
        configure(app, restore: false, database: false)
        tapLabel(app, "Backup to\u{2026}")
        require(label(app, "Select which modules to backup").waitForExistence(timeout: 30), app, "Real module multichoice")
        let kjv = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS '(KJV,'")).firstMatch
        require(kjv.waitForExistence(timeout: 30) && kjv.isHittable, app, "HARNESS: KJV module option")
        kjv.tap()
        tapLabel(app, "OK")
        save(app, filename: modulesFile, destination: destination)
        // Persisted-state launch without controls for the real uninstall, then controls again before Backup.
        sendToBackground(app)
        app.terminate()
        app = reading(reset: false, reference: nil)
        chooseKJV(app, delete: true)
        app = relaunchWithControls(app)
        openBackup(app)
        configure(app, restore: true, database: false)
        tapLabel(app, "Restore or Import from\u{2026}")
        require(label(app, "Load Documents From Files").waitForExistence(timeout: 30), app, "Real InstallZip prelude")
        tapLabel(app, "Proceed")
        pick(app, folderName: "H2 Backup", child: destination, filename: modulesFile)
        awaitLocalModuleInstall(app)
        backToReading(app)
        chooseKJV(app, delete: false)
        defer { app.terminate() }
        require(john3Shown(app), app, "Reimported KJV renders real John 3 text")
        // The toolbar names the active module as a StaticText ("King James Version (1769) ..."); no button label
        // contains "KJV". Require that name and the absence of the NASB name.
        let activeModule = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'King James Version'")).firstMatch
        require(activeModule.waitForExistence(timeout: 20)
                && !app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'New American Standard'")).firstMatch.exists,
                app, "Reimported active module is KJV, not NASB fallback")
    }

    func testAndroidReleaseBackupImports() throws {
        try requirePhone()
        provisionKJV()
        var app = reading(reset: true)
        app = relaunchWithControls(app)
        defer { app.terminate() }
        openBackup(app)
        restoreDatabase(app, folderName: "H2 Fixtures", filename: "db-backup.abdb.zip")
        backToReading(app)
        shot(app, "h2-legacy-restored-active-reading-before-navigation")
        // Production route: reading toolbar overflow "More options" -> "Workspaces \u{2026}" (measured AX tree).
        tapLabel(app, "More options")
        tapButton(app, prefix: "Workspaces")
        // Fixture workspaces.sqlite3 holds exactly "Workspace 1" and "Workspace 2"; the sheet marks the active one
        // "Workspace 1 (current)" (measured AX tree), so match that button exactly rather than the bare name.
        let workspaceButtons = app.buttons.matching(NSPredicate(format:
            "label == 'Workspace 1 (current)' OR label == 'Workspace 1' OR label == 'Workspace 2'"))
        require(app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Workspace 1'")).firstMatch
                    .waitForExistence(timeout: 30), app, "Actual legacy Workspace 1")
        require(label(app, "Workspace 2").exists && workspaceButtons.count == 2, app, "Actual legacy Workspace 2")
        tapLabel(app, "Close")
        guard openBookmarks(app) else { return }
        // Notes are shown by default and the list is lazy, so the note is usually just below the fold.
        // Toggle "Show notes" only if the scan saw no note text at all (toggling blindly switched it OFF:
        // the failing run's tree has no search action, which exists only while notes are shown).
        var scan = scanBookmarksForNote(app, "G2 fixture bookmark note")
        if !scan.found && !scan.sawNotes {
            // Bookmarks screen overflow is labelled "Menu" (measured), not "More options".
            tapLabel(app, "Menu")
            tapButton(app, prefix: "Show notes")
            scan = scanBookmarksForNote(app, "G2 fixture bookmark note")
        }
        require(scan.found, app, "Unique original Android fixture bookmark note (sawNotes=\(scan.sawNotes))")
        // Oracle (plan: "one of its bookmarks is shown"): ONE restored row carries the John 3:20 reference, its verse
        // text and the original note. The row's AX label is "<ref>, <date>, <verse text>, <note>" (measured), so this
        // is not satisfied by a workspace John 3:16 or by the bare note alone.
        // Opening the row is NOT asserted here: in round 8 a tap on this row (log 231.65s, dispatched) left the
        // iOS Bookmarks screen unchanged for 30 s; whether that is a product defect is reported separately.
        let row = app.buttons.matching(NSPredicate(format:
            "label BEGINSWITH 'John 3:20,' AND label CONTAINS 'For every one that doeth evil' "
            + "AND label CONTAINS 'G2 fixture bookmark note'")).firstMatch
        require(row.waitForExistence(timeout: 15), app,
                "Legacy bookmark row is John 3:20 with its verse text and G2 fixture note, not workspace John 3:16")
        XCTContext.runActivity(named: "NOT COVERED: opening a bookmark row on iOS (R3 follow-up)") { activity in
            let note = XCTAttachment(string: "Tapping a restored bookmark row left the iOS Bookmarks screen unchanged "
                + "(possible product defect, unclassified). Not asserted here; see task-5-rest-fix-report.md Round 8.")
            note.name = "NOT COVERED: opening a bookmark row on iOS (R3 follow-up)"
            note.lifetime = .keepAlways
            activity.add(note)
        }
    }
}
