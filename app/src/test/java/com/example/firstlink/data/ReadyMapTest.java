package com.example.firstlink.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.firstlink.model.Event;
import com.example.firstlink.model.User;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ReadyMapTest {

    private static User person(String uid, String role, boolean qualified) {
        User u = new User("ev", role);
        u.setId(uid);
        u.setName(uid);
        u.setQualifiedResponder(qualified);
        return u;
    }

    private static Event venue() {
        Event e = new Event("Game", "Gym", "Org", false);
        e.getLocations().addAll(Arrays.asList("Gym", "Lobby", "Field"));
        e.getAedLocations().add("Gym");
        e.getAedLocations().add("Field");
        e.getFirstAidLocations().add("Lobby");
        e.getResponderPosts().put("nurse", "Gym");
        e.getResponderPosts().put("staff", "Lobby");
        e.getResponderPosts().put("gone", "Old Room");
        return e;
    }

    private static final List<User> PEOPLE = Arrays.asList(
            person("nurse", User.ROLE_ATTENDEE, true),
            person("staff", User.ROLE_STAFF, false),
            person("fan", User.ROLE_ATTENDEE, false),
            person("emt", User.ROLE_ATTENDEE, true),
            person("gone", User.ROLE_ATTENDEE, true));

    @Test
    public void levelsFollowRespondersAndAed() {
        List<ReadyMap.Spot> spots = ReadyMap.coverage(venue(), PEOPLE);
        assertEquals(3, spots.size());
        assertEquals(ReadyMap.Level.COVERED, spots.get(0).level);  // Gym: nurse + AED
        assertEquals(ReadyMap.Level.PARTIAL, spots.get(1).level);  // Lobby: staff, no AED
        assertEquals(ReadyMap.Level.PARTIAL, spots.get(2).level);  // Field: AED, nobody
        assertTrue(spots.get(1).firstAid);
        assertEquals(1, spots.get(0).responders.size());
    }

    @Test
    public void nobodyAndNothingIsAGap() {
        Event e = new Event("Game", "Gym", "Org", false);
        e.getLocations().add("Annex");
        assertEquals(ReadyMap.Level.GAP, ReadyMap.coverage(e, PEOPLE).get(0).level);
    }

    @Test
    public void onlyStaffAndQualifiedCanBePostedAndStalePostsAreUnposted() {
        List<User> unposted = ReadyMap.unposted(venue(), PEOPLE);
        // fan is not a responder; gone's post points at a removed location.
        assertEquals(Arrays.asList("emt", "gone"), uids(unposted));
        assertEquals(Arrays.asList("nurse", "staff", "emt", "gone"),
                uids(ReadyMap.responders(PEOPLE)));
    }

    @Test
    public void drillShowsWhoIsHereWhoCanComeAndWhereTheAedsAre() {
        ReadyMap.Drill drill = ReadyMap.drill(venue(), PEOPLE, "Lobby");
        assertEquals(Arrays.asList("staff"), uids(drill.spot.responders));
        assertEquals(Arrays.asList("nurse", "emt", "gone"), uids(drill.elsewhere));
        assertFalse(drill.spot.aed);
        assertEquals(Arrays.asList("Gym", "Field"), drill.aedLocations);
    }

    private static List<String> uids(List<User> users) {
        String[] ids = new String[users.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = users.get(i).getId();
        return Arrays.asList(ids);
    }
}
