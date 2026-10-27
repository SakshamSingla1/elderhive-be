package com.elderhive.dtos.Payment;

import com.elderhive.enums.PaymentStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Long bookingId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatusEnum status;
    private String stripePaymentIntentId;
    private String failureReason;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
