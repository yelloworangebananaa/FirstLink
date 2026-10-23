import SwiftUI

/// Attendee home. Hick's Law taken seriously: under stress there are exactly two
/// things a person can do here, and the dangerous one is unmistakable.
struct AttendeeDashboardView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store
    @State private var confirmCall = false

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                ApprovedCallBanner(confirmCall: $confirmCall)

                // Von Restorff: the single red object on the screen. Fitts's Law: it is
                // the biggest target in the app, because it is the one you may have to
                // hit while panicking.
                Button {
                    path.append(.reportEmergency)
                } label: {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 48))
                        Text("Report Emergency")
                            .font(.title2.bold())
                        Text("Alerts staff and qualified responders instantly")
                            .font(.footnote)
                            .opacity(0.9)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 34)
                }
                .buttonStyle(.glassProminent)
                .tint(.emergency)

                Button {
                    path.append(.requestHelp)
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: "hand.raised.fill")
                            .font(.title2)
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Request Help").font(.headline)
                            Text("Lost items, directions, accessibility")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .padding(20)
                }
                .buttonStyle(.glass)

                Label("If someone is in danger right now, call 911 first.",
                      systemImage: "phone.fill")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
            }
            .padding(20)
        }
        .navigationTitle(store.event?.name ?? "FirstLink")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                SettingsToolbarButton(path: $path)
            }
        }
        .call911Confirmation(isPresented: $confirmCall)
    }
}

/// Staff home. Chunking: five actions become two labelled groups plus one destructive
/// action held apart from them, so nothing lands next to End Event by accident.
struct StaffDashboardView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store
    @State private var confirmCall = false
    @State private var confirmEndEvent = false

    var body: some View {
        List {
            Section {
                ApprovedCallBanner(confirmCall: $confirmCall)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 20, bottom: 4, trailing: 20))
            }

            Section("Live incidents") {
                Button {
                    path.append(.reportEmergency)
                } label: {
                    Label("Report Emergency", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.emergency)
                        .font(.headline)
                }
                // Zeigarnik Effect: an unresolved count is what makes staff come back to it.
                NavigationRow(title: "Active Emergencies",
                              systemImage: "list.bullet.rectangle",
                              count: store.openEmergencies.count,
                              tint: .emergency) {
                    path.append(.emergencyList)
                }
            }

            Section("Attendee requests") {
                Button {
                    path.append(.requestHelp)
                } label: {
                    Label("Request Help", systemImage: "hand.raised.fill")
                }
                NavigationRow(title: "Help Requests",
                              systemImage: "tray.full",
                              count: store.openHelpRequests.count,
                              tint: .assist) {
                    path.append(.helpList)
                }
            }

            // Planning, not a live queue — organizer only, so attendees and staff never
            // see a screen that edits the event.
            if store.isOrganizer {
                Section("Preparedness") {
                    NavigationRow(title: "ReadyMap",
                                  subtitle: "Spot coverage gaps before anything happens",
                                  systemImage: "map",
                                  count: readyMapGaps,
                                  tint: .pending) {
                        path.append(.readyMap)
                    }
                }
            }

            Section {
                Button("End Event", systemImage: "stop.circle", role: .destructive) {
                    confirmEndEvent = true
                }
            } footer: {
                Text("Ends the event for everyone and stops both join codes working.")
            }
        }
        .navigationTitle(store.event?.name ?? "FirstLink")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                StatusPill(text: "Live", tint: .resolved, systemImage: "dot.radiowaves.left.and.right")
            }
            ToolbarItem(placement: .topBarTrailing) {
                SettingsToolbarButton(path: $path)
            }
        }
        .call911Confirmation(isPresented: $confirmCall)
        .alert("End Event?", isPresented: $confirmEndEvent) {
            Button("Cancel", role: .cancel) {}
            Button("End Event", role: .destructive) { store.endEvent() }
        } message: {
            Text("Join codes stop working and every device signs out. This cannot be undone.")
        }
    }

    /// Places with neither a posted responder nor an AED.
    private var readyMapGaps: Int {
        guard let event = store.event else { return 0 }
        return ReadyMap.coverage(event, store.participants).filter { $0.level == .gap }.count
    }
}

/// List row that carries an unresolved count. Law of Similarity: both live queues use
/// the same row, so the badge means the same thing in both places.
struct NavigationRow: View {
    var title: String
    var subtitle: String? = nil
    var systemImage: String
    var count: Int
    var tint: Color
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                        if let subtitle {
                            Text(subtitle)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                } icon: {
                    Image(systemName: systemImage)
                }
                .foregroundStyle(.primary)
                Spacer()
                if count > 0 {
                    Text("\(count)")
                        .font(.footnote.bold())
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3)
                        .background(tint, in: .capsule)
                }
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .animation(.smooth, value: count)
    }
}

/// Recovery path: the organizer approved after the reporter left the report screen.
/// Zeigarnik again — an approved call is an open loop, so it follows you home.
struct ApprovedCallBanner: View {
    @EnvironmentObject private var store: Store
    @Binding var confirmCall: Bool

    var body: some View {
        if store.myApprovedCall != nil {
            Button {
                confirmCall = true
            } label: {
                Label("911 Approved — Call Now", systemImage: "phone.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.extraLarge)
            .tint(.emergency)
            .transition(.scale.combined(with: .opacity))
        }
    }
}
