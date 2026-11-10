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

    private func privateRow(_ app: XCUIApplication, _ title: String, toggle: Bool = false) -> XCUIElement? {
        let predicate = NSPredicate(format: "label BEGINSWITH %@", title)
        let row = toggle ? app.switches.matching(predicate).firstMatch : app.buttons.matching(predicate).firstMatch
        for _ in 0..<10 {
            if row.exists && row.isHittable {
                var stableFrame: CGRect?
                var stableSince: TimeInterval?
                let stable = NSPredicate { _, _ in
                    guard row.exists && row.isHittable else {
                        stableFrame = nil; stableSince = nil
                        return false
                    }
                    let frame = row.frame
                    let now = ProcessInfo.processInfo.systemUptime
                    if let previous = stableFrame, let since = stableSince,
                       abs(frame.minX - previous.minX) <= 0.5, abs(frame.minY - previous.minY) <= 0.5,
                       abs(frame.width - previous.width) <= 0.5, abs(frame.height - previous.height) <= 0.5 {
                        return now - since >= 1
                    }
                    stableFrame = frame; stableSince = now
                    return false
                }
                guard XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: stable, object: app)], timeout: 15) == .completed else {
                    XCTFail("Synchronization setting did not become stable: \(title)")
                    return nil
                }
                return row
            }
            app.swipeUp()
        }
        XCTFail("Required synchronization setting unavailable: \(title)")
        return nil
    }

    private func editPrivateRow(_ app: XCUIApplication, _ title: String, _ value: String) -> Bool {
        guard let row = privateRow(app, title) else { return false }
        row.tap()
        let field = app.textViews.firstMatch
        guard field.waitForExistence(timeout: 10), field.isHittable, app.textViews.count == 1 else {
            XCTFail("Synchronization editor unavailable: \(title)")
            return false
        }
        field.tap()
        guard XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "hasKeyboardFocus == true"), object: field)], timeout: 10) == .completed else {
            XCTFail("Synchronization editor did not receive focus: \(title)")
            return false
        }
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        for _ in 0..<4 where field.value != nil && (field.value as? String) != "" {
            let length = (field.value as? String)?.count ?? 0
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: max(length, 1) + 4))
        }
        guard field.value == nil || (field.value as? String) == "" else {
            XCTFail("Synchronization editor could not be cleared: \(title)")
            return false
        }
        if !value.isEmpty { field.typeText(value) }
        let apply = app.buttons["Apply"].firstMatch
        guard apply.waitForExistence(timeout: 5) && apply.isHittable else {
            XCTFail("Synchronization editor Apply unavailable: \(title)")
            return false
        }
        apply.tap()
        guard XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in !field.exists }, object: app)], timeout: 10) == .completed else {
            XCTFail("Synchronization editor did not close: \(title)")
            return false
        }
        return true
    }

    func testRealDocumentDownloadProgressInSettings() throws {
        try exerciseDocumentSync(compactMenu: false)
    }

    func testRealDocumentSyncKeepsOverflowMenuCompact() throws {
        try exerciseDocumentSync(compactMenu: true)
    }

    private func exerciseDocumentSync(compactMenu: Bool) throws {
        let env = ProcessInfo.processInfo.environment
        guard let json = env["DOCUMENT_SYNC_VISIBILITY_FIXTURE_JSON"] else {
            throw XCTSkip("Controller-only document-sync fixture not supplied")
        }
        guard env["DOCUMENT_SYNC_VISIBILITY_PROFILE_DISPOSABLE"] == "1" else {
            throw XCTSkip("Explicit disposable-profile opt-in is required before resetting app databases")
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
        if compactMenu {
            guard let activityToggle = privateRow(app, "Show background activity in the menu", toggle: true) else { return }
            if !activityToggle.isSelected { activityToggle.tap() }
            for _ in 0..<10 {
                if ui.rowButton(app, "Device synchronization").exists { break }
                app.swipeDown()
            }
        }
        guard let syncSettings = privateRow(app, "Device synchronization") else { return }
        syncSettings.tap()
        guard let existingDocuments = privateRow(app, "Documents", toggle: true) else { return }
        if existingDocuments.isSelected {
            guard env["DOCUMENT_SYNC_VISIBILITY_RESET_DISPOSABLE"] == "1" else {
                XCTFail("Enabled Documents requires explicit disposable-profile reset opt-in")
                return
            }
            app.swipeDown(); app.swipeDown(); app.swipeDown()
            guard let server = privateRow(app, "Server URL") else { return }
            guard server.label == "Server URL, \(fixture.serverURL)" else {
                XCTFail("Refusing to reset a profile with a different saved server")
                return
            }
            guard let signOut = privateRow(app, "Sign Out") else { return }
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
        guard let documents = privateRow(app, "Documents", toggle: true) else { return }
        guard documents.isEnabled && !documents.isSelected else {
            XCTFail("Fresh disposable profile must have Documents disabled")
            return
        }
        app.swipeDown(); app.swipeDown(); app.swipeDown()
        guard editPrivateRow(app, "Server URL", fixture.serverURL),
              editPrivateRow(app, "User name", fixture.username),
              editPrivateRow(app, "Password", fixture.password),
              editPrivateRow(app, "Sync folder path", fixture.folderPath),
              let documentsToEnable = privateRow(app, "Documents", toggle: true) else { return }
        documentsToEnable.tap()
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
        if !compactMenu && !preauthorized && !allowedNotifications && permission.waitForExistence(timeout: 30) {
            let allow = permission.buttons["Allow"].firstMatch
            guard allow.waitForExistence(timeout: 10) && allow.isHittable else {
                XCTFail("Notification permission Allow unavailable")
                return
            }
            allow.tap()
            allowedNotifications = true
        }
        guard compactMenu || allowedNotifications || preauthorized else {
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

        if compactMenu {
            let oldDetails = progress.label
            // Return through real navigation rather than injecting activity rows into a test host.
            let back = app.buttons["Back"].firstMatch
            guard back.exists && back.isHittable else { XCTFail("Sync settings Back missing"); return }
            back.tap()
            let settingsBack = app.buttons["Back"].firstMatch
            guard settingsBack.waitForExistence(timeout: 10) && settingsBack.isHittable else {
                XCTFail("Application settings Back missing"); return
            }
            settingsBack.tap()
            let reading = app.otherElements["reading-host"].firstMatch
            let ring = app.otherElements["activityRing"].firstMatch
            guard reading.waitForExistence(timeout: 30), ring.waitForExistence(timeout: 10) else {
                ui.tree(app, "compact-menu-missing-reading-ring")
                XCTFail("Reading host and live activity ring missing"); return
            }
            // The active ring replaces the iOS overflow AX label; tap its measured toolbar frame.
            ring.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            let summary = app.buttons.matching(NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@",
                "Document synchronization", "operations")).firstMatch
            guard summary.waitForExistence(timeout: 30) else { XCTFail("Compact document sync summary missing"); return }
            let initial = summary.frame
            XCTAssertLessThanOrEqual(initial.width, 281)
            XCTAssertLessThan(initial.height, 80, "Compact sync row must contain one text line and its progress bar")
            XCTAssertFalse(summary.label.contains(fixture.expectedDocumentName))
            XCTAssertFalse(summary.label.contains("bytes received"))
            let showAll = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Show all (")).firstMatch
            guard showAll.waitForExistence(timeout: 5) && showAll.isHittable else {
                XCTFail("Single compact sync has no path to full details"); return
            }
            let initialShowAll = showAll.frame
            let initialProgress = ring.value as? String
            ui.shot(app, "compact-sync-menu-before-update")
            let changed = NSPredicate { _, _ in
                ring.exists && (ring.value as? String) != initialProgress && summary.exists
            }
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: changed, object: app)], timeout: 30), .completed,
                           "Real transfer progress must change while the menu is open")
            XCTAssertEqual(summary.frame, initial, "Live transfer updates must not resize the compact row")
            XCTAssertEqual(showAll.frame, initialShowAll, "Live updates must not move later menu rows")
            ui.shot(app, "compact-sync-menu-after-update")
            showAll.tap()
            let activityTitle = app.staticTexts["Background activity"].firstMatch
            guard activityTitle.waitForExistence(timeout: 15) else { XCTFail("Show all did not open the full activity view"); return }
            let fullDetails = app.descendants(matching: .any).matching(NSPredicate(
                format: "label CONTAINS %@ AND label CONTAINS %@ AND label CONTAINS %@",
                "operations", "Downloading", "bytes received")).firstMatch
            guard fullDetails.waitForExistence(timeout: 15) else { XCTFail("Full activity view lost document/byte details"); return }
            let detailPattern = #"Downloading ([^:]+): ([0-9]+)(?: / [0-9]+)? bytes received"#
            let regex = try NSRegularExpression(pattern: detailPattern)
            func detail(_ value: String) -> (String, Int64)? {
                let range = NSRange(value.startIndex..., in: value)
                guard let match = regex.firstMatch(in: value, range: range),
                      let nameRange = Range(match.range(at: 1), in: value),
                      let bytesRange = Range(match.range(at: 2), in: value),
                      let bytes = Int64(value[bytesRange]) else { return nil }
                return (String(value[nameRange]), bytes)
            }
            guard let before = detail(oldDetails), let after = detail(fullDetails.label) else {
                XCTFail("Full details must retain the active document name and numeric byte counts"); return
            }
            XCTAssertFalse(after.0.isEmpty)
            XCTAssertTrue(after.0 != before.0 || after.1 > before.1, "Actual document or numeric byte progress must advance")
            ui.shot(app, "compact-sync-full-details")
            return
        }

        // Enter the cloud screen after the transfer has already started.
        guard let syncedDocuments = privateRow(app, "Synced documents") else { return }
        syncedDocuments.tap()
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
