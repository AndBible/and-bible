import Foundation
import MetricKit

struct CrashDiagnosticEnvelope: Codable {
    let occurrence: TimeInterval
    let payload: Data
}

final class RecentCrashDiagnosticStore: NSObject, MXMetricManagerSubscriber {
    static let shared = RecentCrashDiagnosticStore()
    private let queue = DispatchQueue(label: "org.andbible.recent-crash-diagnostic")
    private let storageURL: URL
    private let maximumBytes = 2 * 1024 * 1024
    private var expiryWork: DispatchWorkItem?

    override convenience init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.init(storageURL: support.appendingPathComponent("AndBible/recent-crash-diagnostic.json"))
    }

    init(storageURL: URL) {
        self.storageURL = storageURL
        super.init()
    }

    func start() {
        queue.async { self.scheduleExpiry() }
        MXMetricManager.shared.add(self)
    }

    private func scheduleExpiry() {
        expiryWork?.cancel()
        guard let data = try? Data(contentsOf: storageURL) else { return }
        guard let envelope = try? JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: data) else {
            try? FileManager.default.removeItem(at: storageURL)
            return
        }
        let remaining = envelope.occurrence + 86400 - Date().timeIntervalSince1970
        guard remaining > 0, remaining <= 86400 else {
            try? FileManager.default.removeItem(at: storageURL)
            return
        }
        let work = DispatchWorkItem { [weak self] in self?.scheduleExpiry() }
        expiryWork = work
        queue.asyncAfter(deadline: .now() + remaining + 0.01, execute: work)
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        queue.async {
            guard let latest = payloads.filter({ !($0.crashDiagnostics?.isEmpty ?? true) })
                .max(by: { $0.timeStampEnd < $1.timeStampEnd }),
                  let crashes = latest.dictionaryRepresentation()["crashDiagnostics"],
                  JSONSerialization.isValidJSONObject(crashes),
                  let data = try? JSONSerialization.data(withJSONObject: crashes, options: [.sortedKeys]) else { return }
            self.save(payload: data, occurrence: latest.timeStampEnd)
        }
    }

    // Called on the callback queue; tests may also invoke this directly.
    func save(payload: Data, occurrence: Date, now: Date = Date()) {
        guard occurrence <= now, now.timeIntervalSince(occurrence) <= 86400 else { return }
        if let data = try? Data(contentsOf: storageURL),
           let previous = try? JSONDecoder().decode(CrashDiagnosticEnvelope.self, from: data),
           previous.occurrence > occurrence.timeIntervalSince1970 { return }
        // Keep the timestamp as a tombstone when the newest payload is too large.
        let envelope = CrashDiagnosticEnvelope(occurrence: occurrence.timeIntervalSince1970,
                                               payload: payload.count <= maximumBytes ? payload : Data())
        guard let data = try? JSONEncoder().encode(envelope) else { return }
        try? FileManager.default.createDirectory(at: storageURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        do {
            try data.write(to: storageURL, options: .atomic)
            scheduleExpiry()
        } catch { }

    }
}
