import XCTest

/// Native boundary only. FinnishHostWalkTest, DownloadHostTest and WorkspaceSwitchContentTest scan
/// the complete shared chrome against the shipped resources, including the real Manage Labels route.
/// Here the runtime language switch must reach UIKit accessibility, survive process relaunch, and
/// remain tappable through the reading drawer and the bookmark-to-label-manager navigation.
final class FinnishWalkUITests: RealAppUITestCase {
    private static var kjvEnsured = false

    override func setUp() {
        super.setUp()
        if !Self.kjvEnsured { ensureKJVInstalled(); Self.kjvEnsured = true }
    }

    private func anyLabel(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    private func drawerDrag(_ app: XCUIApplication, down: Bool) {
        let high = app.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.40))
        let low = app.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.73))
        (down ? high : low).press(forDuration: 0.1, thenDragTo: down ? low : high)
        usleep(500_000)
    }

    func testFinnishWalkShowsNoEnglishLeftovers() throws {
        addTeardownBlock {
            let app = XCUIApplication()
            app.launchArguments += ["-task6ResetCrashState", "-standardTestFixture", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
            app.launch()
            app.terminate()
        }
        var app = launch(["-standardTestFixture", "-task5Settings"])
        harnessRequire(waitForSettingsRoot(app), app, "fi-settings-root-missing")
        revealSettingsRow(app, "locale_pref").tap()
        let finnish = app.staticTexts["fi: Suomi"].firstMatch
        let ready = bringIntoBand(app, finnish, band: 0.5...0.9, maxSteps: 40)
        let choices = app.buttons.containing(.staticText, identifier: "fi: Suomi")
        harnessRequire(ready && choices.count == 1 && choices.firstMatch.isHittable, app, "fi-choice-not-settled")
        choices.firstMatch.tap()
        harnessRequire(anyLabel(app, "Sovelluksen asetukset").waitForExistence(timeout: 15), app, "fi-runtime-switch-missing")
        app.terminate()

        // Relaunch without the fixture: locale_pref must persist into the actual native host.
        app = launch(["-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16", "-featureScenario", "bookmarks"])
        harnessRequire(element(app, "debug-feature-ready").waitForExistence(timeout: 90), app, "fi-bookmarks-not-ready")
        let manage = anyLabel(app, "Hallitse kirjanmerkkiryhmi\u{00e4}")
        harnessRequire(manage.waitForExistence(timeout: 15) && manage.isHittable, app, "fi-manage-labels-not-hittable")
        manage.tap()
        let back = app.buttons.matching(NSPredicate(format: "label IN {'Back','Navigate up'}")).firstMatch
        harnessRequire(anyLabel(app, "Ryhm\u{00e4}t").waitForExistence(timeout: 20) && back.isHittable, app, "fi-label-manager-not-open")
        shot(app, "fi-native-manage-labels")
        back.tap()
        harnessRequire(anyLabel(app, "Kirjanmerkit & Omat Merkinn\u{00e4}t").waitForExistence(timeout: 20), app, "fi-bookmarks-return-missing")
        app.terminate()

        for title in ["Varmuuskopioi & palauta", "Laitteiden synkronointi"] {
            app = launch(["-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16"])
            harnessRequire(element(app, "reading-host").waitForExistence(timeout: 90), app, "fi-reading-missing")
            let menu = app.buttons["Valikko"]
            harnessRequire(menu.waitForExistence(timeout: 20) && menu.isHittable, app, "fi-native-menu-missing")
            menu.tap()
            let row = anyLabel(app, title)
            let first = anyLabel(app, "Valitse teos")
            var rewinds = 0
            while !(first.exists && first.isHittable) && rewinds < 10 { drawerDrag(app, down: true); rewinds += 1 }
            var swipes = 0
            while !(row.exists && row.isHittable) && swipes < 12 { drawerDrag(app, down: false); swipes += 1 }
            harnessRequire(row.exists && row.isHittable, app, "fi-native-drawer-row-missing")
            var last = CGRect.null
            _ = waitUntil(timeout: 5) { let frame = row.frame; defer { last = frame }; return frame == last }
            row.tap()
            let destinationBack = app.buttons.matching(NSPredicate(format: "label IN {'Back','Navigate up'}")).firstMatch
            if !destinationBack.waitForExistence(timeout: 10), row.exists { row.tap() }
            harnessRequire(destinationBack.waitForExistence(timeout: 30) && anyLabel(app, title).exists, app, "fi-native-drawer-destination-missing")
            shot(app, "fi-native-\(title)")
            app.terminate()
        }
    }
}
