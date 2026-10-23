package com.example.firstlink.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.firstlink.data.AlertPolicy.Alert;
import com.example.firstlink.data.AlertPolicy.Me;
import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.model.HelpRequest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Every rule that decides whether this phone buzzes, without the backend or Android. */
public class AlertPolicyTest {

    private static final Me RESPONDER = new Me("me", false, true, null);
    private static final Me ATTENDEE = new Me("me", false, false, null);
    private static final Me STAFF = new Me("me", true, false, null);

    private static EmergencyRequest emergency(String id, String reporterUid, String... accepted) {
        EmergencyRequest e = new EmergencyRequest("ev", "attendee", "Main Hall", "", "", "", null);
        e.setId(id);
        e.setReporterUid(reporterUid);
        e.getAcceptedBy().addAll(Arrays.asList(accepted));
        return e;
    }

    private static HelpRequest help(String id, String requesterUid, String problem) {
        HelpRequest h = new HelpRequest("ev", "", "Leak", problem, null);
        h.setId(id);
        h.setRequesterUid(requesterUid);
        return h;
    }

    private static final java.util.function.Function<String, String> NO_CONTACTS = p -> null;

    @Test
    public void firstSnapshotReplaysHistoryWithoutAlerting() {
        AlertPolicy policy = new AlertPolicy();
        assertTrue(policy.onEmergencies(Arrays.asList(emergency("a", "x")), RESPONDER).isEmpty());
    }

    @Test
    public void newEmergencyAlertsRespondersAndStaffOnly() {
        for (Me me : Arrays.asList(RESPONDER, STAFF)) {
            AlertPolicy policy = new AlertPolicy();
            policy.onEmergencies(Collections.<EmergencyRequest>emptyList(), me);
            List<Alert> alerts = policy.onEmergencies(Arrays.asList(emergency("a", "x")), me);
            assertEquals(1, alerts.size());
            assertEquals(Alert.Kind.EMERGENCY, alerts.get(0).kind);
            assertEquals("Main Hall", alerts.get(0).text);
        }
        AlertPolicy policy = new AlertPolicy();
        policy.onEmergencies(Collections.<EmergencyRequest>emptyList(), ATTENDEE);
        assertTrue(policy.onEmergencies(Arrays.asList(emergency("a", "x")), ATTENDEE).isEmpty());
    }

    @Test
    public void ownReportNeverAlertsItsReporter() {
        AlertPolicy policy = new AlertPolicy();
        policy.onEmergencies(Collections.<EmergencyRequest>emptyList(), STAFF);
        assertTrue(policy.onEmergencies(Arrays.asList(emergency("a", "me")), STAFF).isEmpty());
    }

    @Test
    public void reporterHearsOnlyWhenTheAcceptListGrows() {
        AlertPolicy policy = new AlertPolicy();
        policy.onEmergencies(Arrays.asList(emergency("a", "me")), ATTENDEE);
        // Some other field changed — a resolve, a 911 approval.
        assertTrue(policy.onEmergencies(Arrays.asList(emergency("a", "me")), ATTENDEE).isEmpty());

        List<Alert> alerts = policy.onEmergencies(Arrays.asList(emergency("a", "me", "Dana")), ATTENDEE);
        assertEquals(1, alerts.size());
        assertEquals(Alert.Kind.ACCEPTED, alerts.get(0).kind);
        assertEquals("Dana", alerts.get(0).text);

        // A second responder joining the first.
        assertEquals(1, policy.onEmergencies(
                Arrays.asList(emergency("a", "me", "Dana", "Sam")), ATTENDEE).size());
        // Someone else's report being accepted is not my news.
        AlertPolicy other = new AlertPolicy();
        other.onEmergencies(Arrays.asList(emergency("b", "x")), ATTENDEE);
        assertTrue(other.onEmergencies(Arrays.asList(emergency("b", "x", "Dana")), ATTENDEE).isEmpty());
    }

    @Test
    public void helpGoesToStaffWithTheContactNamed() {
        AlertPolicy policy = new AlertPolicy();
        policy.onHelpRequests(Collections.<HelpRequest>emptyList(), STAFF, NO_CONTACTS);
        List<Alert> alerts = policy.onHelpRequests(Arrays.asList(help("h", "x", "Water problem")),
                STAFF, p -> "Dana Reyes");
        assertEquals(1, alerts.size());
        assertEquals("New help request", alerts.get(0).title);
        assertEquals("Water problem — Leak\nContact: Dana Reyes", alerts.get(0).text);
    }

    @Test
    public void helpReachesTheNamedContactEvenIfNotStaff() {
        Me contact = new Me("me", false, false, "Water problem");
        AlertPolicy policy = new AlertPolicy();
        policy.onHelpRequests(Collections.<HelpRequest>emptyList(), contact, NO_CONTACTS);
        List<Alert> alerts = policy.onHelpRequests(
                Arrays.asList(help("h", "x", "Water problem")), contact, NO_CONTACTS);
        assertEquals("You are the contact for this", alerts.get(0).title);

        AlertPolicy attendee = new AlertPolicy();
        attendee.onHelpRequests(Collections.<HelpRequest>emptyList(), ATTENDEE, NO_CONTACTS);
        assertTrue(attendee.onHelpRequests(
                Arrays.asList(help("h", "x", "Water problem")), ATTENDEE, NO_CONTACTS).isEmpty());
    }

    @Test
    public void resetForgetsTheLastEvent() {
        AlertPolicy policy = new AlertPolicy();
        policy.onEmergencies(Collections.<EmergencyRequest>emptyList(), STAFF);
        policy.reset();
        assertTrue(policy.onEmergencies(Arrays.asList(emergency("a", "x")), STAFF).isEmpty());
    }
}
