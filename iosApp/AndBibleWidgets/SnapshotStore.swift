import Foundation

/// Reads the snapshot the app wrote and records which Bibles the Verse widgets want pools for. Both files live in the
/// App Group container (`AppGroupIdentifier` in Info.plist, set per configuration by project.yml).
enum SnapshotStore {
    static let snapshotFile = "widget-snapshot.json"
    static let poolsFile = "widget-pools.json"
    static let requestsFile = "widget-requests.json"
    static let maxRequestedBibles = 6

    static var groupId: String? { Bundle.main.object(forInfoDictionaryKey: "AppGroupIdentifier") as? String }

    static var containerURL: URL? {
        groupId.flatMap { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) }
    }

    static func load() -> SnapshotLoad {
        guard let dir = containerURL,
              let data = try? Data(contentsOf: dir.appendingPathComponent(snapshotFile)) else { return .missing }
        return SnapshotLoad.decode(data, poolsData: try? Data(contentsOf: dir.appendingPathComponent(poolsFile)))
    }

    /// Asks the app to build pools in `initials` too (it reads `widget-requests.json` when it rewrites the snapshot,
    /// which it does the next time it runs or is opened). Keeps the newest few requests.
    static func requestBible(_ initials: String) {
        guard let url = containerURL?.appendingPathComponent(requestsFile) else { return }
        struct Requests: Codable { var bibles: [String] }
        var current = (try? Data(contentsOf: url)).flatMap { try? JSONDecoder().decode(Requests.self, from: $0) }?.bibles ?? []
        guard !current.contains(initials) else { return }
        current.append(initials)
        if current.count > maxRequestedBibles { current.removeFirst(current.count - maxRequestedBibles) }
        if let data = try? JSONEncoder().encode(Requests(bibles: current)) { try? data.write(to: url, options: .atomic) }
    }

    /// Shuffle counters of the Verse widgets, by configuration key (see `VerseConfig.shuffleKey`).
    /// Unit tests set this to a throwaway suite so they never touch the real App Group defaults.
    static var defaultsOverride: UserDefaults?

    static var defaults: UserDefaults? { defaultsOverride ?? groupId.flatMap { UserDefaults(suiteName: $0) } }

    static func shuffleCount(for key: String) -> Int { defaults?.integer(forKey: "shuffle.\(key)") ?? 0 }
    static func bumpShuffle(for key: String) { defaults?.set(shuffleCount(for: key) + 1, forKey: "shuffle.\(key)") }
}
