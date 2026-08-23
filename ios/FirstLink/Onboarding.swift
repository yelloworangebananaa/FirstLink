import SwiftUI

/// Home. Hick's Law: two paths, nothing else competing for the decision.
struct HomeView: View {
    @Binding var path: [Route]

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 18) {
                Image(systemName: "cross.case.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.tint)
                    .symbolEffect(.breathe)
                Text("FirstLink")
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                Text("Connect organizers with qualified attendees while emergency services are on the way.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 36)
            }
            Spacer()
        }
        // Fitts's Law: actions live at the bottom, inside thumb reach, not at the top.
        .safeAreaBar(edge: .bottom) {
            VStack(spacing: 12) {
                PrimaryAction(title: "Join Event", systemImage: "qrcode.viewfinder") {
                    path.append(.joinEvent)
                }
                SecondaryAction(title: "Create Event", systemImage: "plus.circle") {
                    path.append(.createEvent)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                SettingsToolbarButton(path: $path)
            }
        }
    }
}

struct CreateEventView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store

    @State private var name = ""
    @State private var venue = ""
    @State private var organizer = ""
    @State private var require911Approval = false
    @State private var error: String?
    @State private var pickingVenue = false

    var body: some View {
        Form {
            StepHeader(step: 1, title: "Create an Event",
                       subtitle: "Name your event and we'll generate the join codes.")

            // Law of Common Region: a bordered group reads as one idea. Three fields,
            // one section, one heading — not three loose boxes on a page.
            Section("Event") {
                TextField("Event name", text: $name)

                // Tapping the venue opens a real map search rather than asking someone
                // to type an address correctly from memory.
                Button {
                    pickingVenue = true
                } label: {
                    LabeledContent("Venue") {
                        HStack(spacing: 6) {
                            Text(venue.isEmpty ? "Choose on map" : venue)
                                .foregroundStyle(venue.isEmpty ? .secondary : .primary)
                            Image(systemName: "map.fill")
                                .font(.footnote)
                                .foregroundStyle(.tint)
                        }
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)

                TextField("Organizer", text: $organizer)
                    .textContentType(.name)
            }

            Section {
                Toggle("Require approval before calling 911", isOn: $require911Approval)
            } footer: {
                Text("When on, a reporter must wait for you to approve a 911 call. "
                     + "Responders are still alerted immediately either way.")
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.emergency)
                }
            }
        }
        .navigationTitle("New Event")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $pickingVenue) {
            VenuePicker(venue: $venue)
        }
        .safeAreaBar(edge: .bottom) {
            PrimaryAction(title: "Create Event", systemImage: "arrow.right", action: create)
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
        }
    }

    private func create() {
        let fields = [("Event name", name), ("Venue name", venue), ("Organizer name", organizer)]
        if let missing = fields.first(where: { $0.1.trimmed.isEmpty }) {
            error = "\(missing.0) is required"
            return
        }
        error = nil
        store.createEvent(name: name.trimmed, venue: venue.trimmed,
                          organizer: organizer.trimmed, require911Approval: require911Approval)
        // The event creator is automatically staff (organizer role). They never see the
        // qualifications screen, so publish their name from the form instead.
        store.createCurrentUser(role: Role.organizer)
        store.saveProfile(name: organizer.trimmed, qualifications: [], qualifiedResponder: false)
        path.append(.addPlaces)
    }
}

/// Adding a place is now one row in the list it feeds, instead of its own screen.
/// Tesler's Law: the complexity was never essential, so the screen holding it went.
struct AddPlacesView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store

    @State private var draft = ""
    @State private var error: String?
    @FocusState private var typing: Bool

    var body: some View {
        Form {
            StepHeader(step: 2, title: "Add Places",
                       subtitle: "Rooms, entrances, stages — anywhere someone might need help.")

            Section("Add a place") {
                HStack {
                    TextField("Main Hall", text: $draft)
                        .focused($typing)
                        .submitLabel(.done)
                        .onSubmit(add)
                    Button("Add", action: add)
                        .buttonStyle(.glass)
                        .disabled(draft.trimmed.isEmpty)
                }
            }

            if store.locations.isEmpty {
                Section {
                    ContentUnavailableView {
                        Label("No places yet", systemImage: "mappin.slash")
                    } description: {
                        Text("Add at least one before continuing.")
                    }
                }
                .listRowBackground(Color.clear)
            } else {
                Section("\(store.locations.count) place\(store.locations.count == 1 ? "" : "s")") {
                    ForEach(store.locations, id: \.self) { place in
                        Label(place, systemImage: "mappin.circle.fill")
                            .foregroundStyle(.primary)
                    }
                }
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.emergency)
                }
            }
        }
        .navigationTitle("Places")
        .navigationBarTitleDisplayMode(.inline)
        // Doherty Threshold: a tap that changes a list should confirm itself instantly.
        .sensoryFeedback(.success, trigger: store.locations.count)
        .safeAreaBar(edge: .bottom) {
            PrimaryAction(title: "Continue", systemImage: "arrow.right") {
                guard !store.locations.isEmpty else {
                    error = "Add at least one location before continuing"
                    return
                }
                path.append(.roomCodes)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
    }

    private func add() {
        let place = draft.trimmed
        guard !place.isEmpty else { return }
        store.addLocations([place])
        draft = ""
        error = nil
        typing = true
    }
}

struct RoomCodesView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store
    @State private var copied: String?

    var body: some View {
        Form {
            StepHeader(step: 3, title: "Your event is ready",
                       subtitle: "Share the attendee code widely. Share the staff code carefully.")

            codeSection(title: "Attendee code",
                        footer: "Anyone with this code can report emergencies and request help.",
                        code: store.event?.attendeeCode ?? "",
                        tint: .assist)

            codeSection(title: "Staff code",
                        footer: "Staff can see every incident and resolve them. Share carefully.",
                        code: store.event?.staffCode ?? "",
                        tint: .pending)
        }
        .navigationTitle("Join Codes")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .sensoryFeedback(.success, trigger: copied)
        .onAppear(perform: generateCodesIfNeeded)
        .safeAreaBar(edge: .bottom) {
            PrimaryAction(title: "Open Check-In", systemImage: "checkmark.circle") {
                path.removeAll()
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
    }

    @ViewBuilder
    private func codeSection(title: String, footer: String, code: String, tint: Color) -> some View {
        Section {
            VStack(spacing: 14) {
                Text(code)
                    .font(.system(size: 46, weight: .bold, design: .monospaced))
                    .tracking(6)
                    .contentTransition(.numericText())
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 20)
                    .glassEffect(.regular.tint(tint.opacity(0.2)), in: .rect(cornerRadius: 22))

                HStack(spacing: 10) {
                    Button {
                        UIPasteboard.general.string = code
                        copied = code
                    } label: {
                        Label(copied == code ? "Copied" : "Copy",
                              systemImage: copied == code ? "checkmark" : "doc.on.doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)

                    ShareLink(item: "Join my event on FirstLink with code \(code)") {
                        Label("Share", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                }
                .controlSize(.large)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 8, trailing: 20))
        } header: {
            Text(title)
        } footer: {
            Text(footer)
        }
    }

    /// Generate once; reuse on re-entry so codes stay stable.
    private func generateCodesIfNeeded() {
        guard let event = store.event else { return }
        guard event.attendeeCode == nil || event.staffCode == nil else { return }
        let attendee = JoinCode.generate()
        store.saveEventCodes(attendee: attendee, staff: JoinCode.generate(differentFrom: attendee))
    }
}

struct JoinEventView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store

    @State private var code = ""
    @State private var error: String?
    @State private var busy = false
    @FocusState private var typing: Bool

    var body: some View {
        Form {
            Section {
                TextField("ABC123", text: $code)
                    .font(.system(size: 40, weight: .bold, design: .monospaced))
                    .tracking(8)
                    .multilineTextAlignment(.center)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .focused($typing)
                    .submitLabel(.go)
                    .onSubmit { Task { await join() } }
                    .padding(.vertical, 12)
                    .listRowBackground(Color.clear)
            } header: {
                Text("Join code")
            } footer: {
                // Postel's Law: we accept lower case and stray spaces, and say so, rather
                // than rejecting input a person can reasonably produce.
                Text("Six characters. Case and spacing don't matter.")
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.emergency)
                }
            }
        }
        .navigationTitle("Join an Event")
        .navigationBarTitleDisplayMode(.inline)
        .sensoryFeedback(.error, trigger: error)
        .onAppear { typing = true }
        .safeAreaBar(edge: .bottom) {
            PrimaryAction(title: busy ? "Joining…" : "Join Event",
                          systemImage: "arrow.right.circle") {
                Task { await join() }
            }
            .disabled(busy || code.trimmed.isEmpty)
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
    }

    private func join() async {
        guard !code.trimmed.isEmpty else {
            error = "Enter a join code"
            return
        }
        // The lookup is a server round trip, so lock the button meanwhile.
        busy = true
        defer { busy = false }
        do {
            if try await store.join(code: code) == nil {
                error = "That code isn't a live event. Check with your organizer."
            } else {
                error = nil
                path = [.qualifications]
            }
        } catch {
            self.error = "Cannot reach FirstLink servers. Check your connection."
        }
    }
}

struct QualificationsView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store

    /// Miller's Law: six options, one screenful, no scrolling to hold them in mind.
    private static let options: [(String, String)] = [
        ("CPR Certified", "heart.circle"),
        ("First Aid Certified", "cross.case"),
        ("AED Trained", "bolt.heart"),
        ("Medical Professional", "stethoscope"),
        ("Nurse", "cross.vial"),
        ("EMT or Paramedic", "truck.box"),
    ]

    @State private var name = ""
    @State private var selected: Set<String> = []
    @State private var otherText = ""
    @State private var error: String?

    var body: some View {
        Form {
            Section {
                TextField("Your name", text: $name)
                    .textContentType(.givenName)
            } header: {
                Text("Name")
            } footer: {
                Text("Event staff see this so they know who responded.")
            }

            Section {
                ForEach(Self.options, id: \.0) { option, symbol in
                    CheckRow(title: option, systemImage: symbol,
                             isOn: binding(for: option))
                }
                HStack {
                    Image(systemName: "ellipsis.circle")
                        .foregroundStyle(.tint)
                    TextField("Something else", text: $otherText)
                }
            } header: {
                Text("Your qualifications")
            } footer: {
                Text("Leave everything unchecked if you have none — you'll still be "
                     + "able to report emergencies and request help.")
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.emergency)
                }
            }
        }
        .navigationTitle("Responder Profile")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .sensoryFeedback(.selection, trigger: selected)
        .safeAreaBar(edge: .bottom) {
            VStack(spacing: 12) {
                PrimaryAction(title: "Save & Continue", systemImage: "checkmark", action: save)
                Button("Skip for now") {
                    // Skipping still records a name if one was typed, so staff can
                    // tell participants apart even without qualifications.
                    store.saveProfile(name: name.trimmed, qualifications: [],
                                      qualifiedResponder: false)
                    path.removeAll()
                }
                .font(.subheadline)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
    }

    private func binding(for option: String) -> Binding<Bool> {
        Binding(get: { selected.contains(option) },
                set: { isOn in
                    if isOn { selected.insert(option) } else { selected.remove(option) }
                })
    }

    private func save() {
        guard !name.trimmed.isEmpty else {
            error = "Enter your name so staff know who responded"
            return
        }
        var qualifications = Self.options.map(\.0).filter(selected.contains)
        if !otherText.trimmed.isEmpty { qualifications.append(otherText.trimmed) }

        // Anyone with a real qualification is eligible for emergency alerts.
        store.saveProfile(name: name.trimmed,
                          qualifications: qualifications.isEmpty ? ["No Qualifications"] : qualifications,
                          qualifiedResponder: !qualifications.isEmpty)
        path.removeAll()
    }
}

/// Settings-style multi-select row: tap anywhere, checkmark on the trailing edge.
struct CheckRow: View {
    var title: String
    var systemImage: String
    @Binding var isOn: Bool

    var body: some View {
        Button {
            isOn.toggle()
        } label: {
            HStack {
                Label(title, systemImage: systemImage)
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.tint)
                    .opacity(isOn ? 1 : 0)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }
}
