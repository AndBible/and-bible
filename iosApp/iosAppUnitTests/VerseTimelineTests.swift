import XCTest
@testable import iosApp

final class VerseTimelineTests: XCTestCase {
    /// UTC+2, no DST.
    private let zone: VerseSelector.ZoneOffset = { _ in 2 * VerseSelector.hourMs }
    /// 2026-11-20 10:30 UTC = 12:30 local.
    private let now = Date(timeIntervalSince1970: 1_763_634_600)

    private func verses(_ n: Int) -> [PooledVerse] {
        (0..<n).map { PooledVerse(reference: "Ref \($0)", text: "verse \($0)", link: "https://read.andbible.org/Gen.1.\($0 + 1)") }
    }

    private func load(pools: [VersePool], labels: [LabelOption] = [], bibles: Bool = true) -> SnapshotLoad {
        var s = WidgetSnapshot.sample
        s.pools = pools
        s.labels = labels
        if !bibles { s.bibles = []; s.defaultBible = nil }
        return .snapshot(s)
    }

    private func entries(_ l: SnapshotLoad, _ c: VerseConfig) -> [VerseEntryModel] {
        VerseTimeline.entries(snapshot: l, config: c, now: now, zone: zone, formatDate: { _ in "Fri 20 Nov" })
    }

    func testVerseTimelineChangesAtLocalMidnight() {
        let l = load(pools: [VersePool(sourceKey: "random:WHOLE_BIBLE", bible: "WEB", verses: verses(30))])
        let e = entries(l, VerseConfig())
        XCTAssertGreaterThanOrEqual(e.count, 7)
        XCTAssertEqual(e[0].date, now)
        for entry in e.dropFirst() {
            let local = Int64(entry.date.timeIntervalSince1970 * 1000) + 2 * VerseSelector.hourMs
            XCTAssertEqual(local % VerseSelector.dayMs, 0, "entry at \(entry.date) is not a local midnight")
        }
    }

    func testEveryThreeHoursHasEightEntriesPerDay() {
        let l = load(pools: [VersePool(sourceKey: "random:WHOLE_BIBLE", bible: "WEB", verses: verses(30))])
        var c = VerseConfig(); c.frequency = .every3Hours
        let e = entries(l, c)
        // Local midnight after `now` and the one after that: exactly eight boundaries in between (00, 03, ... 21).
        let dayStart = Date(timeIntervalSince1970: 1_763_679_600 - 2 * 3600) // 2026-11-21 00:00 local, as UTC
        let inDay = e.filter { $0.date >= dayStart && $0.date < dayStart.addingTimeInterval(86_400) }
        XCTAssertEqual(inDay.count, 8)
        XCTAssertEqual(e.count, 8 * 7 + 2 - 1, "now's slot (12:30 -> 15:00 is 3 left today) plus the week's boundaries")
    }

    func testOnShuffleHasOneEntry() {
        let l = load(pools: [VersePool(sourceKey: "random:WHOLE_BIBLE", bible: "WEB", verses: verses(30))])
        var c = VerseConfig(); c.frequency = .onShuffle
        XCTAssertEqual(entries(l, c).count, 1)
    }

    func testPoolRecyclesWhenExhausted() {
        let l = load(pools: [VersePool(sourceKey: "random:WHOLE_BIBLE", bible: "WEB", verses: verses(3))])
        let e = entries(l, VerseConfig())
        XCTAssertGreaterThanOrEqual(e.count, 7)
        for entry in e {
            guard case let .verse(v, _, _) = entry.content else { return XCTFail("not a verse: \(entry.content)") }
            XCTAssertTrue(["Ref 0", "Ref 1", "Ref 2"].contains(v.reference))
        }
    }

    func testMissingPoolShowsOpenAppEntry() {
        let l = load(pools: [])
        let e = entries(l, VerseConfig())
        XCTAssertEqual(e.count, 1)
        XCTAssertEqual(e[0].content, .openApp("Open AndBible to load verses"))
    }

    func testNoBiblesShowsOpenAppEntry() {
        let e = entries(load(pools: [], bibles: false), VerseConfig())
        guard case .openApp = e[0].content else { return XCTFail("expected openApp, got \(e[0].content)") }
        guard case .openApp = entries(.missing, VerseConfig())[0].content else { return XCTFail("missing snapshot must open the app") }
        guard case .openApp = entries(.unknownVersion, VerseConfig())[0].content else { return XCTFail("unknown version must open the app") }
    }

    func testDeletedLabelIsUnavailableNotACrash() {
        var c = VerseConfig(); c.sourceKey = "label:GONE"
        guard case .unavailable = entries(load(pools: []), c)[0].content else { return XCTFail("expected unavailable") }
        let known = [LabelOption(id: "L1", name: "Memory")]
        var c2 = VerseConfig(); c2.sourceKey = "label:L1"
        guard case .unavailable = entries(load(pools: [], labels: known), c2)[0].content else { return XCTFail("empty label must be unavailable") }
    }

    func testLabelSourceNameShown() {
        let known = [LabelOption(id: "L1", name: "Memory")]
        let l = load(pools: [VersePool(sourceKey: "label:L1", bible: "WEB", verses: verses(2))], labels: known)
        var c = VerseConfig(); c.sourceKey = "label:L1"
        guard case let .verse(_, name, _) = entries(l, c)[0].content else { return XCTFail("expected a verse") }
        XCTAssertEqual(name, "Memory")
    }

    func testSwiftPickMatchesKotlinVectors() throws {
        let url = try XCTUnwrap(Bundle(for: VerseTimelineTests.self).url(forResource: "verse-selector-vectors", withExtension: "json"))
        struct Vector: Decodable { var poolSize: Int; var seed: String; var period: Int64; var shuffle: Int; var index: Int }
        let vectors = try JSONDecoder().decode([Vector].self, from: Data(contentsOf: url))
        XCTAssertGreaterThan(vectors.count, 300)
        for v in vectors {
            let got = VerseSelector.pick(poolSize: v.poolSize, seed: try XCTUnwrap(Int64(v.seed)), period: v.period, shuffle: v.shuffle)
            XCTAssertEqual(got, v.index, "pool \(v.poolSize) seed \(v.seed) period \(v.period) shuffle \(v.shuffle)")
        }
        XCTAssertNil(VerseSelector.pick(poolSize: 0, seed: 1, period: 1, shuffle: 0))
    }
}
