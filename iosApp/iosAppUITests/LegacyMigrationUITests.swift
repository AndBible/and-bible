import XCTest

/// Simulator end to end of the I3b legacy migration (plan decision M-7, Task 14).
///
/// Opt-in: every test skips unless `I3B_FIXTURE_DIR` is set (pass `TEST_RUNNER_I3B_FIXTURE_DIR=<dir>` to xcodebuild;
/// `scripts/mac-remote.sh` forwards it). `<dir>` holds the unpacked fixture scenarios `rich/` and `extended/`
/// (`unzip` of `sharedLogic/src/jvmTest/resources/legacymigration/fixtures/1.0.2/26/<scenario>.zip`).
///
/// The debug app seeds itself from a fixture directory (`-seedLegacyFixture <dir>`, see `IosLegacyDebugSeed`): the old
/// stores, the sidecar's UserDefaults and Keychain items, and a reset of the new app's migration state. No test passes
/// `-resetData` or `-resetDatabases`, they would wipe the seeded state. Dialogs are found by testTag, never by label,
/// because the `rich` fixture is Finnish. The tests share one simulator app container, so each one seeds its own scenario
/// on its first launch and later launches of the same test omit the seed argument.
final class LegacyMigrationUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    /// After the whole class: one launch with `-clearLegacySeed` removes the seeded stores, UserDefaults and Keychain items
    /// and the migration state, so other suites (which use -resetData, leaving these alone) never inherit them.
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

    // MARK: Helpers

    private func fixtureRoot() throws -> String {
        guard let dir = ProcessInfo.processInfo.environment["I3B_FIXTURE_DIR"], !dir.isEmpty else {
            throw XCTSkip("Legacy migration end to end is opt-in: set TEST_RUNNER_I3B_FIXTURE_DIR to the directory with the unpacked rich/ and extended/ fixtures")
        }
        return dir
    }

    private func scenario(_ name: String) throws -> String {
        let path = try fixtureRoot() + "/" + name
        XCTAssertTrue(FileManager.default.fileExists(atPath: path + "/sidecar.json"), "Missing fixture \(path)/sidecar.json")
        return path
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Launches with exactly [arguments] (a fresh `XCUIApplication` so a previous launch's arguments never leak in).
    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()
        return app
    }

    private func seedArguments(_ dir: String, extra: [String] = []) -> [String] {
        ["-seedLegacyFixture", dir, "-task6ResetCrashState"] + extra
    }

    private func gone(_ app: XCUIApplication, _ id: String, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate { _, _ in !self.element(app, id).exists }
        return XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: app)], timeout: timeout) == .completed
    }

    /// Either reading or Welcome is a valid Ready state: which one depends on the Bible modules installed on the simulator.
    private func waitForReady(_ app: XCUIApplication, timeout: TimeInterval = 120) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            dismissSystemAlert(app)
            if element(app, "reading-host").exists || element(app, "welcome-screen").exists { return true }
            usleep(300_000)
        }
        return false
    }

    /// Dismisses the iOS "You have changed the icon for ..." alert that discrete mode triggers and that blocks our
    /// Compose dialogs. Only SpringBoard alerts, and app-owned alerts whose text mentions the icon, are touched: a
    /// Compose AlertDialog is exposed as an alert too and must never be tapped here (it is a notice to count).
    @discardableResult
    private func dismissSystemAlert(_ app: XCUIApplication) -> Bool {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let spring = springboard.alerts.firstMatch
        if spring.exists, spring.buttons.firstMatch.exists {
            spring.buttons.firstMatch.tap()
            return true
        }
        let iconAlert = app.alerts.containing(NSPredicate(format: "label CONTAINS[c] %@", "icon")).firstMatch
        if iconAlert.exists, iconAlert.buttons.firstMatch.exists {
            iconAlert.buttons.firstMatch.tap()
            return true
        }
        return false
    }

    private func isReady(_ app: XCUIApplication) -> Bool {
        element(app, "reading-host").exists || element(app, "welcome-screen").exists
    }

    /// The confirm button of the Compose dialog on top: the `dialog-confirm` testTag when the framework exports it,
    /// otherwise a button whose label is one of [labels] (the strings `okay` = "OK" in en and fi, `yes` = "Yes" / "Kyllä").
    private func dialogConfirm(_ app: XCUIApplication, _ labels: [String]) -> XCUIElement {
        let tagged = element(app, "dialog-confirm")
        if tagged.exists { return tagged }
        return app.buttons.matching(NSPredicate(format: "label IN %@", labels)).firstMatch
    }

    /// Attaches what is exposed (app tree, SpringBoard tree, screenshot) so a missing dialog can be diagnosed.
    private func diagnoseDialog(_ app: XCUIApplication, _ name: String) {
        for (title, text) in [("app", app.debugDescription),
                              ("springboard", XCUIApplication(bundleIdentifier: "com.apple.springboard").debugDescription)] {
            let attachment = XCTAttachment(string: text)
            attachment.name = "tree-\(title)-\(name)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        shot(app, "dialog-missing-\(name)")
        print("I3B-TREE dialog-missing-\(name)\n\(app.debugDescription)")
    }

    /// Failure-path diagnostics for a screen element that was not found: the xcode export keeps only PNGs, so the tree
    /// is also printed (it lands in the xcodebuild log).
    private func diagnoseMissing(_ app: XCUIApplication, _ name: String) {
        shot(app, "missing-\(name)")
        let attachment = XCTAttachment(string: app.debugDescription)
        attachment.name = "tree-app-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
        print("I3B-TREE missing-\(name)\n\(app.debugDescription)")
    }

    /// Taps the confirm button of the Compose message/confirm dialog currently shown. Returns false (after attaching
    /// diagnostics when [diagnose]) when none appeared within [timeout].
    private func confirmDialog(_ app: XCUIApplication, timeout: TimeInterval, labels: [String] = ["OK"],
                               diagnose: Bool = true, name: String = "dialog") -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            dismissSystemAlert(app)
            if dialogConfirm(app, labels).exists { break }
            usleep(300_000)
        }
        let confirm = dialogConfirm(app, labels)
        guard confirm.exists else {
            if diagnose { diagnoseDialog(app, name) }
            return false
        }
        confirm.tap()
        // The tap counts as the dismissal. Wait briefly for the fade-out, but do NOT require the dialog to disappear: the next
        // notice is shown straight after, with the same tag, so "gone" may never happen between two notices.
        let predicate = NSPredicate { _, _ in !self.dialogConfirm(app, labels).exists }
        _ = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: app)], timeout: 2.5)
        return true
    }

    /// Dismisses the one-time notices one after the other; returns how many there were.
    private func dismissNotices(_ app: XCUIApplication, firstTimeout: TimeInterval, quietTimeout: TimeInterval) -> Int {
        var count = 0
        var timeout = firstTimeout
        while confirmDialog(app, timeout: timeout, diagnose: count == 0, name: "notice-\(count + 1)") {
            count += 1
            shot(app, "notice-dismissed-\(count)")
            timeout = quietTimeout
        }
        return count
    }

    /// Two launches that are killed while the progress view is up, so the third launch finds the migration given up.
    private func killTwiceDuringMigration(_ dir: String) {
        let delay = ["-legacyMigrationStepDelayMs", "20000"]
        let first = launch(seedArguments(dir, extra: delay))
        XCTAssertTrue(element(first, "legacy-progress").waitForExistence(timeout: 90), "Launch 1: no migration progress view")
        first.terminate()
        let second = launch(delay)
        XCTAssertTrue(element(second, "legacy-progress").waitForExistence(timeout: 90), "Launch 2: no migration progress view")
        second.terminate()
    }

    /// Scrolls the current settings list (up, then back down) until a row whose label starts with [text] is hittable.
    /// Polls up to [timeout] seconds, so a row that only appears after startup finished (the import row is primed at
    /// Ready) is waited for. Matches any element type, not just buttons. Returns the element (maybe not existing).
    private func openSettingsRow(_ app: XCUIApplication, startingWith text: String, timeout: TimeInterval = 60,
                                  buttonsOnly: Bool = false) -> XCUIElement {
        let query = buttonsOnly ? app.buttons : app.descendants(matching: .any)
        let row = query.matching(NSPredicate(format: "label BEGINSWITH %@", text)).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        var swipes = 0
        while Date() < deadline {
            dismissSystemAlert(app)
            if row.exists && row.isHittable { return row }
            if swipes % 20 < 10 { app.swipeUp() } else { app.swipeDown() }
            swipes += 1
            usleep(300_000)
        }
        diagnoseMissing(app, "row-\(text.prefix(24))")
        return row
    }

    /// Waits until [row] keeps the same frame for two samples: right after a swipe the list still scrolls, and a tap on a
    /// moving row is cancelled (SyncAcceptanceUITests.waitForStableFrame does the same).
    private func waitForStableFrame(_ row: XCUIElement) {
        var last = row.frame
        var stableSamples = 0
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline && stableSamples < 2 {
            usleep(400_000)
            let now = row.frame
            stableSamples = (now == last && row.exists) ? stableSamples + 1 : 0
            last = now
        }
    }

    /// Scrolls to the settings row (see [openSettingsRow]), waits for it to stop moving and taps it. Retries the tap once
    /// when [arrived] is still false afterwards. Returns whether [arrived] became true.
    private func tapSettingsRow(_ app: XCUIApplication, startingWith text: String, buttonsOnly: Bool = true,
                                arrived: () -> Bool) -> Bool {
        for _ in 0..<2 {
            let row = openSettingsRow(app, startingWith: text, buttonsOnly: buttonsOnly)
            guard row.exists else { return false }
            waitForStableFrame(row)
            if row.isHittable { row.tap() }
            let deadline = Date().addingTimeInterval(8)
            while Date() < deadline {
                dismissSystemAlert(app)
                if arrived() { return true }
                usleep(300_000)
            }
        }
        return false
    }

    // MARK: Tests

    /// `rich` (Finnish, discrete): the disguise comes first, nothing is visible before the PIN, then the three notices.
    func testDiscreteFixtureShowsCalculatorFirst() throws {
        let dir = try scenario("rich")
        let app = launch(seedArguments(dir))
        // The calculator key "=" proves the disguise is up; the migration has not started behind it.
        let equals = app.buttons.matching(NSPredicate(format: "label == %@", "=")).firstMatch
        XCTAssertTrue(equals.waitForExistence(timeout: 60), "The calculator is not shown first")
        XCTAssertFalse(element(app, "legacy-progress").exists, "Migration progress visible behind the calculator")
        XCTAssertFalse(element(app, "reading-host").exists, "Reading content visible before the PIN")
        XCTAssertFalse(element(app, "welcome-screen").exists, "Welcome visible before the PIN")
        shot(app, "i3b-calculator-first")

        for label in ["4", "3", "2", "1", "="] {
            let key = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
            XCTAssertTrue(key.waitForExistence(timeout: 10), "Calculator key \(label) missing")
            key.tap()
        }
        XCTAssertTrue(waitForReady(app), "No reading view or Welcome after the PIN and the migration")
        // The window count and titles are not exposed as accessibility identifiers; the screenshot is the evidence.
        shot(app, "i3b-rich-ready")
        let notices = dismissNotices(app, firstTimeout: 90, quietTimeout: 12)
        // Exactly the iCloud and Nextcloud notices: `icloud_sync_enabled` is true and the seeded sync_adapter is NEXT_CLOUD.
        // The unsent-patches notice needs regular files under Application Support/AndBible/RemoteSyncOutbox/<category>/ (see
        // LegacyExtras `hasUnsentPatches`), which the fixture does not contain (covered by SettingsAndLedgerTest, JVM), and
        // the skipped-bookmarks notice needs unverified bookmarks, which it has none of.
        XCTAssertEqual(notices, 2, "Expected the iCloud and Nextcloud notices (the fixture has no outbox files)")
        XCTAssertTrue(isReady(app), "The app left Ready while the notices were dismissed")
    }

    /// `extended` (English, no disguise): reading, then the Nextcloud notice; Settings keeps the server URL.
    func testExtendedMigratesAndShowsSyncNotice() throws {
        let dir = try scenario("extended")
        let app = launch(seedArguments(dir, extra: ["-task5Settings"]))
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label == %@", "=")).firstMatch.waitForExistence(timeout: 5),
                       "The calculator must not be shown for a non-discrete fixture")
        // -task5Settings starts on Settings; the notice dialog appears on top of whichever screen is first.
        let notices = dismissNotices(app, firstTimeout: 120, quietTimeout: 12)
        XCTAssertGreaterThanOrEqual(notices, 1, "Expected at least the Nextcloud notice")
        shot(app, "i3b-extended-after-notices")

        // Same path as SyncAcceptanceUITests: the "Device synchronization" row, then the backend row proves the screen.
        let backendRow = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Synchronization Backend")).firstMatch
        let opened = tapSettingsRow(app, startingWith: "Device synchronization") { backendRow.exists }
        if !opened { diagnoseMissing(app, "sync-screen") }
        XCTAssertTrue(opened, "Device synchronization screen did not open")
        let backend = openSettingsRow(app, startingWith: "Synchronization Backend", timeout: 20, buttonsOnly: true)
        XCTAssertTrue(backend.exists, "Synchronization Backend row missing")
        shot(app, "i3b-extended-sync-screen")
        let server = openSettingsRow(app, startingWith: "Server URL", timeout: 30, buttonsOnly: true)
        XCTAssertTrue(server.exists, "Server URL row missing")
        // The URL is the generator's seeded gdrive_server_url (I3FixtureGeneratorTests.seedSettingsRows), not in sidecar.json.
        XCTAssertEqual(server.label, "Server URL, https://cloud.example.invalid/remote.php/dav/files/fixture")
        // Sync stays off: the Bookmarks category switch (seeded on in the old app) must not be selected after migration.
        let bookmarks = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Bookmarks")).firstMatch
        for _ in 0..<10 where !(bookmarks.exists && bookmarks.isHittable) { app.swipeUp() }
        if bookmarks.exists {
            XCTAssertFalse(bookmarks.isSelected, "Sync must stay off after the migration")
        } else {
            diagnoseMissing(app, "bookmarks-sync-switch")
            XCTFail("Bookmarks sync switch not found")
        }
        shot(app, "i3b-extended-sync-settings")
    }

    /// W9: two kills while migrating, the third launch lands on the legacy recovery screen, not on crash recovery.
    func testTwoKillsDuringMigrationLandOnLegacyRecovery() throws {
        let dir = try scenario("extended")
        killTwiceDuringMigration(dir)
        let third = launch([])
        XCTAssertTrue(element(third, "legacy-recovery").waitForExistence(timeout: 90), "Launch 3: no legacy recovery screen")
        XCTAssertFalse(element(third, "crash-recovery").exists, "Crash recovery must not preempt the legacy recovery")
        XCTAssertFalse(element(third, "crash-backup").exists)
        XCTAssertTrue(element(third, "legacy-restore").exists, "legacy-restore missing in the given-up state")
        XCTAssertTrue(element(third, "legacy-start-fresh").exists, "legacy-start-fresh missing in the given-up state")
        shot(third, "i3b-legacy-recovery")
    }

    /// From the given-up state: start fresh, then import the old data again from Settings and relaunch.
    func testStartFreshThenImportReplaces() throws {
        let dir = try scenario("extended")
        killTwiceDuringMigration(dir)
        let third = launch([])
        XCTAssertTrue(element(third, "legacy-start-fresh").waitForExistence(timeout: 90), "No start-fresh action on the recovery screen")
        element(third, "legacy-start-fresh").tap()
        let confirm = element(third, "legacy-start-fresh-confirm")
        XCTAssertTrue(confirm.waitForExistence(timeout: 10), "No start-fresh confirmation")
        confirm.tap()
        XCTAssertTrue(waitForReady(third), "Start fresh did not reach Welcome or reading")
        shot(third, "i3b-start-fresh-ready")
        third.terminate()

        // Settings: the import row is offered because the old stores are still there.
        let settings = launch(["-task5Settings"])
        // The row is primed at Ready and is the last of the Advanced category: scroll to it, let the list settle, tap.
        let confirmButton = { self.dialogConfirm(settings, ["Yes", "Kyllä"]) }
        let asked = tapSettingsRow(settings, startingWith: "Import data from the previous app version", buttonsOnly: false) {
            confirmButton().exists
        }
        if !asked { diagnoseMissing(settings, "import-row") }
        XCTAssertTrue(asked, "The import row is not offered or did not open its confirmation")
        XCTAssertTrue(confirmDialog(settings, timeout: 15, labels: ["Yes", "Kyllä"], name: "import-confirm"), "No import confirmation dialog")
        XCTAssertTrue(confirmDialog(settings, timeout: 15, name: "import-reopen"), "No 'reopen the app' message")
        settings.terminate()

        // The next launch runs the migration again, replacing the fresh databases (the app log has the
        // `before-import-*` line, Application Support/LegacyMigration/before-import-* holds the kept databases).
        // -task6ResetCrashState: the test killed the app twice unclean (terminate) since the last settled launch, which must
        // not turn the import launch into the crash-recovery screen (that is a different feature).
        let relaunched = launch(["-task6ResetCrashState"])
        let importReady = waitForReady(relaunched, timeout: 150)
        if !importReady {
            let states = ["legacy-progress", "legacy-recovery", "crash-recovery", "legacy-try-again", "welcome-screen", "reading-host"]
            print("I3B-STATE import launch not Ready; visible: \(states.filter { element(relaunched, $0).exists })")
            diagnoseMissing(relaunched, "import-launch")
        }
        XCTAssertTrue(importReady, "The import launch did not reach Ready")
        XCTAssertFalse(element(relaunched, "legacy-recovery").exists)
        XCTAssertFalse(element(relaunched, "legacy-progress").exists)
        shot(relaunched, "i3b-after-import")
    }

    /// NOT IMPLEMENTED on purpose. ROLLBACK_PENDING needs a rollback that FAILS. A kill during ADOPTING is rolled back
    /// successfully by the next launch (which then migrates again), so no launch argument can leave the databases in an
    /// unsafe state deterministically; `-legacyMigrationStepDelayMs` only delays after the MAP step. The hidden
    /// `legacy-restore` / `legacy-start-fresh` actions for that reason are covered by the Task 4 JVM test only.
    func testRollbackPendingHidesUnsafeActions() throws {
        _ = try fixtureRoot()
        throw XCTSkip("ROLLBACK_PENDING cannot be induced deterministically from the UI; covered by the Task 4 JVM test")
    }

    // testRestoreBackupThenBackReachesReady is intentionally absent: `legacy-restore` resolves the gate with `OpenBackup`, which
    // hands over to the backup-restore flow that needs a backup file the seeded fixtures do not put in the app container
    // (the flow's picker is a system UI), so a deterministic back-to-Ready path is not reachable from the UI test.
}
