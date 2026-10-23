package com.example.firstlink.data;

import com.example.firstlink.model.Event;
import com.example.firstlink.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * ReadyMap Lite: how prepared each place at the venue is, from what the organizer
 * entered. A planning estimate, never a guarantee — it knows where people were
 * posted, not where they are. Pure, so every rule is unit-testable.
 */
public final class ReadyMap {

    /** COVERED: a responder posted here and an AED here. PARTIAL: one of the two. GAP: neither. */
    public enum Level { COVERED, PARTIAL, GAP }

    public static final class Spot {
        public final String location;
        public final List<User> responders;
        public final boolean aed;
        public final boolean firstAid;
        public final Level level;

        Spot(String location, List<User> responders, boolean aed, boolean firstAid) {
            this.location = location;
            this.responders = responders;
            this.aed = aed;
            this.firstAid = firstAid;
            boolean staffed = !responders.isEmpty();
            this.level = staffed && aed ? Level.COVERED
                    : staffed || aed ? Level.PARTIAL : Level.GAP;
        }
    }

    /** A practice incident: computed on this phone only, nothing written, nobody alerted. */
    public static final class Drill {
        public final Spot spot;
        /** Responders posted somewhere else, or not posted yet, who could be sent. */
        public final List<User> elsewhere;
        public final List<String> aedLocations;

        Drill(Spot spot, List<User> elsewhere, List<String> aedLocations) {
            this.spot = spot;
            this.elsewhere = elsewhere;
            this.aedLocations = aedLocations;
        }
    }

    private ReadyMap() {
    }

    /** Who can be posted: qualified responders and staff. */
    public static List<User> responders(List<User> people) {
        List<User> out = new ArrayList<>();
        for (User u : people) {
            if (u.isQualifiedResponder() || u.isStaffLevel()) out.add(u);
        }
        return out;
    }

    /** One spot per event location, in the organizer's order. */
    public static List<Spot> coverage(Event event, List<User> people) {
        List<Spot> spots = new ArrayList<>();
        for (String location : event.getLocations()) spots.add(spot(event, people, location));
        return spots;
    }

    /** Responders with no post, or posted to a place that no longer exists. */
    public static List<User> unposted(Event event, List<User> people) {
        List<User> out = new ArrayList<>();
        for (User u : responders(people)) {
            String post = event.getResponderPosts().get(u.getId());
            if (post == null || !event.getLocations().contains(post)) out.add(u);
        }
        return out;
    }

    public static Drill drill(Event event, List<User> people, String location) {
        Spot here = spot(event, people, location);
        List<User> elsewhere = new ArrayList<>(responders(people));
        elsewhere.removeAll(here.responders);
        return new Drill(here, elsewhere, new ArrayList<>(event.getAedLocations()));
    }

    private static Spot spot(Event event, List<User> people, String location) {
        List<User> posted = new ArrayList<>();
        for (User u : responders(people)) {
            if (location.equals(event.getResponderPosts().get(u.getId()))) posted.add(u);
        }
        return new Spot(location, posted, event.getAedLocations().contains(location),
                event.getFirstAidLocations().contains(location));
    }
}
