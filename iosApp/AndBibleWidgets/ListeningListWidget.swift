import SwiftUI
import WidgetKit

/// Listening list: the speak bookmarks, two rows (medium) or five (large). Row meta line: translation column (58 pt,
/// ellipsised), repeat slot (16 pt, empty when not repeating), progress bar (the rest): the bar starts at the same x and
/// has the same length on every row.
struct ListeningListWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AndBibleListeningList", provider: SpeakProvider()) { entry in
            ListeningListView(entry: entry)
        }
        .configurationDisplayName("Listening list")
        .description("Your speak bookmarks. Tap one to continue listening from there.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct ListeningListView: View {
    let entry: SpeakEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        let p = WPalette(scheme: scheme, mode: mode)
        Group {
            if let snap = entry.load.snapshot, snap.hasBibles {
                content(snap, p)
            } else {
                OpenAppView(text: entry.load.snapshot?.text("app_name", "AndBible") ?? "Open AndBible", palette: p)
            }
        }
        .widgetContentPadding()
        .widgetBackground(p.container)
    }

    @ViewBuilder private func content(_ snap: WidgetSnapshot, _ p: WPalette) -> some View {
        let limit = family == .systemLarge ? 5 : 2
        VStack(alignment: .leading, spacing: 0) {
            WHeader(symbol: "speaker.wave.2.fill", text: snap.text("widget_listening_list_name", "Listening list"), palette: p) {
                if !snap.listening.isEmpty { Text("\(snap.listening.count)").font(.system(size: 12)).foregroundStyle(p.onSurface2) }
            }
            if snap.listening.isEmpty {
                Text(snap.text("widget_listening_list_empty_title", "No speak bookmarks yet"))
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(p.onSurface).padding(.top, 8)
                Text(snap.text("widget_listening_list_empty_text", "Bookmarks with the Speak label appear here."))
                    .font(.system(size: 12)).foregroundStyle(p.onSurface2).lineLimit(family == .systemLarge ? 6 : 3).padding(.top, 2)
                Spacer(minLength: 0)
            } else {
                VStack(spacing: 6) {
                    ForEach(Array(snap.listening.prefix(limit))) { row in ListeningRowView(row: row, p: p) }
                }
                .padding(.top, 8)
                Spacer(minLength: 0)
            }
        }
    }
}

struct ListeningRowView: View {
    static let translationWidth: CGFloat = 58
    static let repeatWidth: CGFloat = 16

    let row: ListeningRowSnapshot
    let p: WPalette

    var body: some View {
        WidgetControl(kind: .speak(WidgetLinks.bookmark(row.bookmarkId))) {
            VStack(alignment: .leading, spacing: 2) {
                Text(row.reference).font(.system(size: 13, weight: row.current ? .bold : .semibold))
                    .foregroundStyle(p.onSurface).lineLimit(1)
                HStack(spacing: 0) {
                    Text(row.translation).font(.system(size: 11)).foregroundStyle(p.onSurface2).lineLimit(1)
                        .truncationMode(.tail).frame(width: Self.translationWidth, alignment: .leading)
                    Group {
                        if row.repeats { Image(systemName: "repeat").font(.system(size: 10, weight: .semibold)).foregroundStyle(p.onSurface2) }
                        else { Color.clear }
                    }
                    .frame(width: Self.repeatWidth)
                    WProgressBar(fraction: Double(row.percent ?? 0) / 100, palette: p, height: 3)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 3).padding(.horizontal, 8)
            .background(RoundedRectangle(cornerRadius: 10).fill(row.current ? p.track.opacity(0.9) : p.track.opacity(0.4)))
        }
    }
}
