import XCTest
import UIKit

/// Production feature UI -> default file ports -> real Files destination proof.
/// Debug contract: -filesFeatureTest mydocs|prompts -filesFeatureToken UUID, after -resetData.
/// A separate harness seeds real DAOs after reset, selects a real external folder, then navigates
/// only when files-feature-enter is tapped. It must never invoke export/import itself.
/// mydocs: document F Documents TOKEN, page F-page-TOKEN (Markdown), exact payload
/// "# F page TOKEN\n\nPayload; \"quoted\", end.\n"; unique page title is under 50 characters.
/// prompts: editable F-prompt-TOKEN, description "Description; \"TOKEN\"", template
/// "First TOKEN; \"quoted\"\nSecond TOKEN", other CSV fields at defaults.
/// Seed a credential-free CUSTOM provider (https://files-fixture.invalid, OPENAI API format),
/// refresh actual PromptService and await configured=true; no key/model/network or gate override.
/// Verification tags require independent selected-folder reads and actual DAO checks, never toasts.
/// Files controls reuse observed selectors. Feature menu hierarchy/filename/import selectors below
/// remain unobserved until actual runtime evidence; ambiguity fails instead of choosing arbitrarily.
final class FilesFeatureUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
        addUIInterruptionMonitor(withDescription: "Notification permission only") { alert in
            guard alert.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
                  alert.buttons["Don't Allow"].exists else { return false }
            alert.buttons["Don't Allow"].tap()
            return true
        }
    }

    private func element(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func evidence(_ app: XCUIApplication, _ step: String) {
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = step
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let tree = XCTAttachment(string: app.debugDescription + "\nSpringBoard:\n" +
            XCUIApplication(bundleIdentifier: "com.apple.springboard").debugDescription)
        tree.name = "tree-\(step)"
        tree.lifetime = .keepAlways
        add(tree)
    }

    private func visible(_ target: XCUIElement, app: XCUIApplication, step: String) {
        let found = target.waitForExistence(timeout: 30)
        evidence(app, "\(step)-\(found ? "visible" : "timeout")")
        XCTAssertTrue(found, "Missing actual feature/system UI at \(step)")
    }

    private func tap(_ target: XCUIElement, app: XCUIApplication, step: String) {
        visible(target, app: app, step: step)
        XCTAssertTrue(target.isHittable && target.isEnabled, "Unusable control at \(step)")
        target.tap()
    }

    private func wait(_ app: XCUIApplication, step: String, condition: @escaping () -> Bool) {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in condition() }, object: nil)
        let result = XCTWaiter.wait(for: [expectation], timeout: 30)
        evidence(app, step)
        XCTAssertEqual(result, .completed, step)
    }

    private func launch(_ mode: String, token: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-task6ResetCrashState", "-standardTestFixture", "-discrete_mode", "NO", "-show_calculator", "NO",
                               "-resetData", "-filesFeatureTest", mode, "-filesFeatureToken", token,
                               "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if alert.waitForExistence(timeout: 5),
           alert.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] 'notifications'")).count > 0,
           alert.buttons["Don't Allow"].exists { alert.buttons["Don't Allow"].tap() }
        visible(element(app, "files-feature-seeded"), app: app, step: "feature-real-dao-seeded")
        XCTAssertFalse(element(app, "files-feature-error").exists)
        return app
    }

    private func localRoot(_ app: XCUIApplication) -> XCUIElement {
        app.otherElements.matching(NSPredicate(format:
            "identifier BEGINSWITH 'DOC.browsingRoot Source: com.apple.FileProvider.LocalStorage,'")).firstMatch
    }

    private func local(_ app: XCUIApplication) {
        let name = UIDevice.current.userInterfaceIdiom == .pad ? "On My iPad" : "On My iPhone"
        let navigation = localRoot(app).navigationBars["FullDocumentManagerViewControllerNavigationBar"]
        let rootTitle = navigation.staticTexts.matching(NSPredicate(format:
            "label == %@ OR label == %@", name, "\(name), Actions Menu")).firstMatch
        if rootTitle.exists { return }
        if navigation.buttons[name].exists && navigation.buttons[name].isHittable {
            navigation.buttons[name].tap()
        } else if UIDevice.current.userInterfaceIdiom == .pad {
            tap(app.cells["DOC.sidebar.item.On My iPad"].firstMatch, app: app, step: "files-local-location")
        } else {
            let location = app.cells.matching(NSPredicate(format: "label == %@", name)).firstMatch
            if !location.exists { tap(app.buttons["Browse"].firstMatch, app: app, step: "files-browse") }
            tap(location, app: app, step: "files-local-location")
        }
        visible(rootTitle, app: app, step: "files-local-root")
    }

    private func folder(_ app: XCUIApplication, name: String) {
        let title = localRoot(app).navigationBars["FullDocumentManagerViewControllerNavigationBar"]
            .buttons.matching(NSPredicate(format: "label == %@", "\(name), Actions Menu")).firstMatch
        if !title.exists {
            let target = localRoot(app).cells.matching(identifier: "\(name), Folder").firstMatch
            let grid = localRoot(app).collectionViews["File View"].firstMatch
            visible(grid, app: app, step: "files-folder-grid")
            // Files virtualizes offscreen cells, so search even before the exact UUID cell exists.
            for _ in 0..<12 {
                if target.exists && target.isHittable { break }
                grid.swipeDown()
            }
            for _ in 0..<24 {
                if target.exists && target.isHittable { break }
                grid.swipeUp()
            }
            evidence(app, "files-exact-folder-scrolled")
            let matches = localRoot(app).cells.matching(identifier: "\(name), Folder")
            XCTAssertEqual(matches.count, 1, "Cannot uniquely identify the exact UI-created folder")
            guard matches.count == 1 else { return }
            var previousSnapshot: [String] = []
            var stableObservations = 0
            var observations: [String] = []
            let started = Date()
            let settled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                let unique = matches.count == 1
                let hittable = unique && target.isHittable
                do {
                    let gridSnapshot = try grid.snapshot()
                    func visibleCells(_ node: XCUIElementSnapshot) -> [String] {
                        let row = node.elementType == .cell && node.frame.intersects(gridSnapshot.frame)
                            ? ["\(node.identifier)|\(NSCoder.string(for: node.frame))"] : []
                        return row + node.children.flatMap { visibleCells($0) }
                    }
                    let snapshot = visibleCells(gridSnapshot) + ["grid|\(NSCoder.string(for: gridSnapshot.frame))"]
                    let changed = snapshot != previousSnapshot
                    stableObservations = unique && hittable && !changed ? stableObservations + 1 : 0
                    observations.append("index=\(observations.count + 1) elapsed=\(Date().timeIntervalSince(started)) unique=\(unique) hittable=\(hittable) changed=\(changed) diff=\(changed ? snapshot.description : "none")")
                    previousSnapshot = snapshot
                    return stableObservations >= 2
                } catch {
                    stableObservations = 0
                    previousSnapshot = []
                    observations.append("index=\(observations.count + 1) elapsed=\(Date().timeIntervalSince(started)) snapshotError=\(error)")
                    return false
                }
            }, object: nil)
            let settledResult = XCTWaiter.wait(for: [settled], timeout: 20)
            let diagnostics = XCTAttachment(string: observations.joined(separator: "\n"))
            diagnostics.name = "feature-folder-stability-observations"
            diagnostics.lifetime = .keepAlways
            add(diagnostics)
            evidence(app, "files-exact-folder-settled")
            XCTAssertEqual(settledResult, .completed, "Exact folder/grid did not settle before tapping")
            guard settledResult == .completed else { return }
            tap(target, app: app, step: "files-exact-folder-scrolled")
        }
        visible(title, app: app, step: "files-exact-folder-open")
        XCTAssertTrue(title.isHittable)
    }

    private func selectFolder(_ app: XCUIApplication, name: String) {
        tap(element(app, "files-feature-folder-select"), app: app, step: "feature-folder-selection")
        local(app)
        if UIDevice.current.userInterfaceIdiom == .pad {
            let newFolders = localRoot(app).navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons
                .matching(identifier: "New Folder")
            visible(newFolders.firstMatch, app: app, step: "files-new-folder")
            XCTAssertEqual(newFolders.count, 1, "Cannot uniquely identify the observed iPad New Folder toolbar button")
            guard newFolders.count == 1 else { return }
            tap(newFolders.firstMatch, app: app, step: "files-new-folder")
        } else {
            tap(localRoot(app).navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons["More"].firstMatch,
                app: app, step: "files-more")
            tap(app.descendants(matching: .any).matching(NSPredicate(format: "label == 'New Folder'")).firstMatch,
                app: app, step: "files-new-folder")
        }
        let rename = localRoot(app).textViews["DOC.inlineRenameField"].firstMatch
        visible(rename, app: app, step: "files-inline-rename")
        XCTAssertTrue(rename.isHittable)
        rename.typeText(name)
        evidence(app, "files-exact-folder-name")
        XCTAssertEqual(rename.value as? String, name)
        tap(app.buttons["Done"].firstMatch, app: app, step: "files-rename-done")
        wait(app, step: "files-rename-dismissed") { !rename.exists }
        folder(app, name: name)
        let open = app.buttons["Open"].firstMatch
        tap(open, app: app, step: "files-folder-confirm")
        wait(app, step: "files-folder-picker-dismissed") { !open.exists }
        visible(element(app, "files-feature-folder-ready"), app: app, step: "feature-retained-real-folder")
        // This tag additionally proves the expected per-mode filename is absent in the selected folder.
        visible(element(app, "files-feature-destination-absent"), app: app, step: "feature-destination-preflight")
        tap(element(app, "files-feature-enter"), app: app, step: "feature-production-navigation")
        visible(element(app, "files-feature-route-ready"), app: app, step: "feature-production-route")
    }

    private func button(_ app: XCUIApplication, label: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func rowMenu(_ app: XCUIApplication, title: String) {
        let row = app.staticTexts.matching(NSPredicate(format: "label == %@", title)).firstMatch
        visible(row, app: app, step: "feature-exact-row")
        // Unobserved Compose iOS hierarchy: require exactly one trailing menu aligned to this row.
        let menus = app.buttons.matching(NSPredicate(format: "label == 'menu' OR label == 'Menu'"))
            .allElementsBoundByIndex.filter {
                $0.isHittable && $0.frame.minX >= row.frame.maxX &&
                    $0.frame.minY <= row.frame.midY && $0.frame.maxY >= row.frame.midY
            }
        evidence(app, "feature-row-menu-candidates")
        XCTAssertEqual(menus.count, 1, "Cannot uniquely identify target row overflow")
        guard menus.count == 1 else { return }
        menus[0].tap()
    }

    private func topMenu(_ app: XCUIApplication) {
        // Unobserved feature selector; actual toolbar must expose exactly one hittable upper menu.
        let title = app.staticTexts["AI Settings"].firstMatch
        visible(title, app: app, step: "prompt-production-title")
        let menus = app.buttons.matching(NSPredicate(format: "label == 'menu' OR label == 'Menu'"))
            .allElementsBoundByIndex.filter { $0.isHittable && abs($0.frame.midY - title.frame.midY) < 30 }
        evidence(app, "prompt-top-menu-candidates")
        XCTAssertEqual(menus.count, 1, "Cannot uniquely identify production toolbar overflow")
        guard menus.count == 1 else { return }
        menus[0].tap()
    }

    private func save(_ app: XCUIApplication, filename: String, folderName: String, confirmation: String,
                      editFilename: Bool) {
        let confirmations = app.navigationBars["FullDocumentManagerViewControllerNavigationBar"].buttons
            .matching(NSPredicate(format: "label == %@", confirmation))
        let confirm = confirmations.firstMatch
        visible(confirm, app: app, step: "feature-native-\(confirmation)-picker")
        XCTAssertEqual(confirmations.count, 1, "Cannot uniquely identify the native picker confirmation")
        guard confirmations.count == 1 else { return }
        local(app)
        folder(app, name: folderName)
        let fields = app.textFields.allElementsBoundByIndex.filter { $0.isHittable }
        evidence(app, "feature-save-filename-fields")
        XCTAssertEqual(fields.count, 1, "Expected one actual picker filename editor")
        guard fields.count == 1 else { return }
        let field = fields[0]
        field.tap()
        let stem = (filename as NSString).deletingPathExtension
        if editFilename {
            // Observed Files editor exposes only the stem; the native picker retains its CSV extension.
            let old = field.value as? String ?? ""
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count) + stem)
        }
        evidence(app, "feature-save-filename-editing")
        XCTAssertEqual(field.value as? String, stem, "Picker stem differs from independent full-path oracle")
        tap(confirm, app: app, step: "feature-save-exact-destination")
        wait(app, step: "feature-save-picker-dismissed") { !confirm.exists }
        visible(element(app, "files-feature-saved-verified"), app: app, step: "feature-independent-saved-payload")
        XCTAssertFalse(element(app, "files-feature-error").exists)
    }

    func testMyDocumentsPageExportSavesExactPayload() {
        let token = UUID().uuidString
        let app = launch("mydocs", token: token)
        defer { app.terminate() }
        let folderName = "F MyDocs \(token)"
        let title = "F-page-\(token)"
        selectFolder(app, name: folderName)
        visible(app.staticTexts["F Documents \(token) - Pages"].firstMatch, app: app, step: "mydocs-document-title")
        rowMenu(app, title: title)
        tap(button(app, label: "Export"), app: app, step: "mydocs-real-page-export")
        tap(button(app, label: "Phone storage"), app: app, step: "mydocs-real-storage-choice")
        save(app, filename: "\(title).md", folderName: folderName, confirmation: "Save", editFilename: false)
        visible(app.staticTexts[title].firstMatch, app: app, step: "mydocs-page-after-save")
    }

    func testPromptCsvExportDeleteAndEditableImportRestoresPayload() {
        let token = UUID().uuidString
        let app = launch("prompts", token: token)
        defer { app.terminate() }
        let folderName = "F Prompts \(token)"
        let name = "F-prompt-\(token)"
        let filename = "F-prompts-\(token).csv"
        selectFolder(app, name: folderName)
        visible(app.staticTexts[name].firstMatch, app: app, step: "prompt-seeded-row")
        topMenu(app)
        tap(button(app, label: "Export prompts to CSV"), app: app, step: "prompt-real-csv-export")
        save(app, filename: filename, folderName: folderName,
             confirmation: UIDevice.current.userInterfaceIdiom == .pad ? "Save" : "Move", editFilename: true)
        rowMenu(app, title: name)
        tap(button(app, label: "Delete"), app: app, step: "prompt-real-delete")
        visible(app.staticTexts[name].firstMatch, app: app, step: "prompt-delete-confirm-title")
        tap(button(app, label: "Yes"), app: app, step: "prompt-delete-confirm")
        visible(element(app, "files-feature-prompt-absent"), app: app, step: "prompt-dao-absent-before-import")
        XCTAssertFalse(app.staticTexts[name].firstMatch.exists, "Original seed still visible before import")
        topMenu(app)
        tap(button(app, label: "Import prompts from CSV"), app: app, step: "prompt-real-csv-import")
        tap(button(app, label: "Import as editable prompts"), app: app, step: "prompt-editable-import-mode")
        let cancel = button(app, label: "Cancel")
        visible(cancel, app: app, step: "prompt-real-open-picker")
        local(app)
        folder(app, name: folderName)
        // Observed Files identity separates the full stem and extension; never accept a visual prefix.
        let stem = (filename as NSString).deletingPathExtension
        let files = localRoot(app).cells.matching(identifier: "\(stem), csv")
        visible(files.firstMatch, app: app, step: "prompt-exact-exported-csv")
        XCTAssertEqual(files.count, 1)
        tap(files.firstMatch, app: app, step: "prompt-select-exported-csv")
        wait(app, step: "prompt-import-picker-dismissed") { !cancel.exists }
        visible(element(app, "files-feature-imported-verified"), app: app, step: "prompt-dao-recreated-exact-csv-fields")
        let importedRow = app.staticTexts[name].firstMatch
        // Deletion anchors the lazy list at Study; the restored Uncategorized row is above it.
        let lists = app.scrollViews.allElementsBoundByIndex.filter { $0.isHittable }
        evidence(app, "prompt-imported-list-container")
        XCTAssertEqual(lists.count, 1, "Cannot uniquely identify the observed production prompt scroll view")
        guard lists.count == 1 else { return }
        for _ in 0..<8 {
            if importedRow.exists && importedRow.isHittable { break }
            lists[0].swipeDown()
        }
        visible(importedRow, app: app, step: "prompt-imported-production-row")
        XCTAssertTrue(importedRow.isHittable, "Exact imported UUID prompt did not become hittable after bounded scrolling")
        XCTAssertFalse(element(app, "files-feature-error").exists)
    }
}
