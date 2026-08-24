import SwiftUI
import UIKit
import UserNotifications

/// Local notifications, raised by the device's own Firestore listener when someone
/// else reports something. See Store.alertIfNeeded for why this works without FCM.
enum Notifications {

    static let emergencyIdKey = "emergency_id"

    static func requestPermission() {
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// High-priority alert for qualified responders; tap opens the responder alert screen.
    static func emergency(id: String, location: String) {
        let content = UNMutableNotificationContent()
        content.title = "EMERGENCY — responder needed"
        content.body = "Location: \(location). Tap to respond."
        content.sound = .default
        // Breaks through Focus modes once the capability is enabled in Signing & Capabilities.
        content.interruptionLevel = .timeSensitive
        content.userInfo = [emergencyIdKey: id]
        post(content)
    }

    /// Notification for staff about a new non-emergency help request.
    static func help(description: String) {
        let content = UNMutableNotificationContent()
        content.title = "New help request"
        content.body = description
        content.sound = .default
        post(content)
    }

    private static func post(_ content: UNMutableNotificationContent) {
        let request = UNNotificationRequest(identifier: UUID().uuidString,
                                            content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}

/// Opens the phone dialer with 911 after an explicit confirmation. iOS shows its own
/// call confirmation on top of this, so nothing dials without two deliberate taps —
/// which is also why the app needs no special entitlement.
enum Dialer {
    static func dial911() {
        guard let url = URL(string: "tel://911") else { return }
        UIApplication.shared.open(url)
    }
}

/// Confirmation alert shared by every "Call 911" button in the app.
extension View {
    func call911Confirmation(isPresented: Binding<Bool>) -> some View {
        alert("Call 911?", isPresented: isPresented) {
            Button("Cancel", role: .cancel) {}
            Button("Open Dialer") { Dialer.dial911() }
        } message: {
            Text("This opens your phone dialer with 911. Only proceed for a real emergency.")
        }
    }
}
