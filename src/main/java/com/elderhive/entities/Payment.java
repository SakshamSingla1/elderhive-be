package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.PaymentStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per Stripe PaymentIntent attempt against a booking (Prompt 97). Mirrors
 * ElderSphere's own Payment entity, trimmed to this prompt's explicit field list plus the
 * minimum extra fields the webhook flow needs to be meaningful (currency, failureReason,
 * paidAt) - no familyUserId/stripeChargeId column: the owning family user and caretaker
 * are resolved by joining through bookingId (BookingRepository) when an access check or
 * notification needs them, rather than denormalizing them here.
 */
@Entity
@Table(name = "payments")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    private BigDecimal amount;

    @Builder.Default
    private String currency = "usd";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatusEnum status;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
