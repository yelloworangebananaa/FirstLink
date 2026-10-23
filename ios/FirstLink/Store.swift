import Foundation
import Supabase

/// Shared data layer backed by Supabase — the port of FirstLinkRepository, and the
/// only file that talks to the backend.
///
/// Realtime changes trigger a re-fetch of that table's rows, which publish straight
/// into `@Published` properties, so screens just read them and SwiftUI repaints
/// itself. That replaces the Android version's setOnChangeListener/refresh() plumbing.
///
/// The current user is also kept in UserDefaults: it describes who you signed in as
/// on this phone, so it must survive a restart before the first fetch lands.
final class Store: ObservableObject {

    static let shared = Store()

    private enum Key {
        static let eventId = "event_id"
        static let role = "role"
        static let name = "name"
        static let qualified = "qualified_responder"
        static let contactFor = "contact_for"
    }

    private enum Table {
        static let events = "events"
        static let emergencies = "emergencies"
        static let help = "help_requests"
        static let users = "users"
        static let contacts = "contacts"
    }

    @Published private(set) var event: Event?
    @Published private(set) var currentUser: User?
    @Published private(set) var emergencies: [EmergencyRequest] = []
    @Published private(set) var helpRequests: [HelpRequest] = []
    @Published private(set) var participants: [User] = []
    @Published private(set) var contacts: [Contact] = []

    /// Set when the organizer ends the event from another device, so the UI can say so.
    @Published var eventEndedNotice = false
    /// Emergency id carried in from a tapped notification.
    @Published var pendingResponderAlert: String?

    /// The anon key is public by design: row level security in supabase/schema.sql is
    /// what keeps one event's data away from everyone else.
    private let client = SupabaseClient(
        supabaseURL: URL(string: "https://wnevnenryvvsmjjecysn.supabase.co")!,
        supabaseKey: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InduZXZuZW5yeXZ2c21qamVjeXNuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI3MjA4NTEsImV4cCI6MjEwODI5Njg1MX0.Z5jvz5wtvjqFsUev_jliiZ6f0dhiilQrRZoXL7tEJkM",
        options: .init(db: .init(decoder: .rows),
                       auth: .init(emitLocalSessionAsInitialSession: true)))
    private let defaults = UserDefaults.standard
    private var channel: RealtimeChannelV2?
    private var listening: Task<Void, Never>?
    /// One re-fetch per table for the current event. Empty when not in an event.
    private var refetchers: [@MainActor () async throws -> Void] = []
    /// The tail of the write queue. See write(_:_:).
    private var lastWrite: Task<Void, Never>?

    /// Decides which snapshot changes this phone should be notified about.
    private var alerts = AlertPolicy()

    private init() {
        if let eventId = defaults.string(forKey: Key.eventId), UUID(uuidString: eventId) == nil {
            // A session saved by the Firebase build: that id means nothing to Supabase.
            clearSession()
        } else if let eventId = defaults.string(forKey: Key.eventId),
                  let role = defaults.string(forKey: Key.role) {
            var user = User(id: uid ?? "", eventId: eventId, role: role)
            user.name = defaults.string(forKey: Key.name)
            // Must be restored, or a qualified responder silently stops being
            // eligible for emergency alerts the first time the app restarts.
            user.qualifiedResponder = defaults.bool(forKey: Key.qualified)
            user.contactFor = defaults.string(forKey: Key.contactFor)
            currentUser = user
            attachListeners(eventId: eventId)
        }
    }

    /// Lowercased, because that is how Postgres prints a uuid back — accepted_uids and
    /// users.id have to compare equal to it.
    var uid: String? { client.auth.currentUser?.id.uuidString.lowercased() }

    /// Anonymous sign-in, kept in the keychain across launches. Every write waits on this.
    @discardableResult
    func signIn() async throws -> String {
        if let uid { return uid }
        return try await client.auth.signInAnonymously().user.id.uuidString.lowercased()
    }

    // MARK: - Listeners

    /// One realtime channel per event. Any change to a table re-fetches that table's
    /// rows for the event and replaces the published array, so AlertPolicy always sees
    /// full lists, and the first fetch is its primed snapshot. RLS applies to both, so
    /// this only works once this phone organizes the event or has a users row in it.
    private func attachListeners(eventId: String) {
        detachListeners()
        alerts.reset()

        let channel = client.channel("event-\(eventId)")
        self.channel = channel
        // Every stream has to be registered before the channel subscribes.
        func changes(_ table: String, _ column: String = "event_id") -> AsyncStream<AnyAction> {
            channel.postgresChange(AnyAction.self, table: table, filter: .eq(column, value: eventId))
        }
        let feeds: [(String, AsyncStream<AnyAction>, @MainActor () async throws -> Void)] = [
            (Table.events, changes(Table.events, "id"), { try await self.fetchEvent(eventId) }),
            (Table.emergencies, changes(Table.emergencies), {
                // Newest first.
                self.emergencies = try await self.rows(Table.emergencies, eventId)
                    .sorted { (a: EmergencyRequest, b) in a.date > b.date }
                self.raise(self.alerts.onEmergencies(self.emergencies, me: self.me))
            }),
            (Table.help, changes(Table.help), {
                self.helpRequests = try await self.rows(Table.help, eventId)
                    .sorted { (a: HelpRequest, b) in a.date > b.date }
                self.raise(self.alerts.onHelpRequests(self.helpRequests, me: self.me,
                                                      contactPersonFor: self.contactPerson(for:)))
            }),
            (Table.users, changes(Table.users), {
                self.participants = try await self.rows(Table.users, eventId)
            }),
            (Table.contacts, changes(Table.contacts), {
                self.contacts = try await self.rows(Table.contacts, eventId)
            }),
        ]

        // Changes made while the socket was down (app backgrounded, signal lost) are
        // caught up by refreshAll(), which the app calls on every return to the foreground.
        refetchers = feeds.map(\.2)
        listening = Task { @MainActor in
            // Subscribe before the first fetch, so nothing lands between the two unseen.
            do { try await channel.subscribeWithError() } catch is CancellationError { return } catch {
                print("realtime: \(error)")
            }
            await withTaskGroup(of: Void.self) { group in
                for (table, stream, refetch) in feeds {
                    group.addTask { @MainActor in
                        var changes = stream.makeAsyncIterator()
                        repeat {
                            do { try await refetch() } catch is CancellationError { return } catch {
                                print("\(table) fetch: \(error)")
                            }
                        } while await changes.next() != nil
                    }
                }
            }
        }
    }

    /// One table's rows for the event. Throws instead of returning once the session has
    /// been cleared, so a fetch already in flight can't repopulate it.
    private func rows<T: Decodable>(_ table: String, _ eventId: String,
                                    where column: String = "event_id") async throws -> [T] {
        let rows: [T] = try await client.from(table).select().eq(column, value: eventId)
            .execute().value
        try Task.checkCancellation()
        // Session cleared (or switched) while the fetch was in flight: drop the result.
        guard defaults.string(forKey: Key.eventId) == eventId else { throw CancellationError() }
        return rows
    }

    private func fetchEvent(_ eventId: String) async throws {
        guard let event: Event = try await rows(Table.events, eventId, where: "id").first else { return }
        self.event = event
        // Ending an event flips active=false; every joined device sees it here.
        if !event.active { clearSession(); eventEndedNotice = true }
    }

    /// Re-fetches every table for the current event.
    func refreshAll() async {
        for refetch in refetchers {
            do { try await refetch() } catch is CancellationError { return } catch { print("refresh: \(error)") }
        }
    }

    private func detachListeners() {
        refetchers = []
        listening?.cancel()
        listening = nil
        if let channel { Task { await client.removeChannel(channel) } }
        channel = nil
    }

    /// Turns the policy's decisions into notifications. Listeners only run while the
    /// app is running or briefly backgrounded — a terminated app cannot be woken this
    /// way, which is what APNs push from a server function would add.
    private func raise(_ toRaise: [AlertPolicy.Alert]) {
        for alert in toRaise {
            switch alert {
            case let .emergency(id, location): Notifications.emergency(id: id, location: location)
            case let .help(title, text): Notifications.help(title: title, body: text)
            case let .accepted(name, noun): Notifications.accepted(by: name, noun: noun)
            }
        }
    }

    private var me: AlertPolicy.Me { AlertPolicy.Me(uid: uid, user: currentUser) }

    /// "Water problem" -> "Dana Reyes". Nil when nobody is named for that problem.
    func contactPerson(for problem: String?) -> String? {
        guard let problem, !problem.isEmpty else { return nil }
        return contacts.first { $0.problem == problem }?.person
    }

    /// Fire-and-forget write; failures are logged, exactly like the Android version.
    /// Writes run one at a time in call order, so "create event, then save codes"
    /// never reaches the server the other way round.
    private func write(_ label: String, _ body: @escaping () async throws -> Void) {
        lastWrite = Task { @MainActor [previous = lastWrite] in
            await previous?.value
            do {
                try await signIn()
                try await body()
            } catch {
                print("\(label): \(error)")
            }
        }
    }

    // MARK: - Event

    /// The id is made here so the event can be shown before the insert lands. The
    /// server fills organizer_uid from the session, which is what RLS keys on.
    func createEvent(name: String, venue: String, organizer: String, require911Approval: Bool) {
        let id = UUID().uuidString.lowercased()
        var created = Event(id: id, name: name, venueName: venue,
                            organizerName: organizer, require911Approval: require911Approval)
        created.active = true

        event = created
        emergencies = []
        helpRequests = []
        defaults.set(id, forKey: Key.eventId)
        attachListeners(eventId: id)

        write("createEvent") { [client, weak self] in
            try await client.from(Table.events).insert(created).execute()
            guard let uid = self?.uid else { return }
            try await client.from(Table.users)
                .upsert(User(id: uid, eventId: id, role: Role.organizer)).execute()
        }
    }

    func saveEventCodes(attendee: String, staff: String) {
        guard var current = event else { return }
        current.attendeeCode = attendee
        current.staffCode = staff
        event = current
        let id = current.id
        write("saveEventCodes") { [client] in
            try await client.from(Table.events)
                .update(["attendee_code": attendee, "staff_code": staff]).eq("id", value: id).execute()
        }
    }

    /// The organizer is the only writer of an event, so the whole list is written.
    func addLocations(_ locations: [String]) {
        guard var current = event else { return }
        current.locations.append(contentsOf: locations)
        event = current
        let id = current.id
        let all = current.locations
        write("addLocations") { [client] in
            try await client.from(Table.events).update(["locations": all]).eq("id", value: id).execute()
        }
    }

    var locations: [String] { event?.locations ?? [] }

    /// Looks the code up on the server, so a code created on another device works.
    /// A code only matches while its event is still active, which is what makes
    /// ending an event invalidate every code everywhere. redeem_code keeps Android's
    /// precedence: staff code, then attendee code, then an Important People contact
    /// code — a person joining with that last one is staff, tagged with their problem
    /// so their phone can single that problem's requests out.
    /// - Returns: the role joined as, or nil when the code is not a live code.
    func join(code: String) async throws -> String? {
        let uid = try await signIn()
        let redeemed: [Redeemed] = try await client
            .rpc("redeem_code", params: ["join_code": code]).execute().value
        guard let r = redeemed.first else { return nil }

        // RLS hides the event until this phone has a users row in it, so write it first.
        var user = User(id: uid, eventId: r.eventId, role: r.role)
        user.contactFor = r.contactFor
        try await client.from(Table.users).upsert(user).execute()
        guard let joined: Event = try await rows(Table.events, r.eventId, where: "id").first
        else { return nil }
        finishJoin(joined, role: r.role, contactFor: r.contactFor)
        return r.role
    }

    private struct Redeemed: Decodable {
        let eventId: String
        let role: String
        let contactFor: String?

        enum CodingKeys: String, CodingKey {
            case role
            case eventId = "event_id", contactFor = "contact_for"
        }
    }

    private func finishJoin(_ joined: Event, role: String, contactFor: String?) {
        event = joined
        var user = User(id: uid ?? "", eventId: joined.id, role: role)
        user.contactFor = contactFor
        currentUser = user
        defaults.set(joined.id, forKey: Key.eventId)
        defaults.set(role, forKey: Key.role)
        defaults.set(contactFor, forKey: Key.contactFor)
        attachListeners(eventId: joined.id)
    }

    /// Flips active=false on the shared event, which every other device sees through
    /// its realtime channel — their codes stop working and their dashboards bail out.
    func endEvent() {
        if let id = event?.id {
            write("endEvent") { [client] in
                try await client.from(Table.events).update(["active": false]).eq("id", value: id).execute()
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
        contacts = []
        [Key.eventId, Key.role, Key.name, Key.qualified, Key.contactFor]
            .forEach(defaults.removeObject(forKey:))
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

        let eventId = defaults.string(forKey: Key.eventId)
        write("saveProfile") { [client, weak self] in
            // End Event / clearSession may have run while this write was queued.
            guard let self, self.defaults.string(forKey: Key.eventId) == eventId, let uid = self.uid else { return }
            var toSave = user
            toSave.id = uid
            self.currentUser = toSave
            try await client.from(Table.users).upsert(toSave).execute()
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

    /// Whether a reporter must wait for the organizer before being offered 911.
    var needsApprovalBeforeCalling: Bool { event?.require911Approval == true }

    /// True when this device is the organizer, i.e. the one who may approve 911 calls.
    var isOrganizer: Bool { currentUser?.role == Role.organizer }

    // MARK: - Emergencies

    /// Awaits the insert (after earlier queued writes) so the caller knows it landed;
    /// on failure the optimistic row is removed and the error thrown.
    func addEmergency(location: String, description: String,
                      consciousness: String, breathing: String) async throws -> EmergencyRequest {
        let request = EmergencyRequest(
            id: UUID().uuidString.lowercased(),
            eventId: event?.id ?? "",
            reporterRole: currentUser?.role ?? Role.attendee,
            reporterUid: uid,
            reporterName: currentUserName,
            location: location,
            description: description,
            consciousness: consciousness,
            breathing: breathing)

        emergencies.insert(request, at: 0)
        do {
            await lastWrite?.value
            try await signIn()
            try await client.from(Table.emergencies).insert(request).execute()
        } catch {
            emergencies.removeAll { $0.id == request.id }
            throw error
        }
        return request
    }

    /// Everything not yet resolved, newest first.
    var openEmergencies: [EmergencyRequest] {
        emergencies.filter { !$0.resolved }
    }

    func emergency(id: String) -> EmergencyRequest? {
        emergencies.first { $0.id == id }
    }

    func acceptEmergency(_ id: String) {
        guard let index = emergencies.firstIndex(where: { $0.id == id }) else { return }
        accept(&emergencies[index], rpc: "accept_emergency",
               inProgress: EmergencyRequest.statusResponderAssigned)
    }

    func resolveEmergency(_ id: String) {
        if let index = emergencies.firstIndex(where: { $0.id == id }) {
            emergencies[index].status = EmergencyRequest.statusResolved
        }
        resolve(id, in: Table.emergencies)
    }

    private func patch(_ id: String, _ change: (inout EmergencyRequest) -> Void) {
        guard let index = emergencies.firstIndex(where: { $0.id == id }) else { return }
        change(&emergencies[index])
    }

    /// True when this device is one of the people who accepted a report.
    func acceptedByMe(_ report: some Report) -> Bool { report.hasAccepted(uid) }

    /// Additive: a second person accepting joins the first rather than replacing them,
    /// and accepting twice is a no-op. Same fields and rule as the Android app; the RPC
    /// applies it atomically on the server, and the local patch shows it right away.
    private func accept<R: Report & AcceptRoster>(_ report: inout R, rpc: String,
                                                  inProgress: String) {
        guard !acceptedByMe(report) else { return }
        let id = report.id
        let responderId = uid ?? ""
        let responderName = currentUserName
        report.status = inProgress
        report.acceptedBy = report.accepters + [responderName]
        report.acceptedUids = (report.acceptedUids ?? []) + [responderId]
        write(rpc) { [client] in
            try await client.rpc(rpc, params: ["report_id": id, "who": responderName]).execute()
        }
    }

    private func resolve(_ id: String, in table: String) {
        write("resolve \(table)") { [client] in
            try await client.from(table).update(["status": EmergencyRequest.statusResolved])
                .eq("id", value: id).execute()
        }
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
        write("setApproval") { [client] in
            try await client.from(Table.emergencies).update(["call_approval_status": value])
                .eq("id", value: id).execute()
        }
    }

    /// An emergency this device reported that the organizer has since approved for a
    /// 911 call. Lets the reporter pick the call back up after navigating away.
    var myApprovedCall: EmergencyRequest? {
        guard let uid else { return nil }
        return emergencies.first {
            $0.reporterUid == uid
                && $0.approval == .approved
                && !$0.resolved
        }
    }

    // MARK: - ReadyMap

    /// Organizer planning data. Writes to the event only; never alerts anyone. Each edit
    /// changes one entry on the server (set_equipment / set_post), so two quick taps
    /// can't overwrite each other.
    func setAED(_ location: String, present: Bool) {
        setEquipment("aed", \.aedLocations, location, present)
    }

    func setFirstAid(_ location: String, present: Bool) {
        setEquipment("first_aid", \.firstAidLocations, location, present)
    }

    private func setEquipment(_ kind: String, _ path: WritableKeyPath<Event, [String]?>,
                              _ location: String, _ present: Bool) {
        guard var current = event else { return }
        var list = (current[keyPath: path] ?? []).filter { $0 != location }
        if present { list.append(location) }
        current[keyPath: path] = list
        event = current
        let id = current.id
        let params: [String: AnyJSON] = ["eid": .string(id), "kind": .string(kind),
                                         "place": .string(location), "present": .bool(present)]
        write("setEquipment") { [client] in
            try await client.rpc("set_equipment", params: params).execute()
        }
    }

    /// Posts a responder at a place, or clears their post when location is nil.
    func postResponder(_ uid: String, at location: String?) {
        guard var current = event else { return }
        var posts = current.responderPosts ?? [:]
        posts[uid] = location
        current.responderPosts = posts
        event = current
        let id = current.id
        let params: [String: AnyJSON] = ["eid": .string(id), "responder": .string(uid),
                                         "place": location.map(AnyJSON.string) ?? .null]
        write("postResponder") { [client] in
            try await client.rpc("set_post", params: params).execute()
        }
    }

    // MARK: - Help requests

    func addHelpRequest(location: String, description: String, problem: String? = nil) {
        let request = HelpRequest(id: UUID().uuidString.lowercased(), eventId: event?.id ?? "",
                                  location: location, description: description,
                                  problem: problem, requesterUid: uid)
        helpRequests.insert(request, at: 0)
        write("addHelpRequest") { [client] in
            try await client.from(Table.help).insert(request).execute()
        }
    }

    /// Everything not yet resolved, newest first.
    var openHelpRequests: [HelpRequest] {
        helpRequests.filter { !$0.resolved }
    }

    /// Additive, same as emergencies: several staff may work the same request.
    func acceptHelpRequest(_ id: String) {
        guard let index = helpRequests.firstIndex(where: { $0.id == id }) else { return }
        accept(&helpRequests[index], rpc: "accept_help_request", inProgress: HelpRequest.statusInProgress)
    }

    func resolveHelpRequest(_ id: String) {
        if let index = helpRequests.firstIndex(where: { $0.id == id }) {
            helpRequests[index].status = HelpRequest.statusResolved
        }
        resolve(id, in: Table.help)
    }
}

/// The writable half of a report's accept roster, for Store.accept.
protocol AcceptRoster {
    var status: String { get set }
    var acceptedBy: [String]? { get set }
    var acceptedUids: [String]? { get set }
}

extension EmergencyRequest: AcceptRoster {}
extension HelpRequest: AcceptRoster {}
