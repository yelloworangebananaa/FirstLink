package com.example.firstlink.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Event {
    private String id;
    private String name;
    private String venueName;
    private String organizerName;
    private boolean require911Approval;
    private String attendeeCode;
    private String staffCode;
    private List<String> locations;
    private boolean active;
    /** ReadyMap: places with an AED, places with a first-aid kit. Organizer-only planning data. */
    private List<String> aedLocations;
    private List<String> firstAidLocations;
    /** ReadyMap: responder uid -> the place the organizer posted them. */
    private Map<String, String> responderPosts;

    /** Empty shell the repository fills from a database row. */
    public Event() {
        this.locations = new ArrayList<>();
    }

    public Event(String name, String venueName, String organizerName, boolean require911Approval) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.venueName = venueName;
        this.organizerName = organizerName;
        this.require911Approval = require911Approval;
        this.locations = new ArrayList<>();
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public boolean isRequire911Approval() {
        return require911Approval;
    }

    public void setRequire911Approval(boolean require911Approval) {
        this.require911Approval = require911Approval;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public String getAttendeeCode() {
        return attendeeCode;
    }

    public void setAttendeeCode(String attendeeCode) {
        this.attendeeCode = attendeeCode;
    }

    public String getStaffCode() {
        return staffCode;
    }

    public void setStaffCode(String staffCode) {
        this.staffCode = staffCode;
    }

    public List<String> getLocations() {
        if (locations == null) {
            locations = new ArrayList<>();
        }
        return locations;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getAedLocations() {
        if (aedLocations == null) aedLocations = new ArrayList<>();
        return aedLocations;
    }

    public void setAedLocations(List<String> aedLocations) {
        this.aedLocations = aedLocations;
    }

    public List<String> getFirstAidLocations() {
        if (firstAidLocations == null) firstAidLocations = new ArrayList<>();
        return firstAidLocations;
    }

    public void setFirstAidLocations(List<String> firstAidLocations) {
        this.firstAidLocations = firstAidLocations;
    }

    public Map<String, String> getResponderPosts() {
        if (responderPosts == null) responderPosts = new HashMap<>();
        return responderPosts;
    }

    public void setResponderPosts(Map<String, String> responderPosts) {
        this.responderPosts = responderPosts;
    }
}
