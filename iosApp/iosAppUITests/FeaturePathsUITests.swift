import XCTest

/// H2 Task 6 feature paths through production routes: cipher unlock, chooser module backup share, general book
/// and maps, Grid chapter choice and the text background picker.
///
/// Prerequisites (host side): Files "H2 Fixtures" holds h2-kjv.zip (seed-files-provider.sh); setUp installs KJV when absent (KjvProvisioning.swift).
/// Fixtures arrive through the production incoming queue: `-incomingRuntimeTest -incomingFixture <alias[,alias]>`
/// (aliases cipher|genbook|maps|dict, see IosIncomingRuntimeHarness); `-incomingRemoveFixtures` deletes a previous
/// run's test-owned fixture first (never KJV). Fixtures and keys:
///  - cipher  -> h2-cipher.zip,  module H2Cipher (locked; key `k4-Test-key`), John 1:1 text
///  - genbook -> h2-genbook.zip, module K4GenBook, keys /Part 1/Chapter 1 ("In the beginning God created the heaven
///               and the earth."), /Part 1/Chapter 2, /Part 2 (John 1:1 text)
///  - maps    -> h2-maps.zip,    module H2Maps (Category=Maps), same keys and text as the genbook
///  - dict    -> h2-dict.zip,    module K4ZLD (AARON, ABBA, ZION) for Task 10
/// The AX labels of the unlock dialog, the backup selection dialog, Grid cells and the general book/map key lists
/// were NOT measured when this was drafted: a missing selector is HARNESS discovery (tree attached), not a product RED.
final class FeaturePathsUITests: RealAppUITestCase {
    /// A fresh install has only the NASB fallback; establish KJV once per class (idempotent, see KjvProvisioning.swift).
    private static var kjvEnsured = false
    override func setUp() {
        super.setUp()
        if !Self.kjvEnsured { ensureKJVInstalled(); Self.kjvEnsured = true }
    }

    private let wrongKey = "wrong-key"
    private let rightKey = "k4-Test-key"

    private func require(_ condition: Bool, _ app: XCUIApplication, _ step: String,
                         file: StaticString = #filePath, line: UInt = #line) {
        if !condition { attachTree(app, step); shot(app, step) }
        XCTAssertTrue(condition, step, file: file, line: line)
    }

    private func byLabel(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    private func byLabel(_ app: XCUIApplication, containing text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    private func tap(_ app: XCUIApplication, _ target: XCUIElement, _ step: String, timeout: TimeInterval = 30) {
        require(target.waitForExistence(timeout: timeout), app, "Missing control: \(step)")
        for _ in 0..<4 where !target.isHittable { app.swipeUp() }
        require(target.isHittable && target.isEnabled, app, "Unusable control: \(step)")
        target.tap()
    }

    private func confirmDialogButton(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    /// Cold incoming confirmation for each queued archive, in order.
    private func confirmInstalls(_ app: XCUIApplication, _ filenames: [String]) {
        for filename in filenames {
            let message = app.staticTexts.matching(NSPredicate(format: "label == %@",
                "Do you want to install module (\(filename))?")).firstMatch
            require(message.waitForExistence(timeout: 60), app, "install-confirm-\(filename)")
            tap(app, confirmDialogButton(app, "OK"), "install-ok-\(filename)")
        }
    }

    /// `chooser: true` starts ON the document chooser (the debug scenario's start route; no reading host, and the
    /// incoming install prompt is not shown there - measured). `chooser: false` starts on reading like the app does.
    private func readingArguments(_ extra: [String], chooser: Bool, document: String = "KJV", ref: String = "John.3.16") -> [String] {
        ["-standardTestFixture", "-resetDatabases"] + (chooser ? ["-featureScenario", "chooser"] : [])
            + ["-openDocument", document, "-openRef", ref] + extra
    }

    /// Cold incoming install on the reading route (where the production prompt is shown), then a clean process.
    private func installFixtures(_ aliases: String, _ filenames: [String]) {
        let app = launch(readingArguments(fixtureArguments(aliases), chooser: false))
        confirmInstalls(app, filenames)
        let prompt = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Do you want to install module'")).firstMatch
        _ = waitUntil(timeout: 60) { !prompt.exists }
        // No completion tag exists for these archives; give the production installer time, then end the process.
        // The following chooser launch proves the install (row present), it is not assumed here.
        sleep(20)
        app.terminate()
    }

    /// The chooser lists Bibles only by default; general books and maps need 'All types'. Idempotent and independent of
    /// earlier state: the chip label ('Document type, <X>') is read first, nothing happens when it already says All types,
    /// otherwise the chip is tapped (re-tapped when no menu item shows: iPad run 7 showed the menu not opening on the
    /// first tap) and the 'All types' menu item (never the chip itself) is chosen.
    private func showAllDocumentTypes(_ app: XCUIApplication) {
        let type = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Document type,'")).firstMatch
        guard type.exists, !type.label.contains("All types") else { return }
        let item = app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS 'All types' AND NOT (label BEGINSWITH 'Document type')")).firstMatch
        var opened = false
        for attempt in 1...3 {
            type.tap()
            if item.waitForExistence(timeout: 6) { opened = true; break }
            if attempt == 1 { attachTree(app, "document-type-menu-not-open"); shot(app, "document-type-menu-not-open") }
        }
        require(opened, app, "HARNESS: 'All types' document type choice")
        item.tap()
        require(waitUntil(timeout: 15) { type.label.contains("All types") }, app, "Document type filter shows All types")
    }

    private func fixtureArguments(_ aliases: String) -> [String] {
        ["-incomingRuntimeTest", "-incomingFixture", aliases, "-incomingRemoveFixtures"]
    }

    private func chooserFilters(_ app: XCUIApplication) -> Bool {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Document type,'")).firstMatch.exists
    }

    /// The chooser opened through the finite debug route, then a document row by its initials prefix. The row list
    /// is loaded when the chooser opens, so a freshly installed module needs the chooser reopened until it shows.
    private func openChooserRow(_ app: XCUIApplication, initials: String, timeout: TimeInterval = 180) -> XCUIElement {
        // Rows are Buttons labelled "<initials> <description>, <language>" (measured). The list is lazy and the
        // fixtures sort below the fold with All types (12 documents), so scroll before reopening the chooser.
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

    /// Scrolls a lazy bottom-sheet grid until the button shows (the Grid book list hides books below the fold).
    private func scrollSheetToButton(_ app: XCUIApplication, _ target: XCUIElement, attempts: Int = 8) -> Bool {
        for _ in 0..<attempts {
            if target.exists && target.isHittable { return true }
            nudgeSheetList(app)
        }
        return target.exists && target.isHittable
    }

    private func clearAndType(_ app: XCUIApplication, field: XCUIElement, _ text: String) {
        field.tap()
        if let current = field.value as? String, !current.isEmpty {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        field.typeText(text)
    }

    /// Measured: the unlock input is an unlabelled TextView (not a TextField/SecureTextField) under the dialog title.
    private func unlockField(_ app: XCUIApplication) -> XCUIElement { app.textViews.firstMatch }

    /// Labels of the displayed document's web view root, measured as `BibleView-<id>/bible/<initials>_ordinal_<n> (production)`
    /// (cipher relaunch tree). It names the module the reading view is rendering, unlike a chooser row or the description.
    private func shownDocumentLabels(_ app: XCUIApplication) -> [String] {
        app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'BibleView-'"))
            .allElementsBoundByIndex.map(\.label)
    }

    private func requireShownModule(_ app: XCUIApplication, _ initials: String, _ step: String) {
        let found = waitUntil(timeout: 30) { self.shownDocumentLabels(app).contains { $0.contains("/\(initials)_") } }
        if !found {
            let a = XCTAttachment(string: shownDocumentLabels(app).joined(separator: "\n"))
            a.name = "shown-document-labels-\(step)"; a.lifetime = .keepAlways; add(a)
        }
        require(found, app, "\(step): the displayed web view belongs to \(initials)")
    }

    /// Visible on-screen labels (static texts and buttons) of the current screen, sorted: an observable of selection state.
    private func screenLabels(_ app: XCUIApplication) -> [String] {
        let screen = app.frame
        let elements = app.staticTexts.allElementsBoundByIndex + app.buttons.allElementsBoundByIndex
        return elements.filter { $0.exists && !$0.label.isEmpty && $0.frame.intersects(screen) }.map(\.label).sorted()
    }

    /// Text of John 1:1 in the web view phrase labels (WebKit splits text into phrase-level static texts).
    private func john11Shown(_ app: XCUIApplication, timeout: TimeInterval = 60) -> Bool {
        let texts = app.webViews.staticTexts
        return waitUntil(timeout: timeout) {
            let labels = (0..<min(texts.count, 400)).map { texts.element(boundBy: $0).label }
            return labels.joined(separator: " ").range(of: #"In the beginning was the Word"#, options: .regularExpression) != nil
        }
    }

    // MARK: cipher

    func testCipherWrongRetryRightPersists() throws {
        installFixtures("cipher", ["h2-cipher.zip"])
        var app = launch(readingArguments([], chooser: true, ref: "John.1.1"))
        let row = openChooserRow(app, initials: "H2Cipher")
        require(row.exists, app, "HARNESS: H2Cipher row in the document chooser after install")
        row.tap()

        let prompt = byLabel(app, containing: "H2Cipher is encrypted and needs passphrase")
        require(prompt.waitForExistence(timeout: 30), app, "Unlock dialog for the locked fixture")
        var field = unlockField(app)
        require(field.waitForExistence(timeout: 10), app, "HARNESS: unlock text field")
        clearAndType(app, field: field, wrongKey)
        tap(app, confirmDialogButton(app, "OK"), "unlock-ok-wrong")

        // Wrong key: the product asks "Passphrase did not work, try again?" and Yes reopens the input.
        let retry = byLabel(app, "Passphrase did not work, try again?")
        require(retry.waitForExistence(timeout: 30), app, "Wrong key must show the retry question")
        shot(app, "cipher-wrong-key-retry")
        tap(app, confirmDialogButton(app, "Yes"), "retry-yes")
        require(byLabel(app, containing: "H2Cipher is encrypted and needs passphrase").waitForExistence(timeout: 30), app,
                "Unlock dialog again after Yes")
        field = unlockField(app)
        clearAndType(app, field: field, rightKey)
        tap(app, confirmDialogButton(app, "OK"), "unlock-ok-right")
        require(waitUntil(timeout: 30) { !self.byLabel(app, containing: "is encrypted and needs passphrase").exists }, app,
                "Right key closes the dialog without the retry question")
        require(!byLabel(app, "Passphrase did not work, try again?").exists, app, "No retry question after the right key")

        // Unlocking does not open the document; choose it again, now it must read.
        let unlocked = openChooserRow(app, initials: "H2Cipher", timeout: 60)
        require(unlocked.exists, app, "H2Cipher row after unlock")
        unlocked.tap()
        require(element(app, "reading-host").waitForExistence(timeout: 60), app, "Reading host after choosing H2Cipher")
        require(john11Shown(app), app, "John 1:1 text from the unlocked module")
        requireShownModule(app, "H2Cipher", "cipher-after-unlock")
        sendToBackground(app)
        app.terminate()

        // Relaunch without reset or incoming flags (NO -resetDatabases: it would drop the stored key) and choose the
        // module in the chooser like a user. Run 3 evidence: `-openDocument H2Cipher` shows John 1:1 with an EMPTY page
        // and no dialog (that route bypasses the chooser's unlock step), so it cannot tell a lost key from a bypass.
        app = launch(["-standardTestFixture", "-featureScenario", "chooser", "-openDocument", "KJV", "-openRef", "John.1.1"])
        defer { app.terminate() }
        let again = openChooserRow(app, initials: "H2Cipher", timeout: 90)
        require(again.exists, app, "H2Cipher row after relaunch")
        again.tap()
        let dialog = byLabel(app, containing: "is encrypted and needs passphrase")
        require(!dialog.waitForExistence(timeout: 10), app,
                "PRODUCT?: unlock dialog shown again after relaunch (stored cipher key lost or not applied at startup)")
        require(element(app, "reading-host").waitForExistence(timeout: 90), app, "Reading host after relaunch")
        require(john11Shown(app), app, "PRODUCT?: John 1:1 text after relaunch (key stored but not applied: page stays empty)")
        require(byLabel(app, containing: "John 1:1").exists, app, "Relaunch reads John 1:1")
        requireShownModule(app, "H2Cipher", "cipher-after-relaunch")
    }

    // MARK: chooser backup

    func testChooserModuleBackupShares() throws {
        let app = launch(readingArguments([], chooser: true))
        defer { app.terminate() }
        require(waitUntil(timeout: 90) { self.chooserFilters(app) }, app, "Document chooser is the start route")

        // Overflow button: the 'Menu' button furthest right (the toolbar overflow, not a left home button).
        // Measured chooser toolbar: Button 'Menu' (the overflow) at the right edge of the top bar, label 'Menu' (iPhone y=67,
        // iPad y=40: the status bar differs, so only 'top strip, right edge' is asserted, not coordinates).
        // The reading screen underneath adds unlabelled buttons only; AX hittability is unreliable here, tap by frame.
        func overflowButtons() -> [XCUIElement] {
            app.buttons.matching(NSPredicate(format: "label == 'Menu'")).allElementsBoundByIndex
                .filter { $0.frame.minY >= 0 && $0.frame.minY < 140 && $0.frame.maxX > app.frame.maxX - 60 }
        }
        // Run 3: the chooser tree held the button but the one-shot query found 0 right after the filters appeared
        // (toolbar still composing); wait for exactly one instead of sampling once.
        _ = waitUntil(timeout: 20) { overflowButtons().count == 1 }
        let menus = overflowButtons()
        require(menus.count == 1, app, "HARNESS: unique chooser overflow Menu button, found \(menus.count)")
        let f = menus[0].frame
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: f.midX, dy: f.midY)).tap()
        tap(app, app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Backup Documents'")).firstMatch, "backup-documents-row")

        // Selection dialog "Select which modules to backup": choose KJV, confirm.
        require(byLabel(app, "Select which modules to backup").waitForExistence(timeout: 30), app, "Module selection dialog")
        let kjv = byLabel(app, containing: "KJV, ")
        tap(app, kjv, "select-kjv")
        tap(app, confirmDialogButton(app, "OK"), "selection-ok")

        // Destination action sheet: Share.
        tap(app, confirmDialogButton(app, "Share"), "destination-share", timeout: 120)

        let activity = app.otherElements["ActivityListView"].firstMatch
        require(activity.waitForExistence(timeout: 60), app, "System share sheet")
        // Measured header (UIActivityContentView): Other label 'AndBibleModulesBackup.abmd' (iOS hides the final
        // extension) and Other label 'ZIP Archive · <size>' (the type description derived from the .zip file).
        let name = app.descendants(matching: .any).matching(NSPredicate(
            format: "label BEGINSWITH 'AndBibleModulesBackup.abmd'")).firstMatch
        require(name.waitForExistence(timeout: 15), app, "Share item is AndBibleModulesBackup.abmd(.zip)")
        let kind = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'ZIP Archive'")).firstMatch
        require(kind.exists, app, "Share item type is a ZIP Archive")
        shot(app, "backup-share-sheet")

        let close = app.buttons["Close"].firstMatch
        if close.exists && close.isHittable {
            close.tap()
        } else if isPad {
            // iPad: the share UI is an anchored popover (right of centre, no Close button; measured). Dismiss it with an
            // outside tap on EMPTY chooser space (left, below the document rows): the corner (0.03, 0.15) used by
            // FilesUITests lands on the H2Cipher row here and opened its unlock dialog (run 5 screenshot).
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.85)).tap()
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55))
                .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95)))
        }
        require(waitUntil(timeout: 20) { !activity.exists }, app, "Share sheet dismissed")
        require(chooserFilters(app), app, "Back on the chooser after cancelling the share sheet")
        require(!byLabel(app, containing: "An error has occurred").exists, app, "Cancel is silent (no error)")
    }

    // MARK: general book and maps

    func testGeneralBookAndMapsOpenFromReading() throws {
        installFixtures("genbook,maps", ["h2-genbook.zip", "h2-maps.zip"])
        let app = launch(readingArguments([], chooser: true))
        defer { app.terminate() }
        for initials in ["K4GenBook", "H2Maps"] {
            let row = openChooserRow(app, initials: initials)
            require(row.exists, app, "HARNESS: \(initials) row in the document chooser")
            row.tap()
            // Run 3: choosing H2Maps showed neither a key list nor reading and logged no 'Set current doc'. Record what
            // follows the tap and retry it once, so a lost tap (HARNESS) is told from an inert maps row (PRODUCT?).
            let followUp = { app.buttons.matching(NSPredicate(format: "label == 'Chapter 1'")).firstMatch.exists
                || self.element(app, "reading-host").exists }
            if !waitUntil(timeout: 15, followUp) {
                attachTree(app, "\(initials)-after-row-tap"); shot(app, "\(initials)-after-row-tap")
                if row.exists && row.isHittable { row.tap() }
                require(waitUntil(timeout: 20, followUp), app,
                        "PRODUCT?: \(initials) row tap has no effect after a retry (no key list, no reading host)")
            }
            // The key chooser (or the first page) shows; pick the key whose text is the Genesis sentence.
            // Measured: choosing the module shows a 'Book' key list (Part 1, Chapter 1, Chapter 2, Part 2 as Buttons).
            let chapter = app.buttons.matching(NSPredicate(format: "label == 'Chapter 1'")).firstMatch
            if chapter.waitForExistence(timeout: 20) { tap(app, chapter, "\(initials)-key-Chapter 1") }
            require(element(app, "reading-host").waitForExistence(timeout: 60), app, "\(initials) reading host")
            let text = app.webViews.staticTexts.matching(NSPredicate(
                format: "label CONTAINS 'In the beginning God created the heaven and the earth'")).firstMatch
            require(text.waitForExistence(timeout: 60), app, "\(initials) page text '/Part 1/Chapter 1'")
            requireShownModule(app, initials, "\(initials)-page")
            shot(app, "\(initials)-page")
            // Back to the chooser for the next module.
            element(app, "debug-open-chooser").tap()
        }
    }

    // MARK: Grid and text background

    private func toolbarTitle(_ app: XCUIApplication, prefix: String) -> XCUIElement? {
        app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).allElementsBoundByIndex
            .first { $0.frame.minY < 160 && $0.frame.height > 0 }
    }

    func testGridAndTextBackgroundPaths() throws {
        var app = launch(readingArguments([], chooser: false))
        require(element(app, "reading-host").waitForExistence(timeout: 90) && john3Shown(app), app,
                "HARNESS: KJV John 3 reading (KJV installed, chooser evidence showed KJV and NASB)")

        // Grid: title tap -> book John -> chapter 4 changes the shown chapter.
        guard let title = toolbarTitle(app, prefix: "John 3") else {
            require(false, app, "HARNESS: toolbar title 'John 3…'"); return
        }
        title.tap()
        require(element(app, "ab-quick-sheet").waitForExistence(timeout: 15), app, "Grid quick sheet")
        // Measured: book buttons carry 3-5 letter short names ('Gen', '2 Ki', 'Judg') and the lazy grid hides the later
        // books; John is the only short name starting with 'Joh' (Job, Joe, Jos are different books).
        let john = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Joh'")).firstMatch
        require(scrollSheetToButton(app, john), app, "HARNESS: John button in the Grid book list after scrolling")
        john.tap()
        tap(app, app.buttons.matching(NSPredicate(format: "label == '4'")).firstMatch, "grid-chapter-4")
        // A verse step may follow; choose verse 1 when it does.
        if waitUntil(timeout: 5, { app.buttons.matching(NSPredicate(format: "label == '1'")).firstMatch.exists
            && self.element(app, "ab-quick-sheet").exists }) {
            app.buttons.matching(NSPredicate(format: "label == '1'")).firstMatch.tap()
        }
        require(waitUntil(timeout: 30) { self.toolbarTitle(app, prefix: "John 4") != nil }, app, "Toolbar shows John 4 after the Grid choice")
        require(waitUntil(timeout: 30) { !self.element(app, "ab-quick-sheet").exists }, app, "Grid sheet closed")
        // Positive marker for the new chapter: John 4 text ('Samaria' occurs in 4:4-9 and nowhere in John 3). Absence of
        // the old text is NOT asserted from the whole tree: the iPad run showed stale off-screen WebKit AX nodes
        // (y < 0) of other chapters after a correct navigation (screenshot: John 4:1 shown). Only a still VISIBLE
        // John 3 phrase would fail: 'Nicodemus' elements inside the screen frame.
        let samaria = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Samaria'")).firstMatch
        require(samaria.waitForExistence(timeout: 30), app, "John 4 text ('Samaria') present after the Grid choice")
        let screen = app.frame
        let visibleNicodemus = app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'"))
            .allElementsBoundByIndex.filter { $0.frame.intersects(screen) && $0.frame.minY >= 0 }
        require(visibleNicodemus.isEmpty, app, "John 3 text ('Nicodemus') still visible after the Grid choice")
        sendToBackground(app)
        app.terminate()

        // Background image row: Color settings -> Change -> Import from gallery… opens the real picker; Cancel keeps state.
        app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16"])
        defer { app.terminate() }
        require(element(app, "reading-host").waitForExistence(timeout: 90) && john3Shown(app), app, "Reading before text options")
        tap(app, app.buttons["More options"].firstMatch, "more-options")
        tap(app, app.buttons["All text options …"].firstMatch, "all-text-options")
        tap(app, byLabel(app, "Color settings"), "color-settings")
        // Observable of the prior selection: every visible label of the colour screen (the Background image row shows
        // the chosen image name, e.g. 'No background') and, one level down, of the image chooser (tiles, None row).
        let settingsBefore = screenLabels(app)
        require(settingsBefore.contains { $0.contains("Background image") }, app, "HARNESS: Background image row on the colour screen")
        tap(app, app.buttons["Change"].firstMatch, "background-image-change")
        let importRow = byLabel(app, "Import from gallery…")
        require(importRow.waitForExistence(timeout: 15), app, "Background image chooser")
        let chooserBefore = screenLabels(app)
        tap(app, importRow, "import-from-gallery")
        let cancel = app.buttons["Cancel"].firstMatch
        require(cancel.waitForExistence(timeout: 30), app, "Real photo picker (Cancel)")
        require(app.buttons["Photos"].exists || app.staticTexts["Photos"].exists || app.navigationBars["Photos"].exists, app,
                "System Photos picker content")
        cancel.tap()
        require(waitUntil(timeout: 20) { !cancel.exists }, app, "Picker dismissed")
        require(importRow.waitForExistence(timeout: 15), app, "Back on the background chooser after cancel")
        require(!byLabel(app, "Importing…").exists, app, "Cancel starts no import")
        require(screenLabels(app) == chooserBefore, app,
                "Image chooser state unchanged after cancel: \(Set(screenLabels(app)).symmetricDifference(Set(chooserBefore)))")
        let up = app.buttons["Back"].firstMatch
        if up.exists && up.isHittable { up.tap() }
        require(waitUntil(timeout: 15) { self.screenLabels(app) == settingsBefore }, app,
                "Colour screen (Background image row value) unchanged after cancel: \(Set(screenLabels(app)).symmetricDifference(Set(settingsBefore)))")
    }
}
