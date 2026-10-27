package com.elderhive.services.impl;

import com.elderhive.config.StripeConfig;
import com.elderhive.dtos.Payment.CreatePaymentIntentResponse;
import com.elderhive.dtos.Payment.PaymentResponse;
import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.Payment;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.entities.User;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.PaymentStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.PaymentRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import com.elderhive.services.NotificationService;
import com.elderhive.services.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reference: ~/ElderSphere/eldersphere-be's PaymentServiceImpl - trimmed to this prompt's
 * explicit scope (no earnings/payouts, that's a later phase's "advanced caretaker
 * operations" territory) and re-keyed around this project's own access-check style
 * (family-party-or-co-manager-or-admin, matching BookingServiceImpl) instead of
 * ElderSphere's flatter familyUserId-equality check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final FamilyElderLinkRepository familyElderLinkRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final NotificationService notificationService;
    private final StripeConfig stripeConfig;

    @Override
    @Transactional
    public CreatePaymentIntentResponse createIntent(Long bookingId, User caller) throws GenericException {
        Booking booking = findBookingOrThrow(bookingId);
        assertFamilyPartyOrAdmin(booking, caller);
        if (booking.getStatus() != BookingStatusEnum.CONFIRMED) {
            throw new GenericException(ExceptionCodeEnum.BOOKING_NOT_PAYABLE, "Only a CONFIRMED booking can be paid for");
        }
        if (!stripeConfig.isConfigured()) {
            throw new GenericException(ExceptionCodeEnum.PAYMENT_GATEWAY_NOT_CONFIGURED, "Payments are not configured on this server");
        }

        Optional<Payment> existing =
                paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatusEnum.PENDING);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            return CreatePaymentIntentResponse.builder()
                    .paymentId(payment.getId())
                    .clientSecret(retrieveClientSecret(payment.getStripePaymentIntentId()))
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .build();
        }

        BigDecimal amount = booking.getCost();
        Payment payment = paymentRepository.save(Payment.builder()
                .bookingId(bookingId)
                .amount(amount)
                .currency("usd")
                .status(PaymentStatusEnum.PENDING)
                .build());

        PaymentIntent intent = createStripeIntent(bookingId, payment.getId(), amount);
        payment.setStripePaymentIntentId(intent.getId());
        payment = paymentRepository.save(payment);

        return CreatePaymentIntentResponse.builder()
                .paymentId(payment.getId())
                .clientSecret(intent.getClientSecret())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .build();
    }

    private PaymentIntent createStripeIntent(Long bookingId, Long paymentId, BigDecimal amount) throws GenericException {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toCents(amount))
                    .setCurrency("usd")
                    .putMetadata("bookingId", String.valueOf(bookingId))
                    .putMetadata("paymentId", String.valueOf(paymentId))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                    .build();
            return PaymentIntent.create(params);
        } catch (StripeException e) {
            log.error("Failed to create Stripe PaymentIntent for booking {}", bookingId, e);
            throw new GenericException(ExceptionCodeEnum.PAYMENT_GATEWAY_NOT_CONFIGURED, "Failed to reach Stripe - please try again shortly");
        }
    }

    private String retrieveClientSecret(String stripePaymentIntentId) throws GenericException {
        try {
            return PaymentIntent.retrieve(stripePaymentIntentId).getClientSecret();
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe PaymentIntent {}", stripePaymentIntentId, e);
            throw new GenericException(ExceptionCodeEnum.PAYMENT_GATEWAY_NOT_CONFIGURED, "Failed to reach Stripe - please try again shortly");
        }
    }

    private static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) throws GenericException {
        if (!stripeConfig.isWebhookConfigured()) {
            throw new GenericException(ExceptionCodeEnum.WEBHOOK_SIGNATURE_INVALID, "Stripe webhook secret is not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new GenericException(ExceptionCodeEnum.WEBHOOK_SIGNATURE_INVALID, "Invalid Stripe webhook signature");
        }

        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                if (stripeObject instanceof PaymentIntent intent) {
                    handleSucceeded(intent);
                }
            }
            case "payment_intent.payment_failed" -> {
                if (stripeObject instanceof PaymentIntent intent) {
                    handleFailed(intent);
                }
            }
            case "charge.refunded" -> {
                if (stripeObject instanceof Charge charge) {
                    handleRefunded(charge);
                }
            }
            default -> log.debug("Ignoring unhandled Stripe webhook event type {}", event.getType());
        }
    }

    private void handleSucceeded(PaymentIntent intent) throws GenericException {
        Payment payment = paymentRepository.findByStripePaymentIntentId(intent.getId()).orElse(null);
        if (payment == null) {
            log.warn("Stripe payment_intent.succeeded for unknown PaymentIntent {}", intent.getId());
            return;
        }
        // Idempotent - Stripe may redeliver the same event more than once.
        if (payment.getStatus() == PaymentStatusEnum.SUCCEEDED) {
            return;
        }

        payment.setStatus(PaymentStatusEnum.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        Map<String, String> variables = paymentVariables(payment, booking, null);
        notificationService.send("PAYMENT_RECEIVED", variables, booking.getFamilyUserId());
        Long caretakerUserId = caretakerProfileRepository.findById(booking.getCaretakerId())
                .map(CaretakerProfile::getUserId).orElse(null);
        if (caretakerUserId != null) {
            notificationService.send("PAYMENT_RECEIVED", variables, caretakerUserId);
        }
    }

    private void handleFailed(PaymentIntent intent) throws GenericException {
        Payment payment = paymentRepository.findByStripePaymentIntentId(intent.getId()).orElse(null);
        if (payment == null) {
            log.warn("Stripe payment_intent.payment_failed for unknown PaymentIntent {}", intent.getId());
            return;
        }
        // A successful or already-refunded payment never moves backwards to FAILED, no
        // matter what Stripe delivers (a stale/out-of-order webhook, say).
        if (payment.getStatus() == PaymentStatusEnum.SUCCEEDED || payment.getStatus() == PaymentStatusEnum.REFUNDED) {
            return;
        }

        String reason = intent.getLastPaymentError() != null ? intent.getLastPaymentError().getMessage() : "Payment failed";
        payment.setStatus(PaymentStatusEnum.FAILED);
        payment.setFailureReason(reason);
        payment = paymentRepository.save(payment);

        Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        // Only the family party is notified - a failed attempt is theirs to retry, the
        // caretaker has nothing actionable here.
        notificationService.send("PAYMENT_FAILED", paymentVariables(payment, booking, reason), booking.getFamilyUserId());
    }

    private void handleRefunded(Charge charge) {
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            return;
        }
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (payment == null) {
            log.warn("Stripe charge.refunded for unknown PaymentIntent {}", paymentIntentId);
            return;
        }
        if (payment.getStatus() == PaymentStatusEnum.REFUNDED) {
            return;
        }

        payment.setStatus(PaymentStatusEnum.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private Map<String, String> paymentVariables(Payment payment, Booking booking, String reason) {
        Map<String, String> variables = new HashMap<>();
        variables.put("elderName", elderProfileRepository.findById(booking.getElderProfileId()).map(ElderProfile::getFullName).orElse("your elder"));
        variables.put("serviceName", serviceOfferingRepository.findById(booking.getServiceOfferingId()).map(ServiceOffering::getName).orElse("your service"));
        variables.put("bookingDate", String.valueOf(booking.getScheduledDate()));
        variables.put("amount", payment.getAmount().toPlainString());
        variables.put("currency", payment.getCurrency().toUpperCase());
        if (reason != null) {
            variables.put("reason", reason);
        }
        return variables;
    }

    @Override
    public PaymentResponse getById(Long id, User caller) throws GenericException {
        Payment payment = findPaymentOrThrow(id);
        Booking booking = findBookingOrThrow(payment.getBookingId());
        assertCanView(booking, caller);
        return toResponse(payment);
    }

    @Override
    public List<PaymentResponse> getByBooking(Long bookingId, User caller) throws GenericException {
        Booking booking = findBookingOrThrow(bookingId);
        assertCanView(booking, caller);
        return paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream().map(this::toResponse).toList();
    }

    private Payment findPaymentOrThrow(Long id) throws GenericException {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.PAYMENT_NOT_FOUND, "Payment not found"));
    }

    private Booking findBookingOrThrow(Long id) throws GenericException {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.BOOKING_NOT_FOUND, "Booking not found"));
    }

    /** Owner, FamilyElderLink co-manager, or ADMIN/SUPER_ADMIN - same shape as
     *  BookingServiceImpl#isFamilyPartyOrAdmin. */
    private void assertFamilyPartyOrAdmin(Booking booking, User caller) throws GenericException {
        if (isFamilyPartyOrAdmin(booking, caller)) {
            return;
        }
        throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to pay for this booking");
    }

    private boolean isFamilyPartyOrAdmin(Booking booking, User caller) {
        if (ADMIN_TIER.contains(caller.getUserType())) {
            return true;
        }
        if (Objects.equals(booking.getFamilyUserId(), caller.getId())) {
            return true;
        }
        return familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(booking.getElderProfileId(), caller.getId());
    }

    private void assertCanView(Booking booking, User caller) throws GenericException {
        if (isFamilyPartyOrAdmin(booking, caller)) {
            return;
        }
        boolean isAssignedCaretaker = caretakerProfileRepository.findByUserId(caller.getId())
                .map(profile -> Objects.equals(profile.getId(), booking.getCaretakerId()))
                .orElse(false);
        if (isAssignedCaretaker) {
            return;
        }
        throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to this payment");
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(payment.getBookingId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .failureReason(payment.getFailureReason())
                .paidAt(payment.getPaidAt())
                .refundedAt(payment.getRefundedAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
