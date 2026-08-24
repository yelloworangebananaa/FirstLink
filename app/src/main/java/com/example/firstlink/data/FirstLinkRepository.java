package com.example.firstlink.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.firstlink.model.Contact;
import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.model.Event;
import com.example.firstlink.model.HelpRequest;
import com.example.firstlink.model.User;
import com.example.firstlink.util.CodeGenerator;
import com.example.firstlink.util.NotificationHelper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared data layer backed by Cloud Firestore.
 *
 * Reads stay synchronous — snapshot listeners keep an in-memory cache fresh and
 * screens read the cache, so activities did not have to become callback-driven.
 * The one genuinely asynchronous operation is joining by code, which has to hit
 * the server before it knows whether the code is real.
 *
 * The current user is deliberately NOT in Firestore: it describes this device's
 * session (who you signed in as, what you selected), not shared event state.
 */
public class FirstLinkRepository {

    private static final String TAG = "FirstLink";
    private static final String PREFS_NAME = "firstlink_prefs";
    private static final String KEY_EVENT_ID = "event_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_NAME = "name";
    private static final String KEY_QUALIFIED = "qualified_responder";
    private static final String KEY_CONTACT_FOR = "contact_for";

    private static final String COL_EVENTS = "events";
    private static final String COL_EMERGENCIES = "emergencies";
    private static final String COL_HELP = "helpRequests";
    private static final String COL_USERS = "users";
    private static final String COL_CONTACTS = "contacts";

    private static FirstLinkRepository instance;

    private final SharedPreferences prefs;
    private final FirebaseFirestore db;

    private Event event;
    private User currentUser;
    private final List<EmergencyRequest> emergencies = new ArrayList<>();
    private final List<HelpRequest> helpRequests = new ArrayList<>();
    private final List<User> participants = new ArrayList<>();
    private final List<Contact> contacts = new ArrayList<>();

    private ListenerRegistration eventListener;
    private ListenerRegistration emergencyListener;
    private ListenerRegistration helpListener;
    private ListenerRegistration participantListener;
    private ListenerRegistration contactListener;

    private Runnable onChange;

    /**
     * Context used only to raise notifications from the Firestore listeners.
     * Application context, so it cannot leak an activity.
     */
    private final Context appContext;

    /**
     * The first snapshot replays every existing document as "added". Without this
     * the app would fire a burst of notifications for old reports on every launch.
     */
    private boolean emergencySnapshotPrimed;
    private boolean helpSnapshotPrimed;

    /**
     * How many people had accepted each report last time we looked, so a snapshot
     * that changed something else (a resolve, an approval) doesn't re-notify.
     */
    private final Map<String, Integer> acceptCounts = new LinkedHashMap<>();

    /** Result of looking a join code up on the server. */
    public interface JoinCallback {
        void onJoined(String role);

        void onInvalidCode();

        void onError(String message);
    }

    private FirstLinkRepository(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();

        String role = prefs.getString(KEY_ROLE, null);
        String eventId = prefs.getString(KEY_EVENT_ID, null);
        if (role != null && eventId != null) {
            currentUser = new User(eventId, role);
            currentUser.setName(prefs.getString(KEY_NAME, null));
            // Must be restored, or a qualified responder silently stops being
            // eligible for emergency alerts the first time the app restarts.
            currentUser.setQualifiedResponder(prefs.getBoolean(KEY_QUALIFIED, false));
            currentUser.setContactFor(prefs.getString(KEY_CONTACT_FOR, null));
            attachListeners(eventId);
        }
    }

    public static synchronized FirstLinkRepository get(Context context) {
        if (instance == null) {
            instance = new FirstLinkRepository(context);
        }
        return instance;
    }

    /** Anonymous sign-in. Every Firestore call waits on this. */
    public Task<Void> ensureSignedIn() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            return Tasks.forResult(null);
        }
        return auth.signInAnonymously().continueWith(task -> {
            if (!task.isSuccessful()) {
                throw new RuntimeException(task.getException());
            }
            return null;
        });
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
        emergencySnapshotPrimed = false;
        helpSnapshotPrimed = false;

        eventListener = db.collection(COL_EVENTS).document(eventId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;
                    event = toEvent(snapshot);
                    notifyChanged();
                });

        emergencyListener = db.collection(COL_EMERGENCIES)
                .whereEqualTo("eventId", eventId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        if (error != null) Log.e(TAG, "emergency listener: " + error.getMessage());
                        return;
                    }
                    emergencies.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        EmergencyRequest e = doc.toObject(EmergencyRequest.class);
                        e.setId(doc.getId());
                        emergencies.add(e);
                    }
                    sortByTimestamp(emergencies, null);

                    if (emergencySnapshotPrimed) {
                        for (DocumentChange change : snapshots.getDocumentChanges()) {
                            EmergencyRequest e =
                                    change.getDocument().toObject(EmergencyRequest.class);
                            e.setId(change.getDocument().getId());
                            if (change.getType() == DocumentChange.Type.ADDED) {
                                maybeAlertForEmergency(e);
                            } else if (change.getType() == DocumentChange.Type.MODIFIED) {
                                maybeAlertReporterOfAcceptance(e.getReporterUid(),
                                        acceptCounts.get(e.getId()), e.getAcceptedBy(),
                                        "emergency");
                            }
                            acceptCounts.put(e.getId(), e.getAcceptedBy().size());
                        }
                    } else {
                        for (EmergencyRequest e : emergencies) {
                            acceptCounts.put(e.getId(), e.getAcceptedBy().size());
                        }
                    }
                    emergencySnapshotPrimed = true;
                    notifyChanged();
                });

        helpListener = db.collection(COL_HELP)
                .whereEqualTo("eventId", eventId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        if (error != null) Log.e(TAG, "help listener: " + error.getMessage());
                        return;
                    }
                    helpRequests.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        HelpRequest h = doc.toObject(HelpRequest.class);
                        h.setId(doc.getId());
                        helpRequests.add(h);
                    }
                    sortByTimestamp(null, helpRequests);

                    if (helpSnapshotPrimed) {
                        for (DocumentChange change : snapshots.getDocumentChanges()) {
                            HelpRequest h = change.getDocument().toObject(HelpRequest.class);
                            h.setId(change.getDocument().getId());
                            if (change.getType() == DocumentChange.Type.ADDED) {
                                maybeAlertForHelp(h);
                            } else if (change.getType() == DocumentChange.Type.MODIFIED) {
                                maybeAlertReporterOfAcceptance(h.getRequesterUid(),
                                        acceptCounts.get(h.getId()), h.getAcceptedBy(),
                                        "request");
                            }
                            acceptCounts.put(h.getId(), h.getAcceptedBy().size());
                        }
                    } else {
                        for (HelpRequest h : helpRequests) {
                            acceptCounts.put(h.getId(), h.getAcceptedBy().size());
                        }
                    }
                    helpSnapshotPrimed = true;
                    notifyChanged();
                });

        contactListener = db.collection(COL_CONTACTS)
                .whereEqualTo("eventId", eventId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        if (error != null) Log.e(TAG, "contact listener: " + error.getMessage());
                        return;
                    }
                    contacts.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Contact c = doc.toObject(Contact.class);
                        c.setId(doc.getId());
                        contacts.add(c);
                    }
                    notifyChanged();
                });

        participantListener = db.collection(COL_USERS)
                .whereEqualTo("eventId", eventId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        if (error != null) Log.e(TAG, "participant listener: " + error.getMessage());
                        return;
                    }
                    participants.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        User u = doc.toObject(User.class);
                        u.setId(doc.getId());
                        participants.add(u);
                    }
                    notifyChanged();
                });
    }

    /**
     * A phone raises its own notification when someone ELSE reports an emergency.
     *
     * This is why cross-device alerts work without Firebase Cloud Messaging: the
     * snapshot listener is already open, so the device hears about the new document
     * and notifies itself. The limit is that listeners only run while the app process
     * is alive — a force-stopped app cannot be woken this way, which is what FCM
     * plus a Cloud Function would add (see FIREBASE_SETUP.md section 6).
     */
    private void maybeAlertForEmergency(EmergencyRequest e) {
        if (isOwnReport(e.getReporterUid())) return;
        if (currentUser == null) return;
        // Emergency alerts are for qualified responders and staff.
        if (!currentUser.isQualifiedResponder() && !currentUser.isStaffLevel()) return;
        NotificationHelper.showEmergencyAlert(appContext, e.getId(), e.getLocation());
    }

    private void maybeAlertForHelp(HelpRequest h) {
        if (isOwnReport(h.getRequesterUid()) || currentUser == null) return;
        String problem = h.getProblem();
        // The standing contact for this problem is alerted alongside the staff, and told
        // it is theirs. They joined with their own code, so nothing else identifies them.
        boolean mine = problem != null && !problem.isEmpty()
                && problem.equals(currentUser.getContactFor());
        if (!mine && !currentUser.isStaffLevel()) return;
        String text = problem == null || problem.isEmpty()
                ? h.getDescription() : problem + " — " + h.getDescription();
        NotificationHelper.showHelpNotification(appContext,
                mine ? "You are the contact for this" : "New help request", text);
    }

    /**
     * The other half of the loop: whoever reported this hears back, on their own
     * device, the moment a staff member accepts it.
     */
    private void maybeAlertReporterOfAcceptance(String reporterUid, Integer knownAccepts,
                                                List<String> acceptedBy, String noun) {
        if (!isOwnReport(reporterUid)) return;
        if (!someoneNewAccepted(knownAccepts, acceptedBy.size())) return;
        NotificationHelper.showAcceptedNotification(
                appContext, acceptedBy.get(acceptedBy.size() - 1), noun);
    }

    /** This device's Firebase Auth uid, or "" before sign-in completes. */
    public String myUid() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        return auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
    }

    /** True when this device is one of the people who accepted a report. */
    public boolean acceptedByMe(List<String> acceptedUids) {
        String uid = myUid();
        return !uid.isEmpty() && acceptedUids.contains(uid);
    }

    /**
     * A snapshot fires for any change to the document — a resolve, a 911 approval. Only a
     * longer accept list means someone new picked the report up. Null = never seen before,
     * which for a document that already has accepts is still news to this device.
     */
    static boolean someoneNewAccepted(Integer knownAccepts, int nowAccepted) {
        return nowAccepted > (knownAccepts == null ? 0 : knownAccepts);
    }

    private boolean isOwnReport(String reporterUid) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        return auth.getCurrentUser() != null
                && auth.getCurrentUser().getUid().equals(reporterUid);
    }

    private void detachListeners() {
        if (eventListener != null) eventListener.remove();
        if (emergencyListener != null) emergencyListener.remove();
        if (helpListener != null) helpListener.remove();
        if (participantListener != null) participantListener.remove();
        if (contactListener != null) contactListener.remove();
        eventListener = null;
        emergencyListener = null;
        helpListener = null;
        participantListener = null;
        contactListener = null;
    }

    /** Newest first. Firestore can't order without an index unless we ask for one. */
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

    private Event toEvent(DocumentSnapshot doc) {
        Event e = doc.toObject(Event.class);
        if (e != null) e.setId(doc.getId());
        return e;
    }

    // ---------------------------------------------------------------- event

    public Event createEvent(String name, String venue, String organizer, boolean require911Approval) {
        final DocumentReference ref = db.collection(COL_EVENTS).document();
        final Event created = new Event(name, venue, organizer, require911Approval);
        created.setId(ref.getId());

        event = created;
        emergencies.clear();
        helpRequests.clear();

        ensureSignedIn().addOnSuccessListener(v -> ref.set(created)
                .addOnFailureListener(e -> Log.e(TAG, "createEvent: " + e.getMessage())));

        prefs.edit().putString(KEY_EVENT_ID, ref.getId()).apply();
        attachListeners(ref.getId());
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
        final String id = event.getId();
        ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_EVENTS).document(id)
                .update("attendeeCode", attendeeCode, "staffCode", staffCode)
                .addOnFailureListener(e -> Log.e(TAG, "saveEventCodes: " + e.getMessage())));
    }

    public void addLocations(List<String> locations) {
        if (event == null) return;
        event.getLocations().addAll(locations);
        final String id = event.getId();
        final Object[] items = locations.toArray();
        ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_EVENTS).document(id)
                .update("locations", FieldValue.arrayUnion(items))
                .addOnFailureListener(e -> Log.e(TAG, "addLocations: " + e.getMessage())));
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
     * ending an event invalidate both codes everywhere.
     */
    public void joinWithCode(final String code, final JoinCallback callback) {
        final String normalized = code.trim().toUpperCase();
        ensureSignedIn()
                .addOnSuccessListener(v -> db.collection(COL_EVENTS)
                        .whereEqualTo("staffCode", normalized)
                        .whereEqualTo("active", true)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(staffSnap -> {
                            if (!staffSnap.isEmpty()) {
                                finishJoin(staffSnap.getDocuments().get(0), User.ROLE_STAFF,
                                        null, callback);
                                return;
                            }
                            db.collection(COL_EVENTS)
                                    .whereEqualTo("attendeeCode", normalized)
                                    .whereEqualTo("active", true)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(attendeeSnap -> {
                                        if (!attendeeSnap.isEmpty()) {
                                            finishJoin(attendeeSnap.getDocuments().get(0),
                                                    User.ROLE_ATTENDEE, null, callback);
                                            return;
                                        }
                                        joinAsContact(normalized, callback);
                                    })
                                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
                        })
                        .addOnFailureListener(e -> callback.onError(e.getMessage())))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * A code the organizer handed to one named person on the Important People screen.
     * They join as staff so they can see and accept requests, tagged with their problem
     * so their phone can single that problem's requests out.
     */
    private void joinAsContact(String code, final JoinCallback callback) {
        db.collection(COL_CONTACTS)
                .whereEqualTo("code", code)
                .limit(1)
                .get()
                .addOnSuccessListener(contactSnap -> {
                    if (contactSnap.isEmpty()) {
                        callback.onInvalidCode();
                        return;
                    }
                    Contact contact = contactSnap.getDocuments().get(0).toObject(Contact.class);
                    if (contact == null) {
                        callback.onInvalidCode();
                        return;
                    }
                    db.collection(COL_EVENTS).document(contact.getEventId()).get()
                            .addOnSuccessListener(eventDoc -> {
                                Event e = eventDoc.exists() ? toEvent(eventDoc) : null;
                                if (e == null || !e.isActive()) {
                                    callback.onInvalidCode();
                                    return;
                                }
                                finishJoin(eventDoc, User.ROLE_STAFF, contact.getProblem(),
                                        callback);
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private void finishJoin(DocumentSnapshot doc, String role, @Nullable String contactFor,
                            JoinCallback callback) {
        event = toEvent(doc);
        currentUser = new User(doc.getId(), role);
        currentUser.setContactFor(contactFor);
        prefs.edit()
                .putString(KEY_EVENT_ID, doc.getId())
                .putString(KEY_ROLE, role)
                .putString(KEY_CONTACT_FOR, contactFor)
                .apply();
        attachListeners(doc.getId());
        callback.onJoined(role);
    }

    /**
     * Flips active=false on the shared event, which every other device sees through
     * its snapshot listener — their codes stop working and their dashboards bail out.
     */
    public void endEvent() {
        if (event != null) {
            final String id = event.getId();
            ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_EVENTS).document(id)
                    .update("active", false)
                    .addOnFailureListener(e -> Log.e(TAG, "endEvent: " + e.getMessage())));
        }
        clearSession();
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
        acceptCounts.clear();
        prefs.edit().remove(KEY_EVENT_ID).remove(KEY_ROLE).remove(KEY_NAME)
                .remove(KEY_QUALIFIED).remove(KEY_CONTACT_FOR).apply();
    }

    // ----------------------------------------------------------------- user

    public User createCurrentUser(String role) {
        String eventId = event == null ? "" : event.getId();
        currentUser = new User(eventId, role);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUser.setId(auth.getCurrentUser().getUid());
        }
        prefs.edit().putString(KEY_ROLE, role).apply();
        return currentUser;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Publishes this person's name and qualifications to the event, so staff can
     * see who is available to respond and who accepted a call.
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

        final User toSave = currentUser;
        ensureSignedIn().addOnSuccessListener(v -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) return;
            String uid = auth.getCurrentUser().getUid();
            toSave.setId(uid);
            db.collection(COL_USERS).document(uid).set(toSave)
                    .addOnFailureListener(e -> Log.e(TAG, "saveProfile: " + e.getMessage()));
        });
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

    public EmergencyRequest addEmergency(String location, String description,
                                         String consciousness, String breathing, String photo) {
        String eventId = event == null ? "" : event.getId();
        String role = currentUser == null ? User.ROLE_ATTENDEE : currentUser.getRole();
        final EmergencyRequest request = new EmergencyRequest(
                eventId, role, location, description, consciousness, breathing, photo);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            request.setReporterUid(auth.getCurrentUser().getUid());
        }
        request.setReporterName(currentUserName());

        final DocumentReference ref = db.collection(COL_EMERGENCIES).document();
        request.setId(ref.getId());
        emergencies.add(0, request);

        ensureSignedIn().addOnSuccessListener(v -> ref.set(request)
                .addOnFailureListener(e -> Log.e(TAG, "addEmergency: " + e.getMessage())));
        return request;
    }

    /** Everything not yet resolved, newest first. */
    public List<EmergencyRequest> getOpenEmergencies() {
        List<EmergencyRequest> open = new ArrayList<>();
        for (EmergencyRequest e : emergencies) {
            if (!EmergencyRequest.STATUS_RESOLVED.equals(e.getStatus())) {
                open.add(e);
            }
        }
        return open;
    }

    public EmergencyRequest getEmergencyById(String id) {
        for (EmergencyRequest e : emergencies) {
            if (e.getId().equals(id)) return e;
        }
        return null;
    }

    /** Additive: a second responder accepting joins the first rather than replacing them. */
    public void acceptEmergency(String emergencyId) {
        EmergencyRequest e = getEmergencyById(emergencyId);
        if (e == null || acceptedByMe(e.getAcceptedUids())) return;
        final String responderId = myUid();
        final String responderName = currentUserName();
        e.setStatus(EmergencyRequest.STATUS_RESPONDER_ASSIGNED);
        e.getAcceptedBy().add(responderName);
        e.getAcceptedUids().add(responderId);
        ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_EMERGENCIES).document(emergencyId)
                .update("status", EmergencyRequest.STATUS_RESPONDER_ASSIGNED,
                        "acceptedBy", FieldValue.arrayUnion(responderName),
                        "acceptedUids", FieldValue.arrayUnion(responderId))
                .addOnFailureListener(err -> Log.e(TAG, "acceptEmergency: " + err.getMessage())));
    }

    public void resolveEmergency(String emergencyId) {
        EmergencyRequest e = getEmergencyById(emergencyId);
        if (e != null) e.setStatus(EmergencyRequest.STATUS_RESOLVED);
        ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_EMERGENCIES).document(emergencyId)
                .update("status", EmergencyRequest.STATUS_RESOLVED)
                .addOnFailureListener(err -> Log.e(TAG, "resolveEmergency: " + err.getMessage())));
    }

    // ------------------------------------------------- 911 call approval

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
        ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_EMERGENCIES).document(emergencyId)
                .update("callApprovalStatus", value)
                .addOnFailureListener(err -> Log.e(TAG, "updateApproval: " + err.getMessage())));
    }

    /** Emergencies waiting on the organizer right now. */
    public List<EmergencyRequest> getPendingApprovals() {
        List<EmergencyRequest> pending = new ArrayList<>();
        for (EmergencyRequest e : emergencies) {
            if (EmergencyRequest.APPROVAL_PENDING.equals(e.getCallApprovalStatus())) {
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
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return null;
        String uid = auth.getCurrentUser().getUid();
        for (EmergencyRequest e : emergencies) {
            if (uid.equals(e.getReporterUid())
                    && EmergencyRequest.APPROVAL_APPROVED.equals(e.getCallApprovalStatus())
                    && !EmergencyRequest.STATUS_RESOLVED.equals(e.getStatus())) {
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
        request.setRequesterUid(myUid());

        final DocumentReference ref = db.collection(COL_HELP).document();
        request.setId(ref.getId());
        helpRequests.add(0, request);

        ensureSignedIn().addOnSuccessListener(v -> ref.set(request)
                .addOnFailureListener(e -> Log.e(TAG, "addHelpRequest: " + e.getMessage())));
        return request;
    }

    /** Everything not yet resolved, newest first. */
    public List<HelpRequest> getOpenHelpRequests() {
        List<HelpRequest> open = new ArrayList<>();
        for (HelpRequest h : helpRequests) {
            if (!HelpRequest.STATUS_RESOLVED.equals(h.getStatus())) {
                open.add(h);
            }
        }
        return open;
    }

    /** Additive, same as emergencies: several staff may work the same request. */
    public void acceptHelpRequest(String helpRequestId) {
        HelpRequest target = null;
        for (HelpRequest h : helpRequests) {
            if (h.getId().equals(helpRequestId)) {
                target = h;
                break;
            }
        }
        if (target == null || acceptedByMe(target.getAcceptedUids())) return;
        final String staffId = myUid();
        final String staffName = currentUserName();
        target.setStatus(HelpRequest.STATUS_IN_PROGRESS);
        target.getAcceptedBy().add(staffName);
        target.getAcceptedUids().add(staffId);
        ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_HELP).document(helpRequestId)
                .update("status", HelpRequest.STATUS_IN_PROGRESS,
                        "acceptedBy", FieldValue.arrayUnion(staffName),
                        "acceptedUids", FieldValue.arrayUnion(staffId))
                .addOnFailureListener(err -> Log.e(TAG, "acceptHelpRequest: " + err.getMessage())));
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

    /** Returns the new contact so the organizer can read its code straight back. */
    public Contact saveContact(String problem, String person) {
        String eventId = event == null ? "" : event.getId();
        final Contact contact = new Contact(eventId, problem, person, CodeGenerator.generate());
        final DocumentReference ref = db.collection(COL_CONTACTS).document();
        contact.setId(ref.getId());
        contacts.add(contact);
        ensureSignedIn().addOnSuccessListener(v -> ref.set(contact)
                .addOnFailureListener(e -> Log.e(TAG, "saveContact: " + e.getMessage())));
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
        for (EmergencyRequest e : emergencies) {
            if (uid.equals(e.getReporterUid())
                    && !EmergencyRequest.STATUS_RESOLVED.equals(e.getStatus())
                    && !e.getAcceptedBy().isEmpty()) {
                return android.text.TextUtils.join(", ", e.getAcceptedBy())
                        + " is responding to your emergency";
            }
        }
        for (HelpRequest h : helpRequests) {
            if (uid.equals(h.getRequesterUid())
                    && !HelpRequest.STATUS_RESOLVED.equals(h.getStatus())
                    && !h.getAcceptedBy().isEmpty()) {
                return android.text.TextUtils.join(", ", h.getAcceptedBy())
                        + " is working on your request";
            }
        }
        return null;
    }

    public void resolveHelpRequest(String helpRequestId) {
        for (HelpRequest h : helpRequests) {
            if (h.getId().equals(helpRequestId)) {
                h.setStatus(HelpRequest.STATUS_RESOLVED);
                break;
            }
        }
        ensureSignedIn().addOnSuccessListener(v -> db.collection(COL_HELP).document(helpRequestId)
                .update("status", HelpRequest.STATUS_RESOLVED)
                .addOnFailureListener(err -> Log.e(TAG, "resolveHelpRequest: " + err.getMessage())));
    }
}
