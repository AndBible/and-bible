import XCTest
@testable import iosApp

/// The Kotlin `WidgetSnapshot` JSON (explicit nulls, as kotlinx.serialization writes them) against the extension's Codable mirror.
final class WidgetSnapshotDecodingTests: XCTestCase {
    private func json(version: Int) -> Data {
        """
        {"version":\(version),"generatedAt":1763632000000,
         "speak":{"phase":"PAUSED","title":null,"reference":"Psalm 23:1","translation":"WEB","percent":34,
                  "verseText":"The LORD is my shepherd.","nextVerseText":null,"statusLine":"Continue from Psalm 23:1 · WEB",
                  "sleepMinutesLeft":18,"autoBookmarkOn":true},
         "listening":[{"bookmarkId":"b1","reference":"John 3:16","translation":"WEB","repeats":true,"percent":12,"current":true},
                      {"bookmarkId":"g1","reference":"Pilgrim","translation":"PP","repeats":false,"percent":null,"current":false}],
         "labels":[{"id":"L1","name":"Memory"}],
         "bibles":[{"initials":"WEB","abbreviation":"WEB"}],
         "defaultBible":"WEB",
         "strings":{"widget_speak_name":"Speak"}}
        """.data(using: .utf8)!
    }

    /// The pools file as `WidgetPools` serializes it.
    private func poolsJson(version: Int = 1) -> Data {
        """
        {"version":\(version),"pools":[{"sourceKey":"label:L1","bible":"WEB","verses":[{"reference":"John 3:16","text":"For God so loved","link":"https://read.andbible.org/John.3.16?document=WEB"}]}]}
        """.data(using: .utf8)!
    }

    func testDecodesVersion1Snapshot() throws {
        guard case let .snapshot(s) = SnapshotLoad.decode(json(version: 1), poolsData: poolsJson()) else { return XCTFail("not a snapshot") }
        XCTAssertEqual(s.speak.phase, "PAUSED")
        XCTAssertNil(s.speak.title)
        XCTAssertEqual(s.speak.percent, 34)
        XCTAssertEqual(s.listening.map(\.percent), [12, nil])
        XCTAssertEqual(s.defaultBible, "WEB")
        XCTAssertEqual(s.pool(sourceKey: "label:L1", bible: nil)?.verses.first?.reference, "John 3:16")
        XCTAssertNil(s.pool(sourceKey: "label:L1", bible: "KJV"))
        XCTAssertEqual(s.text("widget_speak_name", "x"), "Speak")
        XCTAssertEqual(s.text("missing", "fallback"), "fallback")
    }

    func testSnapshotWithoutPoolsFileHasNoPools() throws {
        let s = try XCTUnwrap(SnapshotLoad.decode(json(version: 1)).snapshot)
        XCTAssertEqual(s.speak.phase, "PAUSED")
        XCTAssertTrue(s.pools.isEmpty)
    }

    func testPoolsFileOfUnknownVersionOrGarbageIsIgnored() throws {
        XCTAssertTrue(try XCTUnwrap(SnapshotLoad.decode(json(version: 1), poolsData: poolsJson(version: 2)).snapshot).pools.isEmpty)
        XCTAssertTrue(try XCTUnwrap(SnapshotLoad.decode(json(version: 1), poolsData: Data("nope".utf8)).snapshot).pools.isEmpty)
    }

    func testSnapshotNeverWritesPools() throws {
        let data = try JSONEncoder().encode(WidgetSnapshot.sample)
        XCTAssertFalse(String(decoding: data, as: UTF8.self).contains("pools"))
    }

    func testOpenAppTextComesFromSnapshotStrings() {
        var s = WidgetSnapshot.sample
        XCTAssertEqual(WidgetSnapshot.openAppText(s), "Open AndBible")
        s.strings["widget_open_app"] = "Avaa AndBible"
        XCTAssertEqual(WidgetSnapshot.openAppText(s), "Avaa AndBible")
        XCTAssertEqual(WidgetSnapshot.openAppText(nil), "Open AndBible")
    }

    func testUnknownVersionGivesPlaceholder() {
        XCTAssertEqual(SnapshotLoad.decode(json(version: 2)), .unknownVersion)
        XCTAssertEqual(SnapshotLoad.decode(json(version: 0)), .unknownVersion)
    }

    func testGarbageIsMissing() {
        XCTAssertEqual(SnapshotLoad.decode(Data("not json".utf8)), .missing)
        XCTAssertEqual(SnapshotLoad.decode(Data("{\"version\":1}".utf8)), .missing)
    }

    func testInnerLinkIsFullyPercentEncoded() {
        let link = "https://read.andbible.org/John%203:16?document=KJV&v11n=KJVA&ordinal=12#x"
        let url = WidgetLinks.open(link: link).absoluteString
        let inner = String(url.dropFirst("andbible-widget://open?link=".count))
        for c in ["&", "?", "=", "#", "/", ":"] { XCTAssertFalse(inner.contains(c), "inner link still contains \(c): \(inner)") }
        XCTAssertEqual(inner.removingPercentEncoding, link)
    }
}
