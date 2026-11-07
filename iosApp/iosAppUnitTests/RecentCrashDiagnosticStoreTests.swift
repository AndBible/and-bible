import Foundation
import XCTest
@testable import iosApp

final class RecentCrashDiagnosticStoreTests: XCTestCase {
    func testOlderDeliveryCannotReplaceNewerCrash() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let store = RecentCrashDiagnosticStore(storageURL: url)
        let now = Date()
        store.save(payload: Data("new".utf8), occurrence: now, now: now)
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-1), now: now)
        let envelope = try JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: Data(contentsOf: url))
        XCTAssertEqual(envelope.payload, Data("new".utf8))
    }

    func testOversizedNewerCrashPreventsOlderCrashReturning() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let store = RecentCrashDiagnosticStore(storageURL: url)
        let now = Date()
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-2), now: now)
        store.save(payload: Data(count: 2 * 1024 * 1024 + 1), occurrence: now, now: now)
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-1), now: now)
        let envelope = try JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: Data(contentsOf: url))
        XCTAssertTrue(envelope.payload.isEmpty)
        XCTAssertEqual(envelope.occurrence, now.timeIntervalSince1970)
    }

    func testUnserializableNewerCrashLeavesTombstone() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let store = RecentCrashDiagnosticStore(storageURL: url)
        let now = Date()
        store.save(payload: Data("old".utf8), occurrence: now.addingTimeInterval(-2), now: now)
        store.saveCrashRepresentation(Date(), occurrence: now, now: now)
        store.save(payload: Data("older".utf8), occurrence: now.addingTimeInterval(-1), now: now)
        let envelope = try JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: Data(contentsOf: url))
        XCTAssertEqual(envelope.occurrence, now.timeIntervalSince1970)
        XCTAssertTrue(envelope.payload.isEmpty)
    }

    func testRejectsExpiredFutureAndOversizedPayloads() {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let store = RecentCrashDiagnosticStore(storageURL: url)
        let now = Date()
        store.save(payload: Data([1]), occurrence: now.addingTimeInterval(-86401), now: now)
        store.save(payload: Data([1]), occurrence: now.addingTimeInterval(1), now: now)
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }
}
