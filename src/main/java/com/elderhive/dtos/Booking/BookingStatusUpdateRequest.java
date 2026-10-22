package com.elderhive.dtos.Booking;

import com.elderhive.enums.BookingStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private BookingStatusEnum status;
}
