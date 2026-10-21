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
        XCTAssertTrue(element(app, first).waitForExistence(timeout: 60), "Real Settings root must appear")
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
