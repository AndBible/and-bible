import XCTest

/// The real first-run onboarding, with no debug shortcut after the launch: Welcome -> "Download and start" -> `Yes` ->
/// Download screen -> `OK` (enabled once the first Bible is installed) -> Welcome's gate (b) opens reading.
///
/// Works in both configurations, and exists for the Release one: a Release (TestFlight) install looped back to Welcome
/// after the download (status 2026-11-02-ios-release-onboarding-loop). Release ignores every debug launch argument, so a
/// fresh install there means a real uninstall before the run: `scripts/mac-remote.sh xcode-test iphone --release
/// -only-testing:iosAppUITests/OnboardingUITests` uninstalls `org.andbible.ios` first. In Debug, `-resetData` gives
/// the same clean start. The download is real (network).
///
/// The Advanced download route (`testFreshInstallAdvancedDownloadOpensReading`) is opt-in: `TEST_RUNNER_ONBOARDING_ADVANCED=1`.
///
/// Optional env `TEST_RUNNER_ONBOARDING_LANGUAGE=fi` runs the flow with the app in Finnish (AppleLanguages/AppleLocale
/// launch arguments, which Release honours too; the confirmation button is then 'Kyllä').
final class OnboardingUITests: RealAppUITestCase {
    private var language: String? { ProcessInfo.processInfo.environment["ONBOARDING_LANGUAGE"].flatMap { $0.isEmpty ? nil : $0 } }

    private func languageArguments() -> [String] {
        guard let language else { return [] }
        return ["-AppleLanguages", "(\(language))", "-AppleLocale", language == "fi" ? "fi_FI" : language]
    }

    /// Over migrated old-app data (seeded Release container) the app first shows one-off legacy notices (OK dialogs)
    /// that hide Welcome from accessibility; acknowledge them like a user.
    private func dismissNotices(_ app: XCUIApplication, until target: XCUIElement) {
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        for _ in 0..<5 {
            if target.waitForExistence(timeout: 20) { return }
            guard ok.exists && ok.isHittable else { continue }
            shot(app, "onboarding-notice")
            ok.tap()
        }
    }

    func testFreshInstallOnboardingOpensReading() {
        // Only -resetData and the crash-gate reset (both ignored by Release builds); no fixture, no feature scenario,
        // no -openRef. A foreground kill by an earlier test would otherwise open the crash-recovery gate, not Welcome.
        let app = XCUIApplication()
        app.launchArguments = ["-resetData", "-task6ResetCrashState"] + languageArguments()
        app.launch()
        let welcome = element(app, "welcome-screen")
        dismissNotices(app, until: welcome)
        if !welcome.waitForExistence(timeout: 60) { attachTree(app, "no-welcome") }
        shot(app, "onboarding-welcome")
        XCTAssertTrue(welcome.exists, "Welcome not shown on a fresh install")
        let easy = element(app, "welcome-easy-start")
        XCTAssertTrue(easy.waitForExistence(timeout: 10), "Welcome has no 'Download and start' (welcome-easy-start)")
        easy.tap()
        if language == "fi" {
            let yes = app.buttons.matching(NSPredicate(format: "label == 'Kyllä'")).firstMatch
            if yes.waitForExistence(timeout: 15) { shot(app, "download-confirmation"); yes.tap() }
        } else {
            confirmDownloadIfAsked(app)
        }
        // Optional env ONBOARDING_INTERRUPT: 'background' sends the app to the background right after the download starts
        // and back; 'relaunch' waits for the enabled OK, then terminates and relaunches instead of tapping it.
        let interrupt = ProcessInfo.processInfo.environment["ONBOARDING_INTERRUPT"] ?? ""
        if interrupt == "background" {
            sleep(1)
            XCUIDevice.shared.press(.home)
            sleep(10)
            app.activate()
            shot(app, "onboarding-back-from-background")
        }
        if interrupt == "relaunch" {
            let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
            XCTAssertTrue(waitUntil(timeout: 180) { ok.exists && ok.isEnabled }, "OK never enabled before the relaunch")
            sleep(5)
            sendToBackground(app)
            app.terminate()
            app.launchArguments = languageArguments()
            app.launch()
            let opened = element(app, "reading-host").waitForExistence(timeout: 60)
            sleep(3)
            shot(app, "onboarding-relaunch-before-ok")
            if !opened { attachTree(app, "onboarding-relaunch-before-ok") }
            XCTAssertTrue(opened, "relaunch after the download (OK not tapped) did not open reading (welcome: \(welcome.exists))")
            return
        }
        let okTapped = waitForEnabledOkAndTap(app)
        if !okTapped { attachTree(app, "onboarding-no-enabled-ok") }
        XCTAssertTrue(okTapped, "Download Documents OK not enabled within 180 s (no Bible installed?)")

        // Gate (b): back on Welcome with a usable Bible, reading opens by itself.
        let reading = element(app, "reading-host")
        let opened = reading.waitForExistence(timeout: 60)
        shot(app, "onboarding-after-ok")
        if !opened { attachTree(app, "onboarding-no-reading") }
        XCTAssertTrue(opened, "the onboarding did not open reading after OK (welcome shown: \(welcome.exists))")
        // Reading must stay (not bounce back to Welcome) and render Bible text in the web view.
        let hasText = waitUntil(timeout: 60) { app.webViews.staticTexts.count >= 10 }
        sleep(3)
        shot(app, "onboarding-reading")
        if !hasText || welcome.exists { attachTree(app, "onboarding-reading-state") }
        XCTAssertFalse(welcome.exists, "the app returned to Welcome after opening reading")
        XCTAssertTrue(reading.exists, "reading host gone after opening")
        XCTAssertTrue(hasText, "reading shows no Bible text (\(app.webViews.staticTexts.count) web view texts)")

        // A relaunch must come back to reading, not to Welcome.
        sendToBackground(app)
        app.terminate()
        let again = XCUIApplication()
        again.launchArguments = languageArguments()
        again.launch()
        let back = element(again, "reading-host").waitForExistence(timeout: 60)
        shot(again, "onboarding-relaunch")
        if !back { attachTree(again, "onboarding-relaunch") }
        XCTAssertTrue(back, "relaunch after onboarding did not reopen reading (welcome shown: \(element(again, "welcome-screen").exists))")
        sendToBackground(again)
        if language == nil { checkInterruptedInstallReport(again) }
    }

    /// #3973: an install requested by an earlier run but stopped with the app is reported at the next launch with
    /// "Download again" (retry) and "Cancel" (forget). The record is seeded with a module no repository has, so the
    /// retry is visible as the Download screen's "could not be downloaded" message and nothing gets installed.
    private func checkInterruptedInstallReport(_ previous: XCUIApplication) {
        let marker = "NoSuchModule3973"
        previous.terminate()
        let app = XCUIApplication()
        app.launchArguments = ["-seedInterruptedInstall", "\(marker)@CrossWire"]
        app.launch()
        let again = app.buttons.matching(NSPredicate(format: "label == 'Download again'")).firstMatch
        let reported = again.waitForExistence(timeout: 60)
        shot(app, "interrupted-install-report")
        if !reported { attachTree(app, "interrupted-install-report") }
        XCTAssertTrue(reported, "the interrupted install was not reported at launch")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", marker)).firstMatch.exists,
                      "the report does not name the interrupted document")
        again.tap()
        confirmDownloadIfAsked(app) // the Download screen's own consent question, as on every first visit
        let notFound = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'could not be downloaded'")).firstMatch
        let retried = notFound.waitForExistence(timeout: 120)
        shot(app, "interrupted-install-retry")
        if !retried { attachTree(app, "interrupted-install-retry") }
        XCTAssertTrue(retried, "Download again did not request the document from the Download screen")

        // A missing catalogue entry is a settled error and is forgotten. Seed a separate interrupted request to
        // exercise Cancel; every kill is from the background to avoid opening the crash-recovery gate.
        sendToBackground(app)
        app.terminate()
        app.launchArguments = ["-seedInterruptedInstall", "\(marker)@CrossWire"]
        app.launch()
        XCTAssertTrue(again.waitForExistence(timeout: 60), "the separate interrupted install was not reported")
        app.buttons.matching(NSPredicate(format: "label == 'Cancel'")).firstMatch.tap()
        sendToBackground(app)
        app.terminate()
        app.launchArguments = []
        app.launch()
        XCTAssertTrue(element(app, "reading-host").waitForExistence(timeout: 60), "reading did not open after the cancel")
        sleep(3)
        XCTAssertFalse(again.exists, "a cancelled interrupted install was reported again")
        shot(app, "interrupted-install-cancelled")
        sendToBackground(app)
    }

    /// The Welcome "Advanced" tab's "Download Documents" route (the only download route a Finnish Welcome offers):
    /// pick KJV in the catalogue, wait for the enabled `OK`, and expect gate (b) to open reading.
    func testFreshInstallAdvancedDownloadOpensReading() throws {
        guard ProcessInfo.processInfo.environment["ONBOARDING_ADVANCED"] == "1" else {
            throw XCTSkip("Opt-in: set TEST_RUNNER_ONBOARDING_ADVANCED=1 (the Easy path covers first-run; DownloadHostTest covers the download screen)")
        }
        let finnish = language == "fi"
        let module = ProcessInfo.processInfo.environment["ONBOARDING_MODULE"].flatMap { $0.isEmpty ? nil : $0 } ?? "KJV"
        let app = XCUIApplication()
        app.launchArguments = ["-resetData"] + languageArguments()
        app.launch()
        let welcome = element(app, "welcome-screen")
        XCTAssertTrue(welcome.waitForExistence(timeout: 60), "Welcome not shown on a fresh install")
        let advanced = app.buttons.matching(NSPredicate(format: "label == 'Advanced'")).firstMatch
        if !finnish && advanced.waitForExistence(timeout: 10) { advanced.tap() }
        let rowTitle = finnish ? "Lataa teoksia" : "Download Documents"
        let download = app.buttons.containing(.staticText, identifier: rowTitle).firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 15), "no '\(rowTitle)' row on Welcome")
        download.tap()
        let yes = app.buttons.matching(NSPredicate(format: "label == %@", finnish ? "Kyllä" : "Yes")).firstMatch
        if yes.waitForExistence(timeout: 15) { yes.tap() }
        let kjv = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", module + " ")).firstMatch
        var found = kjv.waitForExistence(timeout: 60)
        for _ in 0..<15 where !found || !kjv.isHittable {
            nudgeList(app, up: true)
            found = kjv.exists
        }
        shot(app, "advanced-catalogue")
        attachTree(app, "advanced-catalogue")
        XCTAssertTrue(found && kjv.isHittable, "\(module) not found in the download catalogue")
        kjv.tap()
        sleep(2)
        shot(app, "advanced-after-kjv-tap")
        attachTree(app, "advanced-after-kjv-tap")
        // A per-document confirmation, if any.
        for label in ["Download", "OK", "Yes", "Lataa", "Kyllä"] {
            let b = app.alerts.buttons[label].firstMatch
            if b.exists { b.tap(); break }
        }
        let okTapped = waitForEnabledOkAndTap(app)
        if !okTapped { attachTree(app, "advanced-no-enabled-ok") }
        XCTAssertTrue(okTapped, "Download Documents OK not enabled within 180 s")
        let reading = element(app, "reading-host")
        let opened = reading.waitForExistence(timeout: 60)
        sleep(3)
        shot(app, "advanced-after-ok")
        if !opened { attachTree(app, "advanced-no-reading") }
        XCTAssertTrue(opened, "advanced download did not open reading after OK (welcome shown: \(welcome.exists))")
        let hasText = waitUntil(timeout: 60) { app.webViews.staticTexts.count >= 10 }
        shot(app, "advanced-reading")
        XCTAssertFalse(welcome.exists, "the app returned to Welcome after opening reading")
        XCTAssertTrue(hasText, "reading shows no Bible text")
        sendToBackground(app)
    }
}
