import XCTest

/// A remote-controlled session for manual real-account checks (e.g. a Google sign-in that needs a human relay),
/// skipped unless `REMOTE_DRIVER_DIR` (pass it as `TEST_RUNNER_REMOTE_DRIVER_DIR`) names a directory on the Mac.
///
/// The test launches the app with the space-separated `REMOTE_DRIVER_ARGS`, then polls `<dir>/cmd` once a second.
/// Each line of that file is one command; after running them the file is deleted and `<dir>/screen.png`,
/// `<dir>/tree.txt` and `<dir>/ack` (the commands' results) are written. Commands:
/// - `tap <label>`: taps the first element whose label equals (else begins with) `<label>`, searching the app,
///   the out-of-process web sign-in sheet (`SafariViewService`) and SpringBoard alerts;
/// - `tapxy <x> <y>`: taps the app at normalized screen coordinates (0..1);
/// - `pressxy <x> <y>`: long-presses (1.5 s) at normalized screen coordinates;
/// - `type <text>`: types into the focused field of the sheet or app;
/// - `typeline <file> <n>`: types line `n` (1-based) of a file without echoing it (secrets);
/// - `swipeup` / `swipedown`; `relaunch` (terminate and launch again with the same arguments); `snap` (only refresh);
/// - `done`: ends the test.
/// The session ends by itself after `REMOTE_DRIVER_TIMEOUT` seconds (default 1800). Nothing is asserted.
final class RemoteDriverUITests: XCTestCase {
    func testRemoteDriver() throws {
        let env = ProcessInfo.processInfo.environment
        guard let dirPath = env["REMOTE_DRIVER_DIR"], !dirPath.isEmpty else {
            throw XCTSkip("REMOTE_DRIVER_DIR not set")
        }
        let dir = URL(fileURLWithPath: dirPath)
        let fm = FileManager.default
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        let timeout = TimeInterval(env["REMOTE_DRIVER_TIMEOUT"] ?? "") ?? 1800
        let args = (env["REMOTE_DRIVER_ARGS"] ?? "").split(separator: " ").map(String.init)

        let app = XCUIApplication()
        app.launchArguments = args
        app.launch()
        let sheet = XCUIApplication(bundleIdentifier: "com.apple.SafariViewService")
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

        func find(_ label: String) -> XCUIElement? {
            for target in [sheet, springboard, app] {
                for format in ["label == %@", "label BEGINSWITH %@", "placeholderValue == %@"] {
                    let match = target.descendants(matching: .any).matching(NSPredicate(format: format, label)).firstMatch
                    if match.exists && match.isHittable { return match }
                }
            }
            return nil
        }

        func typeTarget() -> XCUIApplication { sheet.state == .runningForeground ? sheet : app }

        func snapshot(_ ack: String) {
            let png = XCUIScreen.main.screenshot().pngRepresentation
            try? png.write(to: dir.appendingPathComponent("screen.png"))
            var tree = app.debugDescription
            if sheet.state == .runningForeground { tree += "\n--- SafariViewService ---\n" + sheet.debugDescription }
            try? tree.write(to: dir.appendingPathComponent("tree.txt"), atomically: true, encoding: .utf8)
            try? ack.write(to: dir.appendingPathComponent("ack"), atomically: true, encoding: .utf8)
        }

        snapshot("started \(Date())")
        let cmdURL = dir.appendingPathComponent("cmd")
        let deadline = Date().addingTimeInterval(timeout)
        var finished = false
        while !finished && Date() < deadline {
            guard let text = try? String(contentsOf: cmdURL, encoding: .utf8) else {
                Thread.sleep(forTimeInterval: 1); continue
            }
            try? fm.removeItem(at: cmdURL)
            var results: [String] = []
            for raw in text.split(separator: "\n") {
                let line = raw.trimmingCharacters(in: .whitespaces)
                if line.isEmpty { continue }
                let parts = line.split(separator: " ", maxSplits: 1).map(String.init)
                let rest = parts.count > 1 ? parts[1] : ""
                switch parts[0] {
                case "tap":
                    if let e = find(rest) { e.tap(); results.append("tap ok: \(rest)") } else { results.append("tap NOT FOUND: \(rest)") }
                case "tapxy":
                    let xy = rest.split(separator: " ").compactMap { Double($0) }
                    if xy.count == 2 {
                        app.coordinate(withNormalizedOffset: CGVector(dx: xy[0], dy: xy[1])).tap(); results.append("tapxy ok")
                    } else { results.append("tapxy bad args") }
                case "pressxy":
                    let xy = rest.split(separator: " ").compactMap { Double($0) }
                    if xy.count == 2 {
                        app.coordinate(withNormalizedOffset: CGVector(dx: xy[0], dy: xy[1])).press(forDuration: 1.5)
                        results.append("pressxy ok")
                    } else { results.append("pressxy bad args") }
                case "type":
                    typeTarget().typeText(rest); results.append("typed \(rest.count) chars")
                case "typeline":
                    let a = rest.split(separator: " ").map(String.init)
                    if a.count == 2, let n = Int(a[1]), let content = try? String(contentsOfFile: a[0], encoding: .utf8) {
                        let lines = content.components(separatedBy: .newlines)
                        if n >= 1 && n <= lines.count {
                            typeTarget().typeText(lines[n - 1]); results.append("typed line \(n) (\(lines[n - 1].count) chars)")
                        } else { results.append("typeline: no line \(n)") }
                    } else { results.append("typeline: bad args or unreadable file") }
                case "swipeup": app.swipeUp(); results.append("swipeup ok")
                case "swipedown": app.swipeDown(); results.append("swipedown ok")
                case "relaunch": app.terminate(); app.launch(); results.append("relaunched")
                case "snap": results.append("snap")
                case "done": finished = true; results.append("done")
                default: results.append("unknown: \(parts[0])")
                }
                Thread.sleep(forTimeInterval: 1)
            }
            Thread.sleep(forTimeInterval: 2)
            snapshot(results.joined(separator: "\n") + "\n\(Date())")
        }
    }
}
