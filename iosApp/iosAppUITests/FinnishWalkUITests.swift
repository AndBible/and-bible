import XCTest

/// Review Focus 3 of the I2 close-out: the in-app language switch to Finnish, then the screens that different streams
/// added (R3 reading chrome, H2 feature screens, M/X Backup and synchronization). Known English chrome strings must be
/// ABSENT as accessibility labels and their Finnish values present. Keys that `values-fi/strings.xml` lacks (Back,
/// Menu, More options, Close in the settings editor ...) legitimately stay English and are never asserted.
final class FinnishWalkUITests: RealAppUITestCase {
    /// English chrome string -> Finnish value, both read from `app/src/main/res/values{,-fi}/strings.xml`.
    private struct Chrome {
        let key: String
        let english: String
        let finnish: String
    }

    private let settings = Chrome(key: "settings", english: "Application preferences", finnish: "Sovelluksen asetukset")
    private let download = Chrome(key: "download", english: "Download Documents", finnish: "Lataa teoksia")
    private let all = Chrome(key: "all", english: "All", finnish: "Kaikki")
    private let bookmarks = Chrome(key: "bookmarks", english: "Bookmarks", finnish: "Kirjanmerkit")
    private let manageLabels = Chrome(key: "manage_labels", english: "Manage Labels", finnish: "Hallitse kirjanmerkkiryhmiä")
    private let backup = Chrome(key: "backup_and_restore", english: "Backup & Restore", finnish: "Varmuuskopioi & palauta")
    private let cloudSync = Chrome(key: "cloud_sync_title", english: "Device synchronization", finnish: "Laitteiden synkronointi")
    private let nightMode = Chrome(key: "options_menu_night_mode", english: "Night mode", finnish: "Yötila")
    private let history = Chrome(key: "history", english: "History", finnish: "Historia")
    private let studyPads = Chrome(key: "studypads", english: "Study Pads", finnish: "Opintoalustat")
    private let chooseDocument = Chrome(key: "chooce_document", english: "Choose Document", finnish: "Valitse teos")

    private func anyLabel(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    /// Waits (bounded) for the first Finnish value, then records every missing Finnish label and every English leftover
    /// without stopping, and always attaches a screenshot and the tree for the screen.
    private func check(_ app: XCUIApplication, _ screen: String, _ chrome: [Chrome], waitFor: Chrome? = nil) {
        let probe = waitFor ?? chrome[0]
        _ = waitUntil(timeout: 30) { self.anyLabel(app, probe.finnish).exists }
        shot(app, "fi-\(screen)")
        attachTree(app, "fi-\(screen)")
        for item in chrome {
            if !anyLabel(app, item.finnish).exists {
                XCTFail("fi \(screen): Finnish '\(item.finnish)' (\(item.key)) is not an accessibility label")
            }
            if anyLabel(app, item.english).exists {
                XCTFail("fi \(screen): English '\(item.english)' (\(item.key)) is still an accessibility label")
            }
        }
    }

    /// Reading view in the persisted language (no -standardTestFixture, which would reset `locale_pref` to en).
    private func launchReadingKeepingLanguage() -> XCUIApplication {
        let app = launch(["-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16"])
        harnessRequire(element(app, "reading-host").waitForExistence(timeout: 90), app, "fi-reading-host-missing")
        return app
    }

    /// Same bounded drawer drag as ReadingUITests: `down` reveals content above, otherwise content below.
    private func drawerDrag(_ app: XCUIApplication, down: Bool) {
        let high = app.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.40))
        let low = app.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.73))
        (down ? high : low).press(forDuration: 0.1, thenDragTo: down ? low : high)
        usleep(500_000)
    }

    /// Opens the drawer and scrolls (bounded) until the row with this exact label is hittable; nil with evidence otherwise.
    private func revealDrawerRow(_ app: XCUIApplication, _ label: String, opened: Bool) -> XCUIElement? {
        if !opened {
            let menu = app.buttons["Menu"]
            guard menu.waitForExistence(timeout: 20), menu.isHittable else {
                attachTree(app, "fi-drawer-no-menu"); XCTFail("fi drawer: Menu button not usable"); return nil
            }
            menu.tap()
        }
        let row = anyLabel(app, label)
        let first = anyLabel(app, chooseDocument.finnish)
        var rewinds = 0
        while !(first.exists && first.isHittable) && rewinds < 10 { drawerDrag(app, down: true); rewinds += 1 }
        var swipes = 0
        while !(row.exists && row.isHittable) && swipes < 12 { drawerDrag(app, down: false); swipes += 1 }
        guard row.exists && row.isHittable else {
            attachTree(app, "fi-drawer-no-\(label)"); XCTFail("fi drawer: row '\(label)' not reachable")
            return nil
        }
        return row
    }

    private func upButton(_ app: XCUIApplication) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label IN {'Back','Navigate up'}")).firstMatch
    }

    func testFinnishWalkShowsNoEnglishLeftovers() throws {
        continueAfterFailure = true
        // Real language cleanup whatever happens: -standardTestFixture rewrites `locale_pref` to en (same as the RTL walk).
        addTeardownBlock {
            let app = XCUIApplication()
            app.launchArguments += ["-task6ResetCrashState", "-standardTestFixture", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
            app.launch()
            app.terminate()
        }

        // 1. Switch the language in the running app (the FeatureUITests pattern), starting from a known English state.
        var app = launch(["-standardTestFixture", "-task5Settings"])
        harnessRequire(waitForSettingsRoot(app), app, "fi-settings-root-missing")
        shot(app, "en-settings-before-switch")
        let languageRow = revealSettingsRow(app, "locale_pref")
        languageRow.tap()
        let finnish = app.staticTexts["fi: Suomi"].firstMatch
        let ready = bringIntoBand(app, finnish, band: 0.5...0.9, maxSteps: 40)
        let finnishRows = app.buttons.containing(.staticText, identifier: "fi: Suomi")
        harnessRequire(ready && finnishRows.count == 1 && finnishRows.firstMatch.isHittable, app, "fi-choice-not-settled")
        finnishRows.firstMatch.tap()
        harnessRequire(app.staticTexts[settings.finnish].firstMatch.waitForExistence(timeout: 15), app,
                       "fi-settings-not-refreshed-in-process")
        // The runtime switch itself (same process, no relaunch): Settings is the first Finnish screen.
        check(app, "settings-runtime-switch", [settings])
        app.terminate()

        // 2. Download and Bookmarks (+ Manage Labels) through their real routes, in the persisted language.
        app = launch(["-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16", "-featureScenario", "download"])
        let yes = app.buttons.matching(NSPredicate(format: "label == 'Kyllä'")).firstMatch
        _ = waitUntil(timeout: 90) { yes.exists || self.element(app, "debug-feature-ready").exists }
        if yes.exists { shot(app, "fi-download-consent"); yes.tap() }
        harnessRequire(element(app, "debug-feature-ready").waitForExistence(timeout: 90), app, "fi-download-not-ready")
        check(app, "download", [download, all])
        app.terminate()

        app = launch(["-resetDatabases", "-openDocument", "KJV", "-openRef", "John.3.16", "-featureScenario", "bookmarks"])
        harnessRequire(element(app, "debug-feature-ready").waitForExistence(timeout: 90), app, "fi-bookmarks-not-ready")
        check(app, "bookmarks", [bookmarks, manageLabels])
        // The sort toggle is either of two labels; one of the Finnish ones must be there and neither English one.
        XCTAssertTrue(anyLabel(app, "Aikajärjestys").exists || anyLabel(app, "Kirjajärjestys").exists, "fi bookmarks: Finnish sort label missing")
        XCTAssertFalse(anyLabel(app, "Date order").exists || anyLabel(app, "Biblical order").exists, "fi bookmarks: English sort label left")
        let manage = anyLabel(app, manageLabels.finnish)
        if manage.exists && manage.isHittable {
            manage.tap()
            _ = waitUntil(timeout: 20) { !manage.exists }
            shot(app, "fi-manage-labels"); attachTree(app, "fi-manage-labels")
            XCTAssertFalse(anyLabel(app, manageLabels.english).exists, "fi manage labels: English title left")
        } else {
            XCTFail("fi bookmarks: Manage Labels action not usable")
        }
        app.terminate()

        // 3. Reading overflow (R3): 'More options' has no values-fi entry and stays English; Night mode is translated.
        app = launchReadingKeepingLanguage()
        let more = anyLabel(app, "More options")
        if more.waitForExistence(timeout: 20) && more.isHittable {
            more.tap()
            check(app, "reading-overflow", [nightMode])
        } else {
            attachTree(app, "fi-no-overflow"); XCTFail("fi overflow: 'More options' not usable")
        }
        app.terminate()

        // 4. Drawer rows (R3/H2/M), then Backup & Restore and Device synchronization through their drawer rows.
        app = launchReadingKeepingLanguage()
        let menu = app.buttons["Menu"]
        if menu.waitForExistence(timeout: 20) && menu.isHittable {
            menu.tap()
            // The drawer is a lazy list: each row is evidenced where it is reachable, then the evidence is taken.
            for item in [chooseDocument, history, studyPads, backup, cloudSync] {
                if revealDrawerRow(app, item.finnish, opened: true) == nil { continue }
                XCTAssertFalse(anyLabel(app, item.english).exists, "fi drawer: English '\(item.english)' (\(item.key)) still shown")
            }
            shot(app, "fi-drawer"); attachTree(app, "fi-drawer")
        } else {
            attachTree(app, "fi-drawer-no-menu"); XCTFail("fi drawer: Menu button not usable")
        }
        app.terminate()

        for (screen, item) in [("backup", backup), ("device-sync", cloudSync)] {
            app = launchReadingKeepingLanguage()
            if let row = revealDrawerRow(app, item.finnish, opened: false) {
                row.tap()
                // The screen is open when the reading toolbar's Menu button is gone and an Up control is there.
                let opened = upButton(app).waitForExistence(timeout: 30)
                if !opened { attachTree(app, "fi-\(screen)-not-opened"); XCTFail("fi \(screen): screen did not open") }
                check(app, screen, [item])
            }
            app.terminate()
        }
    }
}
