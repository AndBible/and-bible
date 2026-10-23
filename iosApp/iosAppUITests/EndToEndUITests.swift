import UIKit
import XCTest

/// I2 close-out end-to-end acceptance (spec section 5.3, phase 2 U1): ONE real user journey on a fresh install,
/// with no `-resetDatabases` / KJV provisioning shortcut and only the production routes (Welcome, drawer, reading
/// menu), never the debug "controls" entries:
///
///   Welcome -> Easy start (real KJV download) -> John 3 (Nicodemus) -> long-press verse 3:16 -> bookmark ->
///   label "E2E" -> Bookmarks list (filter "E2E" shows 3:16) -> search "Nicodemus" (>= 3 verses, first result opens
///   John 3) -> Device synchronization (WebDAV fixture, Bookmarks category completes) -> Backup & Restore ->
///   database backup to Files -> the file is listed in the Files picker.
///
/// `testRestoreKeepsLiveSettings` (Review Focus 2) restores a database backup and then proves the reading page still
/// reacts to night mode and text size changes made from the reading menu.
///
/// Opt-in sync fixture: env `TEST_RUNNER_E2E_SYNC_FIXTURE_JSON` (the runner strips the prefix, so the test sees
/// `E2E_SYNC_FIXTURE_JSON`): `{"url":"http://localhost:P/remote.php/dav/files/admin/","user":"admin","password":"...",
/// "sha256":"<optional, https://localhost only>"}`. Without it the chain test FAILS ("E2E sync fixture missing"), so
/// the close-out cannot pass it silently. The remote folder must be empty (a fresh device creates the cloud copy).
///
/// Each test is self-contained and ends with the app terminated. The chain is destructive for the app data
/// (`-resetData`); the restore test leaves night mode and the font size as it found them.
final class EndToEndUITests: RealAppUITestCase {
    private struct E2EFixture: Decodable {
        let url: String
        let user: String
        let password: String
        /// Pins a self-signed certificate of a loopback TLS relay (https://localhost only), like the Task 7 fixture.
        let sha256: String?
    }

    private var syncUI: SyncHarness { SyncHarness(test: self) }

    private let backupStem = "AndBibleDatabaseBackup"
    private let backupSuccess = "My Notes, Bookmarks, Study Pads, Reading Plans and Workspaces backed up successfully."
    private let restoreSectionsTitle = "Which backup sections do you want to restore?"
    private let labelName = "E2E"

    // MARK: fixture

    /// nil when the variable is absent; a decoding or policy problem is reported as its own failure.
    private func syncFixture() -> E2EFixture? {
        guard let json = ProcessInfo.processInfo.environment["E2E_SYNC_FIXTURE_JSON"], !json.isEmpty else { return nil }
        guard let data = json.data(using: .utf8), let fixture = try? JSONDecoder().decode(E2EFixture.self, from: data) else {
            XCTFail("E2E sync fixture invalid: not JSON with url, user and password")
            return nil
        }
        guard let url = URL(string: fixture.url), ["localhost", "127.0.0.1", "::1"].contains(url.host ?? ""),
              ["http", "https"].contains(url.scheme ?? ""), url.user == nil, url.password == nil,
              !fixture.user.isEmpty, !fixture.password.isEmpty else {
            XCTFail("E2E sync fixture invalid: the url must be http(s) to localhost, 127.0.0.1 or ::1, without credentials")
            return nil
        }
        if let fingerprint = fixture.sha256,
           syncUI.normalizedSha256(fingerprint) == nil || url.host != "localhost" || url.scheme != "https" {
            XCTFail("E2E sync fixture invalid: sha256 needs a valid SHA-256 and an https://localhost url")
            return nil
        }
        return fixture
    }

    // MARK: chain

    func testFreshInstallToBackupChain() throws {
        guard ProcessInfo.processInfo.environment["E2E_SYNC_FIXTURE_JSON"]?.isEmpty == false else {
            XCTFail("E2E sync fixture missing: pass TEST_RUNNER_E2E_SYNC_FIXTURE_JSON='{\"url\":...,\"user\":...,\"password\":...}'")
            return
        }
        guard let fixture = syncFixture() else { return }
        var app = installAndRead()
        bookmarkVerse16(app)
        app = relaunch(app)
        assignLabelThroughBookmarks(app)
        app = relaunch(app)
        searchNicodemus(app)
        app = relaunch(app)
        syncBookmarksOverWebDav(app, fixture)
        app = relaunch(app)
        backupDatabaseToFiles(app)
        verifyBackupInFilesPicker(app)
        sendToBackground(app)
        app.terminate()
    }

    /// Stage 1. Fresh install, real Welcome -> Easy start download (KJV), reading John 3.
    private func installAndRead() -> XCUIApplication {
        let app = freshReading()      // -resetData, Welcome, welcome-easy-start, download consent, enabled OK
        selectKJV(app)                // the real chooser; KJV arrives while Easy start finishes
        assertExactJohn316(app)       // Nicodemus phrase label, verse 16 wording, exactly one pane
        let nicodemus = app.webViews.staticTexts.matching(NSPredicate(format: "label == 'Nicodemus'")).count
        harnessRequire(nicodemus >= 1, app, "John 3 shows Nicodemus in the active pane (found \(nicodemus))")
        return app
    }

    /// Stage 2. Long-press a word of verse 16, "Bookmark" -> "Verses" in the selection menu.
    private func bookmarkVerse16(_ app: XCUIApplication) {
        let frame = verse16Word(app)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
            .withOffset(CGVector(dx: frame.midX, dy: frame.midY)).press(forDuration: 1.2)
        // Two-step bookmarking is the default: "Bookmark" first, then "Selection" / "Verses".
        if let bookmark = editMenuItem(app, "Bookmark", timeout: 10) { bookmark.tap() }
        guard let verses = editMenuItem(app, "Verses") else {
            harnessRequire(false, app, "the selection menu offers 'Verses' (two-step: after 'Bookmark')")
            return
        }
        shot(app, "e2e-bookmark-menu")
        verses.tap()
        // The new bookmark opens its modal in the web view; its content is not asserted here, the Bookmarks list is.
        usleep(2_000_000)
        shot(app, "e2e-bookmark-created")
    }

    /// Stage 3. Bookmarks screen: long-press the 3:16 row, "Assign labels", create label "E2E" from the search
    /// query (the label editor then opens with that name), tick it, go back, and prove it with the label filter.
    private func assignLabelThroughBookmarks(_ app: XCUIApplication) {
        openDrawerRow(app, "Bookmarks")
        let row = bookmarkRow316(app)
        row.press(forDuration: 1.2)
        let assign = byLabel(app, "Assign labels")
        harnessRequire(assign.waitForExistence(timeout: 15) && assign.isHittable, app,
                       "long-pressing the 3:16 bookmark row shows the 'Assign labels' action")
        assign.tap()
        // ASSIGN-mode label list. Search -> "E2E" -> New (the host seeds the new label's name with the query).
        let find = byLabel(app, "Find")
        harnessRequire(find.waitForExistence(timeout: 20) && find.isHittable, app, "the label list has a 'Find' action")
        find.tap()
        let field = app.textViews.firstMatch
        harnessRequire(field.waitForExistence(timeout: 15), app, "the label search field opened")
        typeInto(app, field, labelName)
        let new = byLabel(app, "New")
        harnessRequire(new.waitForExistence(timeout: 10) && new.isHittable, app, "the label search bar has a 'New' action")
        new.tap()
        let ok = byLabel(app, "OK")
        harnessRequire(ok.waitForExistence(timeout: 20) && app.staticTexts["Edit label"].firstMatch.exists, app,
                       "the label editor ('Edit label' with 'OK') opened")
        harnessRequire(byLabel(app, labelName).exists, app, "the new label is named '\(labelName)' (seeded from the search)")
        shot(app, "e2e-label-editor")
        ok.tap()
        // Back on the list: the new label's checkbox is named after the label; make sure it is ticked.
        // (The label also appears as a plain text row; the checkbox is the element of a control type.)
        let controlTypes: [XCUIElement.ElementType] = [.checkBox, .switch, .button, .other]
        func box() -> XCUIElement? {
            app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", labelName))
                .allElementsBoundByIndex.first { controlTypes.contains($0.elementType) }
        }
        harnessRequire(waitUntil(timeout: 20) { box() != nil }, app, "the assign list has a checkbox named '\(labelName)'")
        func isOn() -> Bool { box().map { $0.isSelected || ($0.value as? String) == "1" } ?? false }
        if !isOn() {
            box()?.tap()
            harnessRequire(waitUntil(timeout: 10) { isOn() }, app, "the '\(labelName)' checkbox became ticked")
        }
        shot(app, "e2e-label-ticked")
        // Leave the assign screen (saves the assignment), then the filter must list the bookmark under "E2E".
        tapBack(app, "assign-labels")
        let chip = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Filter by label,'")).firstMatch
        harnessRequire(chip.waitForExistence(timeout: 30), app, "back on the Bookmarks list ('Filter by label' chip)")
        chip.tap()
        let option = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", labelName)).firstMatch
        harnessRequire(option.waitForExistence(timeout: 15) && option.isHittable, app,
                       "the label filter offers '\(labelName)'")
        option.tap()
        let filtered = app.buttons.matching(NSPredicate(format: "label == %@", "Filter by label, \(labelName)")).firstMatch
        harnessRequire(filtered.waitForExistence(timeout: 15), app, "the filter chip reads 'Filter by label, \(labelName)'")
        harnessRequire(waitUntil(timeout: 20) { self.bookmarkRowCandidates(app).contains { $0.label.contains("3:16") } },
                       app, "the Bookmarks list filtered by '\(labelName)' shows John 3:16")
        shot(app, "e2e-bookmarks-filtered")
    }

    /// Stage 4. In-place search from the drawer: "Nicodemus", at least 3 verses, the first result opens John 3.
    private func searchNicodemus(_ app: XCUIApplication) {
        openDrawerRow(app, "Find")
        let field = element(app, "reading-search-field")
        harnessRequire(field.waitForExistence(timeout: 15), app, "the in-place search field opened")
        typeInto(app, field, "Nicodemus")
        field.typeText("\n")
        let count = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label MATCHES %@", "[0-9]+ verses in [0-9]+ translations")).firstMatch
        let create = app.buttons.matching(NSPredicate(format: "label == 'Create'")).firstMatch
        // Either the results arrive, or the index prompt comes first (a fresh install has no KJV index).
        _ = waitUntil(timeout: 60) { count.exists || create.exists }
        if !count.exists && create.exists { shot(app, "e2e-search-index-prompt"); create.tap() }
        harnessRequire(count.waitForExistence(timeout: 300), app, "a result count ('<n> verses in <m> translations') after searching 'Nicodemus'")
        let total = Int(count.label.split(separator: " ").first ?? "") ?? 0
        harnessRequire(total >= 3, app, "'Nicodemus' finds at least 3 verses, got '\(count.label)'")
        shot(app, "e2e-search-results")
        // Result cards are labelled "<reference> <text>"; the first one in Bible order is John 3:1.
        let cards = app.descendants(matching: .any).matching(NSPredicate(
            format: "label MATCHES %@ AND label CONTAINS 'Nicodemus'", #"^([1-3] )?[A-Za-z]+( [A-Za-z]+)* [0-9]+:[0-9]+.*"#))
        harnessRequire(waitUntil(timeout: 30) { cards.count > 0 && cards.element(boundBy: 0).isHittable }, app,
                       "the search lists result cards")
        let first = cards.element(boundBy: 0)
        let firstLabel = first.label
        attachText("search-first-card", firstLabel)
        first.tap()
        harnessRequire(firstLabel.hasPrefix("John 3:"), app, "the first result is in John 3, it is '\(firstLabel.prefix(40))'")
        harnessRequire(waitUntil(timeout: 30) { self.chapterMarkerShown(app, 3) } && john3Shown(app, timeout: 30), app,
                       "opening the first result shows John 3 (chapter marker and Nicodemus) in the pane")
    }

    /// Stage 5. Drawer -> Device synchronization -> WebDAV with the fixture -> Bookmarks category -> completion.
    private func syncBookmarksOverWebDav(_ app: XCUIApplication, _ fixture: E2EFixture) {
        openDrawerRow(app, "Device synchronization")
        let backend = syncUI.rowButton(app, "Synchronization Backend")
        harnessRequire(backend.waitForExistence(timeout: 30), app, "the Device synchronization screen opened")
        backend.tap()
        let webDav = syncUI.label(app, "WebDAV")
        harnessRequire(webDav.waitForExistence(timeout: 10), app, "the backend choice lists 'WebDAV'")
        webDav.tap()
        // Credentials first, with the category still off (switching it on starts the real sign-in).
        let bookmarks = syncUI.reveal(app, "Bookmarks", toggle: true)
        harnessRequire(bookmarks.isEnabled && !bookmarks.isSelected, app, "the Bookmarks sync category starts off")
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        syncUI.edit(app, "Server URL", fixture.url)
        syncUI.edit(app, "User name", fixture.user)
        syncUI.edit(app, "Password", fixture.password, sensitive: true)
        _ = syncUI.reveal(app, "Bookmarks", toggle: true)
        bookmarks.tap()

        let certificate = syncUI.label(app, "Untrusted server certificate")
        let fetch = syncUI.label(app, "Copy from Cloud to this device")
        let failures = ["Wrong username or password.", "Signing in to synchronization backend service failed.",
                        "Error occurred in Cloud synchronization", "The address is not a WebDAV folder."]
        func problem() -> String? { failures.first { syncUI.label(app, $0).exists } }
        let decision = waitUntil(timeout: 90) {
            certificate.exists || fetch.exists || problem() != nil || self.syncCompleted(app)
        }
        if let message = problem() { harnessRequire(false, app, "WebDAV sign-in failed: \(message)") }
        harnessRequire(decision, app, "sign-in produced a decision, an error or a completed sync within 90 s")
        if certificate.exists {
            harnessRequire(syncUI.trustPinnedCertificateIfShown(app, serverURL: fixture.url, sha256: fixture.sha256), app,
                           "the pinned certificate decision matched the fixture")
        }
        harnessRequire(!fetch.exists, app,
                       "the remote folder is empty (a 'Copy from Cloud to this device' choice means a previous run left data: reset the Nextcloud folder)")
        // Completion: the Bookmarks row summary gains "Last updated: <time>" once a sync cycle finished.
        let done = waitUntil(timeout: 180) { self.syncCompleted(app) || problem() != nil }
        if let message = problem() { harnessRequire(false, app, "synchronization failed: \(message)") }
        harnessRequire(done && syncCompleted(app), app, "the Bookmarks category is on and shows 'Last updated:' within 180 s")
        shot(app, "e2e-sync-completed")
    }

    /// The Bookmarks switch is selected and its summary carries the "Last updated: ..." line (`last_updated`).
    private func syncCompleted(_ app: XCUIApplication) -> Bool {
        let toggle = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Bookmarks'")).firstMatch
        return toggle.exists && toggle.isSelected && toggle.label.contains("Last updated:")
    }

    /// Stage 6. Drawer -> Backup & Restore -> Database only -> Backup to... -> Phone storage -> Files root -> Save.
    private func backupDatabaseToFiles(_ app: XCUIApplication) {
        openDrawerRow(app, "Backup & Restore")
        harnessRequire(labelStarting(app, "Backup to").waitForExistence(timeout: 20), app, "Backup & Restore opened")
        configure(app, restore: false, database: true)
        harnessTapLabel(app, "Backup to\u{2026}")
        harnessTapLabel(app, "Phone storage")
        harnessRequire(app.buttons["Save"].firstMatch.waitForExistence(timeout: 30), app, "the Files save picker opened")
        local(app)       // "On My iPhone" / "On My iPad" root
        let fields = app.textFields.allElementsBoundByIndex.filter { $0.isHittable }
        harnessRequire(fields.count == 1, app, "the save picker has one file name field, found \(fields.count)")
        let proposed = fields.first?.value as? String ?? ""
        harnessRequire(proposed.hasPrefix(backupStem), app, "the proposed file name starts with \(backupStem), is '\(proposed)'")
        harnessTapLabel(app, "Save")
        // A backup from an earlier run in the same folder asks to replace it.
        let replace = app.buttons.matching(NSPredicate(format: "label == 'Replace'")).firstMatch
        if replace.waitForExistence(timeout: 4) { shot(app, "e2e-backup-replace"); replace.tap() }
        harnessRequire(waitUntil(timeout: 60) { !self.localRoot(app).exists }, app, "the save picker closed")
        harnessRequire(harnessLabel(app, backupSuccess).waitForExistence(timeout: 60), app,
                       "the exact database backup success message")
        shot(app, "e2e-backup-saved")
        harnessTapLabel(app, "OK")
    }

    /// Stage 7. The saved file is listed in the Files picker (opened from Restore or Import, then cancelled).
    private func verifyBackupInFilesPicker(_ app: XCUIApplication) {
        configure(app, restore: true, database: true)
        harnessTapLabel(app, "Restore or Import from\u{2026}")
        harnessRequire(app.buttons["Cancel"].firstMatch.waitForExistence(timeout: 30), app, "the Files import picker opened")
        local(app)
        let name = backupFileName(app)
        harnessRequire(name != nil, app, "Files lists a file named \(backupStem)*.abdb.zip in the app's save location")
        attachText("backup-file-name", name ?? "none")
        app.buttons["Cancel"].firstMatch.tap()
        harnessRequire(waitUntil(timeout: 30) { !self.localRoot(app).exists }, app, "the Files picker closed after Cancel")
    }

    // MARK: restore keeps live settings

    func testRestoreKeepsLiveSettings() throws {
        var app = readingOnKjv()
        backupDatabaseToFiles(app)            // own backup: the test does not depend on the chain's run
        app = relaunch(app)
        restoreFromFilesRoot(app)
        returnToReading(app)
        harnessRequire(john3Shown(app, timeout: 60), app, "John 3 is shown after the restore")

        // Night mode: the page itself must change (pixels of the pane), not only a stored setting.
        let day = paneLuminance(app)
        harnessRequire(day != nil, app, "the pane can be sampled")
        let startLuminance = day ?? 0
        toggleNightMode(app)
        let flipped = waitUntil(timeout: 20) { abs((self.paneLuminance(app) ?? startLuminance) - startLuminance) > 0.2 }
        shot(app, "e2e-restore-night-toggled")
        harnessRequire(flipped, app, "night mode changed the page brightness (was \(startLuminance), now \(paneLuminance(app) ?? -1))")
        toggleNightMode(app)
        harnessRequire(waitUntil(timeout: 20) { abs((self.paneLuminance(app) ?? 0) - startLuminance) < 0.1 }, app,
                       "switching night mode back restores the original page brightness")

        // Text size: a phrase of the page grows when the font size goes up (AX frame width of the phrase).
        guard let before = phraseWidth(app, preferring: nil) else {
            harnessRequire(false, app, "a measurable phrase (Nicodemus / loved / God) is on the page")
            return
        }
        let (old, new) = changeFontSize(app, by: 4)
        harnessRequire(new == old + 4, app, "the font size editor moved from \(old) to \(new), expected +4")
        let grew = waitUntil(timeout: 30) { (self.phraseWidth(app, preferring: before.label)?.width ?? 0) > before.width * 1.05 }
        shot(app, "e2e-restore-font-increased")
        harnessRequire(grew, app, "'\(before.label)' got wider than \(before.width) pt after the font size +4")
        // Leave the setting as found: back down by the same amount, the phrase returns to its width.
        let (_, back) = changeFontSize(app, by: -4)
        harnessRequire(back == old, app, "the font size went back to \(old), is \(back)")
        harnessRequire(waitUntil(timeout: 30) {
            abs((self.phraseWidth(app, preferring: before.label)?.width ?? 0) - before.width) < 1.5
        }, app, "'\(before.label)' returned to its original width")
        sendToBackground(app)
        app.terminate()
    }

    /// Reading on KJV John 3 with a fresh workspace; installs KJV through Easy start when it is missing.
    private func readingOnKjv() -> XCUIApplication {
        let args = ["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16"]
        var app = launch(args)
        let welcome = element(app, "welcome-screen"), host = element(app, "reading-host")
        harnessRequire(waitUntil(timeout: 60) { welcome.exists || host.exists }, app, "neither Welcome nor reading shown")
        if welcome.exists || !john3Shown(app, timeout: 30) {
            app.terminate()
            let fresh = freshReading()
            selectKJV(fresh)
            sendToBackground(fresh)
            fresh.terminate()
            app = launch(args)
        }
        harnessRequire(element(app, "reading-host").waitForExistence(timeout: 60), app, "the reading host is shown")
        harnessRequire(john3Shown(app), app, "John 3 is shown")
        return app
    }

    /// Backup & Restore -> Database restore from the newest-listed `AndBibleDatabaseBackup*.abdb.zip` in the Files
    /// root; answers the section, per-category and overwrite dialogs; returns once the screen is idle again.
    private func restoreFromFilesRoot(_ app: XCUIApplication) {
        openDrawerRow(app, "Backup & Restore")
        harnessRequire(labelStarting(app, "Restore or Import from").waitForExistence(timeout: 20), app, "Backup & Restore opened")
        configure(app, restore: true, database: true)
        harnessTapLabel(app, "Restore or Import from\u{2026}")
        harnessRequire(app.buttons["Cancel"].firstMatch.waitForExistence(timeout: 30), app, "the Files import picker opened")
        local(app)
        guard let file = backupFileCell(app) else {
            harnessRequire(false, app, "Files lists \(backupStem)*.abdb.zip to restore")
            return
        }
        harnessRequire(file.isHittable, app, "the backup file is hittable in the picker")
        file.tap()
        let restoreRow = app.buttons.matching(NSPredicate(format: "label == %@", "Restore or Import from\u{2026}")).firstMatch
        let failures = ["Restoring database was unsuccessful", "An error has occurred"]
        let deadline = Date().addingTimeInterval(240)
        var answered = 0
        var idle = 0
        while Date() < deadline {
            if let failure = failures.first(where: { app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", $0)).firstMatch.exists }) {
                harnessRequire(false, app, "restore failed: \(failure)")
                return
            }
            let sure = harnessLabel(app, "Are you sure?")
            let sections = harnessLabel(app, restoreSectionsTitle)
            let restored = app.staticTexts.matching(NSPredicate(
                format: "label BEGINSWITH 'My Notes, Bookmarks, Study Pads, Reading Plans and Workspaces restored'")).firstMatch
            let category = app.buttons.matching(NSPredicate(format: "label == 'Restore'")).firstMatch
            let importing = app.buttons.matching(NSPredicate(format: "label == 'Import'")).firstMatch
            let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
            if (sure.exists || sections.exists || restored.exists) && ok.exists && ok.isHittable {
                shot(app, "e2e-restore-ok-\(answered)"); ok.tap(); answered += 1; idle = 0
            } else if category.exists && importing.exists && category.isHittable {
                shot(app, "e2e-restore-category-\(answered)"); category.tap(); answered += 1; idle = 0
            } else if answered > 0 && restoreRow.exists && restoreRow.isHittable && !localRoot(app).exists {
                idle += 1
                if idle >= 5 { return }
            } else {
                idle = 0
            }
            usleep(400_000)
        }
        harnessRequire(false, app, "the restore dialogs finished within 240 s (answered \(answered))")
    }

    /// Back from Backup & Restore to the reading page (the drawer may be open over it).
    private func returnToReading(_ app: XCUIApplication) {
        tapBack(app, "backup")
        let host = element(app, "reading-host")
        harnessRequire(host.waitForExistence(timeout: 30), app, "the reading host is shown after Back")
        let scrim = app.buttons.matching(NSPredicate(format: "label == 'Close navigation menu'")).firstMatch
        if scrim.waitForExistence(timeout: 5) {
            scrim.tap()
            harnessRequire(waitUntil(timeout: 10) { !scrim.exists }, app, "the drawer closed")
        }
        harnessRequire(waitUntil(timeout: 30) { app.buttons["Menu"].exists && self.byLabel(app, "More options").exists }, app,
                       "the reading toolbar is back")
    }

    /// Reading menu -> "Night mode" (a checkable overflow row).
    private func toggleNightMode(_ app: XCUIApplication) {
        let more = byLabel(app, "More options")
        harnessRequire(more.waitForExistence(timeout: 15), app, "the reading toolbar has 'More options'")
        more.tap()
        let night = app.buttons["Night mode"]
        harnessRequire(night.waitForExistence(timeout: 10), app, "the overflow menu has 'Night mode'")
        night.tap()
    }

    /// Reading menu -> "All text options" -> Font size sheet; drags the slider by `delta` and confirms with OK.
    /// Returns (old, new) values read from the slider. Back on the reading page afterwards.
    private func changeFontSize(_ app: XCUIApplication, by delta: Int) -> (Int, Int) {
        let more = byLabel(app, "More options")
        harnessRequire(more.waitForExistence(timeout: 15), app, "the reading toolbar has 'More options'")
        more.tap()
        let options = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'All text options'")).firstMatch
        harnessRequire(options.waitForExistence(timeout: 15), app, "the overflow menu has 'All text options'")
        options.tap()
        let row = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Font size'")).firstMatch
        harnessRequire(row.waitForExistence(timeout: 30), app, "the text display screen lists 'Font size'")
        row.tap()
        harnessRequire(waitUntil(timeout: 20) { self.fontSlider(app) != nil }, app, "the font size sheet shows its slider")
        guard let slider = fontSlider(app), let old = sliderValue(slider) else { return (0, 0) }
        // Range 1...60, thumb centre at left + 24 + (v - 1) / 59 * (width - 48) (geometry measured for FeatureFlowsUITests).
        let target = old + Double(delta)
        let width = Double(slider.frame.width)
        func offset(_ value: Double) -> CGFloat { CGFloat((24 + (value - 1) / 59 * (width - 48)) / width) }
        var current = old
        for _ in 0..<6 where current != target {
            let start = slider.coordinate(withNormalizedOffset: CGVector(dx: offset(current), dy: 0.5))
            let end = slider.coordinate(withNormalizedOffset: CGVector(dx: offset(target), dy: 0.5))
            start.press(forDuration: 0.3, thenDragTo: end)
            usleep(800_000)
            current = sliderValue(slider) ?? current
        }
        shot(app, "e2e-font-size-sheet")
        let okButton = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        harnessRequire(okButton.waitForExistence(timeout: 10), app, "the font size sheet has 'OK'")
        okButton.tap()
        let back = app.buttons.matching(NSPredicate(format: "label IN {'Back','Navigate up'}")).firstMatch
        harnessRequire(back.waitForExistence(timeout: 15), app, "the text display screen has Back")
        back.tap()
        harnessRequire(element(app, "reading-host").waitForExistence(timeout: 30), app, "the reading host is shown after the text options")
        return (Int(old), Int(current))
    }

    // MARK: pane measurements

    /// Mean brightness (0...1) of the first Bible pane, from a 16 x 16 downsample of its screenshot.
    private func paneLuminance(_ app: XCUIApplication) -> Double? {
        let pane = firstPane(app)
        guard pane.exists, let cg = pane.screenshot().image.cgImage else { return nil }
        let side = 16
        var pixels = [UInt8](repeating: 0, count: side * side * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: side, height: side, bitsPerComponent: 8,
                                          bytesPerRow: side * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.interpolationQuality = .medium
            context.draw(cg, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drawn else { return nil }
        var sum = 0.0
        for index in stride(from: 0, to: pixels.count, by: 4) {
            sum += (0.299 * Double(pixels[index]) + 0.587 * Double(pixels[index + 1]) + 0.114 * Double(pixels[index + 2])) / 255
        }
        return sum / Double(side * side)
    }

    /// Width of one phrase label of the page (`preferring` keeps the same phrase between two reads).
    private func phraseWidth(_ app: XCUIApplication, preferring label: String?) -> (label: String, width: CGFloat)? {
        for candidate in (label.map { [$0] } ?? ["Nicodemus", "loved", "God"]) {
            let phrase = app.webViews.staticTexts.matching(NSPredicate(format: "label == %@", candidate)).firstMatch
            if phrase.exists, phrase.frame.width > 0 { return (candidate, phrase.frame.width) }
        }
        return nil
    }

    /// The font size control is an `Other` element whose value is a plain number (e.g. "16.0"), wide and low.
    private func fontSlider(_ app: XCUIApplication) -> XCUIElement? {
        app.otherElements.matching(NSPredicate(format: "value MATCHES %@", #"^[0-9]+(\.[0-9]+)?$"#))
            .allElementsBoundByIndex.first { $0.frame.width > 200 && $0.frame.height < 80 }
    }

    private func sliderValue(_ slider: XCUIElement) -> Double? {
        (slider.value as? Double) ?? (slider.value as? String).flatMap(Double.init)
    }

    // MARK: reading-page helpers

    /// A plain word of verse 16 (between the "16" and "17" verse numbers), on screen, from ONE web view snapshot.
    private func verse16Word(_ app: XCUIApplication) -> CGRect {
        let pane = firstPane(app)
        harnessRequire(pane.waitForExistence(timeout: 20), app, "a Bible pane is shown")
        let visible = app.windows.firstMatch.frame
        for attempt in 0..<6 {
            var texts: [(label: String, frame: CGRect)] = []
            func walk(_ node: XCUIElementSnapshot) {
                if node.elementType == .staticText { texts.append((node.label, node.frame)) }
                node.children.forEach(walk)
            }
            if let snapshot = try? pane.snapshot() { walk(snapshot) }
            let labels = texts.map(\.label)
            // The "16" of John 3:16: its verse text (up to the next "17") holds "loved" (John 2:16 may be in the tree too).
            for start in labels.indices where labels[start] == "16" {
                guard let end = labels[(start + 1)...].firstIndex(of: "17"),
                      labels[(start + 1)..<end].contains("loved") else { continue }
                let word = ((start + 1)..<end).first { index in
                    let item = texts[index]
                    return item.label.count >= 3 && item.label.allSatisfy({ $0.isLetter })
                        && item.frame.minY > visible.minY + 200 && item.frame.maxY < visible.maxY - 160
                        && item.frame.minX > 20 && item.frame.maxX < visible.maxX - 20 && item.frame.width > 20
                }
                if let word { return texts[word].frame }
            }
            nudgeList(app, up: attempt % 2 == 0)
        }
        harnessRequire(false, app, "a plain word of John 3:16 is visible in the pane")
        return .zero
    }

    /// An item of the edit menu by exact label; pages through the menu chevron on a narrow screen.
    private func editMenuItem(_ app: XCUIApplication, _ label: String, timeout: TimeInterval = 12) -> XCUIElement? {
        let item = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
        let next = app.descendants(matching: .any).matching(NSPredicate(
            format: "identifier == 'show.next.items.menu.button' OR label == 'Show more items'")).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        var pages = 0
        while Date() < deadline {
            if item.exists { return item }
            if next.exists && next.isHittable && pages < 4 { next.tap(); pages += 1 }
            usleep(500_000)
        }
        return item.exists ? item : nil
    }

    private func firstPane(_ app: XCUIApplication) -> XCUIElement {
        app.webViews.matching(NSPredicate(format: "identifier BEGINSWITH 'bible-webview-'")).firstMatch
    }

    /// The chapter heading the web view renders ("-- 3 --", with em dashes).
    private func chapterMarkerShown(_ app: XCUIApplication, _ chapter: Int) -> Bool {
        app.webViews.staticTexts.matching(NSPredicate(format: "label == %@", "\u{2014} \(chapter) \u{2014}")).firstMatch.exists
    }

    // MARK: navigation helpers

    private func byLabel(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func labelStarting(_ app: XCUIApplication, _ prefix: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    private func attachText(_ name: String, _ text: String) {
        let attachment = XCTAttachment(string: text)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Persists and restarts the app on its saved workspace (no reset, no document or reference override); returns
    /// once John 3 is rendered again. A fresh process also leaves toolbar states that have no way back (search).
    private func relaunch(_ app: XCUIApplication) -> XCUIApplication {
        sendToBackground(app)
        app.terminate()
        let next = launch(["-standardTestFixture"])
        harnessRequire(element(next, "reading-host").waitForExistence(timeout: 90), next, "the reading host is shown after a relaunch")
        harnessRequire(!element(next, "welcome-screen").exists, next, "no Welcome after a relaunch")
        harnessRequire(john3Shown(next, timeout: 60), next, "John 3 is shown after a relaunch")
        return next
    }

    /// Home ("Menu") button -> the drawer row with this exact label, scrolled into reach. Same route as ReadingUITests.
    private func openDrawerRow(_ app: XCUIApplication, _ label: String) {
        let menu = app.buttons["Menu"]
        harnessRequire(menu.waitForExistence(timeout: 20), app, "the reading toolbar has its home ('Menu') button")
        menu.tap()
        let row = byLabel(app, label)
        var found = row.waitForExistence(timeout: 10)
        var swipes = 0
        while !found && swipes < 6 { app.swipeUp(); swipes += 1; usleep(1_200_000); found = row.exists }
        harnessRequire(found, app, "the drawer shows a '\(label)' row")
        _ = waitUntil(timeout: 4) { row.isHittable }
        swipes = 0
        while !row.isHittable && swipes < 8 { app.swipeUp(); swipes += 1; usleep(1_200_000) }
        harnessRequire(row.isHittable, app, "the drawer row '\(label)' is reachable")
        var last = CGRect.null
        _ = waitUntil(timeout: 5) { let frame = row.frame; defer { last = frame }; return frame == last }
        row.tap()
    }

    /// The toolbar's Up control ("Back"), then a short settle.
    private func tapBack(_ app: XCUIApplication, _ step: String) {
        let back = app.buttons["Back"].firstMatch
        harnessRequire(back.waitForExistence(timeout: 15) && back.isHittable, app, "\(step): the screen has a usable 'Back'")
        back.tap()
        usleep(1_000_000)
    }

    /// Bookmark list rows are merged Compose columns; a row is a hittable element below the top bar and chip.
    private func bookmarkRowCandidates(_ app: XCUIApplication) -> [XCUIElement] {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS '3:16'"))
            .allElementsBoundByIndex.filter { $0.frame.minY > 150 && $0.frame.height > 20 && $0.exists }
    }

    private func bookmarkRow316(_ app: XCUIApplication) -> XCUIElement {
        harnessRequire(waitUntil(timeout: 60) { !self.bookmarkRowCandidates(app).isEmpty }, app,
                       "the Bookmarks list shows the John 3:16 bookmark")
        let rows = bookmarkRowCandidates(app)
        attachText("bookmark-row-labels", rows.map(\.label).joined(separator: "\n---\n"))
        // The outermost element (largest frame) is the clickable row, not its title or content child.
        return rows.max { $0.frame.width * $0.frame.height < $1.frame.width * $1.frame.height } ?? rows[0]
    }

    /// Clicks into a text view / field and types `text`, retrying with a clear when the read-back differs
    /// (a controller-bound Compose field can drop the first key after a focus switch).
    private func typeInto(_ app: XCUIApplication, _ field: XCUIElement, _ text: String) {
        for _ in 0..<3 {
            field.tap()
            _ = waitUntil(timeout: 5) { field.debugDescription.contains("Keyboard Focused") }
            usleep(400_000)
            let current = field.value as? String ?? ""
            if current == text { return }
            if !current.isEmpty {
                field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count + 3))
            }
            field.typeText(text)
            if waitUntil(timeout: 5, { (field.value as? String) == text }) { return }
        }
        harnessRequire(false, app, "the field reads '\(field.value as? String ?? "nil")' after typing '\(text)'")
    }

    // MARK: Files helpers

    /// A cell of the Files picker for `AndBibleDatabaseBackup*.abdb.zip` (the root of the local storage is open).
    private func backupFileCell(_ app: XCUIApplication) -> XCUIElement? {
        let cell = localRoot(app).cells.matching(NSPredicate(
            format: "(label CONTAINS %@ OR identifier CONTAINS %@) AND (label CONTAINS '.abdb.zip' OR identifier CONTAINS '.abdb.zip')",
            backupStem, backupStem)).firstMatch
        return cell.waitForExistence(timeout: 30) ? cell : nil
    }

    /// The listed backup's name when it matches `AndBibleDatabaseBackup*.abdb.zip`; Files may show it with or
    /// without the ".zip" suffix in the cell label, so the accessibility identifier and label are both read.
    private func backupFileName(_ app: XCUIApplication) -> String? {
        guard let cell = backupFileCell(app) else { return nil }
        let pattern = "^" + backupStem + ".*\\.abdb\\.zip"
        for text in [cell.identifier, cell.label] {
            let name = text.components(separatedBy: ",").first ?? text
            if name.range(of: pattern, options: .regularExpression) != nil { return name }
        }
        return nil
    }
}
