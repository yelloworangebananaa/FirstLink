package com.example.firstlink.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.firstlink.model.Contact;
import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.model.Event;
import com.example.firstlink.model.HelpRequest;
import com.example.firstlink.model.Report;
import com.example.firstlink.model.User;
import com.example.firstlink.util.CodeGenerator;
import com.example.firstlink.util.NotificationHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared data layer backed by Supabase (Postgres + Realtime, schema in supabase/schema.sql).
 *
 * Reads stay synchronous — realtime keeps an in-memory cache fresh and screens read
 * the cache, so activities did not have to become callback-driven. On any change to
 * a table the whole table is re-fetched for the event and the cached list replaced,
 * so AlertPolicy sees full snapshots exactly as it did with Firestore listeners.
 * The one genuinely asynchronous operation is joining by code, which has to hit
 * the server before it knows whether the code is real.
 *
 * The current user's session (who you signed in as, what you selected) lives on the
 * device; their users row is published so row level security lets them read the event.
 */
public class FirstLinkRepository {

    private static final String TAG = "FirstLink";
    private static final String PREFS_NAME = "firstlink_prefs";
    private static final String KEY_EVENT_ID = "event_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_NAME = "name";
    private static final String KEY_QUALIFIED = "qualified_responder";
    private static final String KEY_CONTACT_FOR = "contact_for";

    private static final String T_EVENTS = "events";
    private static final String T_EMERGENCIES = "emergencies";
    private static final String T_HELP = "help_requests";
    private static final String T_USERS = "users";
    private static final String T_CONTACTS = "contacts";

    private static FirstLinkRepository instance;

    private final SharedPreferences prefs;

    private Event event;
    private User currentUser;
    private final List<EmergencyRequest> emergencies = new ArrayList<>();
    private final List<HelpRequest> helpRequests = new ArrayList<>();
    private final List<User> participants = new ArrayList<>();
    private final List<Contact> contacts = new ArrayList<>();

    /** The realtime subscription, and which event it is for (null when not listening). */
    private Object listener;
    private String listeningTo;

    /**
     * Writes of ours to a table still in flight. A refetch that lands meanwhile may predate
     * them and would wipe the optimistic local copy, so it is dropped; the table is
     * fetched again once they all land.
     */
    private final Map<String, Integer> pending = new HashMap<>();
    /** Newest refetch sent and applied per table, so a slow old response can't overwrite a newer one. */
    private final Map<String, Integer> sent = new HashMap<>();
    private final Map<String, Integer> applied = new HashMap<>();

    private Runnable onChange;

    /**
     * Context used only to raise notifications from the realtime listeners.
     * Application context, so it cannot leak an activity.
     */
    private final Context appContext;

    /** Decides which snapshot changes this phone should be notified about. */
    private final AlertPolicy alerts = new AlertPolicy();

    /** Result of looking a join code up on the server. */
    public interface JoinCallback {
        void onJoined(String role);

        void onInvalidCode();

        void onError(String message);
    }

    /** Sign-in result: error is null on success. */
    public interface SignInCallback {
        void onDone(@Nullable String error);
    }

    private FirstLinkRepository(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String role = prefs.getString(KEY_ROLE, null);
        String eventId = prefs.getString(KEY_EVENT_ID, null);
        if (eventId != null && !isUuid(eventId)) {
            // A session saved by the Firebase build: that id means nothing to Supabase.
            clearSession();
        } else if (role != null && eventId != null) {
            currentUser = new User(eventId, role);
            currentUser.setName(prefs.getString(KEY_NAME, null));
            // Must be restored, or a qualified responder silently stops being
            // eligible for emergency alerts the first time the app restarts.
            currentUser.setQualifiedResponder(prefs.getBoolean(KEY_QUALIFIED, false));
            currentUser.setContactFor(prefs.getString(KEY_CONTACT_FOR, null));
            attachListeners(eventId);
        }
    }

    private static boolean isUuid(String s) {
        return s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    public static synchronized FirstLinkRepository get(Context context) {
        if (instance == null) {
            instance = new FirstLinkRepository(context);
        }
        return instance;
    }

    /** Anonymous sign-in. Every backend call also waits on this itself. */
    public void ensureSignedIn(SignInCallback cb) {
        Backend.signIn((uid, error) -> cb.onDone(error));
    }

    /** Screens that display live lists register here so server updates repaint them. */
    public void setOnChangeListener(@Nullable Runnable listener) {
        this.onChange = listener;
    }

    private void notifyChanged() {
        if (onChange != null) onChange.run();
    }

    // ------------------------------------------------------------- listeners

    private void attachListeners(final String eventId) {
        detachListeners();
        alerts.reset();
        listeningTo = eventId;
        listener = Backend.listen(eventId, (table, error) -> {
            if (error != null) Log.e(TAG, "realtime: " + error);
            else if (eventId.equals(listeningTo)) refetch(table);
        });
    }

    /** Replaces one table's cached rows with the server's, then repaints. */
    private void refetch(final String table) {
        final String eventId = listeningTo;
        if (eventId == null) return;
        final int seq = count(sent, table, 1);
        Backend.select(table, T_EVENTS.equals(table) ? "id" : "event_id", eventId, (json, error) -> {
            if (error != null) {
                Log.e(TAG, table + " fetch: " + error);
                return;
            }
            if (!eventId.equals(listeningTo) || count(pending, table, 0) > 0
                    || seq < count(applied, table, 0)) return;
            applied.put(table, seq);
            try {
                apply(table, new JSONArray(json));
            } catch (JSONException e) {
                Log.e(TAG, table + " parse: " + e.getMessage());
                return;
            }
            notifyChanged();
        });
    }

    private void apply(String table, JSONArray rows) throws JSONException {
        switch (table) {
            case T_EVENTS:
                if (rows.length() > 0) event = toEvent(rows.getJSONObject(0));
                break;
            case T_EMERGENCIES:
                emergencies.clear();
                for (int i = 0; i < rows.length(); i++) emergencies.add(toEmergency(rows.getJSONObject(i)));
                sortByTimestamp(emergencies, null);
                raise(alerts.onEmergencies(emergencies, me()));
                break;
            case T_HELP:
                helpRequests.clear();
                for (int i = 0; i < rows.length(); i++) helpRequests.add(toHelp(rows.getJSONObject(i)));
                sortByTimestamp(null, helpRequests);
                raise(alerts.onHelpRequests(helpRequests, me(), this::contactPersonFor));
                break;
            case T_CONTACTS:
                contacts.clear();
                for (int i = 0; i < rows.length(); i++) contacts.add(toContact(rows.getJSONObject(i)));
                break;
            case T_USERS:
                participants.clear();
                for (int i = 0; i < rows.length(); i++) participants.add(toUser(rows.getJSONObject(i)));
                break;
        }
    }

    /** Adds delta to a per-table counter and returns the new value. */
    private static int count(Map<String, Integer> counter, String table, int delta) {
        Integer now = counter.get(table);
        int next = (now == null ? 0 : now) + delta;
        counter.put(table, next);
        return next;
    }

    /**
     * Sends one write. The optimistic local change is already applied; a failure is
     * logged, and the table is re-fetched once our writes to it land, which also
     * reverts a write the server refused.
     */
    private Backend.Callback<String> sent(final String table, final String what) {
        count(pending, table, 1);
        return (value, error) -> {
            if (error != null) Log.e(TAG, what + ": " + error);
            if (count(pending, table, -1) == 0) refetch(table);
        };
    }

    /**
     * Turns the policy's decisions into notifications. The listeners only run while
     * the app process is alive — a force-stopped app cannot be woken this way, which
     * is what push messages from a server function would add.
     */
    private void raise(List<AlertPolicy.Alert> toRaise) {
        for (AlertPolicy.Alert a : toRaise) {
            switch (a.kind) {
                case EMERGENCY:
                    NotificationHelper.showEmergencyAlert(appContext, a.id, a.text);
                    break;
                case HELP:
                    NotificationHelper.showHelpNotification(appContext, a.title, a.text);
                    break;
                case ACCEPTED:
                    NotificationHelper.showAcceptedNotification(appContext, a.text, a.title);
                    break;
            }
        }
    }

    private AlertPolicy.Me me() {
        return AlertPolicy.Me.of(myUid(), currentUser);
    }

    /** This device's anonymous auth uid, or "" before sign-in completes. */
    public String myUid() {
        return Backend.uid();
    }

    /** True when this device is one of the people who accepted a report. */
    public boolean acceptedByMe(Report report) {
        return report.hasAccepted(myUid());
    }

    private void detachListeners() {
        Backend.stop(listener);
        listener = null;
        listeningTo = null;
    }

    /** Newest first. */
    private void sortByTimestamp(List<EmergencyRequest> es, List<HelpRequest> hs) {
        if (es != null) {
            Collections.sort(es, new Comparator<EmergencyRequest>() {
                @Override
                public int compare(EmergencyRequest a, EmergencyRequest b) {
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                }
            });
        }
        if (hs != null) {
            Collections.sort(hs, new Comparator<HelpRequest>() {
                @Override
                public int compare(HelpRequest a, HelpRequest b) {
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                }
            });
        }
    }

    // ---------------------------------------------------------------- event

    /**
     * The id is made here, not by the server, so screens can use the event at once.
     * Writes are queued in order: the event row lands before createCurrentUser /
     * saveProfile publish the organizer's users row that points at it.
     */
    public Event createEvent(String name, String venue, String organizer, boolean require911Approval) {
        final Event created = new Event(name, venue, organizer, require911Approval);
        created.setId(UUID.randomUUID().toString());

        event = created;
        emergencies.clear();
        helpRequests.clear();

        JSONObject row = new JSONObject();
        put(row, "id", created.getId());
        put(row, "name", name);
        put(row, "venue_name", venue);
        put(row, "organizer_name", organizer);
        put(row, "require_911_approval", require911Approval);
        Backend.insert(T_EVENTS, row, sent(T_EVENTS, "createEvent"));

        prefs.edit().putString(KEY_EVENT_ID, created.getId()).apply();
        attachListeners(created.getId());
        return created;
    }

    public Event getEvent() {
        return event;
    }

    public boolean hasActiveEvent() {
        return event != null && event.isActive();
    }

    public void saveEventCodes(String attendeeCode, String staffCode) {
        if (event == null) return;
        event.setAttendeeCode(attendeeCode);
        event.setStaffCode(staffCode);
        JSONObject patch = new JSONObject();
        put(patch, "attendee_code", attendeeCode);
        put(patch, "staff_code", staffCode);
        updateEvent(patch, "saveEventCodes");
    }

    /** The organizer is the only writer of the event, so the whole list is sent. */
    public void addLocations(List<String> locations) {
        if (event == null) return;
        event.getLocations().addAll(locations);
        JSONObject patch = new JSONObject();
        put(patch, "locations", new JSONArray(event.getLocations()));
        updateEvent(patch, "addLocations");
    }

    private void updateEvent(JSONObject patch, String what) {
        Backend.update(T_EVENTS, event.getId(), patch, sent(T_EVENTS, what));
    }

    /** Returns a copy — callers may decorate it (e.g. spinner placeholder rows). */
    public List<String> getLocations() {
        return event == null
                ? new ArrayList<String>()
                : new ArrayList<>(event.getLocations());
    }

    /**
     * Looks the code up on the server, so a code created on another device works.
     * A code only matches while its event is still active, which is what makes
     * ending an event invalidate both codes everywhere. redeem_code also covers the
     * Important People codes: a code the organizer handed to one named person. They
     * join as staff so they can see and accept requests, tagged with their problem
     * so their phone can single that problem's requests out.
     */
    public void joinWithCode(final String code, final JoinCallback callback) {
        JSONObject params = new JSONObject();
        put(params, "join_code", code); // redeem_code trims and upper-cases itself
        Backend.rpc("redeem_code", params, (json, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            JSONObject match;
            try {
                JSONArray rows = new JSONArray(json);
                if (rows.length() == 0) {
                    callback.onInvalidCode();
                    return;
                }
                match = rows.getJSONObject(0);
            } catch (JSONException e) {
                callback.onError(e.getMessage());
                return;
            }
            finishJoin(str(match, "event_id"), str(match, "role"), str(match, "contact_for"),
                    callback);
        });
    }

    /**
     * Row level security hides the event until this phone's users row says it joined,
     * so that row is written first, then the event is read.
     */
    private void finishJoin(final String eventId, final String role, @Nullable String contactFor,
                            final JoinCallback callback) {
        currentUser = new User(eventId, role);
        currentUser.setContactFor(contactFor);
        Backend.upsert(T_USERS, myRow(), (v, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            Backend.select(T_EVENTS, "id", eventId, (json, err) -> {
                Event joined = null;
                try {
                    JSONArray rows = err == null ? new JSONArray(json) : null;
                    if (rows != null && rows.length() > 0) joined = toEvent(rows.getJSONObject(0));
                } catch (JSONException e) {
                    err = e.getMessage();
                }
                if (joined == null) {
                    callback.onError(err == null ? "event not readable" : err);
                    return;
                }
                event = joined;
                prefs.edit()
                        .putString(KEY_EVENT_ID, eventId)
                        .putString(KEY_ROLE, role)
                        .putString(KEY_CONTACT_FOR, contactFor)
                        .apply();
                attachListeners(eventId);
                callback.onJoined(role);
            });
        });
    }

    /**
     * Flips active=false on the shared event, which every other device sees through
     * realtime — their codes stop working and their dashboards bail out.
     */
    public void endEvent(final Runnable onSuccess, final Runnable onFailure) {
        if (event == null) {
            clearSession();
            onSuccess.run();
            return;
        }
        JSONObject patch = new JSONObject();
        put(patch, "active", false);
        Backend.update(T_EVENTS, event.getId(), patch, (v, error) -> {
            if (error != null) {
                Log.e(TAG, "endEvent: " + error);
                onFailure.run();
                return;
            }
            clearSession();
            onSuccess.run();
        });
    }

    /** Drops this device's session without touching the shared event. */
    public void clearSession() {
        detachListeners();
        event = null;
        currentUser = null;
        emergencies.clear();
        helpRequests.clear();
        participants.clear();
        contacts.clear();
        alerts.reset();
        prefs.edit().remove(KEY_EVENT_ID).remove(KEY_ROLE).remove(KEY_NAME)
                .remove(KEY_QUALIFIED).remove(KEY_CONTACT_FOR).apply();
    }

    // ----------------------------------------------------------------- user

    public User createCurrentUser(String role) {
        String eventId = event == null ? "" : event.getId();
        currentUser = new User(eventId, role);
        String uid = myUid();
        if (!uid.isEmpty()) currentUser.setId(uid);
        prefs.edit().putString(KEY_ROLE, role).apply();
        return currentUser;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Publishes this person's name and qualifications to the event, so staff can
     * see who is available to respond and who accepted a call. The same row is
     * this phone's membership, so it is always written whole.
     */
    public void saveProfile(String name, List<String> qualifications, boolean qualifiedResponder) {
        if (currentUser == null) return;
        currentUser.setName(name);
        currentUser.setQualifications(qualifications);
        currentUser.setQualifiedResponder(qualifiedResponder);
        prefs.edit()
                .putString(KEY_NAME, name)
                .putBoolean(KEY_QUALIFIED, qualifiedResponder)
                .apply();
        Backend.upsert(T_USERS, myRow(), sent(T_USERS, "saveProfile"));
    }

    /** This phone's users row. The id is left to the server default (auth.uid()) when not known yet. */
    private JSONObject myRow() {
        String uid = myUid();
        if (!uid.isEmpty()) currentUser.setId(uid);
        JSONObject row = new JSONObject();
        if (!uid.isEmpty()) put(row, "id", uid);
        put(row, "event_id", currentUser.getEventId());
        put(row, "role", currentUser.getRole());
        put(row, "name", currentUser.getName());
        put(row, "qualifications", new JSONArray(currentUser.getQualifications()));
        put(row, "qualified_responder", currentUser.isQualifiedResponder());
        put(row, "contact_for", currentUser.getContactFor());
        return row;
    }

    /** Everyone who joined this event, as published by their own device. */
    public List<User> getParticipants() {
        return new ArrayList<>(participants);
    }

    /**
     * Who is eligible to receive emergency alerts for this event. This is who the
     * app alerts — not a delivery receipt, which would need a backend to confirm.
     */
    public List<User> getQualifiedResponders() {
        List<User> responders = new ArrayList<>();
        for (User u : participants) {
            if (u.isQualifiedResponder()) responders.add(u);
        }
        return responders;
    }

    private String currentUserName() {
        if (currentUser != null && currentUser.getName() != null
                && !currentUser.getName().trim().isEmpty()) {
            return currentUser.getName();
        }
        return prefs.getString(KEY_NAME, "Unnamed responder");
    }

    // ---------------------------------------------------------- emergencies

    /** Tells the caller whether an emergency actually reached the server. */
    public interface AddEmergencyCallback {
        void onAdded(EmergencyRequest request, boolean ok);
    }

    public EmergencyRequest addEmergency(String location, String description,
                                         String consciousness, String breathing, String photo,
                                         final AddEmergencyCallback cb) {
        String eventId = event == null ? "" : event.getId();
        String role = currentUser == null ? User.ROLE_ATTENDEE : currentUser.getRole();
        final EmergencyRequest request = new EmergencyRequest(
                eventId, role, location, description, consciousness, breathing, photo);

        String uid = myUid();
        if (!uid.isEmpty()) request.setReporterUid(uid);
        request.setReporterName(currentUserName());
        emergencies.add(0, request);

        JSONObject row = new JSONObject();
        put(row, "id", request.getId());
        put(row, "event_id", eventId);
        if (!uid.isEmpty()) put(row, "reporter_uid", uid);
        put(row, "reporter_role", role);
        put(row, "reporter_name", request.getReporterName());
        put(row, "location", location);
        put(row, "description", description);
        put(row, "consciousness", consciousness);
        put(row, "breathing", breathing);
        put(row, "photo", photo);
        final Backend.Callback<String> done = sent(T_EMERGENCIES, "addEmergency");
        Backend.insert(T_EMERGENCIES, row, (v, error) -> {
            if (error != null) {
                emergencies.remove(request);
                notifyChanged();
            }
            done.onResult(v, error);
            cb.onAdded(request, error == null);
        });
        return request;
    }

    /** Everything not yet resolved, newest first. */
    public List<EmergencyRequest> getOpenEmergencies() {
        return open(emergencies);
    }

    public EmergencyRequest getEmergencyById(String id) {
        return find(emergencies, id);
    }

    public void acceptEmergency(String emergencyId) {
        accept(T_EMERGENCIES, "accept_emergency", getEmergencyById(emergencyId),
                EmergencyRequest.STATUS_RESPONDER_ASSIGNED);
    }

    public void resolveEmergency(String emergencyId) {
        resolve(T_EMERGENCIES, emergencyId, getEmergencyById(emergencyId));
    }

    // ------------------------------------------------ shared report lifecycle

    private static <R extends Report> List<R> open(List<R> all) {
        List<R> open = new ArrayList<>();
        for (R r : all) {
            if (!r.resolved()) open.add(r);
        }
        return open;
    }

    @Nullable
    private static <R extends Report> R find(List<R> all, String id) {
        for (R r : all) {
            if (r.getId().equals(id)) return r;
        }
        return null;
    }

    /**
     * Additive: a second person accepting joins the first rather than replacing them,
     * and accepting twice is a no-op. The same rule for emergencies and help requests,
     * enforced atomically on the server by the accept_* functions.
     */
    private void accept(String table, String function, @Nullable Report report, final String inProgress) {
        if (report == null || acceptedByMe(report)) return;
        final String name = currentUserName();
        report.setStatus(inProgress);
        report.getAcceptedBy().add(name);
        report.getAcceptedUids().add(myUid());
        JSONObject params = new JSONObject();
        put(params, "report_id", report.getId());
        put(params, "who", name);
        Backend.rpcWrite(function, params, sent(table, "accept " + table));
    }

    private void resolve(String table, String id, @Nullable Report report) {
        if (report != null) report.setStatus(EmergencyRequest.STATUS_RESOLVED);
        JSONObject patch = new JSONObject();
        put(patch, "status", EmergencyRequest.STATUS_RESOLVED);
        Backend.update(table, id, patch, sent(table, "resolve " + table));
    }

    // ------------------------------------------------- 911 call approval

    /** Whether a reporter must wait for the organizer before being offered 911. */
    public boolean needsApprovalBeforeCalling() {
        return event != null && event.isRequire911Approval();
    }

    /** True when this device is the organizer, i.e. the one who may approve 911 calls. */
    public boolean isOrganizer() {
        return currentUser != null && User.ROLE_ORGANIZER.equals(currentUser.getRole());
    }

    /** Reporter asks the organizer for permission to dial 911. */
    public void requestCallApproval(String emergencyId) {
        updateApproval(emergencyId, EmergencyRequest.APPROVAL_PENDING);
    }

    /** Organizer's answer. Denied leaves the emergency active; it just blocks dialling. */
    public void setCallApproval(String emergencyId, boolean approved) {
        updateApproval(emergencyId, approved
                ? EmergencyRequest.APPROVAL_APPROVED
                : EmergencyRequest.APPROVAL_DENIED);
    }

    private void updateApproval(String emergencyId, final String value) {
        EmergencyRequest e = getEmergencyById(emergencyId);
        if (e != null) e.setCallApprovalStatus(value);
        JSONObject patch = new JSONObject();
        put(patch, "call_approval_status", value);
        Backend.update(T_EMERGENCIES, emergencyId, patch, sent(T_EMERGENCIES, "updateApproval"));
    }

    /** Emergencies waiting on the organizer right now. */
    public List<EmergencyRequest> getPendingApprovals() {
        List<EmergencyRequest> pending = new ArrayList<>();
        for (EmergencyRequest e : emergencies) {
            if (e.approval() == EmergencyRequest.Approval.PENDING) {
                pending.add(e);
            }
        }
        return pending;
    }

    /**
     * An emergency this device reported that the organizer has since approved for a
     * 911 call. Lets the reporter pick the call back up after navigating away.
     */
    @Nullable
    public EmergencyRequest getMyApprovedCall() {
        String uid = myUid();
        if (uid.isEmpty()) return null;
        for (EmergencyRequest e : emergencies) {
            if (uid.equals(e.getReporterUid())
                    && e.approval() == EmergencyRequest.Approval.APPROVED
                    && !e.resolved()) {
                return e;
            }
        }
        return null;
    }

    // -------------------------------------------------------- help requests

    public HelpRequest addHelpRequest(String location, String description,
                                      String problem, String photo) {
        String eventId = event == null ? "" : event.getId();
        final HelpRequest request =
                new HelpRequest(eventId, location, description, problem, photo);
        String uid = myUid();
        request.setRequesterUid(uid);
        helpRequests.add(0, request);

        JSONObject row = new JSONObject();
        put(row, "id", request.getId());
        put(row, "event_id", eventId);
        if (!uid.isEmpty()) put(row, "requester_uid", uid);
        put(row, "location", location);
        put(row, "description", description);
        put(row, "problem", problem);
        put(row, "photo", photo);
        Backend.insert(T_HELP, row, sent(T_HELP, "addHelpRequest"));
        return request;
    }

    /** Everything not yet resolved, newest first. */
    public List<HelpRequest> getOpenHelpRequests() {
        return open(helpRequests);
    }

    /** Additive, same as emergencies: several staff may work the same request. */
    public void acceptHelpRequest(String helpRequestId) {
        accept(T_HELP, "accept_help_request", find(helpRequests, helpRequestId),
                HelpRequest.STATUS_IN_PROGRESS);
    }

    public void resolveHelpRequest(String helpRequestId) {
        resolve(T_HELP, helpRequestId, find(helpRequests, helpRequestId));
    }

    // ------------------------------------------------------------- ReadyMap

    /**
     * Organizer planning data. Writes to the event only; never alerts anyone. Each edit
     * changes one entry on the server (set_equipment / set_post), so two quick taps
     * can't overwrite each other.
     */
    public void setAed(String location, boolean present) {
        setEquipment("aed", event == null ? null : event.getAedLocations(), location, present);
    }

    public void setFirstAid(String location, boolean present) {
        setEquipment("first_aid", event == null ? null : event.getFirstAidLocations(),
                location, present);
    }

    private void setEquipment(String kind, @Nullable List<String> local, String location,
                              boolean present) {
        if (event == null || local == null) return;
        local.remove(location);
        if (present) local.add(location);
        notifyChanged();
        JSONObject params = new JSONObject();
        put(params, "eid", event.getId());
        put(params, "kind", kind);
        put(params, "place", location);
        put(params, "present", present);
        Backend.rpcWrite("set_equipment", params, sent(T_EVENTS, "setEquipment"));
    }

    /** Posts a responder at a place, or clears their post when location is null. */
    public void postResponder(String uid, @Nullable String location) {
        if (event == null) return;
        if (location == null) event.getResponderPosts().remove(uid);
        else event.getResponderPosts().put(uid, location);
        notifyChanged();
        JSONObject params = new JSONObject();
        put(params, "eid", event.getId());
        put(params, "responder", uid);
        put(params, "place", location == null ? JSONObject.NULL : location);
        Backend.rpcWrite("set_post", params, sent(T_EVENTS, "postResponder"));
    }

    // ------------------------------------------------------- important people

    /** The organizer's problem -> person list, one join code each. */
    public List<Contact> getContacts() {
        return new ArrayList<>(contacts);
    }

    /** "Water problem" -> "Dana Reyes". Null when nobody is named for that problem. */
    @Nullable
    public String contactPersonFor(String problem) {
        if (problem == null || problem.isEmpty()) return null;
        for (Contact c : contacts) {
            if (problem.equals(c.getProblem())) return c.getPerson();
        }
        return null;
    }

    /** Tells the caller whether a contact actually reached the server. */
    public interface SaveContactCallback {
        void onSaved(Contact contact, boolean ok, @Nullable String error);
    }

    /**
     * The contact is shown immediately from the local cache, but its code only works
     * on other phones once the write lands — so failure is reported, never swallowed.
     * A silent failure here looks exactly like "the join code doesn't work".
     */
    public Contact saveContact(String problem, String person, final SaveContactCallback cb) {
        String eventId = event == null ? "" : event.getId();
        final Contact contact = new Contact(eventId, problem, person, CodeGenerator.generate());
        contact.setId(UUID.randomUUID().toString());
        contacts.add(contact);

        JSONObject row = new JSONObject();
        put(row, "id", contact.getId());
        put(row, "event_id", eventId);
        put(row, "problem", problem);
        put(row, "person", person);
        put(row, "code", contact.getCode());
        final Backend.Callback<String> done = sent(T_CONTACTS, "saveContact");
        Backend.insert(T_CONTACTS, row, (v, error) -> {
            if (error != null) {
                contacts.remove(contact);
                notifyChanged();
            }
            done.onResult(v, error);
            cb.onSaved(contact, error == null, error);
        });
        return contact;
    }

    /**
     * What the dashboards show the person who reported something: who picked it up.
     * Null when nothing of theirs is open, or nobody has accepted yet.
     */
    @Nullable
    public String getMyReportAcceptedSummary() {
        String uid = myUid();
        if (uid.isEmpty()) return null;
        String who = firstAcceptedOf(emergencies, uid);
        if (who != null) return who + " is responding to your emergency";
        who = firstAcceptedOf(helpRequests, uid);
        return who == null ? null : who + " is working on your request";
    }

    @Nullable
    private static String firstAcceptedOf(List<? extends Report> all, String uid) {
        for (Report r : all) {
            if (uid.equals(r.senderUid()) && !r.resolved() && !r.getAcceptedBy().isEmpty()) {
                return android.text.TextUtils.join(", ", r.getAcceptedBy());
            }
        }
        return null;
    }

    // ------------------------------------------------------- row <-> model

    /** org.json put without the checked exception; null becomes SQL null, not a dropped key. */
    private static void put(JSONObject o, String key, @Nullable Object value) {
        try {
            o.put(key, value == null ? JSONObject.NULL : value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(key, e);
        }
    }

    /** optString turns SQL null into the text "null"; this keeps it null. */
    @Nullable
    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key);
    }

    private static List<String> strings(JSONObject o, String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = o.optJSONArray(key);
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private static final Pattern TIMESTAMP =
            Pattern.compile("(\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d)(?:\\.(\\d+))?(.*)");

    /**
     * created_at ("2026-10-23T18:04:05.123456+00:00") to epoch millis. java.time needs
     * API 26, so the fraction is cut to millis and SimpleDateFormat does the rest.
     */
    private static long millis(@Nullable String ts) {
        if (ts == null) return 0;
        Matcher m = TIMESTAMP.matcher(ts.replace(' ', 'T'));
        if (!m.matches()) return 0;
        String frac = m.group(2) == null ? "000" : (m.group(2) + "00").substring(0, 3);
        String zone = m.group(3).isEmpty() || m.group(3).equals("Z") ? "+00:00" : m.group(3);
        if (zone.length() == 3) zone += ":00";
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                    .parse(m.group(1) + "." + frac + zone).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    private static Event toEvent(JSONObject o) {
        Event e = new Event();
        e.setId(str(o, "id"));
        e.setName(str(o, "name"));
        e.setVenueName(str(o, "venue_name"));
        e.setOrganizerName(str(o, "organizer_name"));
        e.setRequire911Approval(o.optBoolean("require_911_approval"));
        e.setAttendeeCode(str(o, "attendee_code"));
        e.setStaffCode(str(o, "staff_code"));
        e.setLocations(strings(o, "locations"));
        e.setActive(o.optBoolean("active"));
        e.setAedLocations(strings(o, "aed_locations"));
        e.setFirstAidLocations(strings(o, "first_aid_locations"));
        Map<String, String> posts = new HashMap<>();
        JSONObject p = o.optJSONObject("responder_posts");
        if (p != null) {
            for (Iterator<String> it = p.keys(); it.hasNext(); ) {
                String uid = it.next();
                posts.put(uid, p.optString(uid));
            }
        }
        e.setResponderPosts(posts);
        return e;
    }

    private static EmergencyRequest toEmergency(JSONObject o) {
        EmergencyRequest e = new EmergencyRequest();
        e.setId(str(o, "id"));
        e.setEventId(str(o, "event_id"));
        e.setReporterUid(str(o, "reporter_uid"));
        e.setReporterRole(str(o, "reporter_role"));
        e.setReporterName(str(o, "reporter_name"));
        e.setCallApprovalStatus(str(o, "call_approval_status"));
        e.setLocation(str(o, "location"));
        e.setDescription(str(o, "description"));
        e.setConsciousness(str(o, "consciousness"));
        e.setBreathing(str(o, "breathing"));
        e.setPhoto(str(o, "photo"));
        e.setStatus(str(o, "status"));
        e.setAcceptedBy(strings(o, "accepted_by"));
        e.setAcceptedUids(strings(o, "accepted_uids"));
        e.setTimestamp(millis(str(o, "created_at")));
        return e;
    }

    private static HelpRequest toHelp(JSONObject o) {
        HelpRequest h = new HelpRequest();
        h.setId(str(o, "id"));
        h.setEventId(str(o, "event_id"));
        h.setRequesterUid(str(o, "requester_uid"));
        h.setLocation(str(o, "location"));
        h.setDescription(str(o, "description"));
        h.setProblem(str(o, "problem"));
        h.setPhoto(str(o, "photo"));
        h.setStatus(str(o, "status"));
        h.setAcceptedBy(strings(o, "accepted_by"));
        h.setAcceptedUids(strings(o, "accepted_uids"));
        h.setTimestamp(millis(str(o, "created_at")));
        return h;
    }

    private static User toUser(JSONObject o) {
        User u = new User(str(o, "event_id"), str(o, "role"));
        u.setId(str(o, "id"));
        u.setName(str(o, "name"));
        u.setQualifications(strings(o, "qualifications"));
        u.setQualifiedResponder(o.optBoolean("qualified_responder"));
        u.setContactFor(str(o, "contact_for"));
        return u;
    }

    private static Contact toContact(JSONObject o) {
        Contact c = new Contact(str(o, "event_id"), str(o, "problem"), str(o, "person"),
                str(o, "code"));
        c.setId(str(o, "id"));
        return c;
    }
}
