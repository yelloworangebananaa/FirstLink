import SwiftUI

/// Shared by staff and attendees — the reporter returns to their own dashboard,
/// which is always the root of the stack.
///
/// Cognitive Load: the form asks four things and nothing else. Every field a
/// dispatcher does not need is a field a frightened person has to read past.
struct ReportEmergencyView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store

    private enum Stage { case reported, waiting, approved, denied }

    @State private var location: String?
    @State private var consciousness: String?
    @State private var breathing: String?
    @State private var description = ""
    @State private var error: String?
    @State private var stage: Stage?
    @State private var reportedId: String?
    @State private var confirmCall = false
    @State private var sending = false
    @State private var sendFailed = false

    var body: some View {
        Form {
            Section {
                Picker("Location", selection: $location) {
                    Text("Select…").tag(String?.none)
                    ForEach(store.locations, id: \.self) { place in
                        Text(place).tag(String?.some(place))
                    }
                }
            } header: {
                Text("Where")
            } footer: {
                if store.locations.isEmpty {
                    Text("No places were added to this event.")
                }
            }

            // Chunking: the two clinical questions sit together, apart from location and
            // free text, because responders read them as one pair.
            Section("Patient") {
                LabeledContent("Consciousness") {
                    Picker("Consciousness", selection: $consciousness) {
                        Text("Awake").tag(String?.some("Conscious"))
                        Text("Out").tag(String?.some("Unconscious"))
                        Text("?").tag(String?.some("Unknown"))
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }
                LabeledContent("Breathing") {
                    Picker("Breathing", selection: $breathing) {
                        Text("Yes").tag(String?.some("Breathing"))
                        Text("No").tag(String?.some("Not Breathing"))
                        Text("?").tag(String?.some("Unknown"))
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }
            }

            Section {
                TextField("Anything responders should know", text: $description,
                          axis: .vertical)
                    .lineLimit(3...6)
            } header: {
                Text("Details")
            } footer: {
                Text("Optional. Responders are alerted the moment you send.")
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.emergency)
                }
            }
        }
        .navigationTitle("Report Emergency")
        .navigationBarTitleDisplayMode(.inline)
        .tint(.emergency)
        .sensoryFeedback(.impact(weight: .heavy), trigger: reportedId)
        .safeAreaBar(edge: .bottom) {
            PrimaryAction(title: "Send Emergency",
                          systemImage: "exclamationmark.triangle.fill",
                          tint: .emergency, action: send)
                .disabled(sending)
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
        }
        .call911Confirmation(isPresented: $confirmCall)
        .alert("Not sent", isPresented: $sendFailed) {
            Button("Call 911") { confirmCall = true }
            Button("Retry") { send() }
        } message: {
            Text("Your report did not reach the server. If someone is in danger, call 911 now.")
        }
        .alert(alertTitle, isPresented: Binding(get: { stage != nil },
                                                set: { if !$0 { stage = nil } })) {
            alertButtons
        } message: {
            Text(alertMessage)
        }
        // The organizer answers on their own device; the answer arrives through the
        // realtime listener and moves this screen on by itself.
        .onChange(of: approvalStatus) { _, status in
            guard stage == .waiting else { return }
            if status == .approved { stage = .approved }
            if status == .denied { stage = .denied }
        }
    }

    private var approvalStatus: EmergencyRequest.Approval? {
        reportedId.flatMap { store.emergency(id: $0)?.approval }
    }

    private func send() {
        guard let location else { error = "Choose where the emergency is"; return }
        guard let consciousness else { error = "Is the patient conscious?"; return }
        guard let breathing else { error = "Is the patient breathing?"; return }
        error = nil

        guard !sending else { return }
        sending = true
        Task {
            defer { sending = false }
            let request: EmergencyRequest
            do {
                request = try await store.addEmergency(location: location,
                                                       description: description.trimmed,
                                                       consciousness: consciousness,
                                                       breathing: breathing)
            } catch {
                sendFailed = true
                return
            }
            reportedId = request.id

            // No notification for the reporter — they are standing right here. Every other
            // device raises its own alert from the realtime listener in the store.
            if store.needsApprovalBeforeCalling {
                store.requestCallApproval(request.id)
                stage = .waiting
            } else {
                stage = .reported
            }
        }
    }

    // Peak-End Rule: this alert is the moment the whole app is judged on. It confirms
    // what happened, says what is already in motion, and offers the one next action.
    private var alertTitle: String {
        switch stage {
        case .approved: "911 Call Approved"
        case .denied: "911 Call Not Approved"
        default: "Responders Alerted"
        }
    }

    private var alertMessage: String {
        switch stage {
        case .waiting:
            "Qualified responders are on their way. This event requires organizer approval "
            + "before calling 911 — your request has been sent and this screen updates the "
            + "moment they answer."
        case .approved:
            "The organizer approved calling 911 for this emergency."
        case .denied:
            "The organizer did not approve a 911 call. The emergency is still active and "
            + "responders remain alerted."
        default:
            "Qualified responders are on their way. If this is life-threatening, call 911 now."
        }
    }

    @ViewBuilder
    private var alertButtons: some View {
        switch stage {
        case .waiting, .denied:
            Button("Done") { path.removeAll() }
        default:
            Button("Not now", role: .cancel) { path.removeAll() }
            Button("Call 911") { confirmCall = true }
        }
    }
}

/// Shared by staff and attendees — the reporter returns to their own dashboard.
struct RequestHelpView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store

    @State private var location: String?
    @State private var problem: String?
    @State private var description = ""
    @State private var error: String?
    @State private var sent = false

    var body: some View {
        Form {
            Section("What do you need?") {
                TextField("Lost bag, directions, accessibility…",
                          text: $description, axis: .vertical)
                    .lineLimit(3...6)
            }

            Section {
                Picker("Location", selection: $location) {
                    Text("Not specified").tag(String?.none)
                    ForEach(store.locations, id: \.self) { place in
                        Text(place).tag(String?.some(place))
                    }
                }
            } header: {
                Text("Where")
            } footer: {
                Text("Optional, but it helps staff find you faster.")
            }

            if !store.contacts.isEmpty {
                Section {
                    Picker("Problem", selection: $problem) {
                        Text("Not specified").tag(String?.none)
                        ForEach(store.contacts.map(\.problem), id: \.self) { name in
                            Text(name).tag(String?.some(name))
                        }
                    }
                } header: {
                    Text("Problem")
                } footer: {
                    Text("Optional. Alerts the person named for it.")
                }
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.emergency)
                }
            }
        }
        .navigationTitle("Request Help")
        .navigationBarTitleDisplayMode(.inline)
        .sensoryFeedback(.success, trigger: sent)
        .safeAreaBar(edge: .bottom) {
            PrimaryAction(title: "Send Request", systemImage: "paperplane.fill") {
                guard !description.trimmed.isEmpty else {
                    error = "Tell staff what you need"
                    return
                }
                store.addHelpRequest(location: location ?? "", description: description.trimmed, problem: problem)
                // Staff devices raise their own notification from the realtime listener.
                sent = true
                path.removeAll()
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
    }
}
