import XCTest

/// BibleView page asset routes on iOS (#3918): an add-on background image chosen in Color settings and an EPUB image
/// both render in the reading pane. The debug fixture (`-seedPageAssets`, `IosPageAssetsDebugSeed`) installs a striped
/// background image module ("PageAssetsBg") and an unpacked EPUB (`Epub-PageAssets`) with one 300x200 picture;
/// `-probePageAssets` logs each route's HTTP status (`PAGE-ASSETS-PROBE` in the app log) for the status note.
final class PageAssetsUITests: RealAppUITestCase {
    private func require(_ condition: Bool, _ app: XCUIApplication, _ step: String,
                         file: StaticString = #filePath, line: UInt = #line) {
        if !condition { attachTree(app, step); shot(app, step) }
        XCTAssertTrue(condition, step, file: file, line: line)
    }

    private func byLabel(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    private func tap(_ app: XCUIApplication, _ target: XCUIElement, _ step: String, timeout: TimeInterval = 30) {
        require(target.waitForExistence(timeout: timeout), app, "Missing control: \(step)")
        for _ in 0..<4 where !target.isHittable { app.swipeUp() }
        require(target.isHittable && target.isEnabled, app, "Unusable control: \(step)")
        target.tap()
    }

    /** Removes the fixture books and files and the chosen background (fresh databases), so later classes start clean. */
    override func tearDown() {
        let app = launch(["-standardTestFixture", "-resetDatabases", "-clearPageAssets"])
        _ = element(app, "reading-host").waitForExistence(timeout: 90)
        _ = waitUntil(timeout: 3) { false }
        app.terminate()
        super.tearDown()
    }

    func testBackgroundImageAndEpubImageRender() {
        // 1. Choose the seeded background image for the workspace through the real Color settings screen.
        var app = launch(["-standardTestFixture", "-resetDatabases", "-seedPageAssets", "-openRef", "John.3.16"])
        require(element(app, "reading-host").waitForExistence(timeout: 90), app, "Reading shown")
        tap(app, app.buttons["More options"].firstMatch, "more-options")
        tap(app, app.buttons["All text options …"].firstMatch, "all-text-options")
        tap(app, byLabel(app, "Color settings"), "color-settings")
        tap(app, app.buttons["Change"].firstMatch, "background-image-change")
        let tile = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'PageAssetsBg'")).firstMatch
        tap(app, tile, "seeded background tile")
        shot(app, "background-chosen")
        for _ in 0..<4 {
            let back = app.buttons["Back"].firstMatch
            if back.exists && back.isHittable { back.tap() } else { break }
            _ = waitUntil(timeout: 2) { false }
        }
        require(element(app, "reading-host").waitForExistence(timeout: 30), app, "Back on reading")
        _ = waitUntil(timeout: 3) { false }
        shot(app, "bible-with-background")
        app.terminate()

        // 2. Open the EPUB fixture in the same workspace (the background stays chosen) and check its picture.
        app = launch(["-standardTestFixture", "-seedPageAssets", "-probePageAssets",
                      "-openDocument", "Epub-PageAssets", "-openRef", "Chapter with image"])
        defer { app.terminate() }
        require(element(app, "reading-host").waitForExistence(timeout: 90), app, "Reading shown (EPUB)")
        let heading = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS 'served from /epub/'")).firstMatch
        require(heading.waitForExistence(timeout: 60), app, "EPUB chapter text shown")
        let picture = app.webViews.images.matching(NSPredicate(format: "label == 'EPUB fixture picture'")).firstMatch
        let loaded = waitUntil(timeout: 20) { picture.exists && picture.frame.width > 100 && picture.frame.height > 60 }
        _ = waitUntil(timeout: 10) { false } // let the probe (8 s after open) reach the log
        shot(app, "epub-with-background")
        let a = XCTAttachment(string: "picture exists=\(picture.exists) frame=\(picture.frame)"); a.name = "epub-picture-frame"
        a.lifetime = .keepAlways; add(a)
        require(loaded, app, "EPUB picture rendered at its own size (a missing image is an alt-text box)")
    }
}
