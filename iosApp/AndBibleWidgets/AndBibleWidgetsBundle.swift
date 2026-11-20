import SwiftUI
import WidgetKit

/// Placeholder widget so the extension bundle is valid before the real widgets (Task 13) land.
struct PlaceholderWidget: Widget {
    struct Provider: TimelineProvider {
        func placeholder(in context: Context) -> Entry { Entry(date: Date()) }
        func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) { completion(Entry(date: Date())) }
        func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
            completion(Timeline(entries: [Entry(date: Date())], policy: .never))
        }
    }
    struct Entry: TimelineEntry { let date: Date }

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AndBiblePlaceholder", provider: Provider()) { _ in
            Text("AndBible")
        }
        .configurationDisplayName("AndBible")
        .supportedFamilies([.systemSmall])
    }
}

@main
struct AndBibleWidgetsBundle: WidgetBundle {
    var body: some Widget {
        PlaceholderWidget()
    }
}
