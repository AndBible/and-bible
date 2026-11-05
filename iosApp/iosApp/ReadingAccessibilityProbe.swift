#if DEBUG
import UIKit
import WebKit

/// Explicitly enabled XCTest transport for read-only DOM measurements.
final class ReadingAccessibilityProbe {
    private static var timer: Timer?
    private static weak var currentWeb: WKWebView?
    private static var mount = UUID().uuidString
    private static var sample = 0
    private static var publishedSample = 0
    // XCTest transport only; this element does not prove WKWebView accessibility.
    private static let report: UILabel = {
        let label = UILabel(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
        label.isAccessibilityElement = true
        label.accessibilityIdentifier = "reading-dom-probe"
        label.isUserInteractionEnabled = false
        return label
    }()

    private static let metricsScript = #"""
    (() => {
      const words = ['For', 'God', 'so', 'loved'];
      // Offscreen elements are valid; hidden elements are not.
      const rendered = node => {
        const style = getComputedStyle(node);
        return node.getClientRects().length > 0 && style.display !== 'none' && style.visibility !== 'hidden';
      };
      const verse = [...document.querySelectorAll('.verse')].find(v => rendered(v) && v.textContent.includes('For God so loved the world'));
      const measure = (root, word) => {
        if (!root) return null;
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        let text;
        while ((text = walker.nextNode())) {
          if (!rendered(text.parentElement)) continue;
          const match = new RegExp('\\b' + word + '\\b').exec(text.textContent);
          if (!match) continue;
          const range = document.createRange();
          range.setStart(text, match.index); range.setEnd(text, match.index + word.length);
          const rect = range.getBoundingClientRect();
          return {width:rect.width,height:rect.height,top:rect.top,color:getComputedStyle(text.parentElement).color};
        }
        return null;
      };
      const fixture = ['K4GenBook', 'H2Maps'].find(id =>
        [...document.querySelectorAll('[data-book-initials]')].some(node =>
          rendered(node) && node.dataset.bookInitials === id && node.innerText.includes('In the beginning God created the heaven and the earth')));
      return JSON.stringify({genesisFixture:fixture || null, verse16:words.map(w => measure(verse,w))});
    })()
    """#

    static func installIfRequested() {
        guard ProcessInfo.processInfo.arguments.contains("-readingAccessibilityProbe") else { return }
        timer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { _ in tick() }
    }

    private static func readingWebviews() -> [WKWebView] {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).filter { !$0.isHidden }
        var result: [WKWebView] = []
        func walk(_ view: UIView) {
            guard !view.isHidden, view.alpha > 0 else { return }
            if let web = view as? WKWebView, web.window != nil,
               web.accessibilityIdentifier?.hasPrefix("bible-webview-") == true { result.append(web) }
            view.subviews.forEach(walk)
        }
        windows.forEach(walk)
        return result
    }

    private static func tick() {
        let candidates = readingWebviews()
        guard candidates.count == 1, let web = candidates.first else {
            currentWeb = nil
            mount = UUID().uuidString
            report.accessibilityValue = nil
            report.removeFromSuperview()
            return
        }
        if currentWeb !== web {
            currentWeb = web
            mount = UUID().uuidString
            publishedSample = 0
            report.accessibilityValue = nil
        }
        sample += 1
        let currentSample = sample
        let currentMount = mount
        let started = Date().timeIntervalSince1970
        web.evaluateJavaScript(metricsScript) { result, error in
            let candidates = readingWebviews()
            guard candidates.count == 1, candidates.first === web, currentWeb === web,
                  mount == currentMount, currentSample > publishedSample, let window = web.window else { return }
            if error != nil {
                publishedSample = currentSample
                report.accessibilityValue = nil
                return
            }
            guard let json = result as? String, let data = json.data(using: .utf8),
                  var value = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return }
            value["sample"] = currentSample
            value["started"] = started
            value["mount"] = currentMount
            value["windowId"] = web.accessibilityIdentifier
            guard let encoded = try? JSONSerialization.data(withJSONObject: value),
                  let reportJson = String(data: encoded, encoding: .utf8) else { return }
            publishedSample = currentSample
            if report.superview !== window { window.addSubview(report) }
            report.accessibilityValue = reportJson
        }
    }
}
#endif
