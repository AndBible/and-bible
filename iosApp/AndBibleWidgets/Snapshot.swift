import Foundation

/// Mirror of the Kotlin `WidgetSnapshot` (`sharedLogic/.../control/widget/WidgetSnapshot.kt`): the JSON the app writes
/// into the App Group container. The extension cannot load the Kotlin framework, so everything it shows is in here.
struct WidgetSnapshot: Codable, Equatable {
    static let supportedVersion = 1

    var version: Int
    var generatedAt: Int64
    var speak: SpeakSnapshot
    var listening: [ListeningRowSnapshot]
    var labels: [LabelOption]
    var bibles: [BibleOption]
    var defaultBible: String?
    var pools: [VersePool]
    var strings: [String: String]

    /// A pre-localised text; `fallback` (English) when the key is missing.
    func text(_ key: String, _ fallback: String) -> String { strings[key] ?? fallback }

    /// The pool of `sourceKey` in `bible` (nil = the default Bible).
    func pool(sourceKey: String, bible: String?) -> VersePool? {
        guard let wanted = bible ?? defaultBible else { return nil }
        return pools.first { $0.sourceKey == sourceKey && $0.bible == wanted }
    }

    var hasBibles: Bool { !bibles.isEmpty }
}

struct SpeakSnapshot: Codable, Equatable {
    /// `SPEAKING`, `PAUSED` or `STOPPED`.
    var phase: String
    var title: String?
    var reference: String?
    var translation: String?
    var percent: Int?
    var verseText: String
    var nextVerseText: String?
    var statusLine: String
    var sleepMinutesLeft: Int?
    var autoBookmarkOn: Bool

    var isSpeaking: Bool { phase == "SPEAKING" }
}

struct ListeningRowSnapshot: Codable, Equatable, Identifiable {
    var bookmarkId: String
    var reference: String
    var translation: String
    var repeats: Bool
    var percent: Int?
    var current: Bool
    var id: String { bookmarkId }
}

struct LabelOption: Codable, Equatable, Identifiable { var id: String; var name: String }
struct BibleOption: Codable, Equatable, Identifiable {
    var initials: String
    var abbreviation: String
    var id: String { initials }
}

struct VersePool: Codable, Equatable {
    /// `label:<id>`, `random:<PART>` or `plan`.
    var sourceKey: String
    var bible: String
    var verses: [PooledVerse]
}

struct PooledVerse: Codable, Equatable {
    var reference: String
    var text: String
    /// `https://read.andbible.org/...` link of the verse (the app's own URL form).
    var link: String
}

/// What reading the snapshot file gave.
enum SnapshotLoad: Equatable {
    case snapshot(WidgetSnapshot)
    /// No file yet (the app never ran since install, or no Bible was ready) or unreadable.
    case missing
    /// Written by a newer app than this extension understands: show the placeholder, not wrong data.
    case unknownVersion

    static func decode(_ data: Data) -> SnapshotLoad {
        struct VersionOnly: Decodable { var version: Int }
        guard let v = try? JSONDecoder().decode(VersionOnly.self, from: data) else { return .missing }
        guard v.version == WidgetSnapshot.supportedVersion else { return .unknownVersion }
        guard let snap = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) else { return .missing }
        return .snapshot(snap)
    }

    var snapshot: WidgetSnapshot? {
        if case let .snapshot(s) = self { return s }
        return nil
    }
}

extension WidgetSnapshot {
    /// English sample for the widget gallery and placeholders.
    static let sample = WidgetSnapshot(
        version: supportedVersion, generatedAt: 0,
        speak: SpeakSnapshot(
            phase: "SPEAKING", title: "Psalm 23", reference: "Psalm 23:1", translation: "WEB", percent: 34,
            verseText: "The LORD is my shepherd; I shall not want.",
            nextVerseText: "He makes me lie down in green pastures. He leads me beside still waters.",
            statusLine: "Psalm 23:1 · 34 % · WEB", sleepMinutesLeft: 18, autoBookmarkOn: true),
        listening: [
            ListeningRowSnapshot(bookmarkId: "1", reference: "Psalm 23:1", translation: "WEB", repeats: false, percent: 34, current: true),
            ListeningRowSnapshot(bookmarkId: "2", reference: "John 3:16", translation: "WEB", repeats: true, percent: 12, current: false),
            ListeningRowSnapshot(bookmarkId: "3", reference: "Romans 8:28", translation: "KJV", repeats: false, percent: 90, current: false),
            ListeningRowSnapshot(bookmarkId: "4", reference: "Pilgrim's Progress, Part 1", translation: "PilgrimsProgress", repeats: false, percent: nil, current: false),
            ListeningRowSnapshot(bookmarkId: "5", reference: "Genesis 1:1", translation: "WEB", repeats: false, percent: 2, current: false),
        ],
        labels: [], bibles: [BibleOption(initials: "WEB", abbreviation: "WEB")], defaultBible: "WEB",
        pools: [VersePool(sourceKey: "random:WHOLE_BIBLE", bible: "WEB", verses: [
            PooledVerse(reference: "Psalm 23:1", text: "The LORD is my shepherd; I shall not want.",
                        link: "https://read.andbible.org/Ps.23.1?document=WEB&v11n=KJV&ordinal=0")])],
        strings: [:])
}
