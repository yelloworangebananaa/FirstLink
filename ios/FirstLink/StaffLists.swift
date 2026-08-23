import SwiftUI

struct EmergencyListView: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: Store

    @State private var resolving: EmergencyRequest?
    @State private var approvalTarget: EmergencyRequest?
    @State private var approvalGranted = false

    var body: some View {
        List {
            ForEach(store.openEmergencies) { emergency in
                Section {
                    EmergencyCard(emergency: emergency,
                                  isOrganizer: store.isOrganizer,
                                  onApprove: { granted in
                                      approvalGranted = granted
                                      approvalTarget = emergency
                                  })
                    .contentShape(.rect)
                    .onTapGesture {
                        // Fallback path to the responder alert when the notification
                        // was missed or permission was denied.
                        if emergency.status == EmergencyRequest.statusActive {
                            path.append(.responderAlert(emergency.id))
                        }
                    }
                    // Swipe to resolve: the native gesture for exactly this, and it keeps
                    // a destructive action off the card where it could be hit by mistake.
                    .swipeActions(edge: .trailing) {
                        Button("Resolve", systemImage: "checkmark.circle.fill") {
                            resolving = emergency
                        }
                        .tint(.resolved)
                    }
                }
            }
        }
        .navigationTitle("Emergencies")
        .navigationBarTitleDisplayMode(.large)
        .overlay {
            if store.openEmergencies.isEmpty {
                ContentUnavailableView {
                    Label("All Clear", systemImage: "checkmark.shield.fill")
                } description: {
                    Text("No active emergencies at this event.")
                }
            }
        }
        .animation(.smooth, value: store.openEmergencies)
        .sensoryFeedback(.success, trigger: store.openEmergencies.count)
        // Confirmed, because an accidental swipe would hide a live emergency from staff.
        .alert("Mark resolved?", isPresented: presenting($resolving), presenting: resolving) { e in
            Button("Cancel", role: .cancel) {}
            Button("Mark Resolved") { store.resolveEmergency(e.id) }
        } message: { e in
            Text("Removes the emergency at \(e.location) from the active list. Only do this "
                 + "once the patient has been handed over or no longer needs help.")
        }
        // Confirmed both ways: approving authorises a real 911 call, denying blocks one.
        .alert(approvalGranted ? "Approve 911 call?" : "Deny 911 call?",
               isPresented: presenting($approvalTarget), presenting: approvalTarget) { e in
            Button("Cancel", role: .cancel) {}
            Button(approvalGranted ? "Approve" : "Deny",
                   role: approvalGranted ? .destructive : nil) {
                store.setCallApproval(e.id, approved: approvalGranted)
            }
        } message: { e in
            Text(approvalGranted
                 ? "The person reporting the emergency at \(e.location) will be allowed to dial 911."
                 : "The reporter will be told a 911 call was not approved. The emergency stays "
                   + "active and responders remain alerted.")
        }
    }
}

/// One incident. Law of Proximity does the work: location and patient state sit tight
/// together at the top because they are what a responder reads first; provenance and
/// timing sink to the bottom in a quieter voice.
struct EmergencyCard: View {
    var emergency: EmergencyRequest
    var isOrganizer: Bool
    var onApprove: (Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Label(emergency.location, systemImage: "mappin.circle.fill")
                    .font(.title3.bold())
                Spacer()
                StatusPill(text: emergency.status,
                           tint: emergency.status == EmergencyRequest.statusActive
                               ? .emergency : .pending)
            }

            HStack(spacing: 8) {
                vital(emergency.consciousness, symbol: "eye.fill")
                vital(emergency.breathing, symbol: "lungs.fill")
            }

            if !emergency.description.isEmpty {
                Text(emergency.description)
                    .font(.subheadline)
            }

            if let responder = emergency.assignedResponderName, !responder.trimmed.isEmpty {
                Label("Accepted by \(responder)", systemImage: "person.fill.checkmark")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.resolved)
            } else if emergency.status == EmergencyRequest.statusActive {
                Label("Tap to respond", systemImage: "hand.tap.fill")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.tint)
            }

            approvalSection

            Text("\(reporter) · \(shortTime(emergency.date))")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 6)
    }

    private var reporter: String {
        let name = emergency.reporterName?.trimmed ?? ""
        return name.isEmpty ? emergency.reporterRole.capitalized : name
    }

    private func vital(_ text: String, symbol: String) -> some View {
        Label(text, systemImage: symbol)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .glassEffect(.regular, in: .capsule)
    }

    /// The 911 approval row only means anything for events that require approval, so it
    /// stays hidden until the reporter has actually asked. Only the organizer gets the
    /// buttons; everyone else just sees where the request stands.
    @ViewBuilder
    private var approvalSection: some View {
        let approval = emergency.callApprovalStatus
        if approval != EmergencyRequest.approvalNotRequested {
            Divider()
            if approval == EmergencyRequest.approvalPending {
                if isOrganizer {
                    Text("911 call requested — your approval needed")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.pending)
                    HStack(spacing: 10) {
                        Button("Approve 911", systemImage: "phone.fill") { onApprove(true) }
                            .buttonStyle(.glassProminent)
                            .tint(.emergency)
                        Button("Deny") { onApprove(false) }
                            .buttonStyle(.glass)
                    }
                    .font(.subheadline.weight(.semibold))
                    .buttonBorderShape(.capsule)
                } else {
                    Label("Waiting for organizer approval to call 911",
                          systemImage: "clock.fill")
                        .font(.footnote)
                        .foregroundStyle(.pending)
                }
            } else if approval == EmergencyRequest.approvalApproved {
                Label("911 call approved", systemImage: "checkmark.seal.fill")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.resolved)
            } else {
                Label("911 call denied by organizer", systemImage: "nosign")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct HelpListView: View {
    @EnvironmentObject private var store: Store

    var body: some View {
        List {
            ForEach(store.openHelpRequests) { request in
                VStack(alignment: .leading, spacing: 8) {
                    Text(request.description)
                        .font(.headline)
                    Label(request.location.isEmpty ? "No location given" : request.location,
                          systemImage: "mappin.circle")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text(shortTime(request.date))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
                .swipeActions(edge: .trailing) {
                    Button("Resolve", systemImage: "checkmark.circle.fill") {
                        store.resolveHelpRequest(request.id)
                    }
                    .tint(.resolved)
                }
            }
        }
        .navigationTitle("Help Requests")
        .navigationBarTitleDisplayMode(.large)
        .overlay {
            if store.openHelpRequests.isEmpty {
                ContentUnavailableView {
                    Label("Nothing Waiting", systemImage: "tray")
                } description: {
                    Text("Attendee requests show up here as they come in.")
                }
            }
        }
        .animation(.smooth, value: store.openHelpRequests)
        .sensoryFeedback(.success, trigger: store.openHelpRequests.count)
    }
}

/// The alert a qualified responder receives. Opened from the local notification,
/// or by tapping an Active emergency in the staff list.
struct ResponderAlertView: View {
    let emergencyId: String
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let emergency = store.emergency(id: emergencyId) {
                content(emergency)
            } else {
                ContentUnavailableView {
                    Label("Emergency Unavailable", systemImage: "questionmark.circle")
                } description: {
                    Text("This emergency is no longer active.")
                }
            }
        }
        .navigationTitle("Responder Alert")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func content(_ e: EmergencyRequest) -> some View {
        let accepted = e.status != EmergencyRequest.statusActive

        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Selective Attention: under alarm, people see one thing. Make it the place.
                VStack(alignment: .leading, spacing: 10) {
                    Label("Emergency", systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.emergency)
                    Text(e.location)
                        .font(.system(size: 38, weight: .bold, design: .rounded))
                    HStack(spacing: 8) {
                        StatusPill(text: e.consciousness, tint: .emergency, systemImage: "eye.fill")
                        StatusPill(text: e.breathing, tint: .emergency, systemImage: "lungs.fill")
                    }
                    if !e.description.isEmpty {
                        Text(e.description).font(.body)
                    }
                    Text("Reported \(shortTime(e.date))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .glassEffect(.regular.tint(.emergency.opacity(0.14)), in: .rect(cornerRadius: 26))

                if accepted {
                    // Plain-text directions for now — real turn-by-turn needs venue maps.
                    Label("Go to \(e.location)", systemImage: "figure.walk.motion")
                        .font(.headline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(20)
                        .glassEffect(.regular.tint(.resolved.opacity(0.16)),
                                     in: .rect(cornerRadius: 22))
                }

                // Who the app alerted, and who took it. "Alerted" means eligible to receive
                // the alert — confirming delivery to each phone would need a backend receipt.
                VStack(alignment: .leading, spacing: 10) {
                    Text("Responders")
                        .font(.headline)
                    if store.qualifiedResponders.isEmpty {
                        Text("No qualified responders have joined this event yet.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(store.qualifiedResponders) { responder in
                            Label(responder.summary, systemImage: "person.fill")
                                .font(.subheadline)
                        }
                    }
                    Divider()
                    Label(acceptedBy(e), systemImage: "person.fill.checkmark")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(accepted ? .resolved : .secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .glassEffect(.regular, in: .rect(cornerRadius: 22))
            }
            .padding(20)
        }
        .safeAreaBar(edge: .bottom) {
            VStack(spacing: 12) {
                if !accepted {
                    PrimaryAction(title: "Accept — I'm Responding",
                                  systemImage: "figure.run",
                                  tint: .resolved) {
                        store.acceptEmergency(e.id)
                    }
                }
                // Declining leaves the emergency Active for another responder.
                Button(accepted ? "Close" : "Cannot Respond") { dismiss() }
                    .font(.subheadline)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
        .sensoryFeedback(.success, trigger: accepted)
    }

    private func acceptedBy(_ e: EmergencyRequest) -> String {
        let name = e.assignedResponderName?.trimmed ?? ""
        return name.isEmpty ? "Nobody has accepted yet" : "Accepted by \(name)"
    }
}

/// Bridges an optional "what am I confirming" state onto alert(isPresented:).
func presenting<T>(_ value: Binding<T?>) -> Binding<Bool> {
    Binding(get: { value.wrappedValue != nil },
            set: { if !$0 { value.wrappedValue = nil } })
}
