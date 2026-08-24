package com.example.firstlink.model;

import java.util.ArrayList;
import java.util.List;
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
    /** Display names of every responder who accepted. More than one may accept. */
    private List<String> acceptedBy;
    /** Same people, by uid — used to tell "did I accept this?" apart from a name clash. */
    private List<String> acceptedUids;
    private String reporterName;
    /** Base64 JPEG thumbnail from the camera, or null. */
    private String photo;

    /** Required by Firestore's object mapper. */
    public EmergencyRequest() {
    }

    public EmergencyRequest(String eventId, String reporterRole, String location,
                            String description, String consciousness, String breathing,
                            String photo) {
        this.id = UUID.randomUUID().toString();
        this.eventId = eventId;
        this.reporterRole = reporterRole;
        this.location = location;
        this.description = description;
        this.consciousness = consciousness;
        this.breathing = breathing;
        this.photo = photo;
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

    public List<String> getAcceptedBy() {
        if (acceptedBy == null) acceptedBy = new ArrayList<>();
        return acceptedBy;
    }

    public void setAcceptedBy(List<String> acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public List<String> getAcceptedUids() {
        if (acceptedUids == null) acceptedUids = new ArrayList<>();
        return acceptedUids;
    }

    public void setAcceptedUids(List<String> acceptedUids) {
        this.acceptedUids = acceptedUids;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }
}
