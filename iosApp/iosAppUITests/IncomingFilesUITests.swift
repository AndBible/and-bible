import XCTest

/// Production incoming queue -> production InstallZip graph proof, not OS Files/Mail registration proof.
/// Required debug bootstrap contract (Swift calls only IosPlatformBootstrap):
/// - -incomingFixture sword|invalid stages bundled f-rawtext.zip|f-invalid.zip and offers it to
///   actual IosIncomingFiles before graph attachment. Early staging stays in owned temp and survives
///   the existing later -resetData; registration/backend preflight occurs only AFTER startup reset.
///   Do not edit the reset parser or assume it clears UserDefaults.
/// - -incomingRuntimeTest enables buttons incoming-offer-epub / incoming-open-backup. The former
///   offers bundled f-runtime.epub to that SAME queue in the current process, never installs directly.
/// - incoming-runtime-ready appears after the real graph is bound and preflight proves fixture IDs
///   absent from the production registry. Tests are isolated; fixture preflight must not uninstall books.
/// - incoming-sword-content-verified / incoming-epub-content-verified appear only after production
///   registration AND actual backend reads match Genesis 1:1 / both EPUB chapters respectively.
///   Checking fixture bytes, disk presence, or enqueue success cannot set these tags.
/// - incoming-open-backup navigates to real NavRoutes.BACKUP, not restoreModules or installer directly.
///   incoming-backup-route-ready is only a route witness; actions below must use production controls.
/// Fixtures: K4RawText public-domain module; EPUB id f-runtime-fixed, title F Runtime Fixture,
/// chapter text "F runtime chapter one proof." and "F runtime chapter two proof."; truncated ZIP.
/// Debug resources are generated deterministically; the harness uses the actual incoming queue and installer.
final class IncomingFilesUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
        addUIInterruptionMonitor(withDescription: "Notification permission only") { alert in
            guard alert.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
                  alert.buttons["Don't Allow"].exists else { return false }
            alert.buttons["Don't Allow"].tap()
            return true
        }
    }

    private func dismissNotificationPermissionIfPresent() {
        // SpringBoard alerts are absent from the app-only accessibility hierarchy.
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if alert.waitForExistence(timeout: 5),
           alert.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
           alert.buttons["Don't Allow"].exists {
            alert.buttons["Don't Allow"].tap()
        }
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func evidence(_ app: XCUIApplication, _ name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = "tree-\(name)"
        tree.lifetime = .keepAlways
        add(tree)
    }

    private func requireVisible(_ target: XCUIElement, app: XCUIApplication, step: String, timeout: TimeInterval = 30) {
        let found = target.waitForExistence(timeout: timeout)
        evidence(app, "\(step)-\(found ? "visible" : "timeout")")
        XCTAssertTrue(found, "Missing actual UI/content evidence at \(step)")
    }

    private func launch(fixture: String? = nil) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-task6ResetCrashState", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-resetData", "-incomingRuntimeTest", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if let fixture = fixture { app.launchArguments += ["-incomingFixture", fixture] }
        app.launch()
        dismissNotificationPermissionIfPresent()
        // A cold production confirmation covers the overlay; it can appear only after preflight
        // succeeds and the real incoming drainer binds. Assert the overlay after accepting it.
        if fixture == nil {
            requireVisible(element(app, "incoming-runtime-ready"), app: app, step: "incoming-production-graph-ready", timeout: 60)
        }
        return app
    }

    private func confirmInstall(_ app: XCUIApplication, filename: String, expectError: Bool = false) {
        let message = app.staticTexts.matching(NSPredicate(format: "label == %@",
            "Do you want to install module (\(filename))?")).firstMatch
        requireVisible(message, app: app, step: "install-confirm-\(filename)")
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        XCTAssertTrue(ok.isHittable && ok.isEnabled, "Production install confirmation is not usable")
        ok.tap()
        if !expectError {
            requireVisible(element(app, "incoming-runtime-ready"), app: app, step: "incoming-production-graph-ready-after-confirmation", timeout: 60)
        }
    }

    private func requireContent(_ app: XCUIApplication, _ id: String) {
        requireVisible(element(app, id), app: app, step: id, timeout: 120)
        XCTAssertFalse(element(app, "incoming-runtime-error").exists, "Registry/backend fixture verification failed")
    }

    private func offerWarmEpub(_ app: XCUIApplication) {
        let offer = element(app, "incoming-offer-epub")
        requireVisible(offer, app: app, step: "warm-epub-offer")
        XCTAssertTrue(offer.isHittable && offer.isEnabled)
        offer.tap() // No relaunch: the already-bound production queue must receive this arrival.
        confirmInstall(app, filename: "f-runtime.epub")
        requireContent(app, "incoming-epub-content-verified")
    }

    func testColdSwordRegistersRecognizableBackendContent() {
        let app = launch(fixture: "sword")
        defer { app.terminate() }
        confirmInstall(app, filename: "f-rawtext.zip")
        // Actual production K4RawText Gen.1.1 must read:
        // "In the beginning God created the heaven and the earth."
        requireContent(app, "incoming-sword-content-verified")
    }

    func testWarmEpubAfterSwordUsesSameProcessQueue() {
        let app = launch(fixture: "sword")
        defer { app.terminate() }
        confirmInstall(app, filename: "f-rawtext.zip")
        requireContent(app, "incoming-sword-content-verified")
        offerWarmEpub(app)
        requireContent(app, "incoming-sword-content-verified")
    }

    func testInvalidArchiveShowsProductionErrorThenQueueAcceptsEpub() {
        let app = launch(fixture: "invalid")
        defer { app.terminate() }
        confirmInstall(app, filename: "f-invalid.zip", expectError: true)
        // Actual INVALID_FILE terminal feedback uses CoreStrings.invalidModule, not install_failed_reason.
        let error = app.staticTexts.matching(NSPredicate(format: "label == 'File does not contain a valid SWORD module'")).firstMatch
        requireVisible(error, app: app, step: "invalid-archive-production-error")
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        XCTAssertTrue(ok.isHittable && ok.isEnabled)
        ok.tap()
        requireVisible(element(app, "incoming-runtime-ready"), app: app, step: "incoming-production-graph-ready-after-error", timeout: 60)
        XCTAssertFalse(element(app, "incoming-sword-content-verified").exists)
        offerWarmEpub(app)
    }

    func testBackupDocumentsRestoreOpensProductionInstallPicker() {
        let app = launch()
        defer { app.terminate() }
        let openBackup = element(app, "incoming-open-backup")
        requireVisible(openBackup, app: app, step: "backup-destination-trigger")
        XCTAssertTrue(openBackup.isHittable)
        openBackup.tap()
        requireVisible(element(app, "incoming-backup-route-ready"), app: app, step: "production-backup-route")
        // BackupRestoreScreen has two occurrences of each label, ordered Backup then Restore.
        // These production switch selectors still need actual iOS accessibility-tree validation.
        let documents = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Documents'"))
        let database = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Database'"))
        XCTAssertEqual(documents.count, 2, "Cannot unambiguously identify the Restore Documents switch")
        XCTAssertEqual(database.count, 2, "Cannot unambiguously identify the Restore Database switch")
        let restoreDocuments = documents.element(boundBy: 1)
        let restoreDatabase = database.element(boundBy: 1)
        XCTAssertTrue(restoreDocuments.isHittable && restoreDatabase.isHittable)
        if restoreDatabase.isSelected { restoreDatabase.tap() }
        if !restoreDocuments.isSelected { restoreDocuments.tap() }
        XCTAssertFalse(restoreDatabase.isSelected)
        XCTAssertTrue(restoreDocuments.isSelected)
        let restore = app.buttons.matching(NSPredicate(format: "label == 'Restore or Import from…'")).firstMatch
        requireVisible(restore, app: app, step: "production-documents-restore-action")
        XCTAssertTrue(restore.isHittable && restore.isEnabled)
        restore.tap()
        // Real BackupServiceImpl -> IosBackupHost.restoreModules -> bound InstallZipHost prelude.
        requireVisible(app.staticTexts["Load Documents From Files"].firstMatch, app: app, step: "restore-modules-formats-title")
        let formats = app.staticTexts.matching(NSPredicate(format:
            "label BEGINSWITH 'You have to choose the file' AND label CONTAINS 'Supported formats for loading documents'")).firstMatch
        requireVisible(formats, app: app, step: "restore-modules-supported-formats")
        let proceed = app.buttons["Proceed"].firstMatch
        requireVisible(proceed, app: app, step: "restore-modules-proceed")
        XCTAssertTrue(proceed.isHittable && proceed.isEnabled)
        proceed.tap()
        // Only after Proceed can Cancel plus the actual Files root prove the native document picker.
        let cancel = app.buttons["Cancel"].firstMatch
        requireVisible(cancel, app: app, step: "restore-modules-system-picker")
        XCTAssertTrue(cancel.isHittable)
        let filesRoot = app.otherElements.matching(NSPredicate(format: "identifier BEGINSWITH 'DOC.browsingRoot Source:'")).firstMatch
        requireVisible(filesRoot, app: app, step: "restore-modules-real-files-content")
        cancel.tap()
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in !cancel.exists }, object: nil)
        let result = XCTWaiter.wait(for: [gone], timeout: 20)
        evidence(app, "restore-modules-picker-dismissed")
        XCTAssertEqual(result, .completed)
    }
}
