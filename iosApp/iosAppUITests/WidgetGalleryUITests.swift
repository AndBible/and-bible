import XCTest

/// Drives SpringBoard to add the AndBible widgets to the home screen and takes screenshots (evidence for the widget
/// work, #184). Skipped unless `WIDGET_SHOTS` is set (`TEST_RUNNER_WIDGET_SHOTS=1`); not part of the smoke set.
/// Recipe and traps: docs/superpowers/status/2026-11-20-ios-widget-intent-spike.md.
final class WidgetGalleryUITests: XCTestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    private var env: [String: String] { ProcessInfo.processInfo.environment }

    override func setUp() { continueAfterFailure = false }

    private func attach(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); a.name = name; a.lifetime = .keepAlways; add(a)
    }

    private func attachTree(_ name: String) {
        let a = XCTAttachment(string: springboard.debugDescription); a.name = "tree-\(name)"; a.lifetime = .keepAlways; add(a)
    }

    private func find(_ labels: [String], timeout: TimeInterval = 8) -> XCUIElement? {
        let end = Date().addingTimeInterval(timeout)
        repeat {
            for l in labels {
                for format in ["label == %@", "label BEGINSWITH %@", "label CONTAINS %@"] {
                    let m = springboard.descendants(matching: .any).matching(NSPredicate(format: format, l)).firstMatch
                    if m.exists { return m }
                }
            }
            Thread.sleep(forTimeInterval: 0.5)
        } while Date() < end
        return nil
    }

    private func tapCoordinate(_ x: CGFloat, _ y: CGFloat) {
        springboard.coordinate(withNormalizedOffset: CGVector(dx: x, dy: y)).tap()
    }

    /// Opens the widget gallery from the home screen and searches for AndBible.
    private func openGallery() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1)
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45)).press(forDuration: 1.5)
        Thread.sleep(forTimeInterval: 1)
        attach("a-after-longpress")
        if let edit = find(["Edit Home Screen", "Edit"], timeout: 4) { edit.tap() }
        Thread.sleep(forTimeInterval: 1)
        attach("b-edit-mode")
        if let add = find(["Add Widget", "Add"], timeout: 5) { add.tap() } else { tapCoordinate(0.07, 0.06) }
        Thread.sleep(forTimeInterval: 5)
        attach("c-gallery")
        if let search = find(["Search Widgets"], timeout: 10) { search.tap() }
        springboard.typeText("AndBible")
        Thread.sleep(forTimeInterval: 2)
        attach("d-search")
        // The result row is the app; tapping it lists that app's widgets.
        tapCoordinate(0.3, 0.28)
        Thread.sleep(forTimeInterval: 3)
        attach("e-app-widgets")
    }

    private func launchApp() {
        let app = XCUIApplication()
        app.launchArguments += ["-task6ResetCrashState", "-discrete_mode", "NO", "-show_calculator", "NO", "-standardTestFixture", "-openRef", "John.3.16"]
        app.launch()
        Thread.sleep(forTimeInterval: 30)
        attach("0-app")
        app.terminate()
    }

    /// From the open gallery search, opens AndBible's widget carousel and scrolls to page `k`.
    private func selectPage(_ k: Int) {
        if let search = find(["Search Widgets"], timeout: 10) { search.tap() }
        springboard.typeText("AndBible")
        Thread.sleep(forTimeInterval: 2)
        tapCoordinate(0.3, 0.28)
        Thread.sleep(forTimeInterval: 3)
        for _ in 0..<k {
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
                .press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.5)))
            Thread.sleep(forTimeInterval: 1.2)
        }
    }

    private func addFromGallery() {
        tapCoordinate(0.5, 0.94)
        Thread.sleep(forTimeInterval: 2)
    }

    /// Adds the carousel pages in `pages` to the home screen and screenshots it.
    func testAddWidgets() throws {
        guard env["WIDGET_SHOTS"] != nil else { throw XCTSkip("WIDGET_SHOTS not set") }
        XCUIDevice.shared.appearance = .light
        launchApp()
        let pages = (env["WIDGET_PAGES"] ?? "0,1,3,5").split(separator: ",").compactMap { Int($0) }
        var first = true
        for k in pages {
            if !first { tapCoordinate(0.18, 0.035); Thread.sleep(forTimeInterval: 1) }
            if first {
                XCUIDevice.shared.press(.home)
                Thread.sleep(forTimeInterval: 1)
                springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45)).press(forDuration: 1.5)
                Thread.sleep(forTimeInterval: 1)
                if let edit = find(["Edit Home Screen", "Edit"], timeout: 4) { edit.tap() }
                Thread.sleep(forTimeInterval: 1)
                first = false
            }
            if let add = find(["Add Widget", "Add"], timeout: 5) { add.tap() } else { tapCoordinate(0.07, 0.06) }
            Thread.sleep(forTimeInterval: 5)
            selectPage(k)
            attach("gallery-page-\(k)")
            addFromGallery()
            attach("after-add-\(k)")
        }
        // Leave edit mode (Done is top right), then screenshot the home screen.
        tapCoordinate(0.9, 0.06)
        Thread.sleep(forTimeInterval: 2)
        attach("home-light")
        attachTree("home")
        XCUIDevice.shared.appearance = .dark
        Thread.sleep(forTimeInterval: 3)
        attach("home-dark")
        XCUIDevice.shared.appearance = .light
    }

    /// Taps the Speak widget's play button with the app terminated: speech must start headless (iOS 17+), the app UI must
    /// not open. Needs the widgets of `testAddWidgets` on the home screen (the simulator keeps them).
    func testPlayTapHeadless() throws {
        guard env["WIDGET_SHOTS"] != nil else { throw XCTSkip("WIDGET_SHOTS not set") }
        XCUIDevice.shared.appearance = .light
        launchApp()
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 2)
        attach("tap-0-before")
        let app = XCUIApplication()
        let stateBefore = app.state.rawValue
        tapCoordinate(0.73, 0.235)
        Thread.sleep(forTimeInterval: 10)
        attach("tap-1-after-play")
        let note = XCTAttachment(string: "app state before=\(stateBefore) after=\(app.state.rawValue) (1 not running, 3 background, 4 foreground)")
        note.name = "app-state"; note.lifetime = .keepAlways; add(note)
        Thread.sleep(forTimeInterval: 8)
        attach("tap-2-later")
        tapCoordinate(0.73, 0.235)
        Thread.sleep(forTimeInterval: 6)
        attach("tap-3-after-second-tap")
        XCUIDevice.shared.appearance = .dark
        Thread.sleep(forTimeInterval: 3)
        attach("tap-4-dark")
        XCUIDevice.shared.appearance = .light
    }

    /// Screenshots every page of the AndBible widget carousel.
    func testGalleryPages() throws {
        guard env["WIDGET_SHOTS"] != nil else { throw XCTSkip("WIDGET_SHOTS not set") }
        let app = XCUIApplication()
        app.launchArguments += ["-task6ResetCrashState", "-discrete_mode", "NO", "-show_calculator", "NO", "-standardTestFixture", "-openRef", "John.3.16"]
        app.launch()
        Thread.sleep(forTimeInterval: 30)
        attach("0-app")
        app.terminate()
        openGallery()
        for i in 0..<12 {
            attach(String(format: "page-%02d", i))
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
                .press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.5)))
            Thread.sleep(forTimeInterval: 1.5)
        }
    }

    func testExploreGallery() throws {
        guard env["WIDGET_SHOTS"] != nil else { throw XCTSkip("WIDGET_SHOTS not set") }
        let app = XCUIApplication()
        app.launchArguments += ["-task6ResetCrashState", "-discrete_mode", "NO", "-show_calculator", "NO", "-standardTestFixture", "-openRef", "John.3.16"]
        app.launch()
        Thread.sleep(forTimeInterval: 30)
        attach("0-app")
        app.terminate()
        openGallery()
    }
}
