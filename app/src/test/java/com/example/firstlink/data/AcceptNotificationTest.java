package com.example.firstlink.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Guards the one gate that decides whether a reporter's phone buzzes. */
public class AcceptNotificationTest {

    @Test
    public void alertsOnlyWhenTheAcceptListGrows() {
        // First time we see a document someone has already accepted.
        assertTrue(FirstLinkRepository.someoneNewAccepted(null, 1));
        // A second responder joining the first.
        assertTrue(FirstLinkRepository.someoneNewAccepted(1, 2));
        // Some other field changed — a resolve, a 911 approval.
        assertFalse(FirstLinkRepository.someoneNewAccepted(1, 1));
        // Nobody has accepted yet.
        assertFalse(FirstLinkRepository.someoneNewAccepted(null, 0));
    }
}
