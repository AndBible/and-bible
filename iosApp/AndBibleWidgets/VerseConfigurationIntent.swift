import AppIntents
import SwiftUI
import WidgetKit

@available(iOS 17.0, *)
enum VerseSourceKind: String, AppEnum {
    case label, random, plan
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Verses from"
    static var caseDisplayRepresentations: [VerseSourceKind: DisplayRepresentation] = [
        .label: "A label", .random: "Random verse", .plan: "Today's reading plan",
    ]
}

@available(iOS 17.0, *)
enum RandomPartOption: String, AppEnum {
    case wholeBible = "WHOLE_BIBLE", newTestament = "NEW_TESTAMENT", psalmsProverbs = "PSALMS_PROVERBS"
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Random verse from"
    static var caseDisplayRepresentations: [RandomPartOption: DisplayRepresentation] = [
        .wholeBible: "Whole Bible", .newTestament: "New Testament", .psalmsProverbs: "Psalms & Proverbs",
    ]
}

@available(iOS 17.0, *)
enum FrequencyOption: String, AppEnum {
    case daily, every3Hours, onShuffle
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Changes"
    static var caseDisplayRepresentations: [FrequencyOption: DisplayRepresentation] = [
        .daily: "Daily", .every3Hours: "Every few hours", .onShuffle: "Only when I tap shuffle",
    ]
    var frequency: VerseFrequency { VerseFrequency(rawValue: rawValue) ?? .daily }
}

@available(iOS 17.0, *)
struct LabelEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Label"
    static var defaultQuery = LabelQuery()
    var id: String
    var name: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

@available(iOS 17.0, *)
struct LabelQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [LabelEntity] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }
    func suggestedEntities() async throws -> [LabelEntity] {
        (SnapshotStore.load().snapshot?.labels ?? []).map { LabelEntity(id: $0.id, name: $0.name) }
    }
}

@available(iOS 17.0, *)
struct BibleEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Bible"
    static var defaultQuery = BibleQuery()
    var id: String
    var name: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

@available(iOS 17.0, *)
struct BibleQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [BibleEntity] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }
    func suggestedEntities() async throws -> [BibleEntity] {
        (SnapshotStore.load().snapshot?.bibles ?? []).map { BibleEntity(id: $0.initials, name: $0.abbreviation) }
    }
}

/// "Edit Widget" of the Verse widget (iOS 17+). The Bible defaults to the app's default Bible; a chosen one is
/// recorded in `widget-requests.json` so the app builds its verse pools.
@available(iOS 17.0, *)
struct VerseConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Verse"
    static var description = IntentDescription("Choose where the verses come from and how often they change.")

    @Parameter(title: "Verses from", default: .random) var sourceKind: VerseSourceKind
    @Parameter(title: "Label") var label: LabelEntity?
    @Parameter(title: "Random verse from", default: .wholeBible) var randomPart: RandomPartOption
    @Parameter(title: "Changes", default: .daily) var frequency: FrequencyOption
    @Parameter(title: "Bible") var bible: BibleEntity?
    @Parameter(title: "Show shuffle and speak buttons", default: true) var showButtons: Bool
    @Parameter(title: "Show source and date", default: true) var showSource: Bool

    var config: VerseConfig {
        var c = VerseConfig()
        switch sourceKind {
        case .label: c.sourceKey = label.map { "label:\($0.id)" } ?? "label:"
        case .random: c.sourceKey = "random:\(randomPart.rawValue)"
        case .plan: c.sourceKey = "plan"
        }
        c.bible = bible?.id
        c.frequency = frequency.frequency
        c.showButtons = showButtons
        c.showSource = showSource
        return c
    }
}

@available(iOS 17.0, *)
struct VerseProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> VerseEntry { VerseProviderLogic.placeholder }

    func snapshot(for configuration: VerseConfigurationIntent, in context: Context) async -> VerseEntry {
        if context.isPreview { return VerseProviderLogic.placeholder }
        return VerseProviderLogic.timeline(config: configuration.config).entries[0]
    }

    func timeline(for configuration: VerseConfigurationIntent, in context: Context) async -> Timeline<VerseEntry> {
        VerseProviderLogic.timeline(config: configuration.config)
    }
}

@available(iOS 17.0, *)
struct VerseWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "AndBibleVerse", intent: VerseConfigurationIntent.self, provider: VerseProvider()) { entry in
            VerseWidgetView(entry: entry)
        }
        .configurationDisplayName("Verse")
        .description("A verse a day, from a label, a random range or today's reading.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
    }
}
