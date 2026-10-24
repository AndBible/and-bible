import XCTest

/// Real-app Dynamic Type mapping, live updates and Settings reachability.
final class DynamicTypeUITests: RealAppUITestCase {
    func testMeasureFontScaleAtXXXL() {
        let app = launch([
            "-standardTestFixture",
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryXXXL",
            "-debugFontScaleProbe"
        ])
        let probe = element(app, "ab-font-scale")
        XCTAssertTrue(probe.waitForExistence(timeout: 60), "debug font-scale probe must appear")
        attachTree(app, "font-scale-XXXL")
        let raw = probe.value as? String ?? ""
        let result = XCTAttachment(string: "category=UICTContentSizeCategoryXXXL fontScale=\(raw) label=\(probe.label)")
        result.name = "font-scale-measurement-XXXL"
        result.lifetime = .keepAlways
        add(result)
        let scale = Float(raw) ?? .nan
        XCTAssertEqual(scale, 23.0 / 17.0, accuracy: 0.0001, "App mapping must replace, not multiply, the built-in scale")
    }

    private func assertSettingsReachable(at category: String) {
        let app = XCUIApplication()
        app.launchArguments += [
            "-task6ResetCrashState", "-standardTestFixture", "-task5Settings",
            "-discrete_mode", "NO", "-show_calculator", "NO"
        ]
        launchAtTextSize(app, category)
        let first = "settings-row-navigate_to_verse_pref"
        guard app.staticTexts["Application preferences"].firstMatch.waitForExistence(timeout: 60) else {
            XCTFail("Real Settings root must appear")
            return
        }
        let ids = [
            first,
            "settings-row-open_links_in_special_window_pref",
            "settings-row-screen_keep_on_pref",
            "settings-row-double_tap_to_fullscreen",
            "settings-row-auto_fullscreen_pref",
            "settings-row-toolbar_button_actions",
            "settings-row-bible_view_swipe_mode",
            "settings-row-disable_two_step_bookmarking",
            "settings-row-volume_keys_scroll",
            "settings-row-night_mode_pref3",
            "settings-row-global_text_display_settings",
            "settings-row-locale_pref",
            "settings-row-font_size_multiplier",
            "settings-row-display_color_mode",
            "settings-row-disable_animations",
            "settings-row-sync_settings_shortcut",
            "settings-row-ai_settings_shortcut",
            "settings-row-reading_progress_settings_shortcut"
        ]
        // Capture every checked control when visible, rather than only the final scrolled viewport.
        for id in ids {
            assertReachable(app, ids: [id])
            shot(app, "\(category)-\(id)")
        }
        attachTree(app, "settings-reachable-\(category)")
    }

    func testSettingsReachableAtXXXL() {
        assertSettingsReachable(at: "UICTContentSizeCategoryXXXL")
    }

    func testSettingsReachableAtAX5() {
        assertSettingsReachable(at: "UICTContentSizeCategoryAccessibilityXXXL")
    }

    // MARK: AX5 walk of the screens not covered above (Review Focus 4)

    private let ax5Category = "UICTContentSizeCategoryAccessibilityXXXL"

    /// A control that exists, is hittable and lies inside the window. A failure is recorded with the tree and a screenshot
    /// (`continueAfterFailure` is on in the walk) so one bad control does not hide the others; returns whether it is fine.
    @discardableResult
    private func expectUsable(_ app: XCUIApplication, _ control: XCUIElement, _ what: String, screen: String,
                              timeout: TimeInterval = 15) -> Bool {
        guard control.waitForExistence(timeout: timeout) else {
            attachTree(app, "ax5-\(screen)-\(what)-missing"); shot(app, "ax5-\(screen)-\(what)-missing")
            XCTFail("AX5 \(screen): \(what) does not exist")
            return false
        }
        let window = app.windows.firstMatch.frame.insetBy(dx: -1, dy: -1)
        var ok = true
        if !control.isHittable { XCTFail("AX5 \(screen): \(what) is not hittable, frame \(control.frame)"); ok = false }
        if !window.contains(control.frame) { XCTFail("AX5 \(screen): \(what) frame \(control.frame) is outside the window \(window)"); ok = false }
        if !ok { attachTree(app, "ax5-\(screen)-\(what)-unusable"); shot(app, "ax5-\(screen)-\(what)-unusable") }
        return ok
    }

    private func anyLabel(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    /// The iOS "You have changed the icon for ..." SpringBoard alert that the discrete-mode launch argument can trigger sits
    /// over the app and hides its tree (run evidence: the Download launch saw only that alert). Only SpringBoard alerts are touched.
    private func dismissSpringBoardAlert() {
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if alert.exists, alert.buttons.firstMatch.exists { alert.buttons.firstMatch.tap() }
    }

    private func upButton(_ app: XCUIApplication) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label IN {'Back','Navigate up'}")).firstMatch
    }

    /// The download consent dialog at AX5 (a real defect was found here: its answers sat outside the dialog). Accessibility
    /// check: Cancel is hittable, and "Yes" (exact, not "Yes, do not ask again") becomes hittable by scrolling INSIDE the
    /// dialog with bounded drags; then "Yes" is tapped. Does nothing when no dialog appears (consent already persisted).
    private func confirmDownloadConsentAtAX5(_ app: XCUIApplication) {
        let cancel = app.buttons.matching(NSPredicate(format: "label == 'Cancel'")).firstMatch
        let yes = app.buttons.matching(NSPredicate(format: "label == 'Yes'")).firstMatch
        _ = waitUntil(timeout: 90) { self.dismissSpringBoardAlert(); return cancel.exists || self.element(app, "debug-feature-ready").exists }
        guard cancel.exists else { return }
        shot(app, "ax5-download-consent"); attachTree(app, "ax5-download-consent")
        expectUsable(app, cancel, "consent Cancel", screen: "download-consent")
        // Drags start and end in the middle of the screen, where the dialog is; a drag over the scrim would not scroll it.
        var drags = 0
        while !(yes.exists && yes.isHittable) && drags < 12 {
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.62))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.40))
            from.press(forDuration: 0.1, thenDragTo: to)
            usleep(400_000)
            drags += 1
        }
        shot(app, "ax5-download-consent-scrolled"); attachTree(app, "ax5-download-consent-scrolled")
        if expectUsable(app, yes, "consent 'Yes' (after \(drags) scroll drags inside the dialog)", screen: "download-consent", timeout: 5) {
            yes.tap()
        }
    }

    /// Opens a real feature route (`-featureScenario`) at AX5 on a fresh workspace with KJV (the other walks' launch).
    private func launchScenarioAtAX5(_ scenario: String) -> XCUIApplication {
        let app = launch(["-standardTestFixture", "-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16",
                          "-featureScenario", scenario, "-UIPreferredContentSizeCategoryName", ax5Category])
        if scenario == "download" {
            // The persecuted-country consent dialog hides the ready marker; its exact 'Yes' button (not 'Yes, do not ask again').
            confirmDownloadConsentAtAX5(app)
        }
        let ready = element(app, "debug-feature-ready")
        harnessRequire(waitUntil(timeout: 90) { self.dismissSpringBoardAlert(); return ready.exists }, app, "ax5-\(scenario)-not-ready")
        return app
    }

    /// Every screen is opened through its real route (the DEBUG feature scenarios used by the RTL and H2 walks, or the
    /// Settings row for the text display screen), then its primary controls must be hittable and inside the window.
    /// The legacy-migration progress view is NOT walked: its only debug route (`-seedLegacyFixture <dir>`, IosLegacyDebugSeed)
    /// needs the unpacked legacy fixtures (opt-in `I3B_FIXTURE_DIR`, see LegacyMigrationUITests), so it has no standalone route here.
    func testAccessibilitySizeKeepsControlsReachable() {
        continueAfterFailure = true

        // Download: title, Back, the language filter and the first catalogue row.
        var app = launchScenarioAtAX5("download")
        expectUsable(app, anyLabel(app, "Download Documents"), "title", screen: "download")
        expectUsable(app, upButton(app), "Back", screen: "download")
        let filter = app.staticTexts.matching(NSPredicate(format: "label == 'All'")).firstMatch
        if expectUsable(app, filter, "language filter 'All'", screen: "download", timeout: 60) {
            // First row below the filter bar: the catalogue arrives from the network, so allow it time.
            let firstRow = { () -> XCUIElement? in
                app.buttons.allElementsBoundByIndex.first {
                    $0.exists && !$0.label.isEmpty && !$0.frame.isEmpty && $0.frame.minY > filter.frame.maxY
                }
            }
            let loaded = waitUntil(timeout: 60) { firstRow() != nil }
            if loaded, let row = firstRow() {
                expectUsable(app, row, "first catalogue row", screen: "download")
            } else {
                attachTree(app, "ax5-download-no-row"); shot(app, "ax5-download-no-row")
                XCTFail("AX5 download: no catalogue row below the language filter within 60 s")
            }
        }
        shot(app, "ax5-download"); attachTree(app, "ax5-download")
        app.terminate()

        // Bookmarks: title, Back, Manage Labels, the sort toggle, and the first row or the empty-list text.
        app = launchScenarioAtAX5("bookmarks")
        expectUsable(app, anyLabel(app, "Bookmarks & My Notes"), "title", screen: "bookmarks")
        expectUsable(app, upButton(app), "Back", screen: "bookmarks")
        let manageLabels = anyLabel(app, "Manage Labels")
        expectUsable(app, manageLabels, "Manage Labels action", screen: "bookmarks")
        expectUsable(app, app.descendants(matching: .any).matching(
            NSPredicate(format: "label IN {'Date order','Biblical order'}")).firstMatch, "sort toggle", screen: "bookmarks")
        // Rows are Buttons labelled "<date>, <text>" (or "<reference>, <date>, <text>") below the 'Filter by label, ...' button;
        // the per-row 'Show full text' buttons are not rows. An empty list shows its text instead.
        let filterBar = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Filter by label'")).firstMatch
        let emptyList = anyLabel(app, "No items to display")
        let firstBookmarkRow = { () -> XCUIElement? in
            guard filterBar.exists else { return nil }
            return app.buttons.allElementsBoundByIndex.first {
                $0.exists && !$0.label.isEmpty && $0.label != "Show full text" && $0.frame.minY > filterBar.frame.maxY - 1
            }
        }
        _ = waitUntil(timeout: 20) { firstBookmarkRow() != nil || emptyList.exists }
        if let row = firstBookmarkRow() {
            expectUsable(app, row, "first bookmark row", screen: "bookmarks", timeout: 5)
        } else {
            expectUsable(app, emptyList, "empty-list text", screen: "bookmarks", timeout: 5)
        }
        shot(app, "ax5-bookmarks"); attachTree(app, "ax5-bookmarks")

        // The first screen reachable from Bookmarks: Manage Labels. Its Back and its content must be usable too.
        if manageLabels.exists && manageLabels.isHittable {
            manageLabels.tap()
            let closedBookmarks = waitUntil(timeout: 20) { !manageLabels.exists }
            if closedBookmarks {
                expectUsable(app, upButton(app), "Back", screen: "manage-labels")
                shot(app, "ax5-manage-labels"); attachTree(app, "ax5-manage-labels")
            } else {
                attachTree(app, "ax5-manage-labels-not-opened"); XCTFail("AX5 bookmarks: Manage Labels did not open")
            }
        }
        app.terminate()

        // Text display settings (font size and the other display options), opened from the real Settings row.
        app = launch(["-standardTestFixture", "-task5Settings", "-UIPreferredContentSizeCategoryName", ax5Category])
        harnessRequire(waitForSettingsRoot(app), app, "ax5-settings-root-missing")
        let row = revealSettingsRow(app, "global_text_display_settings")
        row.tap()
        let displayTitle = anyLabel(app, "Global text options")
        if expectUsable(app, displayTitle, "title", screen: "text-display") {
            let leave = app.buttons.matching(NSPredicate(format: "label IN {'Back','Navigate up','Close'}")).firstMatch
            expectUsable(app, leave, "Back or Close", screen: "text-display")
        }
        shot(app, "ax5-text-display"); attachTree(app, "ax5-text-display")
        app.terminate()
    }

    /// Controller sets the simulator to Large first, then changes it to AX5 after the ready marker.
    /// No category launch override: this exercises the running app's actual system-trait updates.
    func testFontScaleFollowsLiveSystemTextSizeChange() throws {
        guard ProcessInfo.processInfo.environment["DYNAMIC_TYPE_LIVE_CHANGE"] == "1" else {
            throw XCTSkip("Requires a controller-owned live simulator content-size change")
        }
        let app = launch(["-standardTestFixture", "-debugFontScaleProbe"])
        let probe = element(app, "ab-font-scale")
        XCTAssertTrue(probe.waitForExistence(timeout: 60))
        let initial = try XCTUnwrap(Float(probe.value as? String ?? ""))
        XCTAssertEqual(initial, 1.0, accuracy: 0.01, "Controller must establish the default Large baseline first")
        attachTree(app, "font-scale-live-before")
        XCUIDevice.shared.press(.home)
        print("DYNAMIC_TYPE_LIVE_READY initial=\(initial) app=background")
        // Controller changes simctl content_size, then activates this same running app.
        XCTAssertTrue(waitUntil(timeout: 90) { app.state == .runningForeground }, "Controller must foreground the app after changing the system category")
        let increased = waitUntil(timeout: 30) {
            guard let current = Float(probe.value as? String ?? "") else { return false }
            return current > initial + 0.1
        }
        let system = element(app, "ab-font-scale-system")
        let systemValue = system.value as? String ?? "missing"
        let diagnostic = XCTAttachment(string: "compose=\(probe.value ?? "missing") UIKit=\(systemValue)")
        diagnostic.name = "font-scale-live-boundary-evidence"
        diagnostic.lifetime = .keepAlways
        add(diagnostic)
        attachTree(app, "font-scale-live-after")
        XCTAssertTrue(systemValue.contains("app=UICTContentSizeCategoryAccessibilityXXXL"),
                      "System change must reach UIApplication before attributing a stale density to Compose: \(systemValue)")
        XCTAssertTrue(increased, "Actual Compose LocalDensity must react to the live Large-to-AX5 system change")
        XCTAssertEqual(app.state, .runningForeground)
        XCTAssertEqual(Float(probe.value as? String ?? "") ?? .nan, 53.0 / 17.0, accuracy: 0.0001)
        XCUIDevice.shared.press(.home)
        print("DYNAMIC_TYPE_LIVE_RESTORE_READY app=background")
        XCTAssertTrue(waitUntil(timeout: 90) { app.state == .runningForeground }, "Controller must restore Large and foreground the app")
        XCTAssertTrue(waitUntil(timeout: 30) {
            guard let current = Float(probe.value as? String ?? "") else { return false }
            return abs(current - initial) < 0.01
        }, "Actual Compose density must decrease back to Large without restarting")
        attachTree(app, "font-scale-live-restored")
    }
}
