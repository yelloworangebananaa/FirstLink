// Decodes one row per table exactly as PostgREST returns it (snake_case columns,
// created_at as an ISO 8601 string), with the same decoder Store hands the Supabase
// client, and runs the alert rules — so drift between supabase/schema.sql and the
// models fails here instead of at an event. Needs no Xcode:
//   swiftc ios/FirstLink/Models.swift ios/Checks/main.swift -o /tmp/modelscheck
//   /tmp/modelscheck contract/rows.json
import Foundation

struct Rows: Decodable {
    let events: [Event]
    let emergencies: [EmergencyRequest]
    let help_requests: [HelpRequest]
    let users: [User]
    let contacts: [Contact]
}

struct Docs {
    let event: Event
    let emergency: EmergencyRequest
    let helpRequest: HelpRequest
    let user: User
    let contact: Contact
}

func check(_ ok: Bool, _ what: String) {
    if !ok { print("FAIL: \(what)"); exit(1) }
}

let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
let rows = try JSONDecoder.rows.decode(Rows.self, from: data)
let docs = Docs(event: rows.events[0], emergency: rows.emergencies[0],
                helpRequest: rows.help_requests[0], user: rows.users[0], contact: rows.contacts[0])
let dana = "0d000000-0000-4000-8000-000000000001"

check(docs.emergency.accepters == ["Dana"], "emergency acceptedBy decodes")
check(docs.emergency.hasAccepted(dana), "accept roster matches by uid")
check(!docs.emergency.hasAccepted(""), "empty uid never matches")
check(docs.emergency.approval == .pending, "approval state")
check(docs.helpRequest.senderUid == "0f000000-0000-4000-8000-000000000001", "help requester uid decodes")
check(abs(docs.emergency.date.timeIntervalSince1970 - 1761177600.123456) < 0.000_01,
      "created_at with microseconds decodes")
check(docs.helpRequest.date.timeIntervalSince1970 == 1761177600, "created_at without fraction decodes")
check(docs.event.require911Approval == false && docs.event.responderPosts?["nurse"] == "Gym",
      "event columns decode")
check(docs.helpRequest.problem == "Water problem", "help problem decodes")
check(docs.user.contactFor == "Water problem", "user contactFor decodes")
check(docs.contact.code == "K7PQ2M", "contact decodes")

// Alert rules, mirroring app/src/test/.../AlertPolicyTest.java.
let staff = AlertPolicy.Me(uid: "me", user: User(role: Role.staff))
var policy = AlertPolicy()
check(policy.onEmergencies([docs.emergency], me: staff).isEmpty, "first snapshot is silent")
var fresh = docs.emergency
fresh.id = "em2"
check(policy.onEmergencies([docs.emergency, fresh], me: staff)
      == [.emergency(id: "em2", location: "Main Hall")], "new emergency alerts staff")

let attendee = AlertPolicy.Me(uid: "me", user: User(role: Role.attendee))
var mine = docs.emergency
mine.reporterUid = "me"
mine.acceptedBy = []
var reporter = AlertPolicy()
_ = reporter.onEmergencies([mine], me: attendee)
check(reporter.onEmergencies([mine], me: attendee).isEmpty, "other field change is silent")
mine.acceptedBy = ["Dana"]
check(reporter.onEmergencies([mine], me: attendee) == [.accepted(by: "Dana", noun: "emergency")],
      "reporter hears when someone accepts")

var helpPolicy = AlertPolicy()
_ = helpPolicy.onHelpRequests([], me: staff) { _ in nil }
check(helpPolicy.onHelpRequests([docs.helpRequest], me: staff) { _ in "Dana Reyes" }
      == [.help(title: "New help request",
                text: "Water problem — Leak by the door\nContact: Dana Reyes")],
      "help names the contact for staff")

// ReadyMap, mirroring app/src/test/.../ReadyMapTest.java.
func person(_ id: String, _ role: String, _ qualified: Bool) -> User {
    User(id: id, eventId: "ev1", role: role, name: id, qualifiedResponder: qualified)
}
let people = [person("nurse", Role.attendee, true), person("staff", Role.staff, false),
              person("fan", Role.attendee, false), person("emt", Role.attendee, true),
              person("gone", Role.attendee, true)]
let spots = ReadyMap.coverage(docs.event, people)
check(spots.map(\.level) == [.covered, .partial, .partial], "coverage levels")
check(spots[1].firstAid && spots[0].responders.count == 1, "spot details")
var annex = docs.event
annex.locations = ["Annex"]
annex.aedLocations = nil
check(ReadyMap.coverage(annex, people)[0].level == .gap, "nobody and nothing is a gap")
check(ReadyMap.unposted(docs.event, people).map(\.id) == ["emt", "gone"], "unposted")
let drill = ReadyMap.drill(docs.event, people, at: "Lobby")
check(drill.spot.responders.map(\.id) == ["staff"], "drill: here")
check(drill.elsewhere.map(\.id) == ["nurse", "emt", "gone"], "drill: elsewhere")
check(!drill.spot.aed && drill.aedLocations == ["Gym", "Field"], "drill: AEDs")

print("OK")
