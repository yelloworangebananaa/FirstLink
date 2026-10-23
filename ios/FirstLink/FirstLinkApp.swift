import SwiftUI
import UserNotifications

@main
struct FirstLinkApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        // The realtime socket drops while backgrounded; catch up on whatever changed.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await Store.shared.refreshAll() } }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Notifications.requestPermission()

        // Sign in early so the first backend call doesn't have to wait.
        Task { try? await Store.shared.signIn() }
        return true
    }

    /// Emergency alerts have to be visible even with the app open — the responder
    /// may be looking at another screen.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async
        -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    /// Tapping an emergency notification opens that emergency's responder alert.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        if let id = info[Notifications.emergencyIdKey] as? String {
            await MainActor.run { Store.shared.pendingResponderAlert = id }
        }
    }
}

enum Route: Hashable {
    case createEvent
    case addPlaces
    case roomCodes
    case joinEvent
    case qualifications
    case reportEmergency
    case requestHelp
    case emergencyList
    case helpList
    case responderAlert(String)
    case settings
    case readyMap
}

/// One navigation stack for the whole app. The screen at the bottom of it is decided
/// by the session — no event means the home screen, and a session means that role's
/// dashboard — so ending an event only has to clear the path.
struct RootView: View {
    @StateObject private var store = Store.shared
    @State private var path: [Route] = []
    @AppStorage("appearance") private var appearance: Appearance = .system

    var body: some View {
        NavigationStack(path: $path) {
            sessionRoot
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .createEvent: CreateEventView(path: $path)
                    case .addPlaces: AddPlacesView(path: $path)
                    case .roomCodes: RoomCodesView(path: $path)
                    case .joinEvent: JoinEventView(path: $path)
                    case .qualifications: QualificationsView(path: $path)
                    case .reportEmergency: ReportEmergencyView(path: $path)
                    case .requestHelp: RequestHelpView(path: $path)
                    case .emergencyList: EmergencyListView(path: $path)
                    case .helpList: HelpListView()
                    case .responderAlert(let id): ResponderAlertView(emergencyId: id)
                    case .settings: SettingsView()
                    case .readyMap: ReadyMapView()
                    }
                }
        }
        .environmentObject(store)
        .tint(.assist)
        .preferredColorScheme(appearance.colorScheme)
        // The organizer may have ended the event from another device.
        .onChange(of: store.event == nil) { _, noEvent in
            if noEvent { path.removeAll() }
        }
        .onChange(of: store.pendingResponderAlert) { _, id in
            guard let id else { return }
            path.append(.responderAlert(id))
            store.pendingResponderAlert = nil
        }
        .alert("This event has ended.", isPresented: $store.eventEndedNotice) {
            Button("OK", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var sessionRoot: some View {
        if store.event != nil, let user = store.currentUser {
            if user.isStaffLevel {
                StaffDashboardView(path: $path)
            } else {
                AttendeeDashboardView(path: $path)
            }
        } else {
            HomeView(path: $path)
        }
    }
}
