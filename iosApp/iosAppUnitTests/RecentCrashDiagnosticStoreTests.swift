import Foundation
import XCTest
@testable import iosApp

final class RecentCrashDiagnosticStoreTests: XCTestCase {
    private var url: URL!
    private var now = Date(timeIntervalSince1970: 1_700_000_000.25)

    override func setUp() {
        url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: url)
    }

    private func makeStore() -> RecentCrashDiagnosticStore {
        RecentCrashDiagnosticStore(storageURL: url, clock: { [unowned self] in self.now })
    }

    private func stored() throws -> CrashDiagnosticEnvelope {
        try JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: Data(contentsOf: url))
    }

    private struct FakePayload: CrashDiagnosticSource {
        let hasCrashes: Bool
        let end: Date
        let crashRepresentation: Any?
    }

    func testOlderDeliveryCannotReplaceNewerCrash() throws {
        let store = makeStore()
        store.save(payload: Data("new".utf8), occurrence: now)
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-1))
        XCTAssertEqual(try stored().payload, Data("new".utf8))
    }

    func testOversizedNewerCrashPreventsOlderCrashReturning() throws {
        let store = makeStore()
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-2))
        store.save(payload: Data(count: RecentCrashDiagnosticStore.maximumPayloadBytes + 1), occurrence: now)
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-1))
        let envelope = try stored()
        XCTAssertTrue(envelope.payload.isEmpty)
        XCTAssertEqual(envelope.occurrence, now.timeIntervalSince1970)
    }

    func testPayloadAtTheLimitIsKept() throws {
        makeStore().save(payload: Data(count: RecentCrashDiagnosticStore.maximumPayloadBytes), occurrence: now)
        XCTAssertEqual(try stored().payload.count, RecentCrashDiagnosticStore.maximumPayloadBytes)
    }

    func testUnserializableNewerCrashLeavesTombstone() throws {
        let store = makeStore()
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-2))
        store.saveCrashRepresentation(Date(), occurrence: now)
        store.save(payload: Data("older".utf8), occurrence: now.addingTimeInterval(-1))
        let envelope = try stored()
        XCTAssertEqual(envelope.occurrence, now.timeIntervalSince1970)
        XCTAssertTrue(envelope.payload.isEmpty)
    }

    func testRejectsExpiredAndFuturePayloads() {
        let store = makeStore()
        store.save(payload: Data([1]), occurrence: now.addingTimeInterval(-86401))
        store.save(payload: Data([1]), occurrence: now.addingTimeInterval(1))
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }

    func testAcceptsExactlyTwentyFourHoursOld() throws {
        makeStore().save(payload: Data([1]), occurrence: now.addingTimeInterval(-86400))
        XCTAssertEqual(try stored().payload, Data([1]))
    }

    func testStoredFileIsExcludedFromBackup() throws {
        makeStore().save(payload: Data([1]), occurrence: now)
        XCTAssertEqual(try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    }

    func testIngestStoresNewestPayloadWithCrashesOnly() throws {
        let store = makeStore()
        let crash = { (name: String) -> Any? in [["name": name]] }
        store.ingest([
            FakePayload(hasCrashes: true, end: now.addingTimeInterval(-300), crashRepresentation: crash("older")),
            FakePayload(hasCrashes: true, end: now.addingTimeInterval(-100), crashRepresentation: crash("newest")),
            FakePayload(hasCrashes: false, end: now.addingTimeInterval(-1), crashRepresentation: crash("no-crash")),
        ])
        let envelope = try stored()
        XCTAssertEqual(envelope.occurrence, now.addingTimeInterval(-100).timeIntervalSince1970)
        XCTAssertEqual(String(decoding: envelope.payload, as: UTF8.self), #"[{"name":"newest"}]"#)
    }

    func testIngestWithoutCrashesWritesNothing() {
        makeStore().ingest([FakePayload(hasCrashes: false, end: now, crashRepresentation: nil)])
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }

    func testPurgeDeletesExpiredAndCorruptFilesButKeepsFreshOne() throws {
        let store = makeStore()
        store.save(payload: Data([1]), occurrence: now)
        store.purgeIfStale()
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        now = now.addingTimeInterval(86400)
        store.purgeIfStale()
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path), "exactly 24 h old is still fresh")
        now = now.addingTimeInterval(1)
        store.purgeIfStale()
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
        try Data("not json".utf8).write(to: url)
        store.purgeIfStale()
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }

    /// The Kotlin reader depends on this exact shape: key names, base64 Data, escaped slashes.
    func testOnDiskShapeMatchesCheckedInFixtureThatKotlinReads() throws {
        let fixtureURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("../test-fixtures/crash-envelope.json").standardized
        let fixture = try Data(contentsOf: fixtureURL)
        let payload = Data(#"[{"k":"???>>>~"}]"#.utf8)
        XCTAssertTrue(payload.base64EncodedString().contains("/") && payload.base64EncodedString().contains("+")
                      && payload.base64EncodedString().contains("="))
        now = Date(timeIntervalSince1970: 1_700_000_000.25)
        makeStore().save(payload: payload, occurrence: now)
        XCTAssertEqual(try Data(contentsOf: url), fixture)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        XCTAssertEqual(Set(object.keys), ["occurrence", "payload"])
    }
}
