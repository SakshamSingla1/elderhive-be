package com.elderhive.repositories;

import com.elderhive.entities.Booking;
import com.elderhive.enums.BookingStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByFamilyUserId(Long familyUserId, Pageable pageable);

    Page<Booking> findByCaretakerId(Long caretakerId, Pageable pageable);

    // Backs the future BookingService's slot-availability check (BOOKING_SLOT_UNAVAILABLE)
    // - true if this caretaker already has a non-terminal booking at this exact
    // date/time, so a caller can reject a conflicting booking before it's ever created.
    boolean existsByCaretakerIdAndScheduledDateAndScheduledTimeAndStatusIn(
            Long caretakerId, LocalDate scheduledDate, LocalTime scheduledTime, List<BookingStatusEnum> statuses);

    // Backs AvailableSlotServiceImpl (Prompt 76) - every non-terminal booking this
    // caretaker already has on a given day, so its scheduled times can be subtracted from
    // the candidate slots CaretakerAvailability's weekly schedule generates.
    List<Booking> findByCaretakerIdAndScheduledDateAndStatusIn(
            Long caretakerId, LocalDate scheduledDate, List<BookingStatusEnum> statuses);

    List<Booking> findByRecurringGroupIdOrderByScheduledDateAsc(String recurringGroupId);
}
