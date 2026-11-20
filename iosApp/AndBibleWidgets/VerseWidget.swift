import SwiftUI
import WidgetKit

struct VerseEntry: TimelineEntry {
    let date: Date
    let content: VerseContent
    let config: VerseConfig
}

enum VerseProviderLogic {
    static func timeline(config: VerseConfig, now: Date = Date()) -> Timeline<VerseEntry> {
        let load = SnapshotStore.load()
        if let bible = config.bible, let snap = load.snapshot, snap.pool(sourceKey: config.sourceKey, bible: bible) == nil {
            // Ask the app to build pools for this Bible; it does so the next time it writes the snapshot.
            SnapshotStore.requestBible(bible)
        }
        let models = VerseTimeline.entries(snapshot: load, config: config, now: now)
        let entries = models.map { VerseEntry(date: $0.date, content: $0.content, config: config) }
        return Timeline(entries: entries, policy: .atEnd)
    }

    static var placeholder: VerseEntry {
        let verse = WidgetSnapshot.sample.pools[0].verses[0]
        return VerseEntry(date: Date(), content: .verse(verse, sourceName: "Random verse", periodLabel: VerseTimeline.defaultDateLabel(Date())), config: VerseConfig())
    }
}

/// iOS 16: no Edit Widget configuration (that needs App Intents); a random verse of the whole Bible once a day.
struct VerseLegacyProvider: TimelineProvider {
    func placeholder(in context: Context) -> VerseEntry { VerseProviderLogic.placeholder }
    func getSnapshot(in context: Context, completion: @escaping (VerseEntry) -> Void) {
        completion(context.isPreview ? VerseProviderLogic.placeholder : VerseProviderLogic.timeline(config: VerseConfig()).entries[0])
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<VerseEntry>) -> Void) {
        completion(VerseProviderLogic.timeline(config: VerseConfig()))
    }
}

struct VerseWidgetLegacy: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AndBibleVerse", provider: VerseLegacyProvider()) { entry in
            VerseWidgetView(entry: entry)
        }
        .configurationDisplayName("Verse")
        .description("A verse a day, from the whole Bible.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
    }
}

struct VerseWidgetView: View {
    let entry: VerseEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        let p = WPalette(scheme: scheme, mode: mode)
        Group {
            switch entry.content {
            case let .verse(verse, sourceName, periodLabel):
                if family == .accessoryRectangular {
                    VerseAccessory(verse: verse)
                } else {
                    VerseBody(verse: verse, sourceName: sourceName, periodLabel: periodLabel, config: entry.config, family: family, p: p)
                }
            case let .openApp(text), let .unavailable(text):
                if family == .accessoryRectangular {
                    Text(text).font(.system(size: 12)).lineLimit(3).widgetURL(WidgetLinks.openApp)
                } else {
                    OpenAppView(text: text, palette: p, symbol: "sun.max.fill")
                }
            }
        }
        .widgetContentPadding(family == .systemLarge ? 18 : 14)
        .widgetBackground(p.container)
    }
}

struct VerseBody: View {
    let verse: PooledVerse
    let sourceName: String
    let periodLabel: String
    let config: VerseConfig
    let family: WidgetFamily
    let p: WPalette

    var body: some View {
        let textSize: CGFloat = family == .systemSmall ? 13 : (family == .systemMedium ? 15 : 21)
        let lines = family == .systemSmall ? 5 : (family == .systemMedium ? 3 : 12)
        VStack(alignment: .leading, spacing: 0) {
            WHeader(symbol: "sun.max.fill", text: config.showSource ? "\(sourceName) · \(periodLabel)" : sourceName, palette: p)
            Text(verse.text).font(.system(size: textSize, weight: .medium)).foregroundStyle(p.onSurface)
                .lineLimit(lines).minimumScaleFactor(0.8).padding(.top, family == .systemLarge ? 14 : 6)
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                Text(verse.reference).font(.system(size: family == .systemSmall ? 12 : 13, weight: .semibold))
                    .foregroundStyle(p.onSurface).lineLimit(1)
                Spacer(minLength: 0)
                if config.showButtons && family != .systemSmall {
                    WidgetControl(kind: .speak(WidgetLinks.speakPassage(link: verse.link))) {
                        WIcon(symbol: "speaker.wave.2.fill", palette: p, size: 32, glyph: 16)
                    }
                    WidgetControl(kind: .shuffle(key: config.shuffleKey)) { WIcon(symbol: "shuffle", palette: p, size: 32, glyph: 16) }
                }
            }
        }
        .widgetURL(WidgetLinks.open(link: verse.link))
    }
}

struct VerseAccessory: View {
    let verse: PooledVerse

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(verse.reference).font(.system(size: 11, weight: .bold)).opacity(0.75).lineLimit(1)
            Text(verse.text).font(.system(size: 12.5)).lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(WidgetLinks.open(link: verse.link))
    }
}
