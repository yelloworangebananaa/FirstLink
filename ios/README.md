# FirstLink for iOS

SwiftUI port of the Android app. Same Firestore schema and field names, so an
event created on Android joins from iOS and vice versa.

## Setup

Open `ios/FirstLink.xcodeproj`, wait for the Firebase package to resolve, set your
signing team, run. It works with no `GoogleService-Info.plist`: `configureFirebase()`
in `FirstLinkApp.swift` falls back to a code-built `FirebaseOptions` using the project
values Android already has. Auth and Firestore only need `apiKey` + `projectID`.

That fallback is a stopgap. To finish the setup properly:

1. Firebase console → project `firstlink-93faf` → add an **iOS app** with bundle id
   `com.example.firstlink`.
2. Download `GoogleService-Info.plist` and drop it into `ios/FirstLink/`. The target
   uses a synchronized folder, so it is picked up with no Xcode wiring, and the
   fallback branch stops running on its own.

Until then, Installations / Analytics / Crashlytics / FCM will not work against the
unregistered app id — none of which this app uses.

Anonymous auth and Firestore must both be enabled in the console — same requirements
as the Android app (see `FIREBASE_SETUP.md`), and already done for this project.

## Layout

| File | Android equivalent |
|---|---|
| `Models.swift` | `model/*.java`, `util/CodeGenerator.java` |
| `Store.swift` | `data/FirstLinkRepository.java` |
| `Theme.swift` | `res/values/colors.xml`, `themes.xml`, `values-night/` |
| `Notifications.swift` | `util/NotificationHelper.java`, `util/Dialer.java` |
| `FirstLinkApp.swift` | `MainActivity`, `BaseActivity`, `AndroidManifest` |
| `Onboarding.swift` | Creator / AddPlaces / RoomCreation / Join / Qualifications |
| `Dashboards.swift` | `StaffDashboardActivity`, `AttendeeMainDashboardActivity` |
| `Reporting.swift` | `AttendeeEmergencyActivity`, `AttendeeHelpActivity` |
| `StaffLists.swift` | `StaffView*Activity`, both adapters, `EmergencyResponderAlertActivity` |

## Design

Requires **iOS 26+** — Liquid Glass (`glassEffect`, `.buttonStyle(.glass)` /
`.glassProminent`) is iOS 26 API. `Theme.swift` is now ~130 lines because the design
system is mostly Apple's: system colours, `Form`/`List`, `ContentUnavailableView`,
`safeAreaBar`, swipe actions, `sensoryFeedback`.

Decisions and the [Laws of UX](https://lawsofux.com/) behind them:

| Law | Where |
|---|---|
| Jakob's Law | Native `Form`/`List` everywhere instead of hand-rolled cards |
| Fitts's Law | Primary actions pinned bottom in `safeAreaBar`; emergency button is huge |
| Von Restorff | Red is spent on exactly one thing per screen — the emergency path |
| Hick's Law | Attendee dashboard offers two actions, nothing else |
| Chunking / Miller | Staff dashboard's five actions became two labelled groups plus End Event |
| Goal-Gradient | 3-step progress bar through event setup |
| Zeigarnik | Unresolved counts on the staff queues; approved-911 banner follows you home |
| Doherty Threshold | `sensoryFeedback` haptics + optimistic local writes |
| Peak-End Rule | The post-report alert states what is already in motion |
| Postel's Law | Join codes accept any case and stray spacing; places split on newline or comma |
| Tesler's Law | The separate "add a place" screen is gone — it is one row in the list it feeds |

## Deliberately not ported

- **`GlassBlur`** — it exists because Android has no `backdrop-filter`. iOS alerts already
  float over a blurred, dimmed page.
- **`BaseActivity` back button, activity transitions** — `NavigationStack` gives both.
- **The adapters and `setOnChangeListener`/`refresh()` plumbing** — `@Published` state
  repaints the lists directly.
- **Dead colour tokens** (`gradient_*`, `*_glow`, `on_gradient_*`) — the Android theme kept
  them only so old layouts kept resolving. Nothing here refers to them.

Cross-device alerts still come from the open Firestore listener, not FCM, exactly like
Android — so they arrive while the app is running or briefly backgrounded, not after
it has been terminated.
