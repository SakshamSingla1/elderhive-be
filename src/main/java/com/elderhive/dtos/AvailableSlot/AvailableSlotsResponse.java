package com.elderhive.dtos.AvailableSlot;

import com.elderhive.enums.DayOfWeekEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSlotsResponse {

    private Long caretakerId;
    private Long serviceOfferingId;
    private LocalDate date;
    private DayOfWeekEnum dayOfWeek;
    private Integer slotDurationMinutes;
    // True when the caretaker has no CaretakerAvailability rows for this day of week, so
    // the slots below were generated from AvailableSlotServiceImpl's default 9am-6pm
    // window instead of their own schedule.
    private boolean usingDefaultHours;
    // Each entry is a slot's START time - the booking it would create occupies
    // [slot, slot + slotDurationMinutes).
    private List<LocalTime> availableSlots;
}
