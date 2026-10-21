package com.elderhive.services;

import com.elderhive.dtos.Booking.BookingRequest;
import com.elderhive.dtos.Booking.BookingResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingService {

    /** Caller must own or co-manage (FamilyElderLink) elderProfileId, or be ADMIN/SUPER_ADMIN.
     *  Re-validates the requested slot against AvailableSlotService server-side - the
     *  client's own "it looked free" is never trusted. When request.recurring is true,
     *  creates one booking per week (request.occurrences, 2-12) sharing one
     *  recurringGroupId, validating every occurrence up front and creating nothing if any
     *  single one conflicts. Returns the first (earliest) occurrence either way. */
    BookingResponse create(User caller, BookingRequest request) throws GenericException;

    /** PENDING -> CONFIRMED. Caller must be the assigned caretaker or ADMIN/SUPER_ADMIN. */
    BookingResponse confirm(Long id, User caller) throws GenericException;

    /** CONFIRMED -> IN_PROGRESS. Caller must be the assigned caretaker or ADMIN/SUPER_ADMIN. */
    BookingResponse start(Long id, User caller) throws GenericException;

    /** IN_PROGRESS -> COMPLETED. Caller must be the assigned caretaker or ADMIN/SUPER_ADMIN. */
    BookingResponse complete(Long id, User caller) throws GenericException;

    /** PENDING/CONFIRMED/IN_PROGRESS -> CANCELLED. While PENDING, either the family party
     *  or the assigned caretaker may cancel; once CONFIRMED or IN_PROGRESS, only the
     *  family party may (a caretaker backing out of a commitment needs an admin's help).
     *  ADMIN/SUPER_ADMIN may always cancel, any status. When cancelWholeSeries is true and
     *  this booking has a recurringGroupId, every sibling booking in that series that
     *  isn't already COMPLETED/CANCELLED is cancelled too (permission is checked once,
     *  against THIS booking); otherwise only this one booking is cancelled. */
    BookingResponse cancel(Long id, User caller, boolean cancelWholeSeries) throws GenericException;

    /** Caller must be the family party, the assigned caretaker, or ADMIN/SUPER_ADMIN. */
    BookingResponse getById(Long id, User caller) throws GenericException;

    /** The caller's own bookings as the family party who made them. */
    Page<BookingResponse> getMyBookingsAsFamily(User caller, Pageable pageable);

    /** The caller's own bookings as the assigned caretaker. Empty page if the caller has
     *  no caretaker profile at all - not an error, since a CARETAKER with none yet has
     *  trivially zero bookings. */
    Page<BookingResponse> getMyBookingsAsCaretaker(User caller, Pageable pageable);

    /** Every booking sharing recurringGroupId, oldest occurrence first. Caller must be the
     *  family party or assigned caretaker of that series, or ADMIN/SUPER_ADMIN. */
    List<BookingResponse> getByRecurringGroupId(String recurringGroupId, User caller) throws GenericException;
}
