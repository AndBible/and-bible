import Foundation

enum VerseFrequency: String, CaseIterable { case daily, every3Hours, onShuffle }

/// Pure period and selection maths, a port of the Kotlin `VerseSelector` (the vectors test pins `pick` to it).
/// `offsetMs` is the zone: milliseconds to add to a UTC epoch-ms instant to get local wall-clock time.
struct VerseSelector {
    typealias ZoneOffset = (Int64) -> Int64

    static let hourMs: Int64 = 3_600_000
    static let dayMs: Int64 = 24 * hourMs

    static func deviceZone(_ epochMs: Int64) -> Int64 {
        Int64(TimeZone.current.secondsFromGMT(for: Date(timeIntervalSince1970: Double(epochMs) / 1000))) * 1000
    }

    private static func floorDiv(_ a: Int64, _ b: Int64) -> Int64 {
        let q = a / b
        return (a % b != 0 && ((a < 0) != (b < 0))) ? q - 1 : q
    }

    private static func floorMod(_ a: Int64, _ b: Int64) -> Int64 { a - floorDiv(a, b) * b }

    static func periodIndex(nowMs: Int64, zone: ZoneOffset, frequency: VerseFrequency) -> Int64 {
        let local = nowMs + zone(nowMs)
        let epochDay = floorDiv(local, dayMs)
        switch frequency {
        case .daily: return epochDay
        case .every3Hours: return epochDay * 8 + floorDiv(floorMod(local, dayMs), hourMs) / 3
        case .onShuffle: return 0
        }
    }

    /// UTC ms at which the next period starts (> nowMs), nil for `.onShuffle`.
    static func nextPeriodStart(nowMs: Int64, zone: ZoneOffset, frequency: VerseFrequency) -> Int64? {
        let step: Int64
        switch frequency {
        case .daily: step = dayMs
        case .every3Hours: step = 3 * hourMs
        case .onShuffle: return nil
        }
        let local = nowMs + zone(nowMs)
        var boundaryLocal = (floorDiv(local, step) + 1) * step
        for _ in 0..<3 {
            let t = localToUtc(boundaryLocal, zone)
            if t > nowMs { return t }
            boundaryLocal += step
        }
        return localToUtc(boundaryLocal, zone)
    }

    private static func localToUtc(_ localMs: Int64, _ zone: ZoneOffset) -> Int64 {
        let before = localMs - zone(localMs - dayMs)
        let after = localMs - zone(localMs + dayMs)
        let valid = [before, after].filter { $0 + zone($0) == localMs }
        return valid.min() ?? max(before, after)
    }

    /// SplitMix64 hash of (seed, period, shuffle) modulo the pool size; identical to the Kotlin `VerseSelector.pick`.
    static func pick(poolSize: Int, seed: Int64, period: Int64, shuffle: Int) -> Int? {
        guard poolSize > 0 else { return nil }
        let golden: UInt64 = 0x9E37_79B9_7F4A_7C15
        var h = splitMix(UInt64(bitPattern: seed))
        h = splitMix(h ^ UInt64(bitPattern: period))
        h = splitMix(h ^ (UInt64(bitPattern: Int64(shuffle)) &* golden))
        return Int((h >> 1) % UInt64(poolSize))
    }

    private static func splitMix(_ x: UInt64) -> UInt64 {
        var z = x &+ 0x9E37_79B9_7F4A_7C15
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

/// A Verse widget's configuration (from the Edit Widget intent, or the defaults on iOS 16).
struct VerseConfig: Equatable {
    /// `label:<id>`, `random:WHOLE_BIBLE`, `random:NEW_TESTAMENT`, `random:PSALMS_PROVERBS` or `plan`.
    var sourceKey: String = "random:WHOLE_BIBLE"
    var bible: String? = nil
    var frequency: VerseFrequency = .daily
    var showButtons = true
    var showSource = true

    /// Same source and Bible = same seed and shuffle counter: iOS gives a timeline no widget-instance id.
    var shuffleKey: String { "\(sourceKey)|\(bible ?? "")" }

    var seed: Int64 {
        // FNV-1a over the key: stable across launches (Swift's hashValue is randomised per process).
        var h: UInt64 = 0xcbf2_9ce4_8422_2325
        for b in shuffleKey.utf8 { h = (h ^ UInt64(b)) &* 0x100_0000_01b3 }
        return Int64(bitPattern: h)
    }
}

/// What a Verse widget entry shows.
enum VerseContent: Equatable {
    case verse(PooledVerse, sourceName: String, periodLabel: String)
    /// No pool yet (the app has not built it): "Open AndBible to load verses", tap opens the app.
    case openApp(String)
    /// The source cannot give verses (no Bible, label missing or empty, no plan): explanatory text, tap opens the app.
    case unavailable(String)
}

struct VerseEntryModel: Equatable {
    var date: Date
    var content: VerseContent
}

enum VerseTimeline {
    /// How far ahead entries are scheduled.
    static let horizonDays = 7

    /// Entries from `now` for a week: one at each local period boundary, each picking from the pool by
    /// `VerseSelector.pick` (the pool is recycled when the periods outnumber its verses). `.onShuffle` gives one entry.
    static func entries(
        snapshot: SnapshotLoad, config: VerseConfig, now: Date,
        zone: @escaping VerseSelector.ZoneOffset = VerseSelector.deviceZone,
        formatDate: (Date) -> String = defaultDateLabel
    ) -> [VerseEntryModel] {
        guard let snap = snapshot.snapshot, snap.hasBibles else {
            return [VerseEntryModel(date: now, content: .openApp(openAppText(snapshot.snapshot)))]
        }
        if config.sourceKey.hasPrefix("label:") {
            let id = String(config.sourceKey.dropFirst("label:".count))
            guard let label = snap.labels.first(where: { $0.id == id }) else {
                return [VerseEntryModel(date: now, content: .unavailable(snap.text("widget_verse_unavailable_label_missing", "The label of this widget no longer exists. Tap to choose verses.")))]
            }
            guard snap.pool(sourceKey: config.sourceKey, bible: config.bible) != nil else {
                return [VerseEntryModel(date: now, content: .unavailable(snap.text("widget_verse_unavailable_label_empty", "This label has no Bible bookmarks yet. Tap to choose other verses.")))]
            }
            return build(snap, config, sourceName: label.name, now: now, zone: zone, formatDate: formatDate)
        }
        guard snap.pool(sourceKey: config.sourceKey, bible: config.bible) != nil else {
            if config.sourceKey == "plan" {
                return [VerseEntryModel(date: now, content: .unavailable(snap.text("widget_verse_unavailable_no_reading_plan", "No reading plan is active. Tap to choose other verses.")))]
            }
            // A random source always has a pool once the app ran with this Bible: it is simply not built yet.
            return [VerseEntryModel(date: now, content: .openApp(openAppText(snap)))]
        }
        return build(snap, config, sourceName: sourceName(snap, config.sourceKey), now: now, zone: zone, formatDate: formatDate)
    }

    static func openAppText(_ snap: WidgetSnapshot?) -> String {
        snap?.text("widget_open_app_to_load", "Open AndBible to load verses") ?? "Open AndBible to load verses"
    }

    static func sourceName(_ snap: WidgetSnapshot, _ key: String) -> String {
        if key == "plan" { return snap.text("widget_verse_source_reading_plan", "Today's reading") }
        if key.hasPrefix("random:") {
            let part = key.dropFirst("random:".count).lowercased()
            return snap.text("widget_verse_source_random_\(part)", "Random verse")
        }
        return snap.text("widget_verse_name", "Verse")
    }

    private static func build(
        _ snap: WidgetSnapshot, _ config: VerseConfig, sourceName: String, now: Date,
        zone: @escaping VerseSelector.ZoneOffset, formatDate: (Date) -> String
    ) -> [VerseEntryModel] {
        guard let pool = snap.pool(sourceKey: config.sourceKey, bible: config.bible), !pool.verses.isEmpty else {
            return [VerseEntryModel(date: now, content: .openApp(openAppText(snap)))]
        }
        let shuffle = SnapshotStore.shuffleCount(for: config.shuffleKey)
        let horizon = Int64(now.timeIntervalSince1970 * 1000) + Int64(horizonDays) * VerseSelector.dayMs
        var entries: [VerseEntryModel] = []
        var at = Int64(now.timeIntervalSince1970 * 1000)
        while true {
            let period = VerseSelector.periodIndex(nowMs: at, zone: zone, frequency: config.frequency)
            let index = VerseSelector.pick(poolSize: pool.verses.count, seed: config.seed, period: period, shuffle: shuffle) ?? 0
            let date = Date(timeIntervalSince1970: Double(at) / 1000)
            entries.append(VerseEntryModel(date: date, content: .verse(pool.verses[index], sourceName: sourceName, periodLabel: formatDate(date))))
            guard let next = VerseSelector.nextPeriodStart(nowMs: at, zone: zone, frequency: config.frequency), next < horizon else { break }
            at = next
        }
        return entries
    }

    static func defaultDateLabel(_ date: Date) -> String {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("EEEdMMM")
        return f.string(from: date)
    }
}
