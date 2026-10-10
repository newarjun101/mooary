import SwiftUI
import UIKit
import ComposeApp
import UserNotifications

/// The page colour, mirroring `Paper` and `Espresso` in `ui/theme/Color.kt`.
///
/// This is a duplicated token, which is normally the wrong thing to do. It is here because the
/// bands above and below the page are painted by the window *before* Compose exists, so nothing
/// in Kotlin can reach them. If `Paper` or `Espresso` changes, this changes too -- they are one
/// palette expressed twice, and `PaperInkMatchesThemeTest` is what keeps them honest.
///
/// Resolved from the trait collection rather than hard-coded, so it follows the system the same
/// way `isSystemInDarkTheme()` does on the Compose side.
private extension UIColor {
    static let soulNotePage = UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 56.0 / 255.0, green: 51.0 / 255.0, blue: 52.0 / 255.0, alpha: 1.0)
            : UIColor(red: 245.0 / 255.0, green: 242.0 / 255.0, blue: 239.0 / 255.0, alpha: 1.0)
    }
}

/// Hosts the Compose UI.
///
/// The controller comes from Kotlin as `MainViewControllerKt.MainViewController()`. It is
/// wrapped rather than pushed directly because Compose Multiplatform needs a
/// `UIViewControllerRepresentable` to size and tear down its view correctly inside SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        controller.view.backgroundColor = .soulNotePage
        paintWindowInPageInk()
        // `updateUIViewController` is not guaranteed to run for the first rendered frame.
        // Present after the controller has had a run-loop turn to attach to its window; iOS
        // otherwise ignores the authorization request with no visible dialog.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) {
                _, _ in
            }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Repeated because the trait collection can change under a running app, and because
        // SwiftUI may call update before the key window exists.
        uiViewController.view.backgroundColor = .soulNotePage
        paintWindowInPageInk()
    }
}

/// Paints the window's safe-area bands in the page colour.
///
/// Compose's own view is inset by the safe area, so the strips above and below the page are
/// drawn by the window -- and iOS defaults a window to opaque white. On a warm paper palette
/// that reads as two hard white stripes running the width of the app, which is the single most
/// visible difference between this app on iOS and on Android. No layout is changed: only the
/// colour of the gap the window was already painting.
private func paintWindowInPageInk() {
    let ink = UIColor.soulNotePage
    for scene in UIApplication.shared.connectedScenes {
        guard let windowScene = scene as? UIWindowScene else { continue }
        for window in windowScene.windows {
            guard window.backgroundColor != ink else { return }
            window.backgroundColor = ink
        }
    }
}

@main
struct iosApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                // All edges, not just the keyboard. SwiftUI otherwise lays this out inside the
                // safe area, which leaves Compose's view already inset while Compose also applies
                // `WindowInsets` itself -- so every system inset is counted twice. Measured on an
                // iPhone 16 Pro: the app bar's first content sat at 134pt against a 59pt safe-area
                // edge, and the navigation bar stopped at 840pt (874-34) instead of reaching the
                // screen edge. Going edge-to-edge hands the insets to Compose, which applies them
                // exactly once, and that is the same layout Android gets.
                .ignoresSafeArea()
                // SwiftUI, not UIWindow, is what paints the bands: setting
                // `window.backgroundColor` changed nothing measurable, because the scene's root
                // view covers the window and carries its own (white) background. The root spans
                // the full window while its *content* is safe-area inset, so a background here
                // lands exactly where Compose cannot draw. Compose now spans the full window too,
                // so this is behind its page rather than in front of it -- kept because a window
                // that is briefly page-coloured beats a white flash before the first frame.
                .background(Color(uiColor: .soulNotePage))
        }
    }
}
