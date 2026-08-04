package com.example.firstlink.model;

import java.util.UUID;

public class HelpRequest {
    public static final String STATUS_OPEN = "Open";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_RESOLVED = "Resolved";

    private String id;
    private String eventId;
    private String location;
    private String description;
    private long timestamp;
    private String status;

    /** Required by Firestore's object mapper. */
    public HelpRequest() {
    }

    public HelpRequest(String eventId, String location, String description) {
        this.id = UUID.randomUUID().toString();
        this.eventId = eventId;
        this.location = location;
        this.description = description;
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
