import SwiftUI
import WidgetKit

struct SpeakEntry: TimelineEntry {
    let date: Date
    let load: SnapshotLoad
}

struct SpeakProvider: TimelineProvider {
    func placeholder(in context: Context) -> SpeakEntry { SpeakEntry(date: Date(), load: .snapshot(.sample)) }

    func getSnapshot(in context: Context, completion: @escaping (SpeakEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : SpeakEntry(date: Date(), load: SnapshotStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SpeakEntry>) -> Void) {
        // A single entry; the app rewrites the snapshot and reloads the timeline on every change.
        completion(Timeline(entries: [SpeakEntry(date: Date(), load: SnapshotStore.load())], policy: .never))
    }
}

/// Speak: small (controls + progress), medium (verse + controls), large (verse, next verse, all controls).
struct SpeakWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AndBibleSpeak", provider: SpeakProvider()) { entry in
            SpeakWidgetView(entry: entry)
        }
        .configurationDisplayName("Speak")
        .description("Listen to the Bible with play, pause and skip controls.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct SpeakWidgetView: View {
    let entry: SpeakEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        let p = WPalette(scheme: scheme, mode: mode)
        Group {
            if let snap = entry.load.snapshot, snap.hasBibles {
                switch family {
                case .systemMedium: SpeakMedium(snap: snap, p: p)
                case .systemLarge: SpeakLarge(snap: snap, p: p)
                default: SpeakSmall(snap: snap, p: p)
                }
            } else {
                OpenAppView(text: entry.load.snapshot?.text("app_name", "AndBible") ?? "Open AndBible", palette: p)
            }
        }
        .widgetContentPadding()
        .widgetBackground(p.container)
    }
}

private func speakEyebrow(_ s: SpeakSnapshot, _ snap: WidgetSnapshot) -> String {
    s.isSpeaking ? s.title ?? snap.text("widget_speak_name", "Speak") : snap.text("widget_paused", "Paused")
}

private func playPauseSymbol(_ s: SpeakSnapshot) -> String { s.isSpeaking ? "pause.fill" : "play.fill" }

struct SpeakSmall: View {
    let snap: WidgetSnapshot
    let p: WPalette

    var body: some View {
        let s = snap.speak
        VStack(alignment: .leading, spacing: 0) {
            WHeader(symbol: "speaker.wave.2.fill", text: s.isSpeaking ? snap.text("widget_speak_name", "Speak") : snap.text("widget_paused", "Paused"), palette: p)
            Text(s.isSpeaking ? (s.title ?? s.reference ?? "") : snap.text("widget_continue_listening", "Continue listening"))
                .font(.system(size: 14, weight: .semibold)).foregroundStyle(p.onSurface).lineLimit(2).padding(.top, 6)
            Text([s.reference, s.translation].compactMap { $0 }.joined(separator: " · "))
                .font(.system(size: 12)).foregroundStyle(p.onSurface2).lineLimit(1)
            Spacer(minLength: 0)
            WProgressBar(fraction: Double(s.percent ?? 0) / 100, palette: p).padding(.bottom, 8)
            HStack {
                WidgetControl(kind: .speak(WidgetLinks.speak("prev"))) { WIcon(symbol: "backward.end.fill", palette: p, size: 32, glyph: 16) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("toggle"))) { WIcon(symbol: playPauseSymbol(s), palette: p, size: 44, glyph: 20, primary: true) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("next"))) { WIcon(symbol: "forward.end.fill", palette: p, size: 32, glyph: 16) }
            }
        }
    }
}

struct SpeakMedium: View {
    let snap: WidgetSnapshot
    let p: WPalette

    var body: some View {
        let s = snap.speak
        VStack(alignment: .leading, spacing: 0) {
            WHeader(symbol: "speaker.wave.2.fill", text: speakEyebrow(s, snap), palette: p) {
                if let m = s.sleepMinutesLeft { SleepChip(minutes: m, text: sleepText(snap, m), palette: p) }
            }
            Text(s.verseText.isEmpty ? snap.text("widget_continue_listening", "Continue listening") : s.verseText)
                .font(.system(size: 15, weight: .medium)).foregroundStyle(p.onSurface).lineLimit(3).padding(.top, 6)
            Spacer(minLength: 0)
            HStack(spacing: 8) {
                Text(s.statusLine).font(.system(size: 12)).foregroundStyle(p.onSurface2).lineLimit(1)
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("prev"))) { WIcon(symbol: "backward.end.fill", palette: p, size: 32, glyph: 16) }
                WidgetControl(kind: .speak(WidgetLinks.speak("toggle"))) { WIcon(symbol: playPauseSymbol(s), palette: p, size: 40, glyph: 18, primary: true) }
                WidgetControl(kind: .speak(WidgetLinks.speak("next"))) { WIcon(symbol: "forward.end.fill", palette: p, size: 32, glyph: 16) }
            }
        }
    }
}

struct SpeakLarge: View {
    let snap: WidgetSnapshot
    let p: WPalette

    var body: some View {
        let s = snap.speak
        VStack(alignment: .leading, spacing: 0) {
            WHeader(symbol: "speaker.wave.2.fill", text: speakEyebrow(s, snap), palette: p) {
                if let m = s.sleepMinutesLeft { SleepChip(minutes: m, text: sleepText(snap, m), palette: p) }
            }
            (Text(s.verseText.isEmpty ? snap.text("widget_continue_listening", "Continue listening") : s.verseText)
                .font(.system(size: 18, weight: .medium)).foregroundColor(p.onSurface)
             + Text(s.nextVerseText.map { " " + $0 } ?? "").font(.system(size: 18)).foregroundColor(p.onSurface2))
                .lineLimit(9).padding(.top, 10)
            Spacer(minLength: 0)
            Text(s.statusLine).font(.system(size: 12)).foregroundStyle(p.onSurface2).lineLimit(1).padding(.bottom, 6)
            WProgressBar(fraction: Double(s.percent ?? 0) / 100, palette: p).padding(.bottom, 10)
            HStack {
                WidgetControl(kind: .speak(WidgetLinks.speak("sleep"))) { WIcon(symbol: "moon.fill", palette: p, size: 34, glyph: 16) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("rewind"))) { WIcon(symbol: "gobackward", palette: p, size: 34, glyph: 18) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("prev"))) { WIcon(symbol: "backward.end.fill", palette: p, size: 36, glyph: 18) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("toggle"))) { WIcon(symbol: playPauseSymbol(s), palette: p, size: 52, glyph: 24, primary: true) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("next"))) { WIcon(symbol: "forward.end.fill", palette: p, size: 36, glyph: 18) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("forward"))) { WIcon(symbol: "goforward", palette: p, size: 34, glyph: 18) }
                Spacer(minLength: 0)
                WidgetControl(kind: .speak(WidgetLinks.speak("stop"))) { WIcon(symbol: "stop.fill", palette: p, size: 34, glyph: 16) }
            }
        }
    }
}

func sleepText(_ snap: WidgetSnapshot, _ minutes: Int) -> String {
    // The snapshot carries the number of minutes only; the unit follows the device language.
    Measurement(value: Double(minutes), unit: UnitDuration.minutes).formatted(.measurement(width: .abbreviated, usage: .asProvided))
}
