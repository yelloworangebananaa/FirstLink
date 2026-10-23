package com.example.firstlink.model;

import java.util.List;

/**
 * What emergencies and help requests share: someone sent it, and any number of
 * people can take it on. Method names avoid get/is so they never read as
 * stored fields.
 */
public interface Report {
    String getId();

    String getStatus();

    void setStatus(String status);

    List<String> getAcceptedBy();

    List<String> getAcceptedUids();

    /** Anonymous auth uid of whoever sent it. */
    String senderUid();

    default boolean hasAccepted(String uid) {
        return uid != null && !uid.isEmpty() && getAcceptedUids().contains(uid);
    }

    /** Both kinds use the same wire value for "done". */
    default boolean resolved() {
        return EmergencyRequest.STATUS_RESOLVED.equals(getStatus());
    }
}
