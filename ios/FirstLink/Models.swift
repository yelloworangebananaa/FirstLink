import Foundation

/// Field names and values match the Android app exactly, so both clients can
/// share one Firestore project and see each other's events.

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
}

struct User: Codable, Identifiable, Equatable {
    var id: String = ""
    var eventId: String = ""
    var role: String = Role.attendee
    var name: String?
    var qualifications: [String] = []
    var qualifiedResponder: Bool = false

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

struct EmergencyRequest: Codable, Identifiable, Equatable {
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
    /// Firebase Auth uid, so the reporting device can recognise its own reports.
    var reporterUid: String?
    var reporterName: String?
    var callApprovalStatus: String = approvalNotRequested
    var location: String = ""
    var description: String = ""
    var consciousness: String = ""
    var breathing: String = ""
    /// Milliseconds since epoch — Android writes System.currentTimeMillis().
    var timestamp: Int64 = 0
    var status: String = statusActive
    var assignedResponderId: String?
    var assignedResponderName: String?

    var date: Date { Date(timeIntervalSince1970: Double(timestamp) / 1000) }
    var patientStatus: String { "\(consciousness) • \(breathing)" }
}

struct HelpRequest: Codable, Identifiable, Equatable {
    static let statusOpen = "Open"
    static let statusResolved = "Resolved"

    var id: String = ""
    var eventId: String = ""
    var location: String = ""
    var description: String = ""
    var timestamp: Int64 = 0
    var status: String = statusOpen

    var date: Date { Date(timeIntervalSince1970: Double(timestamp) / 1000) }
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

func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

func shortTime(_ date: Date) -> String {
    date.formatted(date: .omitted, time: .shortened)
}
