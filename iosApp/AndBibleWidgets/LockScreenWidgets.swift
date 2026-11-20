import SwiftUI
import WidgetKit

/// Lock Screen accessories of the speak state: circular play/pause with a progress ring, rectangular reference + bar,
/// inline "Psalm 23:1 · 34 %". (The rectangular verse accessory is a family of the Verse widget.) The system draws
/// accessories monochrome, so nothing here uses colour.
struct SpeakLockScreenWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AndBibleSpeakLock", provider: SpeakProvider()) { entry in
            SpeakLockView(entry: entry)
        }
        .configurationDisplayName("Speak")
        .description("Reading progress and play/pause on the Lock Screen.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct SpeakLockView: View {
    let entry: SpeakEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if let snap = entry.load.snapshot, snap.hasBibles {
            let s = snap.speak
            switch family {
            case .accessoryCircular:
                circular(s)
            case .accessoryInline:
                Text(inlineText(snap, s)).widgetLink(WidgetLinks.speak("toggle"))
            default:
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.reference ?? snap.text("widget_continue_listening", "Continue listening"))
                        .font(.system(size: 12, weight: .bold)).lineLimit(1)
                    Text(detail(snap, s)).font(.system(size: 11)).opacity(0.75).lineLimit(1)
                    Gauge(value: Double(s.percent ?? 0), in: 0...100) { EmptyView() }.gaugeStyle(.accessoryLinearCapacity)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .widgetLink(WidgetLinks.speak("toggle"))
            }
        } else {
            Text("AndBible").widgetURL(WidgetLinks.openApp)
        }
    }

    @ViewBuilder private func circular(_ s: SpeakSnapshot) -> some View {
        let face = ZStack {
            AccessoryWidgetBackground()
            Gauge(value: Double(s.percent ?? 0), in: 0...100) {
                EmptyView()
            } currentValueLabel: {
                Image(systemName: s.isSpeaking ? "pause.fill" : "play.fill").font(.system(size: 18, weight: .semibold))
            }
            .gaugeStyle(.accessoryCircularCapacity)
        }
        if #available(iOS 17.0, *) {
            WidgetControl(kind: .speak(WidgetLinks.speak("toggle"))) { face }
        } else {
            face.widgetURL(WidgetLinks.speak("toggle"))
        }
    }

    private func inlineText(_ snap: WidgetSnapshot, _ s: SpeakSnapshot) -> String {
        guard let ref = s.reference else { return snap.text("app_name", "AndBible") }
        return s.percent.map { "\(ref) · \($0) %" } ?? ref
    }

    private func detail(_ snap: WidgetSnapshot, _ s: SpeakSnapshot) -> String {
        let tr = s.translation ?? ""
        if s.isSpeaking, let pct = s.percent { return "\(pct) % · \(tr)" }
        return "\(snap.text("widget_paused", "Paused")) · \(tr)"
    }
}

private extension View {
    /// A lock screen accessory cannot hold a button on iOS 16; a tap anywhere runs the action by link.
    func widgetLink(_ url: URL) -> some View { widgetURL(url) }
}
