import SwiftUI

/// ReadyMap Lite, organizer only: how prepared each place is, from what was entered.
///
/// Chunking: the whole venue collapses into one line — covered, partial, gaps — before
/// any detail. Von Restorff still holds: nothing here is red, because red means a live
/// emergency and this screen is planning.
struct ReadyMapView: View {
    @EnvironmentObject private var store: Store
    @State private var selected: String?

    var body: some View {
        let event = store.event
        let spots = event.map { ReadyMap.coverage($0, store.participants) } ?? []
        let unposted = event.map { ReadyMap.unposted($0, store.participants).count } ?? 0

        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Planning estimate from what you've entered — not a guarantee of safety.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                if !spots.isEmpty {
                    Text(summary(spots))
                        .font(.headline)
                        .monospacedDigit()
                        .contentTransition(.numericText())

                    if unposted > 0 {
                        Label("\(unposted) responder\(unposted == 1 ? "" : "s") not posted yet",
                              systemImage: "person.crop.circle.badge.questionmark")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.pending)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(20)
                            .glassEffect(.regular.tint(.pending.opacity(0.16)),
                                         in: .rect(cornerRadius: 22))
                    }

                    // Fitts's Law: two columns of big tiles, each one whole tap target.
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12),
                                        GridItem(.flexible(), spacing: 12)],
                              spacing: 12) {
                        ForEach(spots) { spot in
                            Button {
                                selected = spot.location
                            } label: {
                                SpotTile(spot: spot)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(20)
        }
        .navigationTitle("ReadyMap")
        .navigationBarTitleDisplayMode(.large)
        .overlay {
            if spots.isEmpty {
                ContentUnavailableView {
                    Label("No Places Yet", systemImage: "map")
                } description: {
                    Text("Add rooms and zones when creating the event to map coverage.")
                }
            }
        }
        .animation(.smooth, value: spots.map(\.level))
        .sheet(isPresented: presenting($selected)) {
            if let selected {
                SpotDetailView(location: selected)
                    .presentationDetents([.medium, .large])
                    .environmentObject(store)
            }
        }
    }

    private func summary(_ spots: [ReadyMap.Spot]) -> String {
        let covered = spots.filter { $0.level == .covered }.count
        let partial = spots.filter { $0.level == .partial }.count
        let gaps = spots.filter { $0.level == .gap }.count
        return "\(covered) covered · \(partial) partial · \(gaps) gap\(gaps == 1 ? "" : "s")"
    }
}

extension ReadyMap.Level {
    /// Text, colour and symbol together, so colour is never the only signal.
    var label: String {
        switch self {
        case .covered: "Covered"
        case .partial: "Partial"
        case .gap: "Gap"
        }
    }

    var tint: Color { self == .covered ? .resolved : .pending }

    var symbol: String {
        switch self {
        case .covered: "checkmark.shield.fill"
        case .partial: "shield.lefthalf.filled"
        case .gap: "exclamationmark.triangle.fill"
        }
    }

    /// Gaps get the stronger wash, still orange — never red.
    var wash: Double { self == .gap ? 0.24 : 0.12 }
}

/// One place. Law of Proximity: status on top, name under it, then what is there.
struct SpotTile: View {
    var spot: ReadyMap.Spot

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            StatusPill(text: spot.level.label, tint: spot.level.tint,
                       systemImage: spot.level.symbol)
            Text(spot.location)
                .font(.headline)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Label("\(spot.responders.count) responder\(spot.responders.count == 1 ? "" : "s")",
                  systemImage: "person.2.fill")
                .font(.footnote)
                .foregroundStyle(.secondary)
            // Only what is present is shown, so absence never hangs on a dimmed colour.
            HStack(spacing: 10) {
                if spot.aed {
                    Label("AED", systemImage: "bolt.heart.fill")
                }
                if spot.firstAid {
                    Label("Kit", systemImage: "cross.case.fill")
                }
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(.resolved)
        }
        .frame(maxWidth: .infinity, minHeight: 140, alignment: .topLeading)
        .padding(16)
        .contentShape(.rect)
        .glassEffect(.regular.tint(spot.level.tint.opacity(spot.level.wash)).interactive(),
                     in: .rect(cornerRadius: 22))
        .accessibilityElement(children: .combine)
        .accessibilityHint("Edit equipment and responders for this place")
    }
}

/// Everything editable about one place, and nothing else (Hick's Law). Every change
/// writes through the Store at once — there is no Save button to forget.
struct SpotDetailView: View {
    let location: String
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var drilling = false

    var body: some View {
        NavigationStack {
            Group {
                if let event = store.event {
                    form(event)
                } else {
                    ContentUnavailableView("Event Ended", systemImage: "stop.circle")
                }
            }
            .navigationTitle(location)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .sheet(isPresented: $drilling) {
            DrillView(location: location)
                .environmentObject(store)
        }
    }

    @ViewBuilder
    private func form(_ event: Event) -> some View {
        let spot = ReadyMap.drill(event, store.participants, at: location).spot
        let responders = ReadyMap.responders(store.participants)

        Form {
            Section {
                Toggle(isOn: Binding(get: { spot.aed },
                                     set: { store.setAED(location, present: $0) })) {
                    Label("AED here", systemImage: "bolt.heart.fill")
                }
                Toggle(isOn: Binding(get: { spot.firstAid },
                                     set: { store.setFirstAid(location, present: $0) })) {
                    Label("First-aid kit here", systemImage: "cross.case.fill")
                }
            }

            Section {
                if responders.isEmpty {
                    Text("No qualified responders or staff have joined yet.")
                        .foregroundStyle(.secondary)
                }
                ForEach(responders) { person in
                    let post = event.responderPosts?[person.id]
                    let here = post == location
                    Button {
                        // Tapping moves them here, or clears the post if they already are.
                        store.postResponder(person.id, at: here ? nil : location)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(person.displayName)
                                    .foregroundStyle(.primary)
                                if let post, !here, event.locations.contains(post) {
                                    Text("Posted at \(post)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            if here {
                                Image(systemName: "checkmark")
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(.tint)
                            }
                        }
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(here ? .isSelected : [])
                }
            } header: {
                Text("Responders posted here")
            } footer: {
                Text("A post is where someone plans to be, not where they are right now.")
            }
        }
        .sensoryFeedback(.selection, trigger: spot.aed)
        .sensoryFeedback(.selection, trigger: spot.firstAid)
        .sensoryFeedback(.selection, trigger: spot.responders.map(\.id))
        .safeAreaBar(edge: .bottom) {
            PrimaryAction(title: "Run drill here", systemImage: "figure.run") {
                drilling = true
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
    }
}

/// A practice incident. Read-only by construction: it computes from the Store and
/// never calls a write method or Notifications, so nobody's phone goes off.
struct DrillView: View {
    let location: String
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if let event = store.event {
                    content(event, ReadyMap.drill(event, store.participants, at: location))
                } else {
                    ContentUnavailableView("Event Ended", systemImage: "stop.circle")
                }
            }
            .navigationTitle("Drill: \(location)")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaBar(edge: .bottom) {
                PrimaryAction(title: "End Drill", systemImage: "stop.circle") { dismiss() }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 8)
            }
        }
    }

    private func content(_ event: Event, _ drill: ReadyMap.Drill) -> some View {
        List {
            // Tinted with the app's neutral accent, not red: this is practice.
            Section {
                Label("Drill mode — no alerts sent", systemImage: "figure.run.circle.fill")
                    .font(.headline)
                    .foregroundStyle(.assist)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .glassEffect(.regular.tint(.assist.opacity(0.14)), in: .rect(cornerRadius: 22))
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 20, bottom: 4, trailing: 20))
            }

            Section("Already here") {
                if drill.spot.responders.isEmpty {
                    Text("Nobody posted here").foregroundStyle(.secondary)
                }
                ForEach(drill.spot.responders) { person in
                    Label(person.summary, systemImage: "person.fill")
                }
            }

            Section("Could be sent") {
                if drill.elsewhere.isEmpty {
                    Text("Nobody else").foregroundStyle(.secondary)
                }
                ForEach(drill.elsewhere) { person in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(person.summary)
                        Text(postText(event, person))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section("AED") {
                Label(aedText(drill), systemImage: "bolt.heart.fill")
            }

            Section("911") {
                Label(event.require911Approval
                          ? "Organizer approval required before calling 911"
                          : "Reporter is offered 911 immediately",
                      systemImage: "phone.fill")
            }
        }
    }

    private func postText(_ event: Event, _ person: User) -> String {
        guard let post = event.responderPosts?[person.id], event.locations.contains(post)
        else { return "Not posted" }
        return "Posted at \(post)"
    }

    private func aedText(_ drill: ReadyMap.Drill) -> String {
        if drill.spot.aed { return "AED on site here" }
        if drill.aedLocations.isEmpty { return "No AEDs entered" }
        return "Nearest AEDs: \(drill.aedLocations.joined(separator: ", "))"
    }
}
