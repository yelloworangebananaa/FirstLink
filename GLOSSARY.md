# FirstLink glossary and data contract

The Android app (`FirstLinkRepository`) and the iOS app (`Store`) share one Supabase
project, so they must agree on every column name and value below. The database itself
is defined in `supabase/schema.sql`. Change that file, this file, both models, and
`contract/rows.json` together. Run the iOS side of the check with:

```
swiftc ios/FirstLink/Models.swift ios/Checks/main.swift -o /tmp/modelscheck
/tmp/modelscheck contract/rows.json
```

## Terms

- **Event**: one gathering. It has an attendee code and a staff code. Ending it sets `active=false`, which makes every code stop working.
- **Report**: an emergency or a help request. Someone sends it, and any number of people can take it on.
- **Accept**: adding yourself to a report's accept roster. It is additive: a second person joins the first and does not replace them. Accepting twice does nothing.
- **Accept roster**: `accepted_by` holds display names and `accepted_uids` holds the same people by uid. "Did I accept this?" is always checked by uid.
- **Qualified responder**: anyone who ticked a qualification. They get emergency alerts.
- **Contact**: an Important People entry that maps one problem to one person (e.g. "Water problem" → "Dana Reyes"). Each contact has its own join code. Joining with it makes you staff with `contact_for` set to that problem.
- **Approval**: the optional organizer sign-off before a reporter is offered 911. It goes Not Requested → Pending → Approved or Denied.
- **ReadyMap**: the organizer's planning view. For each location it shows the responders posted there plus whether there is an AED and a first-aid kit. **Covered** means a responder and an AED, **Partial** means one of the two, **Gap** means neither. It is a planning estimate: it knows where people were posted, not where they are.
- **Post**: the place an organizer assigns a responder to on ReadyMap.
- **Drill**: a practice incident at one location. It is computed only on the organizer's phone: nothing is written to the database and nobody is alerted.
- **Alert policy**: the rules for whether this phone notifies itself when the event's data changes. It is `AlertPolicy` on both platforms.

## Tables

| Table | Columns |
|---|---|
| `events` | `id`, `organizer_uid`, `name`, `venue_name`, `organizer_name`, `require_911_approval`, `attendee_code`, `staff_code`, `locations[]`, `active`, `aed_locations[]`, `first_aid_locations[]`, `responder_posts{uid: location}` |
| `users` (id = auth uid) | `id`, `event_id`, `role`, `name`, `qualifications[]`, `qualified_responder`, `contact_for` |
| `emergencies` | `id`, `event_id`, `reporter_role`, `reporter_uid`, `reporter_name`, `call_approval_status`, `location`, `description`, `consciousness`, `breathing`, `created_at`, `status`, `accepted_by[]`, `accepted_uids[]`, `photo` |
| `help_requests` | `id`, `event_id`, `location`, `description`, `problem`, `photo`, `requester_uid`, `accepted_by[]`, `accepted_uids[]`, `created_at`, `status` |
| `contacts` | `id`, `event_id`, `problem`, `person`, `code` |

Join codes are looked up with the `redeem_code(join_code)` function, and accepts go through
`accept_emergency` / `accept_help_request`, so they stay additive under concurrent taps.
Row-level security limits every table to the event's organizer and the people who joined it.

## Values

| Field | Values |
|---|---|
| `role` | `organizer`, `staff`, `attendee` |
| emergency `status` | `Active`, `Responder Assigned`, `Resolved` |
| help `status` | `Open`, `In Progress`, `Resolved` |
| `call_approval_status` | `Not Requested`, `Pending`, `Approved`, `Denied` |
