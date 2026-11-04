import XCTest

extension RealAppUITestCase {
    struct DomWordMetrics {
        let width: CGFloat
        let height: CGFloat
        let top: CGFloat
        let color: String
    }

    struct DomReport {
        let sample: Int
        let started: TimeInterval
        let mount: String
        let genesisFixture: String?
        let words: [DomWordMetrics]?
    }

    /// Decode all fields from one accessibility snapshot, not separate samples.
    func readingDomReport(_ app: XCUIApplication) -> DomReport? {
        let probe = element(app, "reading-dom-probe")
        guard probe.exists, let json = probe.value as? String, let data = json.data(using: .utf8),
              let report = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sample = report["sample"] as? Int, let started = report["started"] as? TimeInterval,
              abs(Date().timeIntervalSince1970 - started) <= 10,
              let mount = report["mount"] as? String else { return nil }
        let words = (report["verse16"] as? [[String: Any]]).flatMap { values -> [DomWordMetrics]? in
            guard values.count == 4 else { return nil }
            let metrics = values.compactMap { value -> DomWordMetrics? in
                guard let width = value["width"] as? Double, width > 0,
                      let height = value["height"] as? Double, height > 0,
                      let top = value["top"] as? Double, let color = value["color"] as? String else { return nil }
                return DomWordMetrics(width: width, height: height, top: top, color: color)
            }
            return metrics.count == 4 ? metrics : nil
        }
        return DomReport(sample: sample, started: started, mount: mount,
                         genesisFixture: report["genesisFixture"] as? String, words: words)
    }

    /// Record DOM failures without suppressing the independent AX assertions that follow.
    func withDomEvidence(_ body: () -> Void) {
        let prior = continueAfterFailure
        continueAfterFailure = true
        defer { continueAfterFailure = prior }
        body()
    }

    func settledDomVerse16(_ app: XCUIApplication, _ step: String, after: TimeInterval = 0,
                           matching: ([DomWordMetrics]) -> Bool = { _ in true }) -> [DomWordMetrics] {
        var previous: DomReport?
        var result: [DomWordMetrics]?
        let ready = waitUntil(timeout: 30) {
            guard let now = self.readingDomReport(app), now.started > after,
                  let words = now.words, words.count == 4, matching(words) else { previous = nil; return false }
            defer { previous = now }
            guard let prior = previous, prior.mount == now.mount, prior.sample != now.sample,
                  let old = prior.words, old.count == 4 else { return false }
            if zip(old, words).allSatisfy({ $0.width == $1.width && $0.height == $1.height && $0.color == $1.color }) {
                result = words
                return true
            }
            return false
        }
        withDomEvidence { XCTAssertTrue(ready, "Independent DOM geometry for John 3:16 after \(step)") }
        let evidence = XCTAttachment(string: String(describing: readingDomReport(app)))
        evidence.name = "dom-\(step)"; evidence.lifetime = .keepAlways; add(evidence)
        return result ?? []
    }
}
