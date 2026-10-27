package com.elderhive.enums;

// PENDING -> SUCCEEDED|FAILED, driven entirely by Stripe webhook events
// (PaymentServiceImpl#handleWebhook) - this app never sets SUCCEEDED/FAILED itself. A
// FAILED payment may retry (a fresh PaymentIntent, a new Payment row - see
// PaymentServiceImpl#createIntent), so FAILED is not terminal the way REFUNDED is.
// REFUNDED is only reachable from SUCCEEDED.
public enum PaymentStatusEnum {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED
}
