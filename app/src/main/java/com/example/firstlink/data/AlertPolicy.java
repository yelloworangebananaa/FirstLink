package com.example.firstlink.data;

import androidx.annotation.Nullable;

import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.model.HelpRequest;
import com.example.firstlink.model.Report;
import com.example.firstlink.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Decides, from each snapshot of the event's reports, which notifications this phone
 * should raise. A phone notifies itself when someone ELSE reports something — which
 * is why cross-device alerts work without FCM — and the reporter hears back when
 * someone new accepts. No Firestore, Auth or Android here, so every rule is testable.
 */
public final class AlertPolicy {

    /** Who this phone belongs to, as far as alerting is concerned. */
    public static final class Me {
        final String uid;
        final boolean staffLevel;
        final boolean qualified;
        @Nullable final String contactFor;

        public Me(String uid, boolean staffLevel, boolean qualified, @Nullable String contactFor) {
            this.uid = uid;
            this.staffLevel = staffLevel;
            this.qualified = qualified;
            this.contactFor = contactFor;
        }

        static Me of(String uid, @Nullable User user) {
            return user == null
                    ? new Me(uid, false, false, null)
                    : new Me(uid, user.isStaffLevel(), user.isQualifiedResponder(), user.getContactFor());
        }
    }

    /** EMERGENCY: text is the location. ACCEPTED: title is the noun, text who accepted. */
    public static final class Alert {
        public enum Kind { EMERGENCY, HELP, ACCEPTED }

        public final Kind kind;
        public final String id;
        public final String title;
        public final String text;

        Alert(Kind kind, String id, String title, String text) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.text = text;
        }
    }

    /**
     * The first snapshot replays every existing document as new. Without priming the
     * app would fire a burst of notifications for old reports on every launch.
     */
    private boolean emergenciesPrimed;
    private boolean helpPrimed;

    /**
     * How many people had accepted each report last time we looked, so a snapshot
     * that changed something else (a resolve, an approval) doesn't re-notify. A
     * report we have never counted is a new one.
     */
    private final Map<String, Integer> acceptCounts = new HashMap<>();

    public void reset() {
        emergenciesPrimed = false;
        helpPrimed = false;
        acceptCounts.clear();
    }

    public List<Alert> onEmergencies(List<EmergencyRequest> all, Me me) {
        List<Alert> alerts = diff(all, emergenciesPrimed, me, "emergency", e -> {
            // Emergency alerts are for qualified responders and staff.
            if (!me.qualified && !me.staffLevel) return null;
            return new Alert(Alert.Kind.EMERGENCY, e.getId(), null, e.getLocation());
        });
        emergenciesPrimed = true;
        return alerts;
    }

    public List<Alert> onHelpRequests(List<HelpRequest> all, Me me,
                                      Function<String, String> contactPersonFor) {
        List<Alert> alerts = diff(all, helpPrimed, me, "request", h -> {
            String problem = h.getProblem();
            boolean hasProblem = problem != null && !problem.isEmpty();
            // The standing contact for this problem is alerted alongside the staff, and
            // told it is theirs. They joined with their own code, so nothing else
            // identifies them.
            boolean mine = hasProblem && problem.equals(me.contactFor);
            if (!mine && !me.staffLevel) return null;
            String text = hasProblem ? problem + " — " + h.getDescription() : h.getDescription();
            // Staff see who handles this right on the notification.
            if (!mine) {
                String contact = contactPersonFor.apply(problem);
                if (contact != null && !contact.isEmpty()) text += "\nContact: " + contact;
            }
            return new Alert(Alert.Kind.HELP, h.getId(),
                    mine ? "You are the contact for this" : "New help request", text);
        });
        helpPrimed = true;
        return alerts;
    }

    private <R extends Report> List<Alert> diff(List<R> all, boolean primed, Me me, String noun,
                                                Function<R, Alert> onNewFromSomeoneElse) {
        List<Alert> alerts = new ArrayList<>();
        for (R r : all) {
            Integer known = acceptCounts.get(r.getId());
            int now = r.getAcceptedBy().size();
            boolean mine = me.uid != null && !me.uid.isEmpty() && me.uid.equals(r.senderUid());
            if (primed && known == null && !mine) {
                Alert a = onNewFromSomeoneElse.apply(r);
                if (a != null) alerts.add(a);
            } else if (primed && known != null && mine && now > known) {
                alerts.add(new Alert(Alert.Kind.ACCEPTED, r.getId(), noun,
                        r.getAcceptedBy().get(now - 1)));
            }
            acceptCounts.put(r.getId(), now);
        }
        return alerts;
    }
}
