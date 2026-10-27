package com.elderhive.services;

import com.elderhive.dtos.Payment.CreatePaymentIntentResponse;
import com.elderhive.dtos.Payment.PaymentResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;

import java.util.List;

/**
 * Payments are additive, not blocking (Prompt 97's own documented choice): Phase 10's
 * booking lifecycle (create/confirm/start/complete/cancel) works completely independently
 * of everything in this service - nothing here is a precondition for any BookingService
 * method. A family member MAY start a payment once their booking reaches CONFIRMED;
 * nothing requires them to, and a booking with no Payment row at all, or one still
 * PENDING/FAILED, behaves identically to every other CONFIRMED booking everywhere else in
 * the app (availability, cancellation, the caretaker's own view, ...).
 */
public interface PaymentService {

    /** Creates a fresh Stripe PaymentIntent for this booking, or reuses the existing
     *  PENDING one if createIntent() was already called and the client never completed it
     *  - never creates a second concurrent PENDING attempt for the same booking. Caller
     *  must be the booking's own family party; the booking must be CONFIRMED
     *  (BOOKING_NOT_PAYABLE otherwise) and Stripe must be configured
     *  (PAYMENT_GATEWAY_NOT_CONFIGURED otherwise). */
    CreatePaymentIntentResponse createIntent(Long bookingId, User caller) throws GenericException;

    /** Called by Stripe only (PaymentController#webhook is unauthenticated - signature
     *  verification against stripe.webhook-secret IS the auth). Verifies signatureHeader
     *  against the raw payload, then applies whichever of
     *  payment_intent.succeeded/payment_intent.payment_failed/charge.refunded the event
     *  carries to the matching Payment row (looked up by stripePaymentIntentId) -
     *  idempotent: re-delivering the same event (Stripe's own at-least-once guarantee) is
     *  a no-op once the row is already in the target terminal-for-that-event status. Any
     *  other event type, or one carrying no PaymentIntent payload, is logged and ignored. */
    void handleWebhook(String payload, String signatureHeader) throws GenericException;

    /** Caller must be the payment's own booking's family party, its assigned caretaker, or
     *  ADMIN/SUPER_ADMIN. */
    PaymentResponse getById(Long id, User caller) throws GenericException;

    /** Every payment attempt against this booking, most recent first (so a caller sees
     *  the current FAILED/PENDING/SUCCEEDED state first, not a stale earlier retry).
     *  Same access rule as {@link #getById}. */
    List<PaymentResponse> getByBooking(Long bookingId, User caller) throws GenericException;
}
