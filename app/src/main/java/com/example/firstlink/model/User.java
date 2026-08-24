package com.example.firstlink.model;

import java.util.ArrayList;
import java.util.List;

public class User {
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_STAFF = "staff";
    public static final String ROLE_ATTENDEE = "attendee";

    /** Firebase Auth uid — stable per install, and the id of this user's document. */
    private String id;
    private String eventId;
    private String role;
    private String name;
    private List<String> qualifications;
    private boolean qualifiedResponder;
    /** Set when they joined with a contact code: the problem they are on the hook for. */
    private String contactFor;

    /** Required by Firestore's object mapper. */
    public User() {
        this.qualifications = new ArrayList<>();
    }

    public User(String eventId, String role) {
        this.eventId = eventId;
        this.role = role;
        this.qualifications = new ArrayList<>();
        this.qualifiedResponder = false;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Falls back to the role so lists never show a blank row. */
    public String getDisplayName() {
        return name == null || name.trim().isEmpty() ? "Unnamed " + role : name;
    }

    public boolean isStaffLevel() {
        return ROLE_ORGANIZER.equals(role) || ROLE_STAFF.equals(role);
    }

    public List<String> getQualifications() {
        if (qualifications == null) {
            qualifications = new ArrayList<>();
        }
        return qualifications;
    }

    public void setQualifications(List<String> qualifications) {
        this.qualifications = qualifications;
    }

    public String getContactFor() {
        return contactFor;
    }

    public void setContactFor(String contactFor) {
        this.contactFor = contactFor;
    }

    public boolean isQualifiedResponder() {
        return qualifiedResponder;
    }

    public void setQualifiedResponder(boolean qualifiedResponder) {
        this.qualifiedResponder = qualifiedResponder;
    }

    /** "Alice — CPR Certified, AED Trained" for the staff-facing lists. */
    public String getSummary() {
        List<String> quals = getQualifications();
        if (quals.isEmpty()) return getDisplayName();
        return getDisplayName() + " — " + android.text.TextUtils.join(", ", quals);
    }
}
