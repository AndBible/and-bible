import XCTest

/// Real AppHosts screens, not the PoC harness. The finite debug navigation seam is temporary until stream R joins.
final class FeatureUITests: RealAppUITestCase {
    func testSettingsLanguageSwitch() {
        let app = freshReading(scenario: "plan-settings")
        // Daily reading offers the real selector when no plan is selected.
        let english = app.staticTexts["1-Year through Bible"].firstMatch
        XCTAssertTrue(english.waitForExistence(timeout: 30))
        english.tap()
        XCTAssertTrue(english.waitForExistence(timeout: 30), "Selected daily plan must load before switching language")
        let settings = element(app, "debug-open-settings")
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()
        let keepScreen = app.switches.matching(NSPredicate(format: "label CONTAINS 'Keep screen on'")).firstMatch
        for _ in 0..<8 where !keepScreen.isHittable { app.swipeUp() }
        XCTAssertTrue(keepScreen.exists)
        keepScreen.tap()
        let retainedValue = keepScreen.value as? String
        XCTAssertNotNil(retainedValue)
        let language = app.staticTexts["Application language"].firstMatch
        for _ in 0..<8 where !language.isHittable { app.swipeUp() }
        XCTAssertTrue(language.waitForExistence(timeout: 10))
        language.tap()
        let finnish = app.staticTexts["fi: Suomi"].firstMatch
        for _ in 0..<12 where !finnish.isHittable { app.swipeUp() }
        XCTAssertTrue(finnish.exists)
        finnish.tap()
        XCTAssertTrue(app.staticTexts["Sovelluksen asetukset"].firstMatch.waitForExistence(timeout: 15), "Settings must refresh in the same process")
        let retainedSwitch = app.switches.matching(NSPredicate(format: "label CONTAINS 'Pidä näyttö päällä'")).firstMatch
        for _ in 0..<12 where !retainedSwitch.isHittable { app.swipeDown() }
        XCTAssertTrue(retainedSwitch.exists)
        XCTAssertEqual(retainedSwitch.value as? String, retainedValue, "Language refresh must retain the edited setting")
        shot(app, "language-fi-settings")
        element(app, "debug-feature-back").tap()
        XCTAssertTrue(app.staticTexts["Vuodessa läpi Raamatun"].firstMatch.waitForExistence(timeout: 20), "The already loaded plan name must refresh without restarting")
        XCTAssertFalse(app.staticTexts["1-Year through Bible"].exists)
        shot(app, "language-fi-loaded-plan")
    }

    func testManageLabelsShowsIcons() {
        let app = freshReading(scenario: "labels")
        XCTAssertTrue(app.staticTexts["Feature star"].firstMatch.waitForExistence(timeout: 30))
        XCTAssertTrue(app.staticTexts["Feature default"].firstMatch.exists)
        XCTAssertTrue(element(app, "label-glyph-star").exists, "Custom glyph node must use the actual resource painter")
        XCTAssertTrue(element(app, "label-glyph-default").exists, "Default glyph node must use the actual resource painter")
        shot(app, "manage-labels-real-glyphs")
    }

    func testDownloadAboutDialog() {
        let app = freshReading(scenario: "download")
        let kjv = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'KJV '")).firstMatch
        for _ in 0..<15 where !kjv.isHittable { app.swipeUp() }
        XCTAssertTrue(kjv.waitForExistence(timeout: 30), "Real repository KJV module required")
        kjv.press(forDuration: 1)
        let about = app.buttons["About"].firstMatch
        XCTAssertTrue(about.waitForExistence(timeout: 10))
        about.tap()
        let metadata = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'King James'")).firstMatch
        XCTAssertTrue(metadata.waitForExistence(timeout: 15), "About must show metadata for the selected real module")
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'OSIS ID: KJV'")).firstMatch.waitForExistence(timeout: 10), "About must identify the actual KJV module, not just a background row")
        shot(app, "download-real-kjv-about")
        let ok = app.buttons.matching(NSPredicate(format: "label == 'OK'")).firstMatch
        XCTAssertTrue(ok.exists)
        ok.tap()
    }
}
