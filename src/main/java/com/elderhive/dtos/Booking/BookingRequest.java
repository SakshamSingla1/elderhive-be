package com.elderhive.dtos.Booking;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotNull(message = "Elder profile is required")
    private Long elderProfileId;

    @NotNull(message = "Caretaker is required")
    private Long caretakerId;

    @NotNull(message = "Service offering is required")
    private Long serviceOfferingId;

    @NotNull(message = "Scheduled date is required")
    private LocalDate scheduledDate;

    @NotNull(message = "Scheduled time is required")
    private LocalTime scheduledTime;

    private String notes;

    // When true, creates one Booking per week for `occurrences` weeks (2-12, checked in
    // the service layer since Bean Validation can't express "required only if recurring"
    // cleanly), all sharing one generated recurringGroupId. `occurrences` is ignored for a
    // one-off booking.
    private boolean recurring;

    private Integer occurrences;
}
