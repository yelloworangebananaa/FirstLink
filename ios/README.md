# FirstLink for iOS

SwiftUI port of the Android app, backed by Supabase. Tables and columns are in
`supabase/schema.sql` — the snake_case versions of the Android app's fields — so the
two apps share events once both talk to the same Supabase project.

## Setup

Open `ios/FirstLink.xcodeproj`, wait for the `supabase-swift` package (2.x) to
resolve, set your signing team, run. There is no config file: the project URL and
anon key are in `Store.swift`. The anon key is public by design — the row level
security policies in `schema.sql` are what keep one event's data from everyone else.

The backend needs, once per project (already done for this one):

1. Authentication → Sign In / Providers → enable **Allow anonymous sign-ins**.
2. SQL editor → run all of `supabase/schema.sql` (tables, realtime publication,
   `redeem_code` / `accept_*` RPCs, RLS).

RLS means a phone sees an event's rows only once it organizes the event or has a
`users` row in it. That is why `join(code:)` writes the users row before fetching,
and `createEvent` writes the organizer's.

Wire-format check, no Xcode needed — decodes `contract/rows.json` (rows exactly as
PostgREST returns them) and runs the alert and ReadyMap rules:

    swiftc ios/FirstLink/Models.swift ios/Checks/main.swift -o /tmp/modelscheck
    /tmp/modelscheck contract/rows.json

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
| `ReadyMapView.swift` | `ReadyMapActivity`, `item_ready_spot`, `dialog_ready_spot` |

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
| Von Restorff | Red is spent on exactly one thing per screen — the emergency path; ReadyMap gaps are orange, never red |
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

Cross-device alerts still come from the open realtime channel, not push, exactly like
Android — so they arrive while the app is running or briefly backgrounded, not after
it has been terminated.
