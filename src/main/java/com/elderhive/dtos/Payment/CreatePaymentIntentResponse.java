package com.elderhive.dtos.Payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentIntentResponse {

    private Long paymentId;
    // Stripe.js/Stripe Elements needs this client-side to confirm the PaymentIntent -
    // never log it, it's as sensitive as a short-lived bearer token for this one payment.
    private String clientSecret;
    private BigDecimal amount;
    private String currency;
}
