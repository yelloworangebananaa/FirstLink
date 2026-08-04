# FirstLink — Firebase migration guide

## Status

| Piece | State |
|---|---|
| Gradle, plugin, `google-services.json` | **Done** |
| Anonymous Authentication | **Done** — enabled in console, signing in at app start |
| Cloud Firestore as the shared data layer | **Done** — events, emergencies, help requests |
| Cross-device join by code | **Done and tested** |
| Live list updates via snapshot listeners | **Done** |
| End-event deactivates other devices | **Done** |
| Organizer 911-call approval | **Done** — organizer-only approve/deny, synced across devices |
| Names + qualifications shared with staff | **Done** — `users` collection |
| Cross-device notifications | **Done while the app is running** — see below |
| Security rules | **Development only** — see section 7, must be tightened |
| FCM push to a closed app | **Not done** — see section 6, needs Cloud Functions + Blaze |

### How cross-device alerts work without FCM

Every device already holds an open Firestore snapshot listener on the event. When a
new emergency or help request document arrives, the *receiving* device raises its own
local notification (`FirstLinkRepository.maybeAlertForEmergency`). No push service is
involved, so this costs nothing and needs no Blaze plan.

The limit is process lifetime: listeners only run while the app process is alive
(foreground or backgrounded). A user who force-stops the app, or whose app the system
has killed to reclaim memory, will not be woken. Fixing *that* is what FCM plus the
Cloud Functions in section 6 add — the push arrives through Google's service and starts
the app regardless of its state.

The reporter deliberately gets no notification for their own report.

`FirstLinkRepository` now talks to Firestore. Reads stayed synchronous (snapshot
listeners keep an in-memory cache warm), so the activities barely changed. The one
asynchronous call is `joinWithCode`, which must reach the server to know if a code
is real.

The current user stays on the device on purpose: it describes this phone's session,
not shared event state.

**What still cannot happen:** another person's phone will not ring when an emergency
is reported. Everyone sees it the moment they open the list, because the data is
shared — but an actual push notification to a sleeping phone needs FCM plus a Cloud
Function, which is section 6.

---

## 1. Android Studio setup

1. **Tools → Firebase** opens the Assistant panel. Or go to
   <https://console.firebase.google.com>, **Add project**, name it `firstlink`.
2. In the console: **Add app → Android**. Package name must be exactly
   `com.example.firstlink`. (Debug SHA-1 is optional — anonymous auth doesn't need it.)
3. Download `google-services.json` and put it here:

   ```
   FirstLink/app/google-services.json
   ```

   Directly inside `app/`, next to `build.gradle.kts`. Not in the project root,
   not in `src/`.
4. In the console enable:
   - **Build → Authentication → Sign-in method → Anonymous → Enable**
   - **Build → Firestore Database → Create database** (start in *test mode*, then
     paste the rules from section 7 before anyone real uses it)
   - **Engage → Messaging** (nothing to configure, just needs to exist)

---

## 2. Gradle changes — ALREADY DONE

Steps 1–3 of this section are already applied to your project and the build passes.
`google-services.json` is in `app/`, the plugin runs, and the Auth / Firestore /
Messaging SDKs are on the classpath. This section is kept as a record of what changed.

`gradle/libs.versions.toml` — added to `[versions]`:

```toml
googleServices = "4.5.0"
firebaseBom = "34.16.0"
```

add to `[libraries]`:

```toml
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }
```

add to `[plugins]`:

```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

Root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

dependencies {
    // ...everything already there stays...
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
}
```

The BoM sets all Firebase versions together, so never write version numbers on the
individual `firebase-*` lines.

Keep Gson. Firestore doesn't need it, but nothing forces you to rip it out on day one.

---

## 3. Firestore collections

Four top-level collections. IDs are auto-generated document IDs unless noted.

### `/events/{eventId}`

```json
{
  "name": "Summer Hackathon",
  "venueName": "City Convention Center",
  "organizerName": "Alex Rivera",
  "require911Approval": false,
  "attendeeCode": "KPB8YT",
  "staffCode": "7J452D",
  "locations": ["Room 1", "Room 2", "Main Lobby", "Gym"],
  "active": true,
  "organizerUid": "firebase-auth-uid-of-creator",
  "createdAt": "<serverTimestamp>"
}
```

### `/users/{uid}`

Document ID **is** the Firebase Auth UID, so a user can only ever touch their own row.

```json
{
  "eventId": "abc123",
  "role": "organizer | staff | attendee",
  "qualifications": ["CPR Certified", "AED Trained"],
  "qualifiedResponder": true,
  "fcmToken": "device-token",
  "joinedAt": "<serverTimestamp>"
}
```

### `/emergencies/{emergencyId}`

```json
{
  "eventId": "abc123",
  "reporterUid": "uid",
  "reporterRole": "attendee",
  "location": "Main Lobby",
  "description": "Attendee collapsed near the stage",
  "consciousness": "Unconscious",
  "breathing": "Not Breathing",
  "status": "Active | Responder Assigned | Resolved",
  "assignedResponderId": null,
  "createdAt": "<serverTimestamp>"
}
```

### `/helpRequests/{helpRequestId}`

```json
{
  "eventId": "abc123",
  "reporterUid": "uid",
  "location": "Ballroom",
  "description": "Need a wheelchair at the entrance",
  "status": "Open | In Progress | Resolved",
  "createdAt": "<serverTimestamp>"
}
```

Index note: Firestore will print a console link the first time you run a query that
needs a composite index (`eventId` + `createdAt`). Click it, wait a minute, done.

---

## 4. Event-code lookup

The join screen currently calls `findRoleForCode(code)`, which compares strings in
memory. In Firestore it becomes two queries:

```java
db.collection("events")
  .whereEqualTo("staffCode", code.toUpperCase())
  .whereEqualTo("active", true)
  .limit(1)
  .get()
  .addOnSuccessListener(snap -> {
      if (!snap.isEmpty()) { joinAs(snap.getDocuments().get(0), "staff"); return; }
      // not a staff code — try attendee
      db.collection("events")
        .whereEqualTo("attendeeCode", code.toUpperCase())
        .whereEqualTo("active", true)
        .limit(1)
        .get()
        .addOnSuccessListener(s2 -> {
            if (s2.isEmpty()) showInvalidCodeError();
            else joinAs(s2.getDocuments().get(0), "attendee");
        });
  });
```

Because the query filters `active == true`, ending an event kills its codes for free —
same behaviour you already tested locally.

Codes must be unique across live events. `CodeGenerator` gives you 32^6 ≈ 1 billion
combinations, so collisions are rare, but the honest fix is: after generating, query
for the code, and regenerate if a live event already owns it.

---

## 5. Anonymous auth

Sign in once, as early as possible (in `MainActivity.onCreate`), before any read:

```java
FirebaseAuth auth = FirebaseAuth.getInstance();
if (auth.getCurrentUser() == null) {
    auth.signInAnonymously().addOnFailureListener(e ->
        Toast.makeText(this, "Cannot reach FirstLink servers", Toast.LENGTH_LONG).show());
}
```

The anonymous UID survives app restarts and is what makes `/users/{uid}` rules work.
It is lost on uninstall or "clear data" — acceptable for an event app.

---

## 6. Alerts: who gets what

FCM **topics** are the least-work option. No server-side token bookkeeping.

Two topics per event:

| Topic | Who subscribes | Gets |
|---|---|---|
| `event_{eventId}_responders` | anyone with `qualifiedResponder == true` (staff **and** attendees) | emergency alerts |
| `event_{eventId}_staff` | anyone with role `organizer` or `staff` | help requests |

Subscribe right after qualifications are saved — this replaces the body of
`saveQualifications`:

```java
if (qualifiedResponder) {
    FirebaseMessaging.getInstance().subscribeToTopic("event_" + eventId + "_responders");
}
if (user.isStaffLevel()) {
    FirebaseMessaging.getInstance().subscribeToTopic("event_" + eventId + "_staff");
}
```

Unsubscribe with `unsubscribeFromTopic(...)` when the event ends.

A client **cannot** push to a topic — that needs the Admin SDK, which is why you need
Cloud Functions. Two triggers:

```js
// functions/index.js
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

exports.onEmergency = onDocumentCreated("emergencies/{id}", async (event) => {
  const e = event.data.data();
  await admin.messaging().send({
    topic: `event_${e.eventId}_responders`,
    notification: {
      title: "EMERGENCY — responder needed",
      body: `${e.location}: ${e.consciousness} • ${e.breathing}`,
    },
    data: {emergencyId: event.params.id},
    android: {priority: "high"},
  });
});

exports.onHelpRequest = onDocumentCreated("helpRequests/{id}", async (event) => {
  const h = event.data.data();
  await admin.messaging().send({
    topic: `event_${h.eventId}_staff`,
    notification: {title: "New help request", body: h.description},
    data: {helpRequestId: event.params.id},
    android: {priority: "high"},
  });
});
```

Deploy with `firebase init functions` then `firebase deploy --only functions`.
(Cloud Functions requires the Blaze plan. The free tier allowance is generous —
this workload costs approximately nothing — but a card must be on file.)

On the device, add a service to receive them:

```java
public class FirstLinkMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String emergencyId = message.getData().get("emergencyId");
        if (emergencyId != null) {
            // Same helper you already have — only the trigger changes.
            NotificationHelper.showEmergencyAlert(this, emergencyId,
                    message.getData().get("location"));
        } else {
            NotificationHelper.showHelpNotification(this,
                    message.getNotification().getBody());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // Store on /users/{uid}.fcmToken if you later want per-device sends.
    }
}
```

Manifest:

```xml
<service
    android:name=".FirstLinkMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

`NotificationHelper` stays exactly as it is. Only what *calls* it changes: today
`AttendeeEmergencyActivity` calls it directly on the reporting phone; after this,
FCM calls it on every responder's phone.

---

## 7. Security rules

Test mode leaves your database world-writable. Replace it before real use:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() { return request.auth != null; }
    function myUser()   { return get(/databases/$(database)/documents/users/$(request.auth.uid)).data; }

    match /events/{eventId} {
      // Anyone signed in may look up an event (needed for code lookup).
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.organizerUid == request.auth.uid;
      // Only the organizer can edit or end it.
      allow update: if signedIn() && resource.data.organizerUid == request.auth.uid;
      allow delete: if false;
    }

    match /users/{uid} {
      allow read: if signedIn();
      allow write: if signedIn() && request.auth.uid == uid;
    }

    match /emergencies/{id} {
      allow read:   if signedIn() && myUser().eventId == resource.data.eventId;
      allow create: if signedIn() && myUser().eventId == request.resource.data.eventId;
      // Responders may only claim; they can't rewrite the report.
      allow update: if signedIn() && myUser().eventId == resource.data.eventId
                    && request.resource.data.diff(resource.data).affectedKeys()
                         .hasOnly(['status', 'assignedResponderId']);
    }

    match /helpRequests/{id} {
      allow read:   if signedIn() && myUser().eventId == resource.data.eventId;
      allow create: if signedIn() && myUser().eventId == request.resource.data.eventId;
      allow update: if signedIn() && myUser().eventId == resource.data.eventId
                    && request.resource.data.diff(resource.data).affectedKeys()
                         .hasOnly(['status']);
    }
  }
}
```

Known hole worth naming: `events` is readable by any signed-in user, which is what
makes code lookup work from the client. Someone who enumerated event documents could
read the codes. The tighter version moves lookup into a Cloud Function
(`redeemCode(code)`) that returns only the role, and blocks direct client reads of
`/events` entirely. Do that if this ever handles real events.

---

## 8. Ending an event

Locally, `endEvent()` wipes the phone. With a backend it must reach everyone:

1. Organizer sets `active = false` on the event document.
2. Every client keeps a snapshot listener on its own event:

   ```java
   db.collection("events").document(eventId)
     .addSnapshotListener((doc, err) -> {
         if (doc != null && Boolean.FALSE.equals(doc.getBoolean("active"))) {
             FirebaseMessaging.getInstance()
                 .unsubscribeFromTopic("event_" + eventId + "_responders");
             FirebaseMessaging.getInstance()
                 .unsubscribeFromTopic("event_" + eventId + "_staff");
             FirebaseAuth.getInstance().signOut();
             startActivity(new Intent(this, MainActivity.class)
                 .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
         }
     });
   ```

   Put this in a small base activity that every screen extends, so it fires no matter
   where the user is standing.
3. Codes die automatically, because every lookup query filters `active == true`.
4. Optionally a scheduled Cloud Function deletes ended events' emergencies and help
   requests after N days.

---

## 9. Exactly which methods to replace

Every one of these lives in
`app/src/main/java/com/example/firstlink/data/FirstLinkRepository.java` and is marked
with a `// FIREBASE:` comment in the source. No activity or layout needs to change —
they only ever call this class.

| Method | Becomes |
|---|---|
| `createEvent(...)` | `db.collection("events").add(...)` |
| `getEvent()` | cached document from a snapshot listener |
| `saveEventCodes(...)` | `update("attendeeCode", ..., "staffCode", ...)` |
| `addLocations(...)` | `update("locations", FieldValue.arrayUnion(...))` |
| `getLocations()` | read `locations` off the cached event doc |
| `findRoleForCode(...)` | the two queries in section 4 |
| `endEvent()` | `update("active", false)` + section 8 |
| `createCurrentUser(...)` | `signInAnonymously()` then `users/{uid}.set(...)` |
| `getCurrentUser()` | cached `/users/{uid}` document |
| `saveQualifications(...)` | `users/{uid}.update(...)` + topic subscribe (section 6) |
| `addEmergency(...)` | `db.collection("emergencies").add(...)` |
| `getOpenEmergencies()` | snapshot listener, `whereEqualTo("eventId", ...)` + `whereNotEqualTo("status", "Resolved")` |
| `getEmergencyById(...)` | `emergencies/{id}.get()` |
| `acceptEmergency(...)` | `runTransaction` — assign only if still `Active` |
| `resolveEmergency(...)` | `emergencies/{id}.update("status", "Resolved")` |
| `addHelpRequest(...)` | `db.collection("helpRequests").add(...)` |
| `getOpenHelpRequests()` | snapshot listener, `whereEqualTo("eventId", ...)` + `whereNotEqualTo("status", "Resolved")` |
| `resolveHelpRequest(...)` | `helpRequests/{id}.update("status", "Resolved")` |

The one behavioural difference to plan for: these all become **asynchronous**. Local
methods return a value immediately; Firestore hands you a `Task`. Either give each
repository method a small callback interface, or expose `LiveData` the activities
observe. `acceptEmergency` in particular must become a transaction — otherwise two
responders tapping Accept at the same moment will both believe they own the call.

---

## 10. Suggested order of work

1. Gradle + `google-services.json` + anonymous auth. Build. Nothing else changes yet.
2. Move `Event` and `User` to Firestore. Creation and join flows now work across phones.
3. Move `EmergencyRequest` and `HelpRequest`, with snapshot listeners on the staff lists.
4. Add FCM topics + the two Cloud Functions. **Now a second phone actually rings.**
5. Add the `active == false` listener and end-event sign-out.
6. Paste in the security rules and re-test every screen as a non-organizer.

Test step 4 with two devices — or one physical phone and one emulator — because a
single device cannot tell you whether the push actually left the building.
