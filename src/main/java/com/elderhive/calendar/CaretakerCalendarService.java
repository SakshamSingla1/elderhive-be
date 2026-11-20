package com.elderhive.calendar;

import com.elderhive.exceptions.GenericException;

public interface CaretakerCalendarService {

    /** Returns the caller's subscribable .ics URL, generating a share token on first call. */
    String getOrCreateCalendarLink(Long userId) throws GenericException;

    /** Always issues a brand-new token, invalidating whatever link was shared before. */
    String regenerateCalendarLink(Long userId) throws GenericException;

    /** Resolves {@code token} to a caretaker and renders their upcoming bookings as a
     *  standard iCalendar (.ics) feed - never the caller's own identity, since this backs
     *  a public, unauthenticated-by-cookie route (GenericException(CARETAKER_PROFILE_NOT_FOUND)
     *  on an unknown/revoked token). */
    String buildIcsFeed(String token) throws GenericException;
}
