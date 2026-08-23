import SwiftUI
import UserNotifications

/// Appearance override. Nil means "follow the system", which stays the default —
/// Jakob's Law: the phone already has a preference and most people expect apps to honour it.
enum Appearance: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }

    var symbol: String {
        switch self {
        case .system: "iphone"
        case .light: "sun.max.fill"
        case .dark: "moon.fill"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

struct SettingsView: View {
    @AppStorage("appearance") private var appearance: Appearance = .system
    @EnvironmentObject private var store: Store
    @State private var notificationsAllowed: Bool?

    var body: some View {
        Form {
            Section {
                Picker("Appearance", selection: $appearance) {
                    ForEach(Appearance.allCases) { mode in
                        Label(mode.label, systemImage: mode.symbol).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
            } header: {
                Text("Appearance")
            } footer: {
                Text("System follows your phone's Display & Brightness setting.")
            }

            // Alerts are how a responder finds out at all, so a denied permission is
            // worth surfacing here rather than leaving it to fail silently.
            Section {
                LabeledContent("Emergency alerts") {
                    switch notificationsAllowed {
                    case true:
                        Label("On", systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.resolved)
                            .labelStyle(.titleAndIcon)
                    case false:
                        Label("Off", systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.pending)
                            .labelStyle(.titleAndIcon)
                    case nil:
                        ProgressView()
                    }
                }
                if notificationsAllowed == false {
                    Button("Open iPhone Settings", systemImage: "arrow.up.forward.app") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                }
            } header: {
                Text("Notifications")
            } footer: {
                Text("With alerts off, this phone will not be told when someone reports "
                     + "an emergency, even if you are a qualified responder.")
            }

            if let event = store.event, let user = store.currentUser {
                Section("Event") {
                    LabeledContent("Name", value: event.name)
                    LabeledContent("Venue", value: event.venueName)
                    LabeledContent("You are", value: user.role.capitalized)
                    if user.qualifiedResponder {
                        Label("Receiving emergency alerts", systemImage: "bell.badge.fill")
                            .foregroundStyle(.resolved)
                            .font(.subheadline)
                    }
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            notificationsAllowed = settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
        }
    }
}

/// Gear button, in the same toolbar slot on every screen that offers it.
/// Law of Similarity: one affordance, one place, one meaning.
struct SettingsToolbarButton: View {
    @Binding var path: [Route]

    var body: some View {
        Button("Settings", systemImage: "gearshape.fill") {
            path.append(.settings)
        }
    }
}
