package com.elderhive.services.impl;

import com.elderhive.dtos.AvailableSlot.AvailableSlotsResponse;
import com.elderhive.dtos.Booking.BookingRequest;
import com.elderhive.dtos.Booking.BookingResponse;
import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.entities.User;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.AvailableSlotService;
import com.elderhive.services.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

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
    private UserRepository userRepository;
    @Mock
    private AvailableSlotService availableSlotService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BookingServiceImpl service;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
    private static final LocalTime TIME = LocalTime.of(10, 0);

    private User user(long id, UserTypeEnum type) {
        return User.builder().id(id).userType(type).build();
    }

    private ElderProfile elderProfile(long id, Long familyUserId) {
        return ElderProfile.builder().id(id).familyUserId(familyUserId).fullName("Robert").build();
    }

    private CaretakerProfile caretakerProfile(long id, long userId) {
        return CaretakerProfile.builder().id(id).userId(userId).build();
    }

    private ServiceOffering offering(long id) {
        return ServiceOffering.builder().id(id).name("Weekly Temple Visit").categoryId(1L).basePrice(new BigDecimal("40.00")).build();
    }

    private Booking booking(long id, BookingStatusEnum status) {
        return Booking.builder().id(id).familyUserId(1L).elderProfileId(10L).caretakerId(20L).serviceOfferingId(30L)
                .scheduledDate(DATE).scheduledTime(TIME).status(status).cost(new BigDecimal("40.00")).build();
    }

    private BookingRequest request() {
        return BookingRequest.builder().elderProfileId(10L).caretakerId(20L).serviceOfferingId(30L)
                .scheduledDate(DATE).scheduledTime(TIME).build();
    }

    private void stubAvailable(List<LocalTime> slots) throws GenericException {
        when(availableSlotService.getAvailableSlots(any(), any(), any()))
                .thenReturn(AvailableSlotsResponse.builder().availableSlots(slots).build());
    }

    // ===== create() =====

    @Test
    void createSavesAPendingBookingWithCostFromTheOfferingWhenTheFamilyOwnerBooksAnAvailableSlot() throws GenericException {
        User caller = user(1L, UserTypeEnum.FAMILY_MEMBER);
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));
        stubAvailable(List.of(TIME));
        when(bookingRepository.saveAll(any())).thenAnswer(inv -> {
            List<Booking> toSave = inv.getArgument(0);
            toSave.get(0).setId(100L);
            return toSave;
        });

        BookingResponse response = service.create(caller, request());

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.PENDING);
        assertThat(response.getCost()).isEqualByComparingTo("40.00");
        assertThat(response.getFamilyUserId()).isEqualTo(1L);
        assertThat(response.getRecurringGroupId()).isNull();
    }

    @Test
    void createAllowsACoManagingFamilyMemberEvenWhenNotTheOriginalOwner() throws GenericException {
        User caller = user(2L, UserTypeEnum.FAMILY_MEMBER);
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(10L, 2L)).thenReturn(true);
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));
        stubAvailable(List.of(TIME));
        when(bookingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.create(caller, request());

        assertThat(response.getFamilyUserId()).isEqualTo(2L);
    }

    @Test
    void createAllowsAnAdminToBookForAnyElderProfile() throws GenericException {
        User admin = user(99L, UserTypeEnum.ADMIN);
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));
        stubAvailable(List.of(TIME));
        when(bookingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(admin, request())).isNotNull();
        verify(familyElderLinkRepository, never()).existsByElderProfileIdAndFamilyUserId(any(), any());
    }

    @Test
    void createThrowsForbiddenForAFamilyMemberWithNoRelationToTheElderProfile() {
        User stranger = user(3L, UserTypeEnum.FAMILY_MEMBER);
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(10L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(stranger, request()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void createThrowsElderProfileNotFoundForAnUnknownElderProfile() {
        when(elderProfileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(user(1L, UserTypeEnum.FAMILY_MEMBER),
                request().toBuilder().elderProfileId(999L).build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND);
    }

    @Test
    void createThrowsCaretakerProfileNotFoundForAnUnknownCaretaker() {
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(user(1L, UserTypeEnum.FAMILY_MEMBER),
                request().toBuilder().caretakerId(999L).build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void createThrowsServiceOfferingNotFoundForAnUnknownOffering() {
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(user(1L, UserTypeEnum.FAMILY_MEMBER),
                request().toBuilder().serviceOfferingId(999L).build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND);
    }

    @Test
    void createThrowsSlotUnavailableWhenTheRequestedTimeIsNotInTheComputedAvailableSlots() throws GenericException {
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));
        stubAvailable(List.of(LocalTime.of(11, 0)));

        assertThatThrownBy(() -> service.create(user(1L, UserTypeEnum.FAMILY_MEMBER), request()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BOOKING_SLOT_UNAVAILABLE);
        verify(bookingRepository, never()).saveAll(any());
    }

    @Test
    void createRejectsOccurrencesOutOfTheTwoToTwelveRangeWhenRecurring() {
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));
        BookingRequest recurring = request().toBuilder().recurring(true).occurrences(1).build();

        assertThatThrownBy(() -> service.create(user(1L, UserTypeEnum.FAMILY_MEMBER), recurring))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.VALIDATION_FAILED);
    }

    @Test
    void createBuildsOneBookingPerOccurrenceSharingARecurringGroupId() throws GenericException {
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));
        stubAvailable(List.of(TIME));
        when(bookingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        BookingRequest recurring = request().toBuilder().recurring(true).occurrences(3).build();

        BookingResponse response = service.create(user(1L, UserTypeEnum.FAMILY_MEMBER), recurring);

        assertThat(response.getRecurringGroupId()).isNotNull();
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(bookingRepository).saveAll(captor.capture());
        List<Booking> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(Booking::getScheduledDate)
                .containsExactly(DATE, DATE.plusWeeks(1), DATE.plusWeeks(2));
        assertThat(saved).extracting(Booking::getRecurringGroupId).containsOnly(response.getRecurringGroupId());
        verify(availableSlotService, times(3)).getAvailableSlots(eq(20L), any(), eq(30L));
    }

    @Test
    void createFailsTheWholeRecurringSeriesWhenAnyOccurrenceConflicts() throws GenericException {
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));
        when(availableSlotService.getAvailableSlots(eq(20L), eq(DATE), eq(30L)))
                .thenReturn(AvailableSlotsResponse.builder().availableSlots(List.of(TIME)).build());
        when(availableSlotService.getAvailableSlots(eq(20L), eq(DATE.plusWeeks(1)), eq(30L)))
                .thenReturn(AvailableSlotsResponse.builder().availableSlots(List.of()).build());
        BookingRequest recurring = request().toBuilder().recurring(true).occurrences(3).build();

        assertThatThrownBy(() -> service.create(user(1L, UserTypeEnum.FAMILY_MEMBER), recurring))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BOOKING_SLOT_UNAVAILABLE);
        verify(bookingRepository, never()).saveAll(any());
    }

    // ===== confirm() / start() / complete() =====

    @Test
    void confirmMovesAPendingBookingToConfirmedWhenCalledByTheAssignedCaretakerAndNotifiesTheFamily() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        BookingResponse response = service.confirm(100L, user(200L, UserTypeEnum.CARETAKER));

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.CONFIRMED);
        verify(notificationService).send(eq("BOOKING_CONFIRMED"), any(), eq(1L));
    }

    @Test
    void confirmThrowsForbiddenWhenCalledByAnUnassignedCaretaker() {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(201L)).thenReturn(Optional.of(caretakerProfile(21L, 201L)));

        assertThatThrownBy(() -> service.confirm(100L, user(201L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void confirmAllowsAnAdminOverrideEvenWithNoCaretakerProfile() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        BookingResponse response = service.confirm(100L, user(999L, UserTypeEnum.ADMIN));

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.CONFIRMED);
        verify(caretakerProfileRepository, never()).findByUserId(any());
    }

    @Test
    void confirmThrowsInvalidTransitionWhenTheBookingIsNotPending() {
        Booking existing = booking(100L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));

        assertThatThrownBy(() -> service.confirm(100L, user(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION);
    }

    @Test
    void confirmThrowsBookingNotFoundForAnUnknownBooking() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(999L, user(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BOOKING_NOT_FOUND);
    }

    @Test
    void startMovesAConfirmedBookingToInProgressAndSendsNoNotification() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.start(100L, user(200L, UserTypeEnum.CARETAKER));

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.IN_PROGRESS);
        verify(notificationService, never()).send(anyString(), any(), any());
    }

    @Test
    void startThrowsInvalidTransitionWhenTheBookingIsNotConfirmed() {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));

        assertThatThrownBy(() -> service.start(100L, user(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION);
    }

    @Test
    void completeMovesAnInProgressBookingToCompletedAndNotifiesTheFamily() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.IN_PROGRESS);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        BookingResponse response = service.complete(100L, user(200L, UserTypeEnum.CARETAKER));

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.COMPLETED);
        verify(notificationService).send(eq("BOOKING_COMPLETED"), any(), eq(1L));
    }

    @Test
    void completeThrowsInvalidTransitionWhenTheBookingIsNotInProgress() {
        Booking existing = booking(100L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));

        assertThatThrownBy(() -> service.complete(100L, user(200L, UserTypeEnum.CARETAKER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION);
    }

    // ===== cancel() =====

    @Test
    void cancelAllowsTheFamilyPartyWhilePendingAndNotifiesTheCaretakerNotTheInitiator() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        BookingResponse response = service.cancel(100L, user(1L, UserTypeEnum.FAMILY_MEMBER), false);

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.CANCELLED);
        verify(notificationService).send(eq("BOOKING_CANCELLED"), any(), eq(200L));
        verify(notificationService, never()).send(eq("BOOKING_CANCELLED"), any(), eq(1L));
    }

    @Test
    void cancelAllowsTheAssignedCaretakerWhilePendingAndNotifiesTheFamilyNotTheInitiator() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        BookingResponse response = service.cancel(100L, user(200L, UserTypeEnum.CARETAKER), false);

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.CANCELLED);
        verify(notificationService).send(eq("BOOKING_CANCELLED"), any(), eq(1L));
        verify(notificationService, never()).send(eq("BOOKING_CANCELLED"), any(), eq(200L));
    }

    @Test
    void cancelForbidsTheCaretakerOnceTheBookingIsConfirmed() {
        // Not even stubbed to be the assigned caretaker - cancel's status==CONFIRMED
        // branch only ever checks the family party, so this caller is forbidden
        // regardless of who they are once a booking has moved past PENDING.
        Booking existing = booking(100L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(100L, user(200L, UserTypeEnum.CARETAKER), false))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void cancelStillAllowsTheFamilyPartyOnceTheBookingIsConfirmed() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        BookingResponse response = service.cancel(100L, user(1L, UserTypeEnum.FAMILY_MEMBER), false);

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.CANCELLED);
        verify(notificationService).send(eq("BOOKING_CANCELLED"), any(), eq(200L));
    }

    @Test
    void cancelAllowsAnAdminOverrideRegardlessOfStatusAndNotifiesBothParties() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.IN_PROGRESS);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        BookingResponse response = service.cancel(100L, user(999L, UserTypeEnum.ADMIN), false);

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.CANCELLED);
        verify(notificationService).send(eq("BOOKING_CANCELLED"), any(), eq(1L));
        verify(notificationService).send(eq("BOOKING_CANCELLED"), any(), eq(200L));
    }

    @Test
    void cancelThrowsInvalidTransitionForAnAlreadyCompletedBooking() {
        Booking existing = booking(100L, BookingStatusEnum.COMPLETED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(100L, user(1L, UserTypeEnum.FAMILY_MEMBER), false))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION);
    }

    @Test
    void cancelThrowsInvalidTransitionForAnAlreadyCancelledBooking() {
        Booking existing = booking(100L, BookingStatusEnum.CANCELLED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(100L, user(1L, UserTypeEnum.FAMILY_MEMBER), false))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION);
    }

    @Test
    void cancelWholeSeriesCancelsEverySiblingThatIsNotAlreadyTerminal() throws GenericException {
        Booking target = booking(100L, BookingStatusEnum.PENDING);
        target.setRecurringGroupId("group-1");
        Booking sibling1 = booking(101L, BookingStatusEnum.PENDING);
        sibling1.setRecurringGroupId("group-1");
        Booking sibling2 = booking(102L, BookingStatusEnum.COMPLETED);
        sibling2.setRecurringGroupId("group-1");
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(target));
        when(bookingRepository.findByRecurringGroupIdOrderByScheduledDateAsc("group-1"))
                .thenReturn(List.of(target, sibling1, sibling2));
        when(bookingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        service.cancel(100L, user(1L, UserTypeEnum.FAMILY_MEMBER), true);

        assertThat(target.getStatus()).isEqualTo(BookingStatusEnum.CANCELLED);
        assertThat(sibling1.getStatus()).isEqualTo(BookingStatusEnum.CANCELLED);
        assertThat(sibling2.getStatus()).isEqualTo(BookingStatusEnum.COMPLETED);
    }

    @Test
    void cancelWholeSeriesOnABookingWithNoGroupJustCancelsThatOneBooking() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        service.cancel(100L, user(1L, UserTypeEnum.FAMILY_MEMBER), true);

        verify(bookingRepository, never()).findByRecurringGroupIdOrderByScheduledDateAsc(any());
        verify(bookingRepository).save(existing);
    }

    // ===== getById() / getMyBookingsAsFamily() / getMyBookingsAsCaretaker() / getByRecurringGroupId() =====

    @Test
    void getByIdAllowsTheFamilyPartyTheAssignedCaretakerAndAnAdminButForbidsAStranger() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));

        assertThat(service.getById(100L, user(1L, UserTypeEnum.FAMILY_MEMBER))).isNotNull();

        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        assertThat(service.getById(100L, user(200L, UserTypeEnum.CARETAKER))).isNotNull();

        assertThat(service.getById(100L, user(999L, UserTypeEnum.ADMIN))).isNotNull();

        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(10L, 3L)).thenReturn(false);
        when(caretakerProfileRepository.findByUserId(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(100L, user(3L, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void getMyBookingsAsFamilyDelegatesToTheRepositoryWithTheCallersOwnId() {
        when(bookingRepository.findByFamilyUserId(eq(1L), any())).thenReturn(org.springframework.data.domain.Page.empty());

        service.getMyBookingsAsFamily(user(1L, UserTypeEnum.FAMILY_MEMBER), org.springframework.data.domain.PageRequest.of(0, 10));

        verify(bookingRepository).findByFamilyUserId(eq(1L), any());
    }

    @Test
    void getMyBookingsAsCaretakerReturnsAnEmptyPageWhenTheCallerHasNoCaretakerProfile() {
        when(caretakerProfileRepository.findByUserId(5L)).thenReturn(Optional.empty());

        var page = service.getMyBookingsAsCaretaker(user(5L, UserTypeEnum.CARETAKER), org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        verify(bookingRepository, never()).findByCaretakerId(any(), any());
    }

    @Test
    void getMyBookingsAsCaretakerDelegatesToTheRepositoryWithTheCaretakerProfileId() {
        when(caretakerProfileRepository.findByUserId(200L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(bookingRepository.findByCaretakerId(eq(20L), any())).thenReturn(org.springframework.data.domain.Page.empty());

        service.getMyBookingsAsCaretaker(user(200L, UserTypeEnum.CARETAKER), org.springframework.data.domain.PageRequest.of(0, 10));

        verify(bookingRepository).findByCaretakerId(eq(20L), any());
    }

    @Test
    void getByRecurringGroupIdThrowsNotFoundWhenNoBookingSharesThatGroupId() {
        when(bookingRepository.findByRecurringGroupIdOrderByScheduledDateAsc("missing")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getByRecurringGroupId("missing", user(1L, UserTypeEnum.FAMILY_MEMBER)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BOOKING_NOT_FOUND);
    }

    @Test
    void getByRecurringGroupIdReturnsTheWholeSeriesOrderedWhenCallerHasAccess() throws GenericException {
        Booking b1 = booking(100L, BookingStatusEnum.PENDING);
        Booking b2 = booking(101L, BookingStatusEnum.PENDING);
        when(bookingRepository.findByRecurringGroupIdOrderByScheduledDateAsc("group-1")).thenReturn(List.of(b1, b2));

        List<BookingResponse> result = service.getByRecurringGroupId("group-1", user(1L, UserTypeEnum.FAMILY_MEMBER));

        assertThat(result).hasSize(2);
    }

    // ===== getAll() / adminOverrideStatus() =====

    @Test
    void getAllDelegatesToTheRepositorysCriteriaQueryWithEveryFilter() {
        when(bookingRepository.findByCriteria(eq(1L), eq(20L), eq(BookingStatusEnum.PENDING), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(booking(100L, BookingStatusEnum.PENDING))));

        var page = service.getAll(1L, 20L, BookingStatusEnum.PENDING, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(bookingRepository).findByCriteria(eq(1L), eq(20L), eq(BookingStatusEnum.PENDING), any());
    }

    @Test
    void adminOverrideStatusSetsAnyStatusDirectlyBypassingTheLinearLifecycleGate() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        // PENDING straight to COMPLETED - confirm()/start() would both reject this.
        BookingResponse response = service.adminOverrideStatus(100L, BookingStatusEnum.COMPLETED, user(999L, UserTypeEnum.ADMIN));

        assertThat(response.getStatus()).isEqualTo(BookingStatusEnum.COMPLETED);
        verify(notificationService).send(eq("BOOKING_COMPLETED"), any(), eq(1L));
    }

    @Test
    void adminOverrideStatusToCancelledNotifiesBothParties() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(caretakerProfileRepository.findById(20L)).thenReturn(Optional.of(caretakerProfile(20L, 200L)));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        service.adminOverrideStatus(100L, BookingStatusEnum.CANCELLED, user(999L, UserTypeEnum.ADMIN));

        verify(notificationService).send(eq("BOOKING_CANCELLED"), any(), eq(1L));
        verify(notificationService).send(eq("BOOKING_CANCELLED"), any(), eq(200L));
    }

    @Test
    void adminOverrideStatusToPendingSendsNoNotification() throws GenericException {
        Booking existing = booking(100L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(elderProfileRepository.findById(10L)).thenReturn(Optional.of(elderProfile(10L, 1L)));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(offering(30L)));

        service.adminOverrideStatus(100L, BookingStatusEnum.PENDING, user(999L, UserTypeEnum.ADMIN));

        verify(notificationService, never()).send(anyString(), any(), any());
    }

    @Test
    void adminOverrideStatusThrowsBookingNotFoundForAnUnknownBooking() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adminOverrideStatus(999L, BookingStatusEnum.CANCELLED, user(999L, UserTypeEnum.ADMIN)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BOOKING_NOT_FOUND);
    }
}
