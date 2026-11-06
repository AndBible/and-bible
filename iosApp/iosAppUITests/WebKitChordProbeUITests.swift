import XCTest

/// Issue #3980 characterization, opt-in: env `TEST_RUNNER_WEBKIT_CHORD_PROBE=1` (the runner strips the prefix).
///
/// Measures XCTest's hardware Option+Left chord in a bare contenteditable WKWebView (`-webKitChordProbe`: its own UIKit
/// window, no SwiftUI, Compose or AndBible code). Each round appends " alpha beta", sends one Option+Left chord and
/// types X; a round passes when the X lands before the last "beta". On Xcode 26.6 / iOS 26.5 simulators this failed
/// 10 of 30 rounds on iPad and 4 of 20 on iPhone: UIKit delivered the arrow press to the web view with the Option flag,
/// but the page received no ArrowLeft keydown. The app's `ReadingUITests` Option+arrow caret assertions are expected
/// failures for that reason; when this test passes on a new runtime, make them strict again.
final class WebKitChordProbeUITests: RealAppUITestCase {
    private let rounds = 20

    func testOptionLeftInBareWebKitEditor() throws {
        guard ProcessInfo.processInfo.environment["WEBKIT_CHORD_PROBE"] == "1" else {
            throw XCTSkip("opt-in #3980 system characterization: set TEST_RUNNER_WEBKIT_CHORD_PROBE=1")
        }
        let app = launch(["-webKitChordProbe"])
        let editor = app.webViews.textViews.firstMatch
        guard editor.waitForExistence(timeout: 30) else {
            attachTree(app, "chord-probe-no-editor"); XCTFail("the probe editor is not shown"); return
        }
        editor.tap()
        var outcomes: [String] = []
        var text = ""
        for round in 0..<rounds {
            // A tap at the bottom-right corner puts the caret at the end of the (wrapping) text.
            editor.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.92)).tap()
            let base = text.isEmpty ? "alpha beta" : text + " alpha beta"
            editor.typeText(text.isEmpty ? "alpha beta" : " alpha beta")
            guard waitUntil(timeout: 10, { (editor.value as? String) == base }) else {
                XCTFail("round \(round) setup: expected '\(base)', got '\(editor.value ?? "nil")'"); return
            }
            editor.typeKey(XCUIKeyboardKey.leftArrow, modifierFlags: .option)
            editor.typeText("X")
            _ = waitUntil(timeout: 5) { ((editor.value as? String) ?? "").count == base.count + 1 }
            text = (editor.value as? String) ?? ""
            let expected = String(base.dropLast(4)) + "Xbeta"
            // "...alpha betaX": the arrow was lost; "...alpha betXa": the Option modifier was lost before the app.
            outcomes.append(text == expected ? "pass" : "fail:" + String(text.suffix(12)))
            guard text.count == base.count + 1 else {
                XCTFail("round \(round): not exactly one X inserted: '\(text)'"); return
            }
        }
        let passes = outcomes.filter { $0 == "pass" }.count
        let summary = XCTAttachment(string: "passes=\(passes)/\(rounds) outcomes=\(outcomes)")
        summary.name = "chord-probe-summary"; summary.lifetime = .keepAlways; add(summary)
        XCTAssertEqual(passes, rounds, "Option+Left moved the caret in \(passes) of \(rounds) rounds: \(outcomes)")
    }
}
