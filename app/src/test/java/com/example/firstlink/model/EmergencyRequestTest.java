package com.example.firstlink.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EmergencyRequestTest {

    @Test
    public void approvalStateReadsTheWireValue() {
        EmergencyRequest e = new EmergencyRequest();
        assertEquals(EmergencyRequest.Approval.NOT_REQUESTED, e.approval());
        e.setCallApprovalStatus(EmergencyRequest.APPROVAL_PENDING);
        assertEquals(EmergencyRequest.Approval.PENDING, e.approval());
        e.setCallApprovalStatus(EmergencyRequest.APPROVAL_APPROVED);
        assertEquals(EmergencyRequest.Approval.APPROVED, e.approval());
        e.setCallApprovalStatus(EmergencyRequest.APPROVAL_DENIED);
        assertEquals(EmergencyRequest.Approval.DENIED, e.approval());
    }

    @Test
    public void patientStatusSkipsUnansweredParts() {
        EmergencyRequest e = new EmergencyRequest();
        assertEquals("Patient status not given", e.patientStatus());
        e.setBreathing("Breathing");
        assertEquals("Breathing", e.patientStatus());
        e.setConsciousness("Unconscious");
        assertEquals("Unconscious • Breathing", e.patientStatus());
    }

    @Test
    public void acceptRosterMatchesByUidNotName() {
        EmergencyRequest e = new EmergencyRequest();
        e.getAcceptedUids().add("uid-1");
        assertTrue(e.hasAccepted("uid-1"));
        assertFalse(e.hasAccepted("uid-2"));
        // Before sign-in there is no uid, and that must never match.
        assertFalse(e.hasAccepted(""));
    }
}
