import Foundation

/// Values match the Android app exactly. Each type's CodingKeys are its Supabase
/// table's snake_case columns, so a row decodes straight from PostgREST.

enum Role {
    static let organizer = "organizer"
    static let staff = "staff"
    static let attendee = "attendee"
}

struct Event: Codable, Identifiable, Equatable {
    var id: String = ""
    var name: String = ""
    var venueName: String = ""
    var organizerName: String = ""
    var require911Approval: Bool = false
    var attendeeCode: String?
    var staffCode: String?
    var locations: [String] = []
    var active: Bool = true
    /// ReadyMap: places with an AED or a first-aid kit, and responder uid -> posted place.
    /// Optional because events created before ReadyMap don't have them.
    var aedLocations: [String]?
    var firstAidLocations: [String]?
    var responderPosts: [String: String]?

    enum CodingKeys: String, CodingKey {
        case id, name, locations, active
        case venueName = "venue_name", organizerName = "organizer_name"
        case require911Approval = "require_911_approval"
        case attendeeCode = "attendee_code", staffCode = "staff_code"
        case aedLocations = "aed_locations", firstAidLocations = "first_aid_locations"
        case responderPosts = "responder_posts"
    }
}

struct User: Codable, Identifiable, Equatable {
    var id: String = ""
    var eventId: String = ""
    var role: String = Role.attendee
    var name: String?
    var qualifications: [String] = []
    var qualifiedResponder: Bool = false
    /// Set when this person joined with an Important People code for one problem.
    var contactFor: String?

    enum CodingKeys: String, CodingKey {
        case id, role, name, qualifications
        case eventId = "event_id", qualifiedResponder = "qualified_responder"
        case contactFor = "contact_for"
    }

    var isStaffLevel: Bool { role == Role.organizer || role == Role.staff }

    /// Falls back to the role so lists never show a blank row.
    var displayName: String {
        let n = name?.trimmingCharacters(in: .whitespaces) ?? ""
        return n.isEmpty ? "Unnamed \(role)" : n
    }

    /// "Alice — CPR Certified, AED Trained" for the staff-facing lists.
    var summary: String {
        qualifications.isEmpty
            ? displayName
            : "\(displayName) — \(qualifications.joined(separator: ", "))"
    }
}

/// What emergencies and help requests share: someone sent it, and any number of
/// people can take it on. Accepts are additive — a second person joins the first.
protocol Report {
    var id: String { get }
    var status: String { get }
    var senderUid: String? { get }
    var acceptedBy: [String]? { get }
    var acceptedUids: [String]? { get }
}

extension Report {
    var accepters: [String] { acceptedBy ?? [] }
    /// Before sign-in there is no uid, and that must never match.
    func hasAccepted(_ uid: String?) -> Bool {
        guard let uid, !uid.isEmpty else { return false }
        return (acceptedUids ?? []).contains(uid)
    }
    /// Both kinds use the same wire value for "done".
    var resolved: Bool { status == EmergencyRequest.statusResolved }
}

struct EmergencyRequest: Codable, Identifiable, Equatable, Report {
    static let statusActive = "Active"
    static let statusResponderAssigned = "Responder Assigned"
    static let statusResolved = "Resolved"

    /// Only used when the event requires organizer approval before calling 911.
    static let approvalNotRequested = "Not Requested"
    static let approvalPending = "Pending"
    static let approvalApproved = "Approved"
    static let approvalDenied = "Denied"

    var id: String = ""
    var eventId: String = ""
    var reporterRole: String = Role.attendee
    /// Supabase Auth uid, so the reporting device can recognise its own reports.
    var reporterUid: String?
    var reporterName: String?
    var callApprovalStatus: String = approvalNotRequested
    var location: String = ""
    var description: String = ""
    var consciousness: String = ""
    var breathing: String = ""
    /// The row's created_at. Stamped on this phone, like Android's currentTimeMillis().
    var date = Date()
    var status: String = statusActive
    /// Display names of every responder who accepted, and the same people by uid.
    var acceptedBy: [String]?
    var acceptedUids: [String]?

    enum CodingKeys: String, CodingKey {
        case id, location, description, consciousness, breathing, status
        case eventId = "event_id", reporterRole = "reporter_role"
        case reporterUid = "reporter_uid", reporterName = "reporter_name"
        case callApprovalStatus = "call_approval_status", date = "created_at"
        case acceptedBy = "accepted_by", acceptedUids = "accepted_uids"
    }

    var senderUid: String? { reporterUid }
    /// "Unconscious • Breathing", skipping whatever the reporter left unanswered.
    var patientStatus: String {
        let parts = [consciousness, breathing].filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        return parts.isEmpty ? "Patient status not given" : parts.joined(separator: " • ")
    }

    /// Where a 911 call request stands. Views switch on this, never on the strings.
    enum Approval { case notRequested, pending, approved, denied }

    var approval: Approval {
        switch callApprovalStatus {
        case Self.approvalPending: .pending
        case Self.approvalApproved: .approved
        case Self.approvalDenied: .denied
        default: .notRequested
        }
    }
}

struct HelpRequest: Codable, Identifiable, Equatable, Report {
    static let statusOpen = "Open"
    static let statusInProgress = "In Progress"
    static let statusResolved = "Resolved"

    var id: String = ""
    var eventId: String = ""
    var location: String = ""
    var description: String = ""
    /// Preset problem the organizer defined, e.g. "Water problem".
    var problem: String?
    /// Supabase Auth uid of whoever sent it, so their device can recognise its own request.
    var requesterUid: String?
    var acceptedBy: [String]?
    var acceptedUids: [String]?
    /// The row's created_at.
    var date = Date()
    var status: String = statusOpen

    enum CodingKeys: String, CodingKey {
        case id, location, description, problem, status
        case eventId = "event_id", requesterUid = "requester_uid"
        case acceptedBy = "accepted_by", acceptedUids = "accepted_uids", date = "created_at"
    }

    var senderUid: String? { requesterUid }
}

/// The organizer's "this problem -> this person" list. Each has its own join code.
struct Contact: Codable, Identifiable, Equatable {
    var id: String = ""
    var eventId: String = ""
    var problem: String = ""
    var person: String = ""
    var code: String = ""

    enum CodingKeys: String, CodingKey {
        case id, problem, person, code
        case eventId = "event_id"
    }
}

extension JSONDecoder {
    /// Decodes rows as PostgREST returns them: created_at is an ISO 8601 string with
    /// microseconds. Store's Supabase client and Checks both use this one decoder.
    static var rows: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom {
            try Date.ISO8601FormatStyle().parse($0.singleValueContainer().decode(String.self))
        }
        return decoder
    }
}

/// Decides, from each snapshot of the event's reports, which notifications this phone
/// should raise — the port of Android's AlertPolicy, rule for rule. A phone notifies
/// itself when someone ELSE reports something, and the reporter hears back when
/// someone new accepts. No backend here, so every rule is testable.
struct AlertPolicy {
    struct Me {
        var uid: String?
        var staffLevel = false
        var qualified = false
        var contactFor: String?

        init(uid: String?, user: User?) {
            self.uid = uid
            staffLevel = user?.isStaffLevel ?? false
            qualified = user?.qualifiedResponder ?? false
            contactFor = user?.contactFor
        }
    }

    enum Alert: Equatable {
        case emergency(id: String, location: String)
        case help(title: String, text: String)
        case accepted(by: String, noun: String)
    }

    /// The first fetch returns every existing row as new. Without priming the
    /// app would fire a burst of notifications for old reports on every launch.
    private var emergenciesPrimed = false
    private var helpPrimed = false
    /// How many people had accepted each report last time we looked. A report we have
    /// never counted is a new one; a longer list means someone new picked it up.
    private var acceptCounts: [String: Int] = [:]

    mutating func reset() { self = AlertPolicy() }

    mutating func onEmergencies(_ all: [EmergencyRequest], me: Me) -> [Alert] {
        defer { emergenciesPrimed = true }
        return diff(all, primed: emergenciesPrimed, me: me, noun: "emergency") { e in
            // Emergency alerts are for qualified responders and staff.
            guard me.qualified || me.staffLevel else { return nil }
            return .emergency(id: e.id, location: e.location)
        }
    }

    mutating func onHelpRequests(_ all: [HelpRequest], me: Me,
                                 contactPersonFor: (String?) -> String?) -> [Alert] {
        defer { helpPrimed = true }
        return diff(all, primed: helpPrimed, me: me, noun: "request") { h in
            let problem = h.problem ?? ""
            // The standing contact for this problem is alerted alongside staff.
            let mine = !problem.isEmpty && problem == me.contactFor
            guard mine || me.staffLevel else { return nil }
            var text = problem.isEmpty ? h.description : "\(problem) — \(h.description)"
            if !mine, let contact = contactPersonFor(problem), !contact.isEmpty {
                text += "\nContact: \(contact)"
            }
            return .help(title: mine ? "You are the contact for this" : "New help request",
                         text: text)
        }
    }

    private mutating func diff<R: Report>(_ all: [R], primed: Bool, me: Me, noun: String,
                                          onNewFromSomeoneElse: (R) -> Alert?) -> [Alert] {
        var alerts: [Alert] = []
        for r in all {
            let known = acceptCounts[r.id]
            let now = r.accepters.count
            let mine = !(me.uid ?? "").isEmpty && me.uid == r.senderUid
            if primed, known == nil, !mine, let alert = onNewFromSomeoneElse(r) {
                alerts.append(alert)
            } else if primed, let known, mine, now > known, let last = r.accepters.last {
                alerts.append(.accepted(by: last, noun: noun))
            }
            acceptCounts[r.id] = now
        }
        return alerts
    }
}

/// ReadyMap Lite: how prepared each place at the venue is, from what the organizer
/// entered — the port of Android's ReadyMap, rule for rule. A planning estimate, never
/// a guarantee: it knows where people were posted, not where they are.
enum ReadyMap {
    /// covered: a responder posted here and an AED here. partial: one of the two. gap: neither.
    enum Level { case covered, partial, gap }

    struct Spot: Identifiable {
        let location: String
        let responders: [User]
        let aed: Bool
        let firstAid: Bool
        var id: String { location }
        var level: Level {
            !responders.isEmpty && aed ? .covered : !responders.isEmpty || aed ? .partial : .gap
        }
    }

    /// A practice incident: computed on this phone only, nothing written, nobody alerted.
    struct Drill {
        let spot: Spot
        /// Responders posted somewhere else, or not posted yet, who could be sent.
        let elsewhere: [User]
        let aedLocations: [String]
    }

    /// Who can be posted: qualified responders and staff.
    static func responders(_ people: [User]) -> [User] {
        people.filter { $0.qualifiedResponder || $0.isStaffLevel }
    }

    static func coverage(_ event: Event, _ people: [User]) -> [Spot] {
        event.locations.map { spot(event, people, $0) }
    }

    /// Responders with no post, or posted to a place that no longer exists.
    static func unposted(_ event: Event, _ people: [User]) -> [User] {
        responders(people).filter { u in
            guard let post = event.responderPosts?[u.id] else { return true }
            return !event.locations.contains(post)
        }
    }

    static func drill(_ event: Event, _ people: [User], at location: String) -> Drill {
        let here = spot(event, people, location)
        let hereIds = Set(here.responders.map(\.id))
        return Drill(spot: here,
                     elsewhere: responders(people).filter { !hereIds.contains($0.id) },
                     aedLocations: event.aedLocations ?? [])
    }

    private static func spot(_ event: Event, _ people: [User], _ location: String) -> Spot {
        Spot(location: location,
             responders: responders(people).filter { event.responderPosts?[$0.id] == location },
             aed: event.aedLocations?.contains(location) ?? false,
             firstAid: event.firstAidLocations?.contains(location) ?? false)
    }
}

enum JoinCode {
    // Excludes easily confused characters (0/O, 1/I).
    private static let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")

    static func generate() -> String {
        String((0..<6).map { _ in alphabet.randomElement()! })
    }

    /// Generates a code guaranteed to differ from `other`.
    static func generate(differentFrom other: String) -> String {
        var code = generate()
        while code == other { code = generate() }
        return code
    }
}

func shortTime(_ date: Date) -> String {
    date.formatted(date: .omitted, time: .shortened)
}
