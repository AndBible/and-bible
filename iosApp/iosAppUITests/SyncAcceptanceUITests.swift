import XCTest

/// Opt-in, destructive sync acceptance for a controller-owned disposable account and local dataset.
final class SyncAcceptanceUITests: XCTestCase {
    private struct Fixture: Decodable {
        let serverURL: String
        let username: String
        let password: String
        // Reuses controller-provisioned credentials; this mode does not test password replacement.
        let reuseExistingPassword: Bool?
        let trustedCertificateSha256: String?
        let folderPath: String
        let marker: String
        let reference: String
        let versePhrase: String
        let noteAccessibilityLabel: String
    }

    override func setUp() { continueAfterFailure = false }

    /// The shared sync UI helpers (`SyncTestSupport.swift`).
    private var syncUI: SyncHarness { SyncHarness(test: self) }

    private func fixture() throws -> Fixture {
        let env = ProcessInfo.processInfo.environment
        guard env["TASK7_SYNC_ACCEPTANCE"] == "1" else {
            throw XCTSkip("Controller-only sync acceptance is not enabled")
        }
        let data: Data
        if let path = env["TASK7_SYNC_FIXTURE_FILE"] {
            data = try Data(contentsOf: URL(fileURLWithPath: path))
        } else if let json = env["TASK7_SYNC_FIXTURE_JSON"], let bytes = json.data(using: .utf8) {
            data = bytes
        } else {
            throw XCTSkip("A disposable sync fixture must be supplied to the test runner")
        }
        let result = try JSONDecoder().decode(Fixture.self, from: data)
        guard let url = URL(string: result.serverURL),
              ["localhost", "127.0.0.1", "::1"].contains(url.host ?? ""),
              url.scheme == "https",
              url.user == nil, url.password == nil,
              !result.username.isEmpty, !result.password.isEmpty,
              !result.marker.isEmpty, !result.reference.isEmpty, !result.versePhrase.isEmpty,
              !result.noteAccessibilityLabel.isEmpty else {
            throw NSError(domain: "SyncAcceptanceFixture", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "Invalid local disposable sync fixture"])
        }
        if let fingerprint = result.trustedCertificateSha256 {
            guard syncUI.normalizedSha256(fingerprint) != nil, url.host == "localhost" else {
                throw NSError(domain: "SyncAcceptanceFixture", code: 2,
                              userInfo: [NSLocalizedDescriptionKey: "Certificate fixture requires a valid SHA-256 and exact localhost host"])
            }
        }
        return result
    }

    private func launch(settings: Bool, reference: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-task6ResetCrashState", "-task7AndroidDebugSyncNamespace",
                               "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if settings { app.launchArguments += ["-task5Settings"] }
        else { app.launchArguments += ["-openRef", reference] }
        app.launch()
        return app
    }

    /// Controller-owned watcher status; nil when the file is missing or unreadable.
    private func activityStatus(_ path: String) -> String? {
        try? String(contentsOfFile: path, encoding: .utf8)
    }

    /// Exposes the Sign Out row for a disposable fixture whose Bookmarks category is on but whose signed-in
    /// state is not live (so Sign Out is hidden): toggling Documents runs a real sign-in and scan, then the
    /// 'Start syncing documents?' dialog is Cancelled (never OK). Nothing here toggles a category on, edits
    /// credentials or reads a password. The controller's activity file must read exactly "clear" before the
    /// toggle and again after Cancel, proving no database synchronization started meanwhile.
    private func prepareSignOutRow(_ app: XCUIApplication, _ fixture: Fixture) -> Bool {
        let env = ProcessInfo.processInfo.environment
        guard env["TASK7_RESET_SIGNIN_SCAN"] == "1",
              let activityPath = env["TASK7_RESET_ACTIVITY_FILE"], !activityPath.isEmpty else {
            syncUI.require(false, app, "Sign Out row absent; recovery needs TASK7_RESET_SIGNIN_SCAN=1 and TASK7_RESET_ACTIVITY_FILE")
            return false
        }
        // Preconditions are read from visible row summaries; the password row is never inspected.
        let folderSummary = fixture.folderPath.isEmpty ? "Parent folder for sync data (empty = root)" : fixture.folderPath
        let server = syncUI.reveal(app, "Server URL")
        let serverSaved = server.label == "Server URL, \(fixture.serverURL)"
        syncUI.require(serverSaved, app, "Saved Server URL row does not match the fixture")
        guard serverSaved else { return false }
        let folder = syncUI.reveal(app, "Sync folder path")
        let folderSaved = folder.label == "Sync folder path, \(folderSummary)"
        syncUI.require(folderSaved, app, "Saved Sync folder path row does not match the fixture")
        guard folderSaved else { return false }
        let bookmarks = syncUI.reveal(app, "Bookmarks", toggle: true)
        let documents = syncUI.reveal(app, "Documents", toggle: true)
        let preconditions = bookmarks.isEnabled && bookmarks.isSelected && documents.isEnabled && !documents.isSelected
        syncUI.require(preconditions, app, "Recovery needs Bookmarks selected and Documents off")
        guard preconditions else { return false }
        let beforeScan = activityStatus(activityPath) == "clear"
        syncUI.require(beforeScan, app, "Synchronization activity file is not exactly clear before the Documents scan")
        guard beforeScan else { return false }

        documents.tap()
        let startTitle = syncUI.label(app, "Start syncing documents?")
        let certificateTitle = syncUI.label(app, "Untrusted server certificate")
        let firstDecision = NSPredicate { _, _ in startTitle.exists || certificateTitle.exists }
        let decisionShown = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: firstDecision, object: app)], timeout: 90) == .completed
        syncUI.require(decisionShown, app, "Neither the document-sync dialog nor a certificate decision appeared")
        guard decisionShown else { return false }
        if certificateTitle.exists {
            guard syncUI.trustPinnedCertificateIfShown(app, serverURL: fixture.serverURL, sha256: fixture.trustedCertificateSha256) else { return false }
            let afterTrust = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in startTitle.exists }, object: app)],
                timeout: 90) == .completed
            syncUI.require(afterTrust, app, "Document-sync dialog did not appear after certificate trust")
            guard afterTrust else { return false }
        }
        let cancel = app.buttons.matching(NSPredicate(format: "label == %@", "Cancel")).firstMatch
        let cancelReady = cancel.exists && cancel.isEnabled && cancel.isHittable
        syncUI.require(cancelReady, app, "Document-sync dialog Cancel action unavailable")
        guard cancelReady else { return false }
        cancel.tap()
        let dialogClosed = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in !startTitle.exists }, object: app)],
            timeout: 10) == .completed
        syncUI.require(dialogClosed, app, "Document-sync dialog did not close after Cancel")
        guard dialogClosed else { return false }
        let stillOff = documents.exists && !documents.isSelected && bookmarks.exists && bookmarks.isSelected
        syncUI.require(stillOff, app, "Documents or Bookmarks state changed after Cancel")
        guard stillOff else { return false }
        let afterCancel = activityStatus(activityPath) == "clear"
        syncUI.require(afterCancel, app, "Synchronization activity file is not exactly clear after Cancel")
        guard afterCancel else { return false }

        // Back to Settings and reopen in the same process, so the screen reads the signed-in state.
        let back = app.buttons["Back"].firstMatch
        let backReady = back.waitForExistence(timeout: 10) && back.isEnabled && back.isHittable
        syncUI.require(backReady, app, "Back button unavailable on synchronization settings")
        guard backReady else { return false }
        back.tap()
        let backendRow = syncUI.rowButton(app, "Synchronization Backend")
        let settingsRow = syncUI.rowButton(app, "Device synchronization")
        let onSettings = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in settingsRow.exists && !backendRow.exists }, object: app)],
            timeout: 15) == .completed
        syncUI.require(onSettings, app, "Back did not return to Settings")
        return onSettings
    }

    func testConfigureAndObserveFetchChoice() throws {
        let fixture = try fixture()
        guard let completionPath = ProcessInfo.processInfo.environment["TASK7_SYNC_COMPLETION_FILE"],
              !completionPath.isEmpty else {
            throw XCTSkip("Controller-owned synchronization completion file is required")
        }
        let app = launch(settings: true, reference: fixture.reference)
        syncUI.reveal(app, "Device synchronization").tap()
        let backend = syncUI.rowButton(app, "Synchronization Backend")
        let destination = NSPredicate { _, _ in
            app.staticTexts.matching(NSPredicate(format: "label == %@", "Device synchronization")).firstMatch.exists
                && backend.exists && backend.isHittable
        }
        syncUI.require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: destination, object: app)], timeout: 15) == .completed,
                app, "Device synchronization navigation did not reach its title and backend button")
        backend.tap()
        syncUI.require(syncUI.label(app, "WebDAV").waitForExistence(timeout: 10), app, "WebDAV choice missing")
        syncUI.label(app, "WebDAV").tap()
        // Configure a disabled Bookmarks category; toggling it on invokes actual sign-in and sync.
        let bookmarks = app.switches.matching(NSPredicate(format: "label CONTAINS %@", "Bookmarks")).firstMatch
        _ = syncUI.reveal(app, "Bookmarks", toggle: true)
        syncUI.require(bookmarks.waitForExistence(timeout: 10), app, "Bookmarks synchronization switch missing")
        // Compose 1.11.1 exposes toggle state through the selected trait, not a numeric value.
        syncUI.require(bookmarks.exists && bookmarks.isEnabled && bookmarks.isHittable && !bookmarks.isSelected, app,
                "Disposable fixture must start with Bookmarks synchronization disabled")
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        syncUI.edit(app, "Server URL", fixture.serverURL)
        // A closed editor does not prove validation succeeded or that the URL was saved.
        let settingsReady = NSPredicate { _, _ in
            let serverRow = self.syncUI.rowButton(app, "Server URL")
            return serverRow.exists && serverRow.isHittable && !app.buttons["OK"].firstMatch.exists
        }
        let urlAccepted = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: settingsReady, object: app)], timeout: 10) == .completed
        syncUI.require(urlAccepted, app, "Server URL validation did not return to synchronization settings")
        guard urlAccepted else { return }
        syncUI.reveal(app, "Server URL").tap()
        let persistedURL = app.textViews.firstMatch
        let readbackReady = persistedURL.waitForExistence(timeout: 10)
            && app.textViews.count == 1
            && syncUI.label(app, "Server URL").exists
            && app.buttons["Cancel"].firstMatch.exists
        syncUI.require(readbackReady, app, "Server URL persisted-value editor missing")
        guard readbackReady else { return }
        let urlMatches = (persistedURL.value as? String) == fixture.serverURL
        syncUI.require(urlMatches, app, "Persisted Server URL did not exactly match fixture")
        guard urlMatches else { return }
        let cancel = app.buttons["Cancel"].firstMatch
        syncUI.require(cancel.isEnabled && cancel.isHittable, app, "Server URL readback Cancel action unavailable")
        guard cancel.isEnabled && cancel.isHittable else { return }
        cancel.tap()
        let readbackClosed = NSPredicate { _, _ in !persistedURL.exists }
        let didCloseReadback = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: readbackClosed, object: app)], timeout: 10) == .completed
        syncUI.require(didCloseReadback, app, "Server URL readback editor did not close after Cancel")
        guard didCloseReadback else { return }
        syncUI.edit(app, "User name", fixture.username)
        // Reuse requires prior controller provisioning; real sign-in below still validates credentials.
        // It deliberately does not exercise password entry or replacement, or read the saved secret.
        if !(fixture.reuseExistingPassword ?? false) {
            syncUI.edit(app, "Password", fixture.password, sensitive: true)
        }
        syncUI.edit(app, "Sync folder path", fixture.folderPath)
        _ = syncUI.reveal(app, "Bookmarks", toggle: true)
        bookmarks.tap()

        let fetch = syncUI.label(app, "Copy from Cloud to this device")
        let certificateTitle = app.staticTexts.matching(
            NSPredicate(format: "label == %@", "Untrusted server certificate")).firstMatch
        let signInChoice = NSPredicate { _, _ in fetch.exists || certificateTitle.exists }
        let choiceReady = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: signInChoice, object: app)], timeout: 90) == .completed
        syncUI.require(choiceReady, app, "Neither fetch choice nor certificate decision appeared")
        guard choiceReady else { return }
        guard syncUI.trustPinnedCertificateIfShown(app, serverURL: fixture.serverURL, sha256: fixture.trustedCertificateSha256) else { return }
        syncUI.require(fetch.waitForExistence(timeout: 90), app,
                "Seeded Android cloud was not offered for fetch; empty remote auto-creates and is not fetch evidence")
        fetch.tap()
        let okay = app.buttons["OK"].firstMatch
        syncUI.require(okay.waitForExistence(timeout: 10), app, "Fetch replacement confirmation missing")
        okay.tap()
        let confirmed = NSPredicate { _, _ in !okay.exists && !fetch.exists }
        syncUI.require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: confirmed, object: app)], timeout: 10) == .completed,
                app, "Fetch confirmation did not close")
        syncUI.shot(app, "task7-sync-fetch-choice-confirmed")
        // Sign-in may await the fetch dialogs; observe ON only after handling them, not before.
        let enabled = NSPredicate { _, _ in bookmarks.exists && bookmarks.isSelected }
        syncUI.require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: enabled, object: app)], timeout: 15) == .completed,
                app, "Bookmarks synchronization did not become selected after fetch confirmation")
        // The controller writes a terminal status only after observing this app run's actual sync logs.
        // Selection is not completion; missing, unreadable or unknown file contents never signal success.
        var completionStatus: String?
        let completion = NSPredicate { _, _ in
            completionStatus = try? String(contentsOfFile: completionPath, encoding: .utf8)
            return completionStatus == "complete" || completionStatus == "failed"
        }
        let completionObserved = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: completion, object: app)], timeout: 180) == .completed
        syncUI.require(completionObserved && completionStatus == "complete", app,
                "Controller did not confirm successful synchronization completion for this app run")
        // Actual fetched-note acceptance remains a separate test after this completion gate.
    }

    /// Disposable fixtures only: signs out through the real UI, clearing sync state, not user data.
    func testResetDisposableSyncViaSignOut() throws {
        let fixture = try fixture()
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TASK7_RESET_SYNC"] == "1",
                          "Controller-only disposable synchronization reset is not enabled")
        let app = launch(settings: true, reference: fixture.reference)
        guard syncUI.openDeviceSynchronization(app, "Reset did not reach the real synchronization settings destination") else { return }
        // The rendered row merges its title and summary into the Button label.
        let signOut = syncUI.rowButton(app, "Sign Out")
        let signOutTitle = app.staticTexts.matching(NSPredicate(format: "label == %@", "Sign Out")).firstMatch
        if !signOut.waitForExistence(timeout: 10) {
            // Opt-in recovery only (TASK7_RESET_SIGNIN_SCAN=1); otherwise this fails closed inside the helper.
            guard prepareSignOutRow(app, fixture) else { return }
            guard syncUI.openDeviceSynchronization(
                app, "Recovery did not reopen the real synchronization settings destination") else { return }
        }
        let signOutReady = signOut.waitForExistence(timeout: 10)
            && signOutTitle.exists && signOut.isEnabled && signOut.isHittable
        syncUI.require(signOutReady, app, "Existing Sign Out row unavailable; no category will be enabled to expose it")
        guard signOutReady else { return }
        syncUI.waitForStableFrame(signOut, app, "Sign Out")
        signOut.tap()
        let confirmation = app.staticTexts.matching(NSPredicate(
            format: "label == %@", "Are you sure that you want to sign out and reset synchronization?")).firstMatch
        let okay = app.buttons.matching(NSPredicate(format: "label == %@", "OK")).firstMatch
        let confirmationReady = confirmation.waitForExistence(timeout: 10)
            && okay.exists && okay.isEnabled && okay.isHittable
        syncUI.require(confirmationReady, app, "Exact synchronization sign-out confirmation or OK action missing")
        guard confirmationReady else { return }
        okay.tap()
        let confirmationClosed = NSPredicate { _, _ in !confirmation.exists }
        let closed = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: confirmationClosed, object: app)], timeout: 10) == .completed
        syncUI.require(closed, app, "Synchronization sign-out confirmation did not close")
        guard closed else { return }
        let bookmarks = syncUI.reveal(app, "Bookmarks", toggle: true)
        let reset = NSPredicate { _, _ in
            !confirmation.exists && bookmarks.exists && bookmarks.isEnabled
                && bookmarks.isHittable && !bookmarks.isSelected
        }
        let resetObserved = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: reset, object: app)], timeout: 30) == .completed
        syncUI.require(resetObserved, app, "Bookmarks did not become unselected after synchronization sign-out")
        guard resetObserved else { return }
        let documents = syncUI.reveal(app, "Documents", toggle: true)
        let documentsOff = documents.exists && documents.isEnabled && !documents.isSelected && !bookmarks.isSelected
        syncUI.require(documentsOff, app, "Bookmarks and Documents are not both off after synchronization sign-out")
        guard documentsOff else { return }
        // Signed out, the credential rows must be editable again. Only enablement is read, never a value.
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        for title in ["Server URL", "User name", "Password", "Sync folder path"] {
            let row = syncUI.reveal(app, title)
            syncUI.require(row.isEnabled, app, "Credential row is not editable after sign-out: \(title)")
        }
        syncUI.shot(app, "task7-disposable-sync-signed-out")
        XCUIDevice.shared.press(.home)
        syncUI.require(app.wait(for: .runningBackground, timeout: 10), app, "Signed-out app did not background")
    }

    /// Controller-only document provisioning through Welcome; never resets sync or bookmark data.
    func testProvisionBibleWithoutReset() throws {
        let fixture = try fixture()
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TASK7_PROVISION_BIBLE"] == "1",
                          "Controller-only Bible provisioning is not enabled")
        var app = launch(settings: false, reference: fixture.reference)
        let welcome = app.descendants(matching: .any).matching(identifier: "welcome-screen").firstMatch
        let reading = app.descendants(matching: .any).matching(identifier: "reading-host").firstMatch
        let startup = NSPredicate { _, _ in welcome.exists || reading.exists }
        let started = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: startup, object: app)], timeout: 60) == .completed
        syncUI.require(started, app, "Provisioning launch reached neither Welcome nor reading")
        guard started else { return }
        if welcome.exists {
            let easy = app.descendants(matching: .any).matching(identifier: "welcome-easy-start").firstMatch
            let easyReady = easy.waitForExistence(timeout: 10) && easy.isEnabled && easy.isHittable
            syncUI.require(easyReady, app, "Welcome easy-start action unavailable")
            guard easyReady else { return }
            easy.tap()
            let yes = app.buttons.matching(NSPredicate(format: "label == %@", "Yes")).firstMatch
            let okay = app.buttons.matching(NSPredicate(format: "label == %@", "OK")).firstMatch
            let downloadEntry = NSPredicate { _, _ in yes.exists || okay.exists }
            let enteredDownload = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: downloadEntry, object: app)], timeout: 30) == .completed
            syncUI.require(enteredDownload, app, "Download confirmation or first-download OK gate missing")
            guard enteredDownload else { return }
            if yes.exists {
                syncUI.require(yes.isEnabled && yes.isHittable, app, "Download confirmation Yes unavailable")
                guard yes.isEnabled && yes.isHittable else { return }
                yes.tap()
            }
            let installed = NSPredicate { _, _ in okay.exists && okay.isEnabled && okay.isHittable }
            let installedBible = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: installed, object: app)], timeout: 180) == .completed
            syncUI.require(installedBible, app, "First-download OK did not enable after Bible installation")
            guard installedBible else { return }
            okay.tap()
            let reachedReading = reading.waitForExistence(timeout: 60)
            syncUI.require(reachedReading, app, "Provisioning did not reach reading after download")
            guard reachedReading else { return }
            // Startup may consume openRef before documents exist. Relaunch without resetting any data.
            XCUIDevice.shared.press(.home)
            let backgrounded = app.wait(for: .runningBackground, timeout: 10)
            syncUI.require(backgrounded, app, "Provisioned reading app did not background before relaunch")
            guard backgrounded else { return }
            app.terminate()
            app = launch(settings: false, reference: fixture.reference)
        }
        let finalReading = app.descendants(matching: .any).matching(identifier: "reading-host").firstMatch
        let readingReady = finalReading.waitForExistence(timeout: 60)
        syncUI.require(readingReady, app, "Provisioned app did not reach reading")
        guard readingReady else { return }
        let verse = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.versePhrase)).firstMatch
        let passageReady = verse.waitForExistence(timeout: 60)
        syncUI.require(passageReady, app, "Provisioned Bible did not render the fixture passage")
        guard passageReady else { return }
        syncUI.shot(app, "task7-bible-provisioned-without-reset")
        XCUIDevice.shared.press(.home)
        syncUI.require(app.wait(for: .runningBackground, timeout: 10), app, "Provisioned app did not background")
    }

    /// The in-verse note icon is not exposed to accessibility, so the tap is calibrated on the verse frame
    /// (observed KJV, iPhone 16 layout only) and verified by the modal's real marker.
    func testReadingShowsFetchedAndroidNoteByIcon() throws {
        let fixture = try fixture()
        let app = launch(settings: false, reference: fixture.reference)
        let reading = app.descendants(matching: .any).matching(identifier: "reading-host").firstMatch
        syncUI.require(reading.waitForExistence(timeout: 60), app, "Reading requires an installed Bible; no download/reset is performed")
        let verse = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.versePhrase)).firstMatch
        syncUI.require(verse.waitForExistence(timeout: 60), app, "Fixture passage did not render")
        syncUI.require(verse.isHittable, app, "Fixture verse is not hittable")
        let frame = verse.frame
        syncUI.shot(app, "task7-ios-note-icon-before-tap")
        let origin = app.coordinate(withNormalizedOffset: .zero)
        // Calibrated to the observed KJV iPhone 16 layout: the note icon follows the wrapped last line.
        origin.withOffset(CGVector(dx: frame.minX + 80, dy: frame.maxY - 15)).tap()
        let marker = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.marker)).firstMatch
        syncUI.require(marker.waitForExistence(timeout: 30), app, "Exact Android marker missing from the real in-verse note modal")
        syncUI.shot(app, "task7-ios-fetched-android-note")
        XCUIDevice.shared.press(.home)
        syncUI.require(app.wait(for: .runningBackground, timeout: 10), app, "Reading app did not background")
        app.terminate()
    }

    /// Run after controller-observed sync completion, with a real Bible and the Android note on the fixture reference.
    func testReadingShowsFetchedAndroidNote() throws {
        let fixture = try fixture()
        let app = launch(settings: false, reference: fixture.reference)
        let reading = app.descendants(matching: .any).matching(identifier: "reading-host").firstMatch
        syncUI.require(reading.waitForExistence(timeout: 60), app, "Reading requires an installed Bible; no download/reset is performed")
        let verse = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.versePhrase)).firstMatch
        syncUI.require(verse.waitForExistence(timeout: 60), app, "Fixture passage did not render")
        // The controller supplies the observed in-verse note element label from this runtime's AX tree.
        let note = app.webViews.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", fixture.noteAccessibilityLabel)).firstMatch
        syncUI.require(note.waitForExistence(timeout: 30) && note.isHittable, app,
                "Observed in-verse note selector missing; inspect AX rather than substituting a drawer route")
        note.tap()
        let marker = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.marker)).firstMatch
        syncUI.require(marker.waitForExistence(timeout: 30), app, "Exact Android marker missing from the real in-verse note modal")
        syncUI.shot(app, "task7-ios-fetched-android-note")
        XCUIDevice.shared.press(.home)
        syncUI.require(app.wait(for: .runningBackground, timeout: 10), app, "Reading app did not background")
        app.terminate()
    }
}
