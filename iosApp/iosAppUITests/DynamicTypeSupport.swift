import XCTest

/// Launches at a system category without changing the app's own text-size preferences.
func launchAtTextSize(_ app: XCUIApplication, _ category: String) {
    app.launchArguments += ["-UIPreferredContentSizeCategoryName", category]
    app.launch()
}

private func dynamicTypeElement(_ app: XCUIApplication, _ id: String) -> XCUIElement {
    app.descendants(matching: .any).matching(identifier: id).firstMatch
}

private func attachDynamicTypeTree(_ app: XCUIApplication, _ name: String) {
    let attachment = XCTAttachment(string: app.debugDescription)
    attachment.name = name
    attachment.lifetime = .keepAlways
    XCTContext.runActivity(named: name) { $0.add(attachment) }
}

/// XCUITest has no parent traversal: the smallest containing scroll view is the nearest observable ancestor.
private func nearestDynamicTypeScrollView(_ app: XCUIApplication, _ id: String) -> XCUIElement? {
    let scrollViews = app.scrollViews.allElementsBoundByIndex
    let ancestors = scrollViews.filter {
        $0.descendants(matching: .any).matching(identifier: id).firstMatch.exists
    }
    if let nearest = ancestors.min(by: {
        $0.frame.width * $0.frame.height < $1.frame.width * $1.frame.height
    }) {
        return nearest
    }
    // Lazy rows have no AX node until revealed; only an unambiguous scroll view is a safe fallback.
    return scrollViews.count == 1 ? scrollViews[0] : nil
}

/// Supply identifiers in top-to-bottom order so upward swipes can reveal lazy rows without guessing direction.
func assertReachable(_ app: XCUIApplication, ids: [String],
                     file: StaticString = #filePath, line: UInt = #line) {
    for id in ids {
        let target = dynamicTypeElement(app, id)
        for _ in 0..<20 {
            if target.exists && target.isHittable { break }
            guard let scrollView = nearestDynamicTypeScrollView(app, id) else { break }
            scrollView.swipeUp()
        }
        if !target.exists || !target.isHittable {
            attachDynamicTypeTree(app, "dynamic-type-unreachable-\(id)")
            XCTFail("Control \(id) is not reachable after at most 20 scroll-view swipes", file: file, line: line)
        }
    }
}

/// Checks only AX-label suffix and window bounds; screenshot review must still check glyph clipping and overlap.
func assertNoTruncatedStaticText(_ app: XCUIApplication, ids: [String]) {
    let window = app.windows.firstMatch
    guard window.exists else {
        attachDynamicTypeTree(app, "dynamic-type-no-window")
        XCTFail("No app window is available for text bounds checks")
        return
    }
    for id in ids {
        let text = app.staticTexts.matching(identifier: id).firstMatch
        guard text.exists else {
            attachDynamicTypeTree(app, "dynamic-type-missing-text-\(id)")
            XCTFail("Static text \(id) does not exist")
            continue
        }
        let label = text.label.trimmingCharacters(in: .whitespacesAndNewlines)
        let frame = text.frame
        let fits = !frame.isEmpty && !frame.isNull && !frame.isInfinite && window.frame.contains(frame)
        let ellipsized = label.hasSuffix("\u{2026}")
        if ellipsized || !fits {
            attachDynamicTypeTree(app, "dynamic-type-truncated-text-\(id)")
        }
        XCTAssertFalse(ellipsized, "Static text \(id) ends in an ellipsis: \(label)")
        XCTAssertTrue(fits, "Static text \(id) has frame \(frame) outside window \(window.frame)")
    }
}
