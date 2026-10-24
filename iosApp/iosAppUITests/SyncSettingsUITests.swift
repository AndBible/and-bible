import XCTest

/// H2 Task 9 (W8): sync settings on iOS. Drives the real Settings -> Device synchronization route and the real
/// `NavRoutes.SYNC_SETTINGS` route (debug launch argument `-h2SyncSettings`, the entry R3's drawer will use).
/// Preconditions are established by the test itself: the first launch passes `-standardTestFixture` (English, discrete
/// off); the persistence relaunches deliberately omit it and any reset so the saved settings must survive.
///
/// What this class can and cannot prove without a server: the https policy and the saved URL need none; a category
/// cannot be switched ON (that runs a real sign-in, covered by SyncAcceptanceUITests), so the category check is that it
/// is OFF in the saved state, stays off across a relaunch, and that a rejected URL is not stored. The iOS sync settings
/// screen has no separate sync-log screen at this HEAD; that part of the brief is reported, not faked.
final class SyncSettingsUITests: RealAppUITestCase {
    private let publicHttpURL = "http://example.org/nc"
    private let localHttpURL = "http://127.0.0.1:8080/nc"

    private func openSyncFromSettings(_ app: XCUIApplication) {
        harnessRequire(waitForSettingsRoot(app), app, "Settings root did not appear")
        _ = revealSettingsRow(app, "sync_settings_shortcut")
        settingsRow(app, "sync_settings_shortcut").tap()
        awaitSyncScreen(app)
    }

    private func awaitSyncScreen(_ app: XCUIApplication) {
        let title = app.staticTexts["Device synchronization"].firstMatch
        harnessRequire(title.waitForExistence(timeout: 60) && settingsRow(app, "sync_adapter").waitForExistence(timeout: 30),
                       app, "Device synchronization screen did not appear")
    }

    private func chooseNextcloud(_ app: XCUIApplication) {
        _ = revealSettingsRow(app, "sync_adapter")
        settingsRow(app, "sync_adapter").tap()
        let choice = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "NextCloud")).firstMatch
        harnessRequire(choice.waitForExistence(timeout: 10), app, "NextCloud choice missing")
        choice.tap()
        harnessRequire(waitUntil(timeout: 15) {
            self.settingsRow(app, "sync_adapter").label.contains("NextCloud")
        }, app, "Adapter row did not show NextCloud after choosing it")
    }

    private func serverURLRowLabel(_ app: XCUIApplication) -> String {
        _ = revealSettingsRow(app, "cloud_sync_server_url")
        return settingsRow(app, "cloud_sync_server_url").label
    }

    private func bookmarksSwitchIsOff(_ app: XCUIApplication) -> Bool {
        let toggle = revealSettingsRow(app, "sync_enable_bookmarks")
        return toggle.exists && !toggle.isSelected && (toggle.value as? String) != "1"
    }

    func testCategoriesLogAndHttpPolicy() {
        var app = launch(["-standardTestFixture", "-task5Settings"])
        openSyncFromSettings(app)
        chooseNextcloud(app)

        // Public plain http: refused with the https-required message, and nothing is stored.
        replaceSettingsText(app, rowKey: "cloud_sync_server_url", with: publicHttpURL)
        let refusal = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "requires a secure https://")).firstMatch
        harnessRequire(refusal.waitForExistence(timeout: 15), app, "Public http URL did not show the https-required message")
        shot(app, "sync-https-required")
        let ok = app.buttons["OK"].firstMatch
        harnessRequire(ok.waitForExistence(timeout: 5), app, "https-required dialog has no OK")
        ok.tap()
        harnessRequire(waitUntil(timeout: 10) { !refusal.exists }, app, "https-required dialog did not close")
        harnessRequire(!serverURLRowLabel(app).contains("example.org"), app, "Rejected public http URL was stored")

        // Loopback plain http is allowed (no server needs to be reachable to save it).
        replaceSettingsText(app, rowKey: "cloud_sync_server_url", with: localHttpURL)
        harnessRequire(closeSettingsEditor(app), app, "Editor did not close for the local http URL")
        harnessRequire(!refusal.exists, app, "Loopback http URL was refused")
        harnessRequire(waitUntil(timeout: 15) { self.serverURLRowLabel(app).contains("127.0.0.1:8080/nc") }, app,
                       "Loopback http URL was not saved")

        // Categories: off in the saved state (switching on needs a real sign-in).
        harnessRequire(bookmarksSwitchIsOff(app), app, "Bookmarks category must be off without a signed-in account")
        shot(app, "sync-settings-saved")

        // Relaunch without fixture or reset, directly through NavRoutes.SYNC_SETTINGS: everything persisted.
        XCUIDevice.shared.press(.home)
        usleep(1_000_000)
        app.terminate()
        app = launch(["-h2SyncSettings"])
        awaitSyncScreen(app)
        harnessRequire(!element(app, "welcome-screen").exists, app, "Unexpected Welcome on relaunch")
        harnessRequire(settingsRow(app, "sync_adapter").label.contains("NextCloud"), app, "Adapter choice did not persist")
        harnessRequire(serverURLRowLabel(app).contains("127.0.0.1:8080/nc"), app, "Server URL did not persist")
        harnessRequire(bookmarksSwitchIsOff(app), app, "Bookmarks category is no longer off after relaunch")
        shot(app, "sync-settings-after-relaunch-direct-route")
        app.terminate()
    }
}
