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
            guard normalizedSha256(fingerprint) != nil, url.host == "localhost" else {
                throw NSError(domain: "SyncAcceptanceFixture", code: 2,
                              userInfo: [NSLocalizedDescriptionKey: "Certificate fixture requires a valid SHA-256 and exact localhost host"])
            }
        }
        return result
    }

    private func normalizedSha256(_ value: String) -> String? {
        let pattern = "^(?:[0-9A-Fa-f]{64}|(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})$"
        guard value.range(of: pattern, options: .regularExpression) == (value.startIndex..<value.endIndex) else { return nil }
        return value.replacingOccurrences(of: ":", with: "").uppercased()
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

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }

    private func tree(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(string: app.debugDescription)
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }

    private func require(_ condition: Bool, _ app: XCUIApplication, _ message: String,
                         sensitive: Bool = false, file: StaticString = #filePath, line: UInt = #line) {
        // A password editor must never be copied into a hierarchy attachment.
        if !condition && !sensitive { tree(app, "sync-failure-hierarchy"); shot(app, "sync-failure") }
        XCTAssertTrue(condition, message, file: file, line: line)
    }

    private func label(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    private func rowButton(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", text)).firstMatch
    }

    // A hittable row can still move during residual scrolling and cancel its tap gesture.
    private func waitForStableFrame(_ target: XCUIElement, _ app: XCUIApplication, _ text: String) {
        var stableFrame: CGRect?
        var stableSince: TimeInterval?
        let frameStable = NSPredicate { _, _ in
            guard target.exists && target.isHittable else {
                stableFrame = nil
                stableSince = nil
                return false
            }
            let frame = target.frame
            let now = ProcessInfo.processInfo.systemUptime
            if let baseline = stableFrame, let since = stableSince,
               abs(frame.origin.x - baseline.origin.x) <= 0.5,
               abs(frame.origin.y - baseline.origin.y) <= 0.5,
               abs(frame.size.width - baseline.size.width) <= 0.5,
               abs(frame.size.height - baseline.size.height) <= 0.5 {
                return now - since >= 1.0
            }
            stableFrame = frame
            stableSince = now
            return false
        }
        require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: frameStable, object: app)], timeout: 15) == .completed,
                app, "Settings row did not remain frame-stable and hittable for one second: \(text)")
    }

    private func reveal(_ app: XCUIApplication, _ text: String, toggle: Bool = false) -> XCUIElement {
        let target = toggle
            ? app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", text)).firstMatch
            : rowButton(app, text)
        for _ in 0..<10 {
            if target.exists && target.isHittable {
                waitForStableFrame(target, app, text)
                return target
            }
            app.swipeUp()
        }
        require(target.exists && target.isHittable, app, "Required settings row not reachable: \(text)")
        waitForStableFrame(target, app, text)
        return target
    }

    /// Handles the untrusted-certificate decision only when it is shown, and only for the pinned local fixture.
    /// Returns false (after recording a failure) when the dialog is unexpected or does not match exactly;
    /// returns true when no dialog is shown or the verified certificate was trusted once.
    private func trustPinnedCertificateIfShown(_ app: XCUIApplication, _ fixture: Fixture) -> Bool {
        let certificateTitle = app.staticTexts.matching(
            NSPredicate(format: "label == %@", "Untrusted server certificate")).firstMatch
        guard certificateTitle.exists else { return true }
        let body = app.staticTexts.matching(NSPredicate(
            format: "label BEGINSWITH %@",
            "The certificate of localhost is not trusted by Android.")).firstMatch
        let trust = app.buttons.matching(NSPredicate(
            format: "label == %@", "Trust this certificate")).firstMatch
        guard let suppliedFingerprint = fixture.trustedCertificateSha256,
              let expectedFingerprint = normalizedSha256(suppliedFingerprint),
              URL(string: fixture.serverURL)?.host == "localhost" else {
            require(false, app, "Certificate trust requires an explicit localhost fixture fingerprint")
            return false
        }
        let lines = body.exists ? body.label.components(separatedBy: .newlines) : []
        let hashes = lines.filter { $0.hasPrefix("SHA-256: ") }
        let certificateMatches = lines.contains("Subject: localhost")
            && lines.contains("Issuer: CN=localhost, O=AndBible disposable test")
            && hashes.count == 1
            && normalizedSha256(String(hashes[0].dropFirst("SHA-256: ".count))) == expectedFingerprint
            && trust.exists && trust.isEnabled && trust.isHittable
        require(certificateMatches, app, "Certificate identity or full SHA-256 did not match localhost fixture")
        guard certificateMatches else { return false }
        shot(app, "task7-localhost-certificate-verified")
        trust.tap()
        return true
    }

    /// Opens the real Device synchronization destination from Settings and waits for its title and backend row.
    private func openDeviceSynchronization(_ app: XCUIApplication, _ failure: String) -> Bool {
        reveal(app, "Device synchronization").tap()
        let backend = rowButton(app, "Synchronization Backend")
        let destination = NSPredicate { _, _ in
            app.staticTexts.matching(NSPredicate(format: "label == %@", "Device synchronization")).firstMatch.exists
                && backend.exists && backend.isHittable
        }
        let arrived = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: destination, object: app)], timeout: 15) == .completed
        require(arrived, app, failure)
        return arrived
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
            require(false, app, "Sign Out row absent; recovery needs TASK7_RESET_SIGNIN_SCAN=1 and TASK7_RESET_ACTIVITY_FILE")
            return false
        }
        // Preconditions are read from visible row summaries; the password row is never inspected.
        let folderSummary = fixture.folderPath.isEmpty ? "Parent folder for sync data (empty = root)" : fixture.folderPath
        let server = reveal(app, "Server URL")
        let serverSaved = server.label == "Server URL, \(fixture.serverURL)"
        require(serverSaved, app, "Saved Server URL row does not match the fixture")
        guard serverSaved else { return false }
        let folder = reveal(app, "Sync folder path")
        let folderSaved = folder.label == "Sync folder path, \(folderSummary)"
        require(folderSaved, app, "Saved Sync folder path row does not match the fixture")
        guard folderSaved else { return false }
        let bookmarks = reveal(app, "Bookmarks", toggle: true)
        let documents = reveal(app, "Documents", toggle: true)
        let preconditions = bookmarks.isEnabled && bookmarks.isSelected && documents.isEnabled && !documents.isSelected
        require(preconditions, app, "Recovery needs Bookmarks selected and Documents off")
        guard preconditions else { return false }
        let beforeScan = activityStatus(activityPath) == "clear"
        require(beforeScan, app, "Synchronization activity file is not exactly clear before the Documents scan")
        guard beforeScan else { return false }

        documents.tap()
        let startTitle = label(app, "Start syncing documents?")
        let certificateTitle = label(app, "Untrusted server certificate")
        let firstDecision = NSPredicate { _, _ in startTitle.exists || certificateTitle.exists }
        let decisionShown = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: firstDecision, object: app)], timeout: 90) == .completed
        require(decisionShown, app, "Neither the document-sync dialog nor a certificate decision appeared")
        guard decisionShown else { return false }
        if certificateTitle.exists {
            guard trustPinnedCertificateIfShown(app, fixture) else { return false }
            let afterTrust = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in startTitle.exists }, object: app)],
                timeout: 90) == .completed
            require(afterTrust, app, "Document-sync dialog did not appear after certificate trust")
            guard afterTrust else { return false }
        }
        let cancel = app.buttons.matching(NSPredicate(format: "label == %@", "Cancel")).firstMatch
        let cancelReady = cancel.exists && cancel.isEnabled && cancel.isHittable
        require(cancelReady, app, "Document-sync dialog Cancel action unavailable")
        guard cancelReady else { return false }
        cancel.tap()
        let dialogClosed = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in !startTitle.exists }, object: app)],
            timeout: 10) == .completed
        require(dialogClosed, app, "Document-sync dialog did not close after Cancel")
        guard dialogClosed else { return false }
        let stillOff = documents.exists && !documents.isSelected && bookmarks.exists && bookmarks.isSelected
        require(stillOff, app, "Documents or Bookmarks state changed after Cancel")
        guard stillOff else { return false }
        let afterCancel = activityStatus(activityPath) == "clear"
        require(afterCancel, app, "Synchronization activity file is not exactly clear after Cancel")
        guard afterCancel else { return false }

        // Back to Settings and reopen in the same process, so the screen reads the signed-in state.
        let back = app.buttons["Back"].firstMatch
        let backReady = back.waitForExistence(timeout: 10) && back.isEnabled && back.isHittable
        require(backReady, app, "Back button unavailable on synchronization settings")
        guard backReady else { return false }
        back.tap()
        let backendRow = rowButton(app, "Synchronization Backend")
        let settingsRow = rowButton(app, "Device synchronization")
        let onSettings = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in settingsRow.exists && !backendRow.exists }, object: app)],
            timeout: 15) == .completed
        require(onSettings, app, "Back did not return to Settings")
        return onSettings
    }

    private func edit(_ app: XCUIApplication, _ title: String, _ value: String, sensitive: Bool = false) {
        reveal(app, title).tap()
        require(label(app, title).waitForExistence(timeout: 10) && app.buttons["Apply"].firstMatch.exists, app,
                "Settings editor title or Apply action missing", sensitive: sensitive)
        // Compose exposes EditableText as TextView, including password inputs with secure IME traits.
        let editors = app.textViews
        let field = editors.firstMatch
        require(field.waitForExistence(timeout: 10), app, "Settings text editor missing", sensitive: sensitive)
        require(editors.count == 1 && field.isEnabled && field.isHittable, app,
                "Expected one enabled, hittable settings TextView editor", sensitive: sensitive)
        if !sensitive && (field.value as? String) == value {
            let cancel = app.buttons["Cancel"].firstMatch
            let canCancel = cancel.waitForExistence(timeout: 5) && cancel.isEnabled && cancel.isHittable
            require(canCancel, app, "Unchanged settings editor Cancel action unavailable")
            guard canCancel else { return }
            cancel.tap()
            let closed = NSPredicate { _, _ in !field.exists }
            let didClose = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: closed, object: app)], timeout: 10) == .completed
            require(didClose, app, "Unchanged settings editor did not close after Cancel")
            return
        }
        // Initial selection does not request focus, and a focus tap can collapse that selection.
        field.tap()
        let focused = NSPredicate(format: "hasKeyboardFocus == true")
        let focusReady = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: focused, object: field)], timeout: 10) == .completed
        require(focusReady, app, "Settings text editor did not acquire keyboard focus", sensitive: sensitive)
        guard focusReady else { return }

        // Compose 1.11.1 omits accessibilityValue for an empty EditableText.
        func isEmpty() -> Bool { field.value == nil || (field.value as? String) == "" }
        if !isEmpty() {
            // Select All is available for password inputs too; never use Copy/Cut or the clipboard.
            field.press(forDuration: 1.0)
            let selectAll = app.menuItems["Select All"].firstMatch
            let selectionReady = selectAll.waitForExistence(timeout: 5) && selectAll.isHittable
            require(selectionReady, app, "Settings editor Select All action missing", sensitive: sensitive)
            guard selectionReady else { return }
            selectAll.tap()
            field.typeText(XCUIKeyboardKey.delete.rawValue)
        }
        let cleared = isEmpty()
        require(cleared, app, "Settings editor was not fully cleared", sensitive: sensitive)
        guard cleared else { return }
        if !value.isEmpty { field.typeText(value) }
        if !sensitive {
            require((field.value as? String) == value, app, "Settings editor value did not match fixture")
        }
        let apply = app.buttons["Apply"].firstMatch
        require(apply.waitForExistence(timeout: 5), app, "Settings Apply button missing", sensitive: sensitive)
        apply.tap()
        let closed = NSPredicate { _, _ in !field.exists }
        require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: closed, object: app)], timeout: 10) == .completed,
                app, "Settings editor did not close", sensitive: sensitive)
    }

    func testConfigureAndObserveFetchChoice() throws {
        let fixture = try fixture()
        guard let completionPath = ProcessInfo.processInfo.environment["TASK7_SYNC_COMPLETION_FILE"],
              !completionPath.isEmpty else {
            throw XCTSkip("Controller-owned synchronization completion file is required")
        }
        let app = launch(settings: true, reference: fixture.reference)
        reveal(app, "Device synchronization").tap()
        let backend = rowButton(app, "Synchronization Backend")
        let destination = NSPredicate { _, _ in
            app.staticTexts.matching(NSPredicate(format: "label == %@", "Device synchronization")).firstMatch.exists
                && backend.exists && backend.isHittable
        }
        require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: destination, object: app)], timeout: 15) == .completed,
                app, "Device synchronization navigation did not reach its title and backend button")
        backend.tap()
        require(label(app, "WebDAV").waitForExistence(timeout: 10), app, "WebDAV choice missing")
        label(app, "WebDAV").tap()
        // Configure a disabled Bookmarks category; toggling it on invokes actual sign-in and sync.
        let bookmarks = app.switches.matching(NSPredicate(format: "label CONTAINS %@", "Bookmarks")).firstMatch
        _ = reveal(app, "Bookmarks", toggle: true)
        require(bookmarks.waitForExistence(timeout: 10), app, "Bookmarks synchronization switch missing")
        // Compose 1.11.1 exposes toggle state through the selected trait, not a numeric value.
        require(bookmarks.exists && bookmarks.isEnabled && bookmarks.isHittable && !bookmarks.isSelected, app,
                "Disposable fixture must start with Bookmarks synchronization disabled")
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        edit(app, "Server URL", fixture.serverURL)
        // A closed editor does not prove validation succeeded or that the URL was saved.
        let settingsReady = NSPredicate { _, _ in
            let serverRow = self.rowButton(app, "Server URL")
            return serverRow.exists && serverRow.isHittable && !app.buttons["OK"].firstMatch.exists
        }
        let urlAccepted = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: settingsReady, object: app)], timeout: 10) == .completed
        require(urlAccepted, app, "Server URL validation did not return to synchronization settings")
        guard urlAccepted else { return }
        reveal(app, "Server URL").tap()
        let persistedURL = app.textViews.firstMatch
        let readbackReady = persistedURL.waitForExistence(timeout: 10)
            && app.textViews.count == 1
            && label(app, "Server URL").exists
            && app.buttons["Cancel"].firstMatch.exists
        require(readbackReady, app, "Server URL persisted-value editor missing")
        guard readbackReady else { return }
        let urlMatches = (persistedURL.value as? String) == fixture.serverURL
        require(urlMatches, app, "Persisted Server URL did not exactly match fixture")
        guard urlMatches else { return }
        let cancel = app.buttons["Cancel"].firstMatch
        require(cancel.isEnabled && cancel.isHittable, app, "Server URL readback Cancel action unavailable")
        guard cancel.isEnabled && cancel.isHittable else { return }
        cancel.tap()
        let readbackClosed = NSPredicate { _, _ in !persistedURL.exists }
        let didCloseReadback = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: readbackClosed, object: app)], timeout: 10) == .completed
        require(didCloseReadback, app, "Server URL readback editor did not close after Cancel")
        guard didCloseReadback else { return }
        edit(app, "User name", fixture.username)
        // Reuse requires prior controller provisioning; real sign-in below still validates credentials.
        // It deliberately does not exercise password entry or replacement, or read the saved secret.
        if !(fixture.reuseExistingPassword ?? false) {
            edit(app, "Password", fixture.password, sensitive: true)
        }
        edit(app, "Sync folder path", fixture.folderPath)
        _ = reveal(app, "Bookmarks", toggle: true)
        bookmarks.tap()

        let fetch = label(app, "Copy from Cloud to this device")
        let certificateTitle = app.staticTexts.matching(
            NSPredicate(format: "label == %@", "Untrusted server certificate")).firstMatch
        let signInChoice = NSPredicate { _, _ in fetch.exists || certificateTitle.exists }
        let choiceReady = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: signInChoice, object: app)], timeout: 90) == .completed
        require(choiceReady, app, "Neither fetch choice nor certificate decision appeared")
        guard choiceReady else { return }
        guard trustPinnedCertificateIfShown(app, fixture) else { return }
        require(fetch.waitForExistence(timeout: 90), app,
                "Seeded Android cloud was not offered for fetch; empty remote auto-creates and is not fetch evidence")
        fetch.tap()
        let okay = app.buttons["OK"].firstMatch
        require(okay.waitForExistence(timeout: 10), app, "Fetch replacement confirmation missing")
        okay.tap()
        let confirmed = NSPredicate { _, _ in !okay.exists && !fetch.exists }
        require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: confirmed, object: app)], timeout: 10) == .completed,
                app, "Fetch confirmation did not close")
        shot(app, "task7-sync-fetch-choice-confirmed")
        // Sign-in may await the fetch dialogs; observe ON only after handling them, not before.
        let enabled = NSPredicate { _, _ in bookmarks.exists && bookmarks.isSelected }
        require(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: enabled, object: app)], timeout: 15) == .completed,
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
        require(completionObserved && completionStatus == "complete", app,
                "Controller did not confirm successful synchronization completion for this app run")
        // Actual fetched-note acceptance remains a separate test after this completion gate.
    }

    /// Disposable fixtures only: signs out through the real UI, clearing sync state, not user data.
    func testResetDisposableSyncViaSignOut() throws {
        let fixture = try fixture()
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TASK7_RESET_SYNC"] == "1",
                          "Controller-only disposable synchronization reset is not enabled")
        let app = launch(settings: true, reference: fixture.reference)
        guard openDeviceSynchronization(app, "Reset did not reach the real synchronization settings destination") else { return }
        // The rendered row merges its title and summary into the Button label.
        let signOut = rowButton(app, "Sign Out")
        let signOutTitle = app.staticTexts.matching(NSPredicate(format: "label == %@", "Sign Out")).firstMatch
        if !signOut.waitForExistence(timeout: 10) {
            // Opt-in recovery only (TASK7_RESET_SIGNIN_SCAN=1); otherwise this fails closed inside the helper.
            guard prepareSignOutRow(app, fixture) else { return }
            guard openDeviceSynchronization(
                app, "Recovery did not reopen the real synchronization settings destination") else { return }
        }
        let signOutReady = signOut.waitForExistence(timeout: 10)
            && signOutTitle.exists && signOut.isEnabled && signOut.isHittable
        require(signOutReady, app, "Existing Sign Out row unavailable; no category will be enabled to expose it")
        guard signOutReady else { return }
        waitForStableFrame(signOut, app, "Sign Out")
        signOut.tap()
        let confirmation = app.staticTexts.matching(NSPredicate(
            format: "label == %@", "Are you sure that you want to sign out and reset synchronization?")).firstMatch
        let okay = app.buttons.matching(NSPredicate(format: "label == %@", "OK")).firstMatch
        let confirmationReady = confirmation.waitForExistence(timeout: 10)
            && okay.exists && okay.isEnabled && okay.isHittable
        require(confirmationReady, app, "Exact synchronization sign-out confirmation or OK action missing")
        guard confirmationReady else { return }
        okay.tap()
        let confirmationClosed = NSPredicate { _, _ in !confirmation.exists }
        let closed = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: confirmationClosed, object: app)], timeout: 10) == .completed
        require(closed, app, "Synchronization sign-out confirmation did not close")
        guard closed else { return }
        let bookmarks = reveal(app, "Bookmarks", toggle: true)
        let reset = NSPredicate { _, _ in
            !confirmation.exists && bookmarks.exists && bookmarks.isEnabled
                && bookmarks.isHittable && !bookmarks.isSelected
        }
        let resetObserved = XCTWaiter.wait(
            for: [XCTNSPredicateExpectation(predicate: reset, object: app)], timeout: 30) == .completed
        require(resetObserved, app, "Bookmarks did not become unselected after synchronization sign-out")
        guard resetObserved else { return }
        let documents = reveal(app, "Documents", toggle: true)
        let documentsOff = documents.exists && documents.isEnabled && !documents.isSelected && !bookmarks.isSelected
        require(documentsOff, app, "Bookmarks and Documents are not both off after synchronization sign-out")
        guard documentsOff else { return }
        // Signed out, the credential rows must be editable again. Only enablement is read, never a value.
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        for title in ["Server URL", "User name", "Password", "Sync folder path"] {
            let row = reveal(app, title)
            require(row.isEnabled, app, "Credential row is not editable after sign-out: \(title)")
        }
        shot(app, "task7-disposable-sync-signed-out")
        XCUIDevice.shared.press(.home)
        require(app.wait(for: .runningBackground, timeout: 10), app, "Signed-out app did not background")
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
        require(started, app, "Provisioning launch reached neither Welcome nor reading")
        guard started else { return }
        if welcome.exists {
            let easy = app.descendants(matching: .any).matching(identifier: "welcome-easy-start").firstMatch
            let easyReady = easy.waitForExistence(timeout: 10) && easy.isEnabled && easy.isHittable
            require(easyReady, app, "Welcome easy-start action unavailable")
            guard easyReady else { return }
            easy.tap()
            let yes = app.buttons.matching(NSPredicate(format: "label == %@", "Yes")).firstMatch
            let okay = app.buttons.matching(NSPredicate(format: "label == %@", "OK")).firstMatch
            let downloadEntry = NSPredicate { _, _ in yes.exists || okay.exists }
            let enteredDownload = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: downloadEntry, object: app)], timeout: 30) == .completed
            require(enteredDownload, app, "Download confirmation or first-download OK gate missing")
            guard enteredDownload else { return }
            if yes.exists {
                require(yes.isEnabled && yes.isHittable, app, "Download confirmation Yes unavailable")
                guard yes.isEnabled && yes.isHittable else { return }
                yes.tap()
            }
            let installed = NSPredicate { _, _ in okay.exists && okay.isEnabled && okay.isHittable }
            let installedBible = XCTWaiter.wait(
                for: [XCTNSPredicateExpectation(predicate: installed, object: app)], timeout: 180) == .completed
            require(installedBible, app, "First-download OK did not enable after Bible installation")
            guard installedBible else { return }
            okay.tap()
            let reachedReading = reading.waitForExistence(timeout: 60)
            require(reachedReading, app, "Provisioning did not reach reading after download")
            guard reachedReading else { return }
            // Startup may consume openRef before documents exist. Relaunch without resetting any data.
            XCUIDevice.shared.press(.home)
            let backgrounded = app.wait(for: .runningBackground, timeout: 10)
            require(backgrounded, app, "Provisioned reading app did not background before relaunch")
            guard backgrounded else { return }
            app.terminate()
            app = launch(settings: false, reference: fixture.reference)
        }
        let finalReading = app.descendants(matching: .any).matching(identifier: "reading-host").firstMatch
        let readingReady = finalReading.waitForExistence(timeout: 60)
        require(readingReady, app, "Provisioned app did not reach reading")
        guard readingReady else { return }
        let verse = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.versePhrase)).firstMatch
        let passageReady = verse.waitForExistence(timeout: 60)
        require(passageReady, app, "Provisioned Bible did not render the fixture passage")
        guard passageReady else { return }
        shot(app, "task7-bible-provisioned-without-reset")
        XCUIDevice.shared.press(.home)
        require(app.wait(for: .runningBackground, timeout: 10), app, "Provisioned app did not background")
    }

    /// The in-verse note icon is not exposed to accessibility, so the tap is calibrated on the verse frame
    /// (observed KJV, iPhone 16 layout only) and verified by the modal's real marker.
    func testReadingShowsFetchedAndroidNoteByIcon() throws {
        let fixture = try fixture()
        let app = launch(settings: false, reference: fixture.reference)
        let reading = app.descendants(matching: .any).matching(identifier: "reading-host").firstMatch
        require(reading.waitForExistence(timeout: 60), app, "Reading requires an installed Bible; no download/reset is performed")
        let verse = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.versePhrase)).firstMatch
        require(verse.waitForExistence(timeout: 60), app, "Fixture passage did not render")
        require(verse.isHittable, app, "Fixture verse is not hittable")
        let frame = verse.frame
        shot(app, "task7-ios-note-icon-before-tap")
        let origin = app.coordinate(withNormalizedOffset: .zero)
        // Calibrated to the observed KJV iPhone 16 layout: the note icon follows the wrapped last line.
        origin.withOffset(CGVector(dx: frame.minX + 80, dy: frame.maxY - 15)).tap()
        let marker = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.marker)).firstMatch
        require(marker.waitForExistence(timeout: 30), app, "Exact Android marker missing from the real in-verse note modal")
        shot(app, "task7-ios-fetched-android-note")
        XCUIDevice.shared.press(.home)
        require(app.wait(for: .runningBackground, timeout: 10), app, "Reading app did not background")
        app.terminate()
    }

    /// Run after controller-observed sync completion, with a real Bible and the Android note on the fixture reference.
    func testReadingShowsFetchedAndroidNote() throws {
        let fixture = try fixture()
        let app = launch(settings: false, reference: fixture.reference)
        let reading = app.descendants(matching: .any).matching(identifier: "reading-host").firstMatch
        require(reading.waitForExistence(timeout: 60), app, "Reading requires an installed Bible; no download/reset is performed")
        let verse = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.versePhrase)).firstMatch
        require(verse.waitForExistence(timeout: 60), app, "Fixture passage did not render")
        // The controller supplies the observed in-verse note element label from this runtime's AX tree.
        let note = app.webViews.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", fixture.noteAccessibilityLabel)).firstMatch
        require(note.waitForExistence(timeout: 30) && note.isHittable, app,
                "Observed in-verse note selector missing; inspect AX rather than substituting a drawer route")
        note.tap()
        let marker = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", fixture.marker)).firstMatch
        require(marker.waitForExistence(timeout: 30), app, "Exact Android marker missing from the real in-verse note modal")
        shot(app, "task7-ios-fetched-android-note")
        XCUIDevice.shared.press(.home)
        require(app.wait(for: .runningBackground, timeout: 10), app, "Reading app did not background")
        app.terminate()
    }
}
