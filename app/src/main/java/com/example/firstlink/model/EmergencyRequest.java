package com.example.firstlink.model;

import java.util.UUID;

public class EmergencyRequest {
    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_RESPONDER_ASSIGNED = "Responder Assigned";
    public static final String STATUS_RESOLVED = "Resolved";

    /** Only used when the event requires organizer approval before calling 911. */
    public static final String APPROVAL_NOT_REQUESTED = "Not Requested";
    public static final String APPROVAL_PENDING = "Pending";
    public static final String APPROVAL_APPROVED = "Approved";
    public static final String APPROVAL_DENIED = "Denied";

    private String id;
    private String eventId;
    private String reporterRole;
    /** Firebase Auth uid, so the reporting device can recognise its own reports. */
    private String reporterUid;
    private String callApprovalStatus;
    private String location;
    private String description;
    private String consciousness;
    private String breathing;
    private long timestamp;
    private String status;
    private String assignedResponderId;
    private String assignedResponderName;
    private String reporterName;

    /** Required by Firestore's object mapper. */
    public EmergencyRequest() {
    }

    public EmergencyRequest(String eventId, String reporterRole, String location,
                            String description, String consciousness, String breathing) {
        this.id = UUID.randomUUID().toString();
        this.eventId = eventId;
        this.reporterRole = reporterRole;
        this.location = location;
        this.description = description;
        this.consciousness = consciousness;
        this.breathing = breathing;
        this.timestamp = System.currentTimeMillis();
        this.status = STATUS_ACTIVE;
        this.callApprovalStatus = APPROVAL_NOT_REQUESTED;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getReporterRole() {
        return reporterRole;
    }

    public void setReporterRole(String reporterRole) {
        this.reporterRole = reporterRole;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConsciousness() {
        return consciousness;
    }

    public void setConsciousness(String consciousness) {
        this.consciousness = consciousness;
    }

    public String getBreathing() {
        return breathing;
    }

    public void setBreathing(String breathing) {
        this.breathing = breathing;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getReporterUid() {
        return reporterUid;
    }

    public void setReporterUid(String reporterUid) {
        this.reporterUid = reporterUid;
    }

    public String getCallApprovalStatus() {
        return callApprovalStatus == null ? APPROVAL_NOT_REQUESTED : callApprovalStatus;
    }

    public void setCallApprovalStatus(String callApprovalStatus) {
        this.callApprovalStatus = callApprovalStatus;
    }

    public String getAssignedResponderName() {
        return assignedResponderName;
    }

    public void setAssignedResponderName(String assignedResponderName) {
        this.assignedResponderName = assignedResponderName;
    }

    public String getAssignedResponderId() {
        return assignedResponderId;
    }

    public void setAssignedResponderId(String assignedResponderId) {
        this.assignedResponderId = assignedResponderId;
    }
}
