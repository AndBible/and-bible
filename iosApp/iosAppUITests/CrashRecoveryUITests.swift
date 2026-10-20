import XCTest

/// Run on a disposable simulator: -resetData deletes app files, -seedCrashRecovery seeds the real preferences.
final class CrashRecoveryUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// No terminate() or seeded recovery: both exits must come from the uncaught Kotlin dispatch callback.
    func testTwoFatalForegroundCrashesRecoverAfterBackgroundRearm() {
        let app = XCUIApplication()
        // Keep the real debug action above Compose Settings, not the native WKWebView touch surface.
        let base = ["-task6DebugActions", "-task5Settings", "-discrete_mode", "NO", "-show_calculator", "NO",
                    "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchArguments = base + ["-task6ResetCrashState"]
        app.launch()
        XCTAssertTrue(element(app, "task6-crash").waitForExistence(timeout: 60))

        // Background intentionally clears the dirty marker. Foreground must rearm it before the real crash.
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10))
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        fatalCrash(app)

        // Do not carry the reset flag into either subsequent launch: the crash count is persistent.
        app.launchArguments = base
        app.launch()
        XCTAssertTrue(element(app, "task6-crash").waitForExistence(timeout: 60))
        XCTAssertFalse(element(app, "crash-recovery").exists)
        fatalCrash(app)

        app.launch()
        XCTAssertTrue(element(app, "crash-recovery").waitForExistence(timeout: 30))
        XCTAssertFalse(element(app, "task6-crash").exists)
        XCTAssertFalse(element(app, "reading-host").exists)
        shot(app, "task6-two-fatal-crashes-recovery")
    }

    private func fatalCrash(_ app: XCUIApplication) {
        captureFatalTap(app, phase: "before")
        let crash = element(app, "task6-crash")
        let started = Date()
        crash.tap()
        let terminated = app.wait(for: .notRunning, timeout: 15)
        let elapsed = Date().timeIntervalSince(started)
        if !terminated && app.state != .notRunning {
            captureFatalTap(app, phase: "failed-after")
        }
        XCTAssertTrue(terminated, "The action must terminate, not display a caught error")
        XCTAssertGreaterThanOrEqual(elapsed, 1.5, "Crash must be delayed by two seconds")
    }

    private func captureFatalTap(_ app: XCUIApplication, phase: String) {
        let crash = element(app, "task6-crash")
        print("Task6 fatal tap \(phase): crash.frame=\(crash.frame), isHittable=\(crash.isHittable), app.frame=\(app.frame)")
        shot(app, "task6-fatal-tap-\(phase)")
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = "task6-fatal-tap-\(phase)-hierarchy"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
    }

    func testDebugErrorShowsActualLocalizedDialog() {
        let app = XCUIApplication()
        app.launchArguments = ["-task6DebugActions", "-task6ResetCrashState", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(element(app, "task6-error").waitForExistence(timeout: 60))
        element(app, "task6-error").tap()
        XCTAssertTrue(app.staticTexts["Test error (debug build)"].firstMatch.waitForExistence(timeout: 15))
        shot(app, "task6-localized-test-error")
        app.buttons["OK"].firstMatch.tap()
        XCTAssertFalse(app.staticTexts["Test error (debug build)"].firstMatch.exists)
        XCTAssertEqual(app.state, .runningForeground)
    }

    func testSeededRecoveryOpensRealUninitialisedBackup() {
        let app = XCUIApplication()
        app.launchArguments = ["-resetData", "-seedCrashRecovery", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(element(app, "crash-recovery").waitForExistence(timeout: 30))
        XCTAssertFalse(element(app, "reading-host").exists)
        XCTAssertFalse(element(app, "welcome-screen").exists)
        shot(app, "crash-recovery")
        element(app, "crash-backup").tap()
        XCTAssertTrue(element(app, "recovery-backup-uninitialised").waitForExistence(timeout: 30),
                      "Real Backup route must report databases still closed")
        XCTAssertTrue(app.staticTexts["Backup & Restore"].firstMatch.waitForExistence(timeout: 15))
        XCTAssertFalse(element(app, "reading-host").exists)
        XCTAssertFalse(element(app, "welcome-screen").exists)
        shot(app, "crash-recovery-backup")
        app.buttons["Back"].firstMatch.tap()
        XCTAssertTrue(element(app, "crash-recovery").waitForExistence(timeout: 15))
        XCTAssertFalse(element(app, "reading-host").exists)
        XCTAssertFalse(element(app, "welcome-screen").exists)
    }
}
