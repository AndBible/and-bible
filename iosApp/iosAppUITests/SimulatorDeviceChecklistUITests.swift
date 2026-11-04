import XCTest

/// Opt-in simulator checklist for migration recovery and persistence on a seeded legacy install.
/// Set TEST_RUNNER_I3B_FIXTURE_DIR to the unpacked rich/ and extended/ fixture root.
final class SimulatorDeviceChecklistUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    /// Remove the seeded legacy files and migration state so other UI suites start clean.
    override class func tearDown() {
        guard ProcessInfo.processInfo.environment["I3B_FIXTURE_DIR"]?.isEmpty == false else { return }
        MainActor.assumeIsolated {
            let app = XCUIApplication()
            app.launchArguments = ["-clearLegacySeed", "-task6ResetCrashState"]
            app.launch()
            _ = app.wait(for: .runningForeground, timeout: 30)
            app.terminate()
        }
        super.tearDown()
    }

    private func fixtureScenario(_ name: String) throws -> String {
        guard let root = ProcessInfo.processInfo.environment["I3B_FIXTURE_DIR"], !root.isEmpty else {
            throw XCTSkip("Set TEST_RUNNER_I3B_FIXTURE_DIR to the unpacked I3B fixtures")
        }
        let path = root + "/" + name
        XCTAssertTrue(FileManager.default.fileExists(atPath: path + "/sidecar.json"), "Missing fixture \(path)/sidecar.json")
        return path
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func screenshot(_ app: XCUIApplication, named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()
        return app
    }

    private func waitForReady(_ app: XCUIApplication, timeout: TimeInterval = 150) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element(app, "reading-host").exists || element(app, "welcome-screen").exists || element(app, "dialog-confirm").exists {
                return true
            }
            usleep(300_000)
        }
        return false
    }

    private func dismissSystemAlert(_ app: XCUIApplication) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let alert = springboard.alerts.firstMatch
        if alert.exists, alert.buttons.firstMatch.exists { alert.buttons.firstMatch.tap() }
    }

    private func confirmButton(_ app: XCUIApplication) -> XCUIElement {
        let tagged = element(app, "dialog-confirm")
        if tagged.exists { return tagged }
        return app.buttons.matching(NSPredicate(format: "label IN %@", ["OK", "Yes", "Kyllä"])).firstMatch
    }

    /// Dismiss each modal notice and return the number shown.
    private func dismissNotices(_ app: XCUIApplication, firstTimeout: TimeInterval, quietTimeout: TimeInterval) -> Int {
        var count = 0
        var timeout = firstTimeout
        while Date().addingTimeInterval(timeout) > Date() {
            let deadline = Date().addingTimeInterval(timeout)
            while Date() < deadline {
                dismissSystemAlert(app)
                if confirmButton(app).exists { break }
                usleep(300_000)
            }
            let confirm = confirmButton(app)
            guard confirm.exists else { break }
            confirm.tap()
            count += 1
            screenshot(app, named: "migration-notice-dismissed-\(count)")
            timeout = quietTimeout
        }
        return count
    }

    private func openSettingsRow(_ app: XCUIApplication, startingWith label: String, timeout: TimeInterval = 30) -> XCUIElement {
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        var swipes = 0
        while Date() < deadline {
            dismissSystemAlert(app)
            if row.exists && row.isHittable { return row }
            if swipes % 2 == 0 { app.swipeUp() } else { app.swipeDown() }
            swipes += 1
            usleep(300_000)
        }
        return row
    }

    private func serverURL(_ app: XCUIApplication) -> String? {
        let deviceSync = openSettingsRow(app, startingWith: "Device synchronization")
        guard deviceSync.exists && deviceSync.isHittable else { return nil }
        deviceSync.tap()
        let backend = openSettingsRow(app, startingWith: "Synchronization Backend")
        guard backend.exists else { return nil }
        let server = openSettingsRow(app, startingWith: "Server URL")
        return server.exists ? server.label : nil
    }

    func testSingleKillResumesMigrationAndPersistsMigratedStateAfterRelaunch() throws {
        let fixture = try fixtureScenario("extended")
        let progressDelay = ["-legacyMigrationStepDelayMs", "20000"]

        let interrupted = launch(["-seedLegacyFixture", fixture, "-task6ResetCrashState"] + progressDelay)
        XCTAssertTrue(element(interrupted, "legacy-progress").waitForExistence(timeout: 90), "Initial migration progress was not shown")
        screenshot(interrupted, named: "migration-in-progress-before-single-kill")
        interrupted.terminate()

        // Do not reset crash state here: recovery must complete without either recovery UI.
        let resumed = launch(progressDelay + ["-task5Settings"])
        XCTAssertTrue(element(resumed, "legacy-progress").waitForExistence(timeout: 90), "Migration did not resume after the single kill")
        screenshot(resumed, named: "migration-resumed-progress")
        XCTAssertTrue(waitForReady(resumed), "Resumed migration did not reach Ready")
        XCTAssertFalse(element(resumed, "legacy-recovery").exists, "Legacy recovery must not appear after one interrupted migration")
        XCTAssertFalse(element(resumed, "crash-recovery").exists, "Crash recovery must not preempt the resumed migration")
        XCTAssertFalse(element(resumed, "crash-backup").exists, "Crash backup recovery must not appear")

        let notices = dismissNotices(resumed, firstTimeout: 120, quietTimeout: 12)
        XCTAssertEqual(notices, 1, "Expected the extended fixture's one-time Nextcloud notice")
        XCTAssertEqual(serverURL(resumed), "Server URL, https://cloud.example.invalid/remote.php/dav/files/fixture",
                       "The migrated server URL must be available in settings")
        screenshot(resumed, named: "migration-resumed-ready-with-migrated-server")
        resumed.terminate()

        let relaunched = launch(["-task5Settings"])
        XCTAssertTrue(waitForReady(relaunched), "Relaunch did not reach Ready")
        XCTAssertFalse(element(relaunched, "legacy-recovery").exists, "Legacy recovery appeared after relaunch")
        XCTAssertFalse(element(relaunched, "crash-recovery").exists, "Crash recovery appeared after relaunch")
        XCTAssertEqual(dismissNotices(relaunched, firstTimeout: 12, quietTimeout: 3), 0,
                       "The migration notice must not be shown a second time")
        XCTAssertEqual(serverURL(relaunched), "Server URL, https://cloud.example.invalid/remote.php/dav/files/fixture",
                       "The migrated server URL must persist after relaunch")
        screenshot(relaunched, named: "migration-data-persisted-after-relaunch")
        relaunched.terminate()
    }
}
