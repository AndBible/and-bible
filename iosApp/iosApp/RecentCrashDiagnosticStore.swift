import Foundation
import MetricKit

/// On-disk envelope of the retained crash diagnostic.
///
/// The Kotlin reader (`readCrashDiagnostic` in `IosBugReport.kt`, sharedUi) parses this JSON by hand: keep the key
/// names, the base64 `Data` encoding and the `.sortedKeys` output in step with it. The checked-in sample
/// `iosApp/test-fixtures/crash-envelope.json` is asserted byte-for-byte against this encoder by
/// `RecentCrashDiagnosticStoreTests` and read by `IosReportLifecycleTest` on the Kotlin side.
struct CrashDiagnosticEnvelope: Codable {
    /// Crash time, seconds since 1970.
    let occurrence: TimeInterval
    /// JSON array of MetricKit crash diagnostics; empty when the newest crash was too large or not serializable.
    let payload: Data

    static func encoded(_ envelope: CrashDiagnosticEnvelope) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(envelope)
    }
}

/// What the store needs from a MetricKit payload; lets tests supply payloads (`MXDiagnosticPayload` has no public init).
protocol CrashDiagnosticSource {
    var hasCrashes: Bool { get }
    var end: Date { get }
    var crashRepresentation: Any? { get }
}

extension MXDiagnosticPayload: CrashDiagnosticSource {
    var hasCrashes: Bool { !(crashDiagnostics?.isEmpty ?? true) }
    var end: Date { timeStampEnd }
    var crashRepresentation: Any? { dictionaryRepresentation()["crashDiagnostics"] }
}

/// Keeps the newest MetricKit crash diagnostic of the last 24 hours in one file so the bug report can attach it.
///
/// File location: `Application Support/AndBible/recent-crash-diagnostic.json`; the Kotlin reader builds the same path
/// (`recentCrashDiagnostic` in `IosBugReport.kt`). Keep both in step.
///
/// Threading: `didReceive` hops to `queue`. `save`, `saveCrashRepresentation`, `ingest` and `purgeIfStale` have no
/// state besides the file; call them on `queue` (or from a single thread, as the tests do).
final class RecentCrashDiagnosticStore: NSObject, MXMetricManagerSubscriber {
    static let shared = RecentCrashDiagnosticStore()
    static let maximumAge: TimeInterval = 86400
    static let maximumPayloadBytes = 2 * 1024 * 1024
    private let queue = DispatchQueue(label: "org.andbible.recent-crash-diagnostic")
    private let storageURL: URL
    private let clock: () -> Date

    override convenience init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        // Path shared with IosBugReport.kt (recentCrashDiagnostic).
        self.init(storageURL: support.appendingPathComponent("AndBible/recent-crash-diagnostic.json"))
    }

    init(storageURL: URL, clock: @escaping () -> Date = Date.init) {
        self.storageURL = storageURL
        self.clock = clock
        super.init()
    }

    /// Drops an expired or unreadable file, then subscribes. The report reader checks the age again, so no timer is needed.
    func start() {
        queue.async { self.purgeIfStale() }
        MXMetricManager.shared.add(self)
    }

    func purgeIfStale() {
        guard let data = try? Data(contentsOf: storageURL) else { return }
        guard let envelope = try? JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: data) else {
            remove()
            return
        }
        let age = clock().timeIntervalSince1970 - envelope.occurrence
        if age < 0 || age > Self.maximumAge { remove() }
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        queue.async { self.ingest(payloads) }
    }

    /// Stores the newest payload that carries crashes (by `timeStampEnd`); payloads without crashes are ignored.
    func ingest(_ payloads: [some CrashDiagnosticSource]) {
        guard let latest = payloads.filter(\.hasCrashes).max(by: { $0.end < $1.end }) else { return }
        saveCrashRepresentation(latest.crashRepresentation, occurrence: latest.end)
    }

    func saveCrashRepresentation(_ crashes: Any?, occurrence: Date) {
        let data: Data
        if let crashes, JSONSerialization.isValidJSONObject(crashes),
           let encoded = try? JSONSerialization.data(withJSONObject: crashes, options: [.sortedKeys]) {
            data = encoded
        } else {
            data = Data()
        }
        save(payload: data, occurrence: occurrence)
    }

    func save(payload: Data, occurrence: Date) {
        let now = clock()
        guard occurrence <= now, now.timeIntervalSince(occurrence) <= Self.maximumAge else { return }
        if let data = try? Data(contentsOf: storageURL),
           let previous = try? JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: data),
           previous.occurrence > occurrence.timeIntervalSince1970 { return }
        // Keep the timestamp as a tombstone when the newest payload is too large.
        let envelope = CrashDiagnosticEnvelope(occurrence: occurrence.timeIntervalSince1970,
                                               payload: payload.count <= Self.maximumPayloadBytes ? payload : Data())
        guard let data = try? CrashDiagnosticEnvelope.encoded(envelope) else { return }
        do {
            try FileManager.default.createDirectory(at: storageURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: storageURL, options: .atomic)
            // The atomic write replaces the file, so the flag is set after every write. A diagnostic is device-local.
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            var url = storageURL
            try url.setResourceValues(values)
        } catch {
            NSLog("Could not store the crash diagnostic: \(error)")
        }
    }

    private func remove() {
        do { try FileManager.default.removeItem(at: storageURL) } catch {
            NSLog("Could not remove the crash diagnostic: \(error)")
        }
    }
}
