package com.elderhive.services.impl;

import com.elderhive.config.StripeConfig;
import com.elderhive.dtos.Payment.CreatePaymentIntentResponse;
import com.elderhive.dtos.Payment.PaymentResponse;
import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.Payment;
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
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ElderProfileRepository elderProfileRepository;
    @Mock
    private FamilyElderLinkRepository familyElderLinkRepository;
    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;
    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private StripeConfig stripeConfig;

    @InjectMocks
    private PaymentServiceImpl service;

    private static final Long BOOKING_ID = 10L;
    private static final Long FAMILY_USER_ID = 1L;
    private static final Long CARETAKER_ID = 20L;
    private static final Long CARETAKER_USER_ID = 2L;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("u" + id + "@example.com").userType(type).build();
    }

    private Booking booking(BookingStatusEnum status) {
        return Booking.builder().id(BOOKING_ID).familyUserId(FAMILY_USER_ID).elderProfileId(5L)
                .caretakerId(CARETAKER_ID).serviceOfferingId(6L).status(status).cost(new BigDecimal("49.99")).build();
    }

    private Payment pendingPayment() {
        return Payment.builder().id(100L).bookingId(BOOKING_ID).amount(new BigDecimal("49.99")).currency("usd")
                .status(PaymentStatusEnum.PENDING).stripePaymentIntentId("pi_existing").build();
    }

    // ==== createIntent ====

    @Test
    void createIntentThrowsBookingNotFoundWhenBookingMissing() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createIntent(BOOKING_ID, user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.BOOKING_NOT_FOUND);
    }

    @Test
    void createIntentThrowsForbiddenWhenCallerIsNeitherFamilyPartyNorAdmin() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.createIntent(BOOKING_ID, user(999L, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void createIntentThrowsBookingNotPayableWhenNotConfirmed() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.PENDING)));

        assertThatThrownBy(() -> service.createIntent(BOOKING_ID, user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.BOOKING_NOT_PAYABLE);
    }

    @Test
    void createIntentThrowsGatewayNotConfiguredWhenStripeIsNotConfigured() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        when(stripeConfig.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.createIntent(BOOKING_ID, user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.PAYMENT_GATEWAY_NOT_CONFIGURED);
    }

    @Test
    void createIntentReusesAnExistingPendingPaymentRatherThanCreatingANewOne() throws Exception {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        when(stripeConfig.isConfigured()).thenReturn(true);
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(BOOKING_ID, PaymentStatusEnum.PENDING))
                .thenReturn(Optional.of(pendingPayment()));

        PaymentIntent retrieved = mock(PaymentIntent.class);
        when(retrieved.getClientSecret()).thenReturn("secret_reused");

        try (MockedStatic<PaymentIntent> stripeStatic = mockStatic(PaymentIntent.class)) {
            stripeStatic.when(() -> PaymentIntent.retrieve("pi_existing")).thenReturn(retrieved);

            CreatePaymentIntentResponse response = service.createIntent(BOOKING_ID, user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER));

            assertThat(response.getPaymentId()).isEqualTo(100L);
            assertThat(response.getClientSecret()).isEqualTo("secret_reused");
            stripeStatic.verify(() -> PaymentIntent.create(any(com.stripe.param.PaymentIntentCreateParams.class)), never());
        }
    }

    @Test
    void createIntentCreatesAFreshStripeIntentWhenNoPendingPaymentExists() throws Exception {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        when(stripeConfig.isConfigured()).thenReturn(true);
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(BOOKING_ID, PaymentStatusEnum.PENDING))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(200L);
            }
            return p;
        });

        PaymentIntent created = mock(PaymentIntent.class);
        when(created.getId()).thenReturn("pi_new");
        when(created.getClientSecret()).thenReturn("secret_new");

        try (MockedStatic<PaymentIntent> stripeStatic = mockStatic(PaymentIntent.class)) {
            stripeStatic.when(() -> PaymentIntent.create(any(com.stripe.param.PaymentIntentCreateParams.class))).thenReturn(created);

            CreatePaymentIntentResponse response = service.createIntent(BOOKING_ID, user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER));

            assertThat(response.getPaymentId()).isEqualTo(200L);
            assertThat(response.getClientSecret()).isEqualTo("secret_new");
            assertThat(response.getAmount()).isEqualByComparingTo("49.99");
            verify(paymentRepository, times(2)).save(any(Payment.class));
        }
    }

    // ==== handleWebhook ====

    @Test
    void handleWebhookThrowsWebhookSignatureInvalidWhenWebhookSecretIsNotConfigured() {
        when(stripeConfig.isWebhookConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.handleWebhook("{}", "sig"))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    void handleWebhookThrowsWebhookSignatureInvalidWhenSignatureDoesNotVerify() {
        when(stripeConfig.isWebhookConfigured()).thenReturn(true);
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad signature", "sig"));

            assertThatThrownBy(() -> service.handleWebhook("{}", "bad-sig"))
                    .isInstanceOf(GenericException.class)
                    .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.WEBHOOK_SIGNATURE_INVALID);
        }
    }

    private Event mockEvent(String type, com.stripe.model.StripeObject dataObject) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.ofNullable(dataObject));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    @Test
    void handleWebhookOnSucceededMarksPaymentSucceededAndNotifiesFamilyAndCaretaker() throws Exception {
        when(stripeConfig.isWebhookConfigured()).thenReturn(true);
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_existing");

        Payment payment = pendingPayment();
        when(paymentRepository.findByStripePaymentIntentId("pi_existing")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        when(caretakerProfileRepository.findById(CARETAKER_ID))
                .thenReturn(Optional.of(CaretakerProfile.builder().id(CARETAKER_ID).userId(CARETAKER_USER_ID).build()));

        Event event = mockEvent("payment_intent.succeeded", intent);
        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);

            service.handleWebhook("{}", "sig");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatusEnum.SUCCEEDED);
            assertThat(payment.getPaidAt()).isNotNull();
            verify(notificationService).send(eq("PAYMENT_RECEIVED"), any(), eq(FAMILY_USER_ID));
            verify(notificationService).send(eq("PAYMENT_RECEIVED"), any(), eq(CARETAKER_USER_ID));
        }
    }

    @Test
    void handleWebhookOnSucceededIsIdempotentWhenAlreadySucceeded() throws Exception {
        when(stripeConfig.isWebhookConfigured()).thenReturn(true);
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_existing");

        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatusEnum.SUCCEEDED);
        when(paymentRepository.findByStripePaymentIntentId("pi_existing")).thenReturn(Optional.of(payment));

        Event event = mockEvent("payment_intent.succeeded", intent);
        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);

            service.handleWebhook("{}", "sig");

            verify(paymentRepository, never()).save(any());
            verify(notificationService, never()).send(any(), any(), anyLong());
        }
    }

    @Test
    void handleWebhookOnPaymentFailedMarksPaymentFailedAndNotifiesOnlyTheFamilyParty() throws Exception {
        when(stripeConfig.isWebhookConfigured()).thenReturn(true);
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_existing");
        when(intent.getLastPaymentError()).thenReturn(null);

        Payment payment = pendingPayment();
        when(paymentRepository.findByStripePaymentIntentId("pi_existing")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));

        Event event = mockEvent("payment_intent.payment_failed", intent);
        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);

            service.handleWebhook("{}", "sig");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatusEnum.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("Payment failed");
            verify(notificationService).send(eq("PAYMENT_FAILED"), any(), eq(FAMILY_USER_ID));
            verify(notificationService, never()).send(any(), any(), eq(CARETAKER_USER_ID));
        }
    }

    @Test
    void handleWebhookOnChargeRefundedMarksPaymentRefunded() throws Exception {
        when(stripeConfig.isWebhookConfigured()).thenReturn(true);
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn("pi_existing");

        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatusEnum.SUCCEEDED);
        when(paymentRepository.findByStripePaymentIntentId("pi_existing")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event event = mockEvent("charge.refunded", charge);
        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);

            service.handleWebhook("{}", "sig");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatusEnum.REFUNDED);
            assertThat(payment.getRefundedAt()).isNotNull();
        }
    }

    @Test
    void handleWebhookIgnoresAnUnhandledEventTypeWithoutError() throws Exception {
        when(stripeConfig.isWebhookConfigured()).thenReturn(true);
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        Event event = mockEvent("customer.created", null);
        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);

            service.handleWebhook("{}", "sig");

            verify(paymentRepository, never()).save(any());
        }
    }

    // ==== getById / getByBooking ====

    @Test
    void getByIdThrowsPaymentNotFoundWhenMissing() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(100L, user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.PAYMENT_NOT_FOUND);
    }

    @Test
    void getByIdAllowsTheAssignedCaretakerEvenThoughTheyAreNotTheFamilyParty() throws Exception {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment()));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(any(), any())).thenReturn(false);
        when(caretakerProfileRepository.findByUserId(CARETAKER_USER_ID))
                .thenReturn(Optional.of(CaretakerProfile.builder().id(CARETAKER_ID).userId(CARETAKER_USER_ID).build()));

        PaymentResponse response = service.getById(100L, user(CARETAKER_USER_ID, UserTypeEnum.CARETAKER));

        assertThat(response.getId()).isEqualTo(100L);
    }

    @Test
    void getByIdThrowsForbiddenWhenCallerHasNoRelationToTheBooking() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment()));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(any(), any())).thenReturn(false);
        when(caretakerProfileRepository.findByUserId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(100L, user(999L, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode()).isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void getByBookingReturnsEveryAttemptMostRecentFirst() throws Exception {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking(BookingStatusEnum.CONFIRMED)));
        Payment older = pendingPayment();
        older.setStatus(PaymentStatusEnum.FAILED);
        Payment newer = pendingPayment();
        when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of(newer, older));

        List<PaymentResponse> responses = service.getByBooking(BOOKING_ID, user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getStatus()).isEqualTo(PaymentStatusEnum.PENDING);
        assertThat(responses.get(1).getStatus()).isEqualTo(PaymentStatusEnum.FAILED);
    }
}
