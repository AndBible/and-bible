#if DEBUG
import UIKit
import WebKit

/// Debug-only characterization fixture for issue #3980 (`-webKitChordProbe`, see `WebKitChordProbeUITests`).
///
/// Shows a bare contenteditable WKWebView in its own UIKit window: no SwiftUI responder, no Compose, no AndBible
/// WebView subclass or bootstrap. XCTest's hardware Option+Left chord reaches this view's `pressesBegan` with the
/// Option flag, yet WebKit intermittently dispatches no ArrowLeft keydown (the page sees only AltLeft down/up). This
/// fixture keeps that system-side loss measurable on new Xcode/iOS runtimes, so the expected failures of the reading
/// note editor's Option+arrow tests can be made strict once it no longer reproduces here.
enum WebKitChordProbe {
    static var isRequested: Bool { ProcessInfo.processInfo.arguments.contains("-webKitChordProbe") }

    static func makeController() -> UIViewController {
        let web = TracingWebView(frame: .zero, configuration: WKWebViewConfiguration())
        web.configuration.userContentController.add(DomLog.shared, name: "probe")
        web.loadHTMLString("""
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;padding:40px 8px">
        <div contenteditable="true" style="font-size:22px;min-height:60px;border:1px solid #888"></div>
        <script>
        for (const type of ['keydown', 'keyup']) document.addEventListener(type, e => {
          window.webkit.messageHandlers.probe.postMessage(JSON.stringify(
            {type, key: e.key, code: e.code, alt: e.altKey, offset: getSelection().focusOffset}));
        }, true);
        </script></body></html>
        """, baseURL: nil)
        let content = UIViewController()
        web.frame = content.view.bounds
        web.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        content.view.addSubview(web)
        return WindowLauncher(content: content)
    }

    /// Placeholder for the SwiftUI scene that moves the fixture into a separate, key UIKit window.
    private final class WindowLauncher: UIViewController {
        private let content: UIViewController
        private static var window: UIWindow?

        init(content: UIViewController) {
            self.content = content
            super.init(nibName: nil, bundle: nil)
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard WindowLauncher.window == nil, let scene = view.window?.windowScene else { return }
            let window = UIWindow(windowScene: scene)
            window.rootViewController = content
            window.windowLevel = .normal + 1
            window.makeKeyAndVisible()
            WindowLauncher.window = window
        }
    }

    /// Logs what the page receives (`3980-dom`), next to what UIKit delivered to the web view (`3980-native`).
    private final class DomLog: NSObject, WKScriptMessageHandler {
        static let shared = DomLog()
        func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
            appLog("3980-dom \(message.body)")
        }
    }

    private final class TracingWebView: WKWebView {
        override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
            for press in presses {
                appLog("3980-native began hid=\(press.key?.keyCode.rawValue ?? -1) flags=\(press.key?.modifierFlags.rawValue ?? 0)")
            }
            super.pressesBegan(presses, with: event)
        }
    }
}
#endif
