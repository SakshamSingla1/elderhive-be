package com.elderhive.dtos.CaretakerAvailability;

import com.elderhive.enums.DayOfWeekEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class CaretakerAvailabilityRequest {

    @NotNull(message = "Day of week is required")
    private DayOfWeekEnum dayOfWeek;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;
}
