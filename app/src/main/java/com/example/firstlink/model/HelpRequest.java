package com.example.firstlink.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HelpRequest {
    public static final String STATUS_OPEN = "Open";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_RESOLVED = "Resolved";

    private String id;
    private String eventId;
    private String location;
    private String description;
    /** Preset problem the organizer defined, e.g. "Water problem". Empty when free-text only. */
    private String problem;
    /** Base64 JPEG thumbnail from the camera, or null. */
    private String photo;
    /** Firebase Auth uid of whoever sent it, so their device can recognise its own request. */
    private String requesterUid;
    /** Display names of every staff member who accepted. More than one may accept. */
    private List<String> acceptedBy;
    /** Same people, by uid — used to decide who may see the contact for this problem. */
    private List<String> acceptedUids;
    private long timestamp;
    private String status;

    /** Required by Firestore's object mapper. */
    public HelpRequest() {
    }

    public HelpRequest(String eventId, String location, String description,
                       String problem, String photo) {
        this.id = UUID.randomUUID().toString();
        this.eventId = eventId;
        this.location = location;
        this.description = description;
        this.problem = problem;
        this.photo = photo;
        this.timestamp = System.currentTimeMillis();
        this.status = STATUS_OPEN;
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

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getRequesterUid() {
        return requesterUid;
    }

    public void setRequesterUid(String requesterUid) {
        this.requesterUid = requesterUid;
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
}
