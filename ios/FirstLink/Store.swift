import Foundation
import FirebaseAuth
import FirebaseFirestore

/// Shared data layer backed by Cloud Firestore — the port of FirstLinkRepository.
///
/// Snapshot listeners publish straight into `@Published` properties, so screens
/// just read them and SwiftUI repaints itself. That replaces the Android version's
/// setOnChangeListener/refresh() plumbing entirely.
///
/// The current user is deliberately NOT in this device's Firestore session state:
/// it describes who you signed in as on this phone, not shared event state.
final class Store: ObservableObject {

    static let shared = Store()

    private enum Key {
        static let eventId = "event_id"
        static let role = "role"
        static let name = "name"
        static let qualified = "qualified_responder"
    }

    private enum Col {
        static let events = "events"
        static let emergencies = "emergencies"
        static let help = "helpRequests"
        static let users = "users"
    }

    @Published private(set) var event: Event?
    @Published private(set) var currentUser: User?
    @Published private(set) var emergencies: [EmergencyRequest] = []
    @Published private(set) var helpRequests: [HelpRequest] = []
    @Published private(set) var participants: [User] = []

    /// Set when the organizer ends the event from another device, so the UI can say so.
    @Published var eventEndedNotice = false
    /// Emergency id carried in from a tapped notification.
    @Published var pendingResponderAlert: String?

    private let db = Firestore.firestore()
    private let defaults = UserDefaults.standard
    private var listeners: [ListenerRegistration] = []

    /// The first snapshot replays every existing document as "added". Without this
    /// the app would fire a burst of notifications for old reports on every launch.
    private var emergenciesPrimed = false
    private var helpPrimed = false

    private init() {
        if let eventId = defaults.string(forKey: Key.eventId),
           let role = defaults.string(forKey: Key.role) {
            var user = User(id: uid ?? "", eventId: eventId, role: role)
            user.name = defaults.string(forKey: Key.name)
            // Must be restored, or a qualified responder silently stops being
            // eligible for emergency alerts the first time the app restarts.
            user.qualifiedResponder = defaults.bool(forKey: Key.qualified)
            currentUser = user
            attachListeners(eventId: eventId)
        }
    }

    private var uid: String? { Auth.auth().currentUser?.uid }

    /// Anonymous sign-in. Every Firestore write waits on this.
    @discardableResult
    func signIn() async throws -> String {
        if let uid { return uid }
        return try await Auth.auth().signInAnonymously().user.uid
    }

    // MARK: - Listeners

    private func attachListeners(eventId: String) {
        detachListeners()
        emergenciesPrimed = false
        helpPrimed = false

        listeners.append(db.collection(Col.events).document(eventId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self, let event = try? snapshot?.data(as: Event.self) else { return }
                self.event = event
                // Ending an event flips active=false; every joined device sees it here.
                if !event.active { self.clearSession(); self.eventEndedNotice = true }
            })

        listeners.append(db.collection(Col.emergencies).whereField("eventId", isEqualTo: eventId)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self, let snapshot else {
                    if let error { print("emergency listener: \(error)") }
                    return
                }
                // Newest first. Firestore can't order without an index unless we ask for one.
                self.emergencies = snapshot.documents
                    .compactMap { try? $0.data(as: EmergencyRequest.self) }
                    .sorted { $0.timestamp > $1.timestamp }

                if self.emergenciesPrimed {
                    for change in snapshot.documentChanges where change.type == .added {
                        if let e = try? change.document.data(as: EmergencyRequest.self) {
                            self.alertIfNeeded(e)
                        }
                    }
                }
                self.emergenciesPrimed = true
            })

        listeners.append(db.collection(Col.help).whereField("eventId", isEqualTo: eventId)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self, let snapshot else {
                    if let error { print("help listener: \(error)") }
                    return
                }
                self.helpRequests = snapshot.documents
                    .compactMap { try? $0.data(as: HelpRequest.self) }
                    .sorted { $0.timestamp > $1.timestamp }

                if self.helpPrimed {
                    for change in snapshot.documentChanges where change.type == .added {
                        if let h = try? change.document.data(as: HelpRequest.self) {
                            self.alertIfNeeded(h)
                        }
                    }
                }
                self.helpPrimed = true
            })

        listeners.append(db.collection(Col.users).whereField("eventId", isEqualTo: eventId)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self, let snapshot else {
                    if let error { print("participant listener: \(error)") }
                    return
                }
                self.participants = snapshot.documents.compactMap { try? $0.data(as: User.self) }
            })
    }

    private func detachListeners() {
        listeners.forEach { $0.remove() }
        listeners = []
    }

    /// A phone raises its own notification when someone ELSE reports an emergency.
    ///
    /// This is why cross-device alerts work without Firebase Cloud Messaging: the
    /// snapshot listener is already open, so the device hears about the new document
    /// and notifies itself. The limit is that listeners only run while the app is
    /// running or briefly backgrounded — a terminated app cannot be woken this way,
    /// which is what FCM plus a Cloud Function would add.
    private func alertIfNeeded(_ e: EmergencyRequest) {
        guard e.reporterUid != uid, let user = currentUser else { return }
        // Emergency alerts are for qualified responders and staff.
        guard user.qualifiedResponder || user.isStaffLevel else { return }
        Notifications.emergency(id: e.id, location: e.location)
    }

    private func alertIfNeeded(_ h: HelpRequest) {
        guard currentUser?.isStaffLevel == true else { return }
        Notifications.help(description: h.description)
    }

    /// Fire-and-forget write; failures are logged, exactly like the Android version.
    private func write(_ label: String, _ body: @escaping () async throws -> Void) {
        Task { @MainActor in
            do {
                try await signIn()
                try await body()
            } catch {
                print("\(label): \(error)")
            }
        }
    }

    // MARK: - Event

    func createEvent(name: String, venue: String, organizer: String, require911Approval: Bool) {
        let ref = db.collection(Col.events).document()
        var created = Event(id: ref.documentID, name: name, venueName: venue,
                            organizerName: organizer, require911Approval: require911Approval)
        created.active = true

        event = created
        emergencies = []
        helpRequests = []
        defaults.set(ref.documentID, forKey: Key.eventId)
        attachListeners(eventId: ref.documentID)

        write("createEvent") { try ref.setData(from: created) }
    }

    func saveEventCodes(attendee: String, staff: String) {
        guard var current = event else { return }
        current.attendeeCode = attendee
        current.staffCode = staff
        event = current
        let id = current.id
        write("saveEventCodes") { [db] in
            try await db.collection(Col.events).document(id)
                .updateData(["attendeeCode": attendee, "staffCode": staff])
        }
    }

    func addLocations(_ locations: [String]) {
        guard var current = event else { return }
        current.locations.append(contentsOf: locations)
        event = current
        let id = current.id
        write("addLocations") { [db] in
            try await db.collection(Col.events).document(id)
                .updateData(["locations": FieldValue.arrayUnion(locations)])
        }
    }

    var locations: [String] { event?.locations ?? [] }

    /// Looks the code up on the server, so a code created on another device works.
    /// A code only matches while its event is still active, which is what makes
    /// ending an event invalidate both codes everywhere.
    /// - Returns: the role joined as, or nil when the code is not a live event code.
    func join(code: String) async throws -> String? {
        try await signIn()
        let normalized = code.trimmingCharacters(in: .whitespaces).uppercased()

        for (field, role) in [("staffCode", Role.staff), ("attendeeCode", Role.attendee)] {
            let snapshot = try await db.collection(Col.events)
                .whereField(field, isEqualTo: normalized)
                .whereField("active", isEqualTo: true)
                .limit(to: 1)
                .getDocuments()
            if let doc = snapshot.documents.first,
               let joined = try? doc.data(as: Event.self) {
                event = joined
                currentUser = User(id: uid ?? "", eventId: joined.id, role: role)
                defaults.set(joined.id, forKey: Key.eventId)
                defaults.set(role, forKey: Key.role)
                attachListeners(eventId: joined.id)
                return role
            }
        }
        return nil
    }

    /// Flips active=false on the shared event, which every other device sees through
    /// its snapshot listener — their codes stop working and their dashboards bail out.
    func endEvent() {
        if let id = event?.id {
            write("endEvent") { [db] in
                try await db.collection(Col.events).document(id).updateData(["active": false])
            }
        }
        clearSession()
    }

    /// Drops this device's session without touching the shared event.
    func clearSession() {
        detachListeners()
        event = nil
        currentUser = nil
        emergencies = []
        helpRequests = []
        participants = []
        [Key.eventId, Key.role, Key.name, Key.qualified].forEach(defaults.removeObject(forKey:))
    }

    // MARK: - User

    func createCurrentUser(role: String) {
        currentUser = User(id: uid ?? "", eventId: event?.id ?? "", role: role)
        defaults.set(role, forKey: Key.role)
    }

    /// Publishes this person's name and qualifications to the event, so staff can
    /// see who is available to respond and who accepted a call.
    func saveProfile(name: String, qualifications: [String], qualifiedResponder: Bool) {
        guard var user = currentUser else { return }
        user.name = name
        user.qualifications = qualifications
        user.qualifiedResponder = qualifiedResponder
        currentUser = user
        defaults.set(name, forKey: Key.name)
        defaults.set(qualifiedResponder, forKey: Key.qualified)

        write("saveProfile") { [db, weak self] in
            guard let uid = self?.uid else { return }
            var toSave = user
            toSave.id = uid
            self?.currentUser = toSave
            try db.collection(Col.users).document(uid).setData(from: toSave)
        }

    }

    /// Who is eligible to receive emergency alerts for this event. This is who the
    /// app alerts — not a delivery receipt, which would need a backend to confirm.
    var qualifiedResponders: [User] { participants.filter(\.qualifiedResponder) }

    private var currentUserName: String {
        let name = currentUser?.name?.trimmingCharacters(in: .whitespaces) ?? ""
        if !name.isEmpty { return name }
        return defaults.string(forKey: Key.name) ?? "Unnamed responder"
    }

    /// True when this device is the organizer, i.e. the one who may approve 911 calls.
    var isOrganizer: Bool { currentUser?.role == Role.organizer }

    // MARK: - Emergencies

    @discardableResult
    func addEmergency(location: String, description: String,
                      consciousness: String, breathing: String) -> EmergencyRequest {
        let ref = db.collection(Col.emergencies).document()
        let request = EmergencyRequest(
            id: ref.documentID,
            eventId: event?.id ?? "",
            reporterRole: currentUser?.role ?? Role.attendee,
            reporterUid: uid,
            reporterName: currentUserName,
            location: location,
            description: description,
            consciousness: consciousness,
            breathing: breathing,
            timestamp: nowMillis())

        emergencies.insert(request, at: 0)
        write("addEmergency") { try ref.setData(from: request) }
        return request
    }

    /// Everything not yet resolved, newest first.
    var openEmergencies: [EmergencyRequest] {
        emergencies.filter { $0.status != EmergencyRequest.statusResolved }
    }

    func emergency(id: String) -> EmergencyRequest? {
        emergencies.first { $0.id == id }
    }

    func acceptEmergency(_ id: String) {
        let responderId = uid ?? ""
        let responderName = currentUserName
        patch(id) {
            $0.status = EmergencyRequest.statusResponderAssigned
            $0.assignedResponderId = responderId
            $0.assignedResponderName = responderName
        }
        write("acceptEmergency") { [db] in
            try await db.collection(Col.emergencies).document(id).updateData([
                "status": EmergencyRequest.statusResponderAssigned,
                "assignedResponderId": responderId,
                "assignedResponderName": responderName,
            ])
        }
    }

    func resolveEmergency(_ id: String) {
        patch(id) { $0.status = EmergencyRequest.statusResolved }
        write("resolveEmergency") { [db] in
            try await db.collection(Col.emergencies).document(id)
                .updateData(["status": EmergencyRequest.statusResolved])
        }
    }

    private func patch(_ id: String, _ change: (inout EmergencyRequest) -> Void) {
        guard let index = emergencies.firstIndex(where: { $0.id == id }) else { return }
        change(&emergencies[index])
    }

    // MARK: - 911 call approval

    /// Reporter asks the organizer for permission to dial 911.
    func requestCallApproval(_ id: String) {
        setApproval(id, EmergencyRequest.approvalPending)
    }

    /// Organizer's answer. Denied leaves the emergency active; it just blocks dialling.
    func setCallApproval(_ id: String, approved: Bool) {
        setApproval(id, approved ? EmergencyRequest.approvalApproved : EmergencyRequest.approvalDenied)
    }

    private func setApproval(_ id: String, _ value: String) {
        patch(id) { $0.callApprovalStatus = value }
        write("setApproval") { [db] in
            try await db.collection(Col.emergencies).document(id)
                .updateData(["callApprovalStatus": value])
        }
    }

    /// An emergency this device reported that the organizer has since approved for a
    /// 911 call. Lets the reporter pick the call back up after navigating away.
    var myApprovedCall: EmergencyRequest? {
        guard let uid else { return nil }
        return emergencies.first {
            $0.reporterUid == uid
                && $0.callApprovalStatus == EmergencyRequest.approvalApproved
                && $0.status != EmergencyRequest.statusResolved
        }
    }

    // MARK: - Help requests

    func addHelpRequest(location: String, description: String) {
        let ref = db.collection(Col.help).document()
        let request = HelpRequest(id: ref.documentID, eventId: event?.id ?? "",
                                  location: location, description: description,
                                  timestamp: nowMillis())
        helpRequests.insert(request, at: 0)
        write("addHelpRequest") { try ref.setData(from: request) }
    }

    /// Everything not yet resolved, newest first.
    var openHelpRequests: [HelpRequest] {
        helpRequests.filter { $0.status != HelpRequest.statusResolved }
    }

    func resolveHelpRequest(_ id: String) {
        if let index = helpRequests.firstIndex(where: { $0.id == id }) {
            helpRequests[index].status = HelpRequest.statusResolved
        }
        write("resolveHelpRequest") { [db] in
            try await db.collection(Col.help).document(id)
                .updateData(["status": HelpRequest.statusResolved])
        }
    }
}
