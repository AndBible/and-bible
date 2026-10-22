import XCTest

/// Shared KJV provisioning for the XCUITest classes that need an installed KJV (BackupUITests, FeaturePathsUITests,
/// InstallRobustnessUITests). A freshly installed app has only the bundled NASB fallback, so each such class establishes
/// its own precondition with `ensureKJVInstalled()` instead of relying on an earlier class having left KJV behind.
///
/// `provisionKJV()` is the original BackupUITests bootstrap, moved verbatim: reset launch -> controls relaunch -> Backup
/// -> Restore -> Files picker install of `H2 Fixtures/h2-kjv.zip`. Selectors are label/identifier based (no iPhone
/// geometry) and `local(_:)` has an iPad branch (sidebar `On My iPad`); the iPad Files-picker path is NOT measured yet
/// (the Backup acceptance tests skip on iPad), so a miss there is HARNESS discovery with the tree attached.
///
/// Manual prerequisites (host side, never seeded by the tests): Files > On My iPhone/iPad > `H2 Fixtures` must hold
/// `h2-kjv.zip` and `db-backup.abdb.zip`, copied with `iosApp/scripts/seed-files-provider.sh <udid> <file> "H2 Fixtures"`.
extension RealAppUITestCase {
    func harnessRequire(_ condition: Bool, _ app: XCUIApplication, _ step: String,
                        file: StaticString = #filePath, line: UInt = #line) {
        if !condition { attachTree(app, step); shot(app, step) }
        XCTAssertTrue(condition, step, file: file, line: line)
    }

    func harnessLabel(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    func harnessTapLabel(_ app: XCUIApplication, _ text: String) {
        let target = harnessLabel(app, text)
        harnessRequire(target.waitForExistence(timeout: 30), app, "Missing control: \(text)")
        if !target.isHittable { app.swipeUp() }
        harnessRequire(target.isHittable && target.isEnabled, app, "Unusable control: \(text)")
        target.tap()
    }

    /// The single real chooser toolbar title "Document" above the filter buttons (empty when the chooser is not current).
    func harnessChooserTitles(_ app: XCUIApplication) -> [XCUIElement] {
        let language = app.buttons["Language, All"].firstMatch
        let type = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Document type,'")).firstMatch
        guard language.exists && type.exists else { return [] }
        return app.staticTexts.matching(NSPredicate(format: "label == 'Document'"))
            .allElementsBoundByIndex.filter {
                !$0.frame.isEmpty && $0.frame.maxY <= min(language.frame.minY, type.frame.minY)
            }
    }

    /// Every launch with `-incomingRuntimeTest` runs the harness preflight, which fails ("An error has occurred", no
    /// `incoming-runtime-ready`) while K4RawText or the f-runtime EPUB is registered. IncomingFilesUITests and the
    /// kill test leave exactly those behind, so a later class that launches with the incoming harness (Backup,
    /// FeaturePaths) would stall for 90 s. Deletes them through the production chooser reached like a user
    /// (reading -> drawer "Choose Document" -> long press -> Delete -> Yes), on a launch WITHOUT the incoming flags.
    /// (`-featureScenario chooser` is NOT usable: it opens a selection-mode list where a long press only selects.)
    /// Nothing to clean on a Welcome store. The drawer home is the leftmost top-bar 'Menu' button (no fixed geometry);
    /// if it cannot be found (iPad drawer unmeasured) the cleanup is skipped with the tree attached, not failed.
    func removeLeftoverIncomingFixtures() {
        let app = launch(["-standardTestFixture", "-incomingRuntimeTest", "-incomingRuntimeControls", "-incomingPurgeLeftovers"])
        defer { app.terminate() }
        let welcome = element(app, "welcome-screen")
        harnessRequire(waitUntil(timeout: 90) { welcome.exists || self.element(app, "incoming-runtime-ready").exists }, app,
                       "HARNESS: incoming preflight after purging leftover fixtures")
    }

    /// Idempotent precondition: leaves KJV installed and the app terminated. Looks for a KJV chooser row on the
    /// current store (no reset); when the Welcome screen shows or the row is absent after a scroll scan, runs `provisionKJV()`.
    func ensureKJVInstalled() {
        removeLeftoverIncomingFixtures()
        let app = launch(["-standardTestFixture", "-incomingRuntimeTest", "-incomingRuntimeControls",
                          "-featureScenario", "chooser", "-openRef", "John.3.16"])
        let welcome = element(app, "welcome-screen")
        harnessRequire(waitUntil(timeout: 90) { welcome.exists || self.harnessChooserTitles(app).count == 1 }, app,
                       "HARNESS: Welcome or document chooser before the KJV presence check")
        var present = false
        if !welcome.exists {
            let row = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'KJV '")).firstMatch
            present = row.waitForExistence(timeout: 15)
            for _ in 0..<6 where !present {
                nudgeList(app, up: true)
                present = row.exists
            }
        }
        app.terminate()
        if !present { provisionKJV(cleanFirst: false) }
    }

    func provisionKJV(cleanFirst: Bool = true) {
        if cleanFirst { removeLeftoverIncomingFixtures() }
        var app = launch(["-resetDatabases", "-standardTestFixture", "-incomingRuntimeTest", "-incomingRuntimeControls",
                          "-featureScenario", "chooser", "-openRef", "John.3.16"])
        let welcome = element(app, "welcome-screen")
        harnessRequire(waitUntil(timeout: 90) { welcome.exists || self.harnessChooserTitles(app).count == 1 }, app,
                "HARNESS: real Welcome or document chooser before local KJV bootstrap")
        let installedKJV = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'KJV '")).firstMatch
        if welcome.exists || !installedKJV.waitForExistence(timeout: 10) {
            if !welcome.exists {
                // KJV is absent (e.g. a previous run uninstalled it). The measured chooser toolbar Up button is
                // covered by the debug-controls "OK" overlay pills (not hittable), so do not tap through the
                // toolbar: relaunch persisted with the debug controls, which is the same route the tests use
                // before every Backup entry, and open Backup from there.
                app = relaunchWithControls(app)
            }
            openBackup(app)
            configure(app, restore: true, database: false)
            harnessTapLabel(app, "Restore or Import from\u{2026}")
            harnessRequire(harnessLabel(app, "Load Documents From Files").waitForExistence(timeout: 30), app,
                    "HARNESS: real local KJV InstallZip prelude")
            harnessTapLabel(app, "Proceed")
            pick(app, folderName: "H2 Fixtures", filename: "h2-kjv.zip")
            awaitLocalModuleInstall(app)
            // Bootstrap only: the acceptance restore below must inspect immediate state before relaunch.
            app.terminate()
            app = launch(["-standardTestFixture", "-incomingRuntimeTest", "-incomingRuntimeControls", "-featureScenario", "chooser",
                          "-openDocument", "KJV", "-openRef", "John.3.16"])
        }
        // debug-open-chooser is an overlay, not proof that the chooser is the current destination.
        harnessRequire(waitUntil(timeout: 90) { self.harnessChooserTitles(app).count == 1 }, app,
                "HARNESS: actual document chooser context after local provisioning")
        let kjv = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'KJV '")).firstMatch
        harnessRequire(kjv.waitForExistence(timeout: 30) && kjv.isHittable, app,
                "HARNESS: actual installed KJV chooser row required, no NASB fallback")
        selectKJV(app)
        assertExactJohn316(app)
        sendToBackground(app)
        app.terminate()
    }

    func awaitLocalModuleInstall(_ app: XCUIApplication) {
        let deadline = Date().addingTimeInterval(120)
        var overwriteAccepted = false
        while Date() < deadline {
            let overwrite = app.staticTexts["Overwrite files?"].firstMatch
            if overwrite.exists {
                harnessRequire(!overwriteAccepted, app, "HARNESS: unexpected repeated overwrite prompt")
                harnessTapLabel(app, "Yes")
                overwriteAccepted = true
            }
            let error = app.staticTexts.matching(NSPredicate(format:
                "label == 'An error has occurred' OR label == 'File does not contain a valid SWORD module'"))
            harnessRequire(!error.firstMatch.exists, app, "HARNESS: local module install error")
            let backup = app.buttons["Restore or Import from\u{2026}"].firstMatch
            if !localRoot(app).exists && backup.exists && backup.isHittable
                && !app.staticTexts["Load Documents From Files"].exists && !overwrite.exists {
                attachTree(app, "h2-local-module-install-returned-backup")
                shot(app, "h2-local-module-install-returned-backup")
                return // Termination only; the chooser and exact KJV text prove installation.
            }
            usleep(500_000)
        }
        harnessRequire(false, app, "HARNESS: local module install did not return to usable Backup within 120s")
    }

    /// Persists state, terminates and relaunches with the debug controls and NO reset/document/reference
    /// override. Only for use BEFORE a Backup entry, never between restore completion and its oracles.
    func relaunchWithControls(_ app: XCUIApplication) -> XCUIApplication {
        sendToBackground(app)
        app.terminate()
        let next = launch(["-standardTestFixture", "-incomingRuntimeTest", "-incomingRuntimeControls"])
        harnessRequire(element(next, "incoming-runtime-ready").waitForExistence(timeout: 90), next,
                "HARNESS: incoming entry preflight after controls relaunch")
        return next
    }

    func openBackup(_ app: XCUIApplication) {
        let entry = element(app, "incoming-open-backup")
        harnessRequire(entry.waitForExistence(timeout: 30) && entry.isEnabled && entry.isHittable,
                app, "HARNESS: enabled existing debug backup entry")
        entry.tap()
        let documents = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Documents'"))
        let database = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Database'"))
        let restore = app.buttons.matching(NSPredicate(format: "label == %@", "Restore or Import from\u{2026}"))
        let ready = waitUntil(timeout: 30) {
            !self.element(app, "incoming-open-backup").exists
                && !self.element(app, "incoming-offer-epub").exists
                && documents.count == 2 && database.count == 2 && restore.count == 1
                && restore.element(boundBy: 0).isHittable
        }
        harnessRequire(ready, app, "HARNESS: hidden debug actions and actual ordered Backup/Restore surface")
        guard ready else { return }
        harnessRequire(documents.element(boundBy: 1).isEnabled && database.element(boundBy: 1).isEnabled,
                app, "HARNESS: enabled Restore Documents/Database switches at ordered index 1")
    }

    func switchKind(_ app: XCUIApplication, _ name: String, index: Int, on: Bool) {
        let matches = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", name))
        harnessRequire(matches.count == 2, app, "HARNESS: expected ordered backup/restore \(name) switches")
        let control = matches.element(boundBy: index)
        for _ in 0..<6 {
            if control.isHittable { break }
            app.swipeUp()
        }
        harnessRequire(control.isHittable, app, "HARNESS: reveal \(name) switch")
        if control.isSelected != on { control.tap() }
        harnessRequire(control.isSelected == on, app, "HARNESS: validate switch AX state \(name)")
    }

    func configure(_ app: XCUIApplication, restore: Bool, database: Bool) {
        let index = restore ? 1 : 0
        switchKind(app, "Database", index: index, on: database)
        switchKind(app, "Documents", index: index, on: !database)
        if !restore {
            let application = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Application'"))
            harnessRequire(application.count == 1, app, "HARNESS: Application switch AX")
            if application.firstMatch.isSelected { application.firstMatch.tap() }
            harnessRequire(!application.firstMatch.isSelected, app, "Application backup must be off")
        }
    }

    func localRoot(_ app: XCUIApplication) -> XCUIElement {
        app.otherElements.matching(NSPredicate(format:
            "identifier BEGINSWITH 'DOC.browsingRoot Source: com.apple.FileProvider.LocalStorage,'")).firstMatch
    }

    func local(_ app: XCUIApplication) {
        let name = isPad ? "On My iPad" : "On My iPhone"
        let navigation = localRoot(app).navigationBars["FullDocumentManagerViewControllerNavigationBar"]
        let title = navigation.staticTexts.matching(NSPredicate(format:
            "label == %@ OR label == %@", name, "\(name), Actions Menu")).firstMatch
        if title.exists { return }
        // The Files picker reopens in its last folder (e.g. "H2 Backup/Database-<uuid>"), where the back
        // button carries the parent folder name, not the root name. Walk up until the root title shows.
        for _ in 0..<8 where !title.exists {
            let back = navigation.buttons.element(boundBy: 0)
            guard back.exists && back.isHittable, !back.label.isEmpty, back.label != "Browse",
                  !["Cancel", "Save", "More"].contains(back.label), !back.label.hasSuffix("Actions Menu")
            else { break }
            back.tap()
            _ = waitUntil(timeout: 5) { title.exists || navigation.buttons.element(boundBy: 0).label != back.label }
        }
        if title.exists { return }
        let parent = navigation.buttons[name].firstMatch
        if parent.exists && parent.isHittable { parent.tap() } else {
            if !isPad && !app.cells[name].exists { harnessTapLabel(app, "Browse") }
            let location = isPad ? app.cells["DOC.sidebar.item.On My iPad"].firstMatch : app.cells[name].firstMatch
            harnessRequire(location.waitForExistence(timeout: 30) && location.isHittable,
                    app, "HARNESS: local Files location")
            location.tap()
        }
        harnessRequire(title.waitForExistence(timeout: 30), app, "HARNESS: local Files root")
    }

    func folder(_ app: XCUIApplication, _ name: String) {
        let root = localRoot(app)
        let grid = root.collectionViews["File View"].firstMatch
        let cell = root.cells.matching(identifier: "\(name), Folder").firstMatch
        harnessRequire(grid.waitForExistence(timeout: 30), app, "HARNESS: Files grid")
        attachTree(app, "h2-files-before-folder-scan-\(name)")
        shot(app, "h2-files-before-folder-scan-\(name)")
        for _ in 0..<24 {
            // Done only when the cell is fully on screen, not merely hittable while clipped at an edge.
            if cell.exists && cell.isHittable && app.frame.contains(cell.frame) { break }
            harnessRequire(root.exists && grid.exists, app, "HARNESS: Files provider lost before scan")
            let bounds = grid.frame.intersection(app.frame)
            harnessRequire(bounds.height > 100 && bounds.width > 40, app, "HARNESS: visible Files grid bounds")
            // Default drags content up (reveals lower rows); a cell above the visible grid top needs the reverse.
            let above = cell.exists && !cell.frame.isEmpty && cell.frame.minY < bounds.minY
            let startFraction: CGFloat = above ? 0.35 : 0.75
            let endFraction: CGFloat = above ? 0.75 : 0.35
            let start = app.coordinate(withNormalizedOffset: .zero).withOffset(
                CGVector(dx: bounds.midX, dy: bounds.minY + bounds.height * startFraction))
            let end = app.coordinate(withNormalizedOffset: .zero).withOffset(
                CGVector(dx: bounds.midX, dy: bounds.minY + bounds.height * endFraction))
            start.press(forDuration: 0.1, thenDragTo: end)
            harnessRequire(root.exists && grid.exists, app, "HARNESS: Files provider lost after forward scan")
        }
        harnessRequire(cell.exists && cell.isHittable && app.frame.contains(cell.frame), app, "HARNESS: exact folder \(name)")
        harnessRequire(waitForStableOnScreen(app, cell), app, "HARNESS: Files folder stability \(name)")
        cell.tap()
        let title = root.navigationBars["FullDocumentManagerViewControllerNavigationBar"]
            .buttons["\(name), Actions Menu"].firstMatch
        harnessRequire(title.waitForExistence(timeout: 30), app, "HARNESS: entered folder \(name)")
    }

    func pick(_ app: XCUIApplication, folderName: String, child: String? = nil, filename: String) {
        harnessRequire(app.buttons["Cancel"].firstMatch.waitForExistence(timeout: 30), app, "Actual Files import picker")
        local(app)
        folder(app, folderName)
        if let child { folder(app, child) }
        let file = localRoot(app).cells.matching(NSPredicate(format:
            "label CONTAINS %@ OR identifier CONTAINS %@", filename, filename)).firstMatch
        if !file.waitForExistence(timeout: 30) {
            let present = localRoot(app).cells.allElementsBoundByIndex.map { $0.identifier }.joined(separator: " | ")
            harnessRequire(false, app, "HARNESS: \(filename) is not in \(folderName)\(child.map { "/" + $0 } ?? "") "
                + "(seed it with iosApp/scripts/seed-files-provider.sh); folder holds: \(present)")
        }
        harnessRequire(file.isHittable, app, "HARNESS: exact seeded/exported Files item \(filename)")
        file.tap()
    }
}
