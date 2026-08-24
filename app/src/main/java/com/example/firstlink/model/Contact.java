package com.example.firstlink.model;

/**
 * "Water problem — Dana Reyes, code WX7Q2A".
 *
 * Its own collection rather than a map on the event, because the join screen has to
 * find an event from nothing but a code — an equality query on one field, the same
 * shape as the attendee and staff code lookups.
 */
public class Contact {

    private String id;
    private String eventId;
    private String problem;
    private String person;
    private String code;

    /** Required by Firestore's object mapper. */
    public Contact() {
    }

    public Contact(String eventId, String problem, String person, String code) {
        this.eventId = eventId;
        this.problem = problem;
        this.person = person;
        this.code = code;
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

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getPerson() {
        return person;
    }

    public void setPerson(String person) {
        this.person = person;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
