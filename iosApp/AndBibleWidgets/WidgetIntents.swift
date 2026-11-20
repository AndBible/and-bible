import AppIntents
import Foundation
import WidgetKit

/// Runs a speak action in the app. Two implementations: the app target (`WidgetIntentBridge+App.swift`) calls the Kotlin
/// side headless, the extension (`WidgetIntentBridge+Extension.swift`) does nothing: for an `AudioPlaybackIntent` the
/// system runs `perform()` in the app process, never in the extension.
enum WidgetIntentBridge {
    static func run(url: String) async -> Bool { await WidgetIntentBridgeImpl.run(url: url) }
}

/// A speak button (iOS 17+): `url` is the `andbible-widget://speak/...`, `bookmark/<id>` or `speak-passage?link=` URL the
/// same button uses as a link on iOS 16. The intent type is compiled into BOTH targets: the extension shows the button,
/// the app target runs it, without opening the app UI (spike `2026-11-20-ios-widget-intent-spike.md`).
@available(iOS 17.0, *)
struct SpeakWidgetIntent: AudioPlaybackIntent {
    static var title: LocalizedStringResource = "Speak"

    @Parameter(title: "URL") var url: String

    init() { url = "" }
    init(url: String) { self.url = url }

    func perform() async throws -> some IntentResult {
        _ = await WidgetIntentBridge.run(url: url)
        return .result()
    }
}

/// Verse shuffle (iOS 17+): bumps the counter of this configuration; runs in the extension.
@available(iOS 17.0, *)
struct VerseShuffleIntent: AppIntent {
    static var title: LocalizedStringResource = "Shuffle verse"

    @Parameter(title: "Key") var key: String

    init() { key = "" }
    init(key: String) { self.key = key }

    func perform() async throws -> some IntentResult {
        SnapshotStore.bumpShuffle(for: key)
        WidgetCenter.shared.reloadTimelines(ofKind: "AndBibleVerse")
        return .result()
    }
}

/// URLs of the widget URL scheme (`WidgetUrl.kt` parses them in the app).
enum WidgetLinks {
    static let scheme = "andbible-widget"

    static func speak(_ command: String) -> URL { URL(string: "\(scheme)://speak/\(command)")! }

    static func bookmark(_ id: String) -> URL {
        URL(string: "\(scheme)://bookmark/\(id.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? id)")!
    }

    /// The inner link must be fully percent-encoded: `URLComponents` leaves `&`, `?`, `=` and `#` alone, and an
    /// unencoded `&` would end the link at the app.
    static func encodeInner(_ link: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return link.addingPercentEncoding(withAllowedCharacters: allowed) ?? link
    }

    static func open(link: String) -> URL { URL(string: "\(scheme)://open?link=\(encodeInner(link))")! }
    static func speakPassage(link: String) -> URL { URL(string: "\(scheme)://speak-passage?link=\(encodeInner(link))")! }
    static var openApp: URL { URL(string: "\(scheme)://open")! }
}
