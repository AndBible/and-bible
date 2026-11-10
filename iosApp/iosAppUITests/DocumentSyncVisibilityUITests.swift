import XCTest

/// Controller-only smoke test against a disposable, seeded localhost WebDAV account.
final class DocumentSyncVisibilityUITests: XCTestCase {
    private struct Fixture: Decodable {
        let serverURL: String
        let username: String
        let password: String
        let folderPath: String
        let trustedCertificateSha256: String
        let expectedDocumentName: String
    }

    override func setUp() { continueAfterFailure = false }
    private var ui: SyncHarness { SyncHarness(test: self) }

    func testRealDocumentDownloadProgressInSettings() throws {
        let env = ProcessInfo.processInfo.environment
        guard let json = env["DOCUMENT_SYNC_VISIBILITY_FIXTURE_JSON"] else {
            throw XCTSkip("Controller-only document-sync fixture not supplied")
        }
        guard let data = json.data(using: .utf8),
              let fixture = try? JSONDecoder().decode(Fixture.self, from: data),
              let url = URL(string: fixture.serverURL), url.scheme == "https",
              url.host == "localhost", url.user == nil, url.password == nil,
              !fixture.username.isEmpty, !fixture.password.isEmpty, !fixture.expectedDocumentName.isEmpty,
              ui.normalizedSha256(fixture.trustedCertificateSha256) != nil else {
            XCTFail("Invalid disposable localhost HTTPS document-sync fixture")
            return
        }
        let app = XCUIApplication()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        var allowedNotifications = false
        let permissionMonitor = addUIInterruptionMonitor(withDescription: "Allow notifications") { alert in
            guard alert.label.localizedCaseInsensitiveContains("notifications") else { return false }
            let allow = alert.buttons["Allow"].firstMatch
            guard allow.exists && allow.isHittable else { return false }
            allow.tap()
            allowedNotifications = true
            return true
        }
        defer { removeUIInterruptionMonitor(permissionMonitor) }
        app.launchArguments = ["-task6ResetCrashState", "-standardTestFixture", "-resetDatabases", "-task5Settings", "-task7AndroidDebugSyncNamespace",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        guard ui.openDeviceSynchronization(app, "Synchronization settings unavailable") else { return }
        let existingDocuments = ui.reveal(app, "Documents", toggle: true)
        if existingDocuments.isSelected {
            guard env["DOCUMENT_SYNC_VISIBILITY_RESET_DISPOSABLE"] == "1" else {
                XCTFail("Enabled Documents requires explicit disposable-profile reset opt-in")
                return
            }
            app.swipeDown(); app.swipeDown(); app.swipeDown()
            let server = ui.reveal(app, "Server URL")
            guard server.label == "Server URL, \(fixture.serverURL)" else {
                XCTFail("Refusing to reset a profile with a different saved server")
                return
            }
            let signOut = ui.reveal(app, "Sign Out")
            guard signOut.isEnabled && signOut.isHittable else { XCTFail("Disposable Sign Out unavailable"); return }
            signOut.tap()
            let confirmation = app.staticTexts["Are you sure that you want to sign out and reset synchronization?"].firstMatch
            guard confirmation.waitForExistence(timeout: 10) else { XCTFail("Sign Out confirmation missing"); return }
            let confirm = app.buttons["OK"].firstMatch
            guard confirm.exists && confirm.isEnabled else { XCTFail("Sign Out confirmation unavailable"); return }
            confirm.tap()
            let documentsOff = NSPredicate { _, _ in
                existingDocuments.exists && existingDocuments.isEnabled && !existingDocuments.isSelected
            }
            guard XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: documentsOff, object: app)], timeout: 30) == .completed else {
                XCTFail("Documents did not turn off after disposable Sign Out")
                return
            }
            app.swipeDown(); app.swipeDown(); app.swipeDown()
        }
        // Sign Out leaves the lazy settings list scrolled down; return to its top before querying the backend.
        for _ in 0..<10 {
            if ui.rowButton(app, "Synchronization Backend").exists { break }
            app.swipeDown()
        }
        let backend = ui.rowButton(app, "Synchronization Backend")
        guard backend.waitForExistence(timeout: 10) && backend.isHittable else {
            XCTFail("Synchronization Backend row unavailable after reset")
            return
        }
        backend.tap()
        let webdav = ui.label(app, "WebDAV")
        guard webdav.waitForExistence(timeout: 10) else { XCTFail("WebDAV choice missing"); return }
        webdav.tap()
        let documents = ui.reveal(app, "Documents", toggle: true)
        guard documents.isEnabled && !documents.isSelected else {
            XCTFail("Fresh disposable profile must have Documents disabled")
            return
        }
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        ui.edit(app, "Server URL", fixture.serverURL)
        ui.edit(app, "User name", fixture.username, sensitive: true)
        ui.edit(app, "Password", fixture.password, sensitive: true)
        ui.edit(app, "Sync folder path", fixture.folderPath)
        ui.reveal(app, "Documents", toggle: true).tap()
        let start = ui.label(app, "Start syncing documents?")
        let certificate = ui.label(app, "Untrusted server certificate")
        let decision = NSPredicate { _, _ in start.exists || certificate.exists }
        guard XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: decision, object: app)], timeout: 90) == .completed else {
            XCTFail("Neither certificate nor document-sync confirmation appeared")
            return
        }
        if certificate.exists {
            let body = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@",
                "The certificate of localhost is not trusted by")).firstMatch
            let lines = body.exists ? body.label.components(separatedBy: .newlines) : []
            let hashes = lines.filter { $0.hasPrefix("SHA-256: ") }
            let trust = app.buttons["Trust this certificate"].firstMatch
            guard lines.contains("Subject: localhost"),
                  hashes.count == 1,
                  ui.normalizedSha256(String(hashes[0].dropFirst("SHA-256: ".count))) == ui.normalizedSha256(fixture.trustedCertificateSha256),
                  trust.exists && trust.isEnabled && trust.isHittable else {
                XCTFail("Localhost certificate identity or fingerprint did not match")
                return
            }
            trust.tap()
        }
        guard start.waitForExistence(timeout: 90) else { XCTFail("Document-sync confirmation missing"); return }
        let ok = app.buttons["OK"].firstMatch
        guard ok.waitForExistence(timeout: 10) && ok.isEnabled else { XCTFail("Document-sync confirmation unavailable"); return }
        ok.tap()
        let permission = springboard.alerts.containing(NSPredicate(format: "label CONTAINS[c] 'notifications'")).firstMatch
        let preauthorized = env["DOCUMENT_SYNC_VISIBILITY_PREAUTHORIZED"] == "1"
        if !preauthorized && !allowedNotifications && permission.waitForExistence(timeout: 30) {
            let allow = permission.buttons["Allow"].firstMatch
            guard allow.waitForExistence(timeout: 10) && allow.isHittable else {
                XCTFail("Notification permission Allow unavailable")
                return
            }
            allow.tap()
            allowedNotifications = true
        }
        guard allowedNotifications || preauthorized else {
            XCTFail("Notification permission prompt not observed; use preauthorization only with independently verified OS grant")
            return
        }

        // Require the real running detail, not merely an enabled switch or injected activity.
        // The controller must throttle archive responses so this state is observable.
        let progress = app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS %@ AND label CONTAINS %@ AND label CONTAINS %@",
            "operations", "Downloading", "bytes received")).firstMatch
        guard progress.waitForExistence(timeout: 90) else {
            XCTFail("No live document download count and bytes observed; check seeded archives and throttling")
            return
        }
        XCTAssertTrue(progress.label.range(of: #"[0-9]+ / [0-9]+ operations"#, options: .regularExpression) != nil,
                      "Download progress must include the operation count")
        XCTAssertTrue(progress.label.range(of: #"[0-9]+(?: / [0-9]+)? bytes received"#, options: .regularExpression) != nil,
                      "Download progress must include byte counts")
        // Settings also shows credential rows; never attach a screenshot of this screen.

        // Enter the cloud screen after the transfer has already started.
        ui.reveal(app, "Synced documents").tap()
        let cloudTitle = ui.label(app, "Synced documents")
        guard cloudTitle.waitForExistence(timeout: 30) else { XCTFail("Synced documents screen missing"); return }
        let find = app.buttons["Find"].firstMatch
        guard find.waitForExistence(timeout: 5) && find.isHittable else { XCTFail("Cloud search unavailable"); return }
        find.tap()
        let search = app.textViews.firstMatch
        guard search.waitForExistence(timeout: 5) && search.isHittable else { XCTFail("Cloud search editor unavailable"); return }
        search.tap()
        search.typeText(fixture.expectedDocumentName)
        let cloudItem = app.buttons.matching(NSPredicate(
            format: "label CONTAINS[c] %@", fixture.expectedDocumentName)).firstMatch
        guard cloudItem.waitForExistence(timeout: 10) else {
            XCTFail("Late cloud-screen entry did not show the expected seeded document")
            return
        }
        ui.shot(app, "document-sync-cloud-late-entry")

        // Stay backgrounded for ten seconds; a genuine document-sync notification must be delivered.
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 10)
        let notification = springboard.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS[c] %@", "Document synchronization")).firstMatch
        if !notification.exists {
            let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.01))
            let middle = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
            top.press(forDuration: 0.1, thenDragTo: middle)
        }
        guard notification.waitForExistence(timeout: 30) else {
            XCTFail("No real document-sync notification after backgrounding; check authorization and relay timing")
            return
        }
        ui.shot(springboard, "document-sync-background-notification")
        notification.tap()
        let activity = app.staticTexts["Background activity"].firstMatch
        guard activity.waitForExistence(timeout: 60) else {
            XCTFail("Tapping the document-sync notification did not open Background activity")
            return
        }
        let item = app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS %@", "Document synchronization")).firstMatch
        guard item.waitForExistence(timeout: 30) else { XCTFail("Document sync activity missing"); return }
        ui.shot(app, "document-sync-activity-after-notification")
        let finished = app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS %@", "Document sync queue finished")).firstMatch
        guard finished.waitForExistence(timeout: 120) else {
            XCTFail("Document sync did not reach a successful final queue state")
            return
        }
        ui.shot(app, "document-sync-queue-finished")
    }
}
