package com.elderhive.dtos.Payment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentIntentRequest {

    @NotNull(message = "Booking is required")
    private Long bookingId;
}
