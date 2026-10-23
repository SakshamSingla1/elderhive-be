package com.elderhive.services.impl;

import com.elderhive.dtos.AvailableSlot.AvailableSlotsResponse;
import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerAvailability;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.DayOfWeekEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerAvailabilityRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import com.elderhive.services.AvailableSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Computes a caretaker's free slots for one day: start from CaretakerAvailability's (Prompt
 * 66) weekly schedule for that day of week - or a sensible default window when the
 * caretaker hasn't set one - cut it into serviceOfferingId-sized slots, then subtract
 * whatever's already booked (Prompt 75's Booking, Prompt 76 adds the finder this needs).
 * Read-only, no entity mutation, so no @Transactional needed.
 */
@Service
@RequiredArgsConstructor
public class AvailableSlotServiceImpl implements AvailableSlotService {

    private static final int DEFAULT_SLOT_DURATION_MINUTES = 60;
    private static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(18, 0);
    // A booking still holds its slot unless it's been cancelled - COMPLETED bookings are
    // necessarily in the past relative to any date this endpoint is asked about, but
    // excluding them from the "occupied" set costs nothing and keeps the intent explicit.
    private static final List<BookingStatusEnum> OCCUPYING_STATUSES =
            List.of(BookingStatusEnum.PENDING, BookingStatusEnum.CONFIRMED, BookingStatusEnum.IN_PROGRESS);

    private final CaretakerProfileRepository caretakerProfileRepository;
    private final CaretakerAvailabilityRepository caretakerAvailabilityRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingRepository bookingRepository;

    @Override
    public AvailableSlotsResponse getAvailableSlots(Long caretakerId, LocalDate date, Long serviceOfferingId) throws GenericException {
        if (!caretakerProfileRepository.existsById(caretakerId)) {
            throw new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found");
        }
        ServiceOffering offering = serviceOfferingRepository.findById(serviceOfferingId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND, "Service offering not found"));
        int slotDurationMinutes = offering.getDurationMinutes() != null ? offering.getDurationMinutes() : DEFAULT_SLOT_DURATION_MINUTES;

        DayOfWeekEnum dayOfWeek = DayOfWeekEnum.valueOf(date.getDayOfWeek().name());
        List<CaretakerAvailability> windows = caretakerAvailabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(caretakerId)
                .stream().filter(slot -> slot.getDayOfWeek() == dayOfWeek).toList();

        boolean usingDefaultHours = windows.isEmpty();
        List<Window> effectiveWindows = usingDefaultHours
                ? List.of(new Window(DEFAULT_WINDOW_START, DEFAULT_WINDOW_END))
                : windows.stream().map(w -> new Window(w.getStartTime(), w.getEndTime())).toList();

        Set<LocalTime> bookedTimes = bookingRepository.findByCaretakerIdAndScheduledDateAndStatusIn(caretakerId, date, OCCUPYING_STATUSES)
                .stream().map(Booking::getScheduledTime).collect(Collectors.toSet());

        TreeSet<LocalTime> availableSlots = new TreeSet<>();
        for (Window window : effectiveWindows) {
            LocalTime slotStart = window.start();
            while (true) {
                LocalTime slotEnd = slotStart.plusMinutes(slotDurationMinutes);
                // LocalTime arithmetic wraps at midnight rather than overflowing (e.g.
                // 23:00 + 60min = 00:00), so a naive "!isAfter(window.end())" check never
                // trips for a window that runs late enough to wrap - this slotEnd.isBefore
                // check catches that wraparound explicitly and stops the window there,
                // same as reaching window.end() normally would. A caretaker's availability
                // is scoped to a single calendar day, so a slot is never offered past
                // midnight into the next date anyway.
                if (slotEnd.isBefore(slotStart) || slotEnd.isAfter(window.end())) {
                    break;
                }
                if (!bookedTimes.contains(slotStart)) {
                    availableSlots.add(slotStart);
                }
                slotStart = slotEnd;
            }
        }

        return AvailableSlotsResponse.builder()
                .caretakerId(caretakerId)
                .serviceOfferingId(serviceOfferingId)
                .date(date)
                .dayOfWeek(dayOfWeek)
                .slotDurationMinutes(slotDurationMinutes)
                .usingDefaultHours(usingDefaultHours)
                .availableSlots(List.copyOf(availableSlots))
                .build();
    }

    private record Window(LocalTime start, LocalTime end) {
    }
}
