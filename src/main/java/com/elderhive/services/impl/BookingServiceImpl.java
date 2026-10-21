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
import com.elderhive.services.BookingService;
import com.elderhive.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mirrors ElderSphere's BookingServiceImpl for create()'s core shape (slot
 * re-validation, recurring-series all-or-nothing creation, cost from
 * ServiceOffering.basePrice) but adds the rich per-transition ownership/permission
 * checking this prompt asks for - ElderSphere's own updateStatus() has none at all. Every
 * ADMIN_TIER bypass below matches this project's existing precedent everywhere else
 * (ElderProfileServiceImpl, CaretakerAvailabilityServiceImpl, ...).
 */
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);
    private static final List<BookingStatusEnum> CANCELLABLE_STATUSES =
            List.of(BookingStatusEnum.PENDING, BookingStatusEnum.CONFIRMED, BookingStatusEnum.IN_PROGRESS);
    private static final int MIN_RECURRING_OCCURRENCES = 2;
    private static final int MAX_RECURRING_OCCURRENCES = 12;

    private final BookingRepository bookingRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final FamilyElderLinkRepository familyElderLinkRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final UserRepository userRepository;
    private final AvailableSlotService availableSlotService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public BookingResponse create(User caller, BookingRequest request) throws GenericException {
        ElderProfile elderProfile = elderProfileRepository.findById(request.getElderProfileId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND, "Elder profile not found"));
        assertFamilyPartyOrAdmin(elderProfile, caller);
        CaretakerProfile caretaker = caretakerProfileRepository.findById(request.getCaretakerId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker not found"));
        ServiceOffering offering = serviceOfferingRepository.findById(request.getServiceOfferingId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND, "Service offering not found"));

        int occurrences = 1;
        String recurringGroupId = null;
        if (request.isRecurring()) {
            Integer requested = request.getOccurrences();
            if (requested == null || requested < MIN_RECURRING_OCCURRENCES || requested > MAX_RECURRING_OCCURRENCES) {
                throw new GenericException(ExceptionCodeEnum.VALIDATION_FAILED,
                        "occurrences must be between " + MIN_RECURRING_OCCURRENCES + " and " + MAX_RECURRING_OCCURRENCES
                                + " when recurring is true");
            }
            occurrences = requested;
            recurringGroupId = UUID.randomUUID().toString();
        }

        // Validate every occurrence's slot up front - all-or-nothing. Creating 7 of 8
        // weekly occurrences and leaving the caller to notice and fix the 8th themselves
        // would be a worse outcome than simply rejecting the whole request.
        List<Booking> toSave = new ArrayList<>();
        for (int i = 0; i < occurrences; i++) {
            LocalDate occurrenceDate = request.getScheduledDate().plusWeeks(i);
            assertSlotAvailable(request.getCaretakerId(), occurrenceDate, request.getServiceOfferingId(), request.getScheduledTime());
            toSave.add(Booking.builder()
                    .familyUserId(caller.getId())
                    .elderProfileId(elderProfile.getId())
                    .caretakerId(caretaker.getId())
                    .serviceOfferingId(offering.getId())
                    .scheduledDate(occurrenceDate)
                    .scheduledTime(request.getScheduledTime())
                    .status(BookingStatusEnum.PENDING)
                    .cost(offering.getBasePrice())
                    .notes(request.getNotes())
                    .recurringGroupId(recurringGroupId)
                    .build());
        }

        List<Booking> saved = bookingRepository.saveAll(toSave);
        return toResponse(saved.get(0), elderProfile, caretaker, offering);
    }

    private void assertSlotAvailable(Long caretakerId, LocalDate date, Long serviceOfferingId, LocalTime requestedTime)
            throws GenericException {
        AvailableSlotsResponse available = availableSlotService.getAvailableSlots(caretakerId, date, serviceOfferingId);
        if (!available.getAvailableSlots().contains(requestedTime)) {
            throw new GenericException(ExceptionCodeEnum.BOOKING_SLOT_UNAVAILABLE,
                    "This caretaker is not available at " + requestedTime + " on " + date);
        }
    }

    @Override
    @Transactional
    public BookingResponse confirm(Long id, User caller) throws GenericException {
        Booking booking = findOrThrow(id);
        assertAssignedCaretakerOrAdmin(booking, caller);
        assertTransition(booking, BookingStatusEnum.PENDING, BookingStatusEnum.CONFIRMED);
        booking.setStatus(BookingStatusEnum.CONFIRMED);
        Booking saved = bookingRepository.save(booking);
        // The caretaker is always the actor here (assertAssignedCaretakerOrAdmin), so the
        // family party is the one who doesn't already know.
        notify("BOOKING_CONFIRMED", saved, saved.getFamilyUserId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse start(Long id, User caller) throws GenericException {
        Booking booking = findOrThrow(id);
        assertAssignedCaretakerOrAdmin(booking, caller);
        assertTransition(booking, BookingStatusEnum.CONFIRMED, BookingStatusEnum.IN_PROGRESS);
        booking.setStatus(BookingStatusEnum.IN_PROGRESS);
        // No notification template is seeded for "started" (only CONFIRMED/CANCELLED/
        // COMPLETED exist, see V16) - nothing to send here.
        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse complete(Long id, User caller) throws GenericException {
        Booking booking = findOrThrow(id);
        assertAssignedCaretakerOrAdmin(booking, caller);
        assertTransition(booking, BookingStatusEnum.IN_PROGRESS, BookingStatusEnum.COMPLETED);
        booking.setStatus(BookingStatusEnum.COMPLETED);
        Booking saved = bookingRepository.save(booking);
        // Notifies the family member - this is also what unlocks leaving a review once
        // Phase 12 lands (a completed booking is the review gate).
        notify("BOOKING_COMPLETED", saved, saved.getFamilyUserId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long id, User caller, boolean cancelWholeSeries) throws GenericException {
        Booking booking = findOrThrow(id);
        assertCanCancel(booking, caller);
        if (!CANCELLABLE_STATUSES.contains(booking.getStatus())) {
            throw new GenericException(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION,
                    "Cannot cancel a booking that is already " + booking.getStatus());
        }

        if (cancelWholeSeries && booking.getRecurringGroupId() != null) {
            List<Booking> series = bookingRepository.findByRecurringGroupIdOrderByScheduledDateAsc(booking.getRecurringGroupId());
            series.stream().filter(b -> CANCELLABLE_STATUSES.contains(b.getStatus())).forEach(b -> b.setStatus(BookingStatusEnum.CANCELLED));
            bookingRepository.saveAll(series);
        } else {
            booking.setStatus(BookingStatusEnum.CANCELLED);
            bookingRepository.save(booking);
        }

        Booking reloaded = findOrThrow(id);
        notifyCancellation(reloaded, caller);
        return toResponse(reloaded);
    }

    @Override
    public BookingResponse getById(Long id, User caller) throws GenericException {
        Booking booking = findOrThrow(id);
        assertCanView(booking, caller);
        return toResponse(booking);
    }

    @Override
    public Page<BookingResponse> getMyBookingsAsFamily(User caller, Pageable pageable) {
        return bookingRepository.findByFamilyUserId(caller.getId(), pageable).map(this::toResponse);
    }

    @Override
    public Page<BookingResponse> getMyBookingsAsCaretaker(User caller, Pageable pageable) {
        return caretakerProfileRepository.findByUserId(caller.getId())
                .map(profile -> bookingRepository.findByCaretakerId(profile.getId(), pageable).map(this::toResponse))
                .orElseGet(() -> Page.empty(pageable));
    }

    @Override
    public List<BookingResponse> getByRecurringGroupId(String recurringGroupId, User caller) throws GenericException {
        List<Booking> series = bookingRepository.findByRecurringGroupIdOrderByScheduledDateAsc(recurringGroupId);
        if (series.isEmpty()) {
            throw new GenericException(ExceptionCodeEnum.BOOKING_NOT_FOUND, "No recurring booking series found for this ID");
        }
        assertCanView(series.get(0), caller);
        return series.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private Booking findOrThrow(Long id) throws GenericException {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.BOOKING_NOT_FOUND, "Booking not found"));
    }

    private void assertTransition(Booking booking, BookingStatusEnum expectedCurrent, BookingStatusEnum next) throws GenericException {
        if (booking.getStatus() != expectedCurrent) {
            throw new GenericException(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION,
                    "Cannot move this booking to " + next + " - it must be " + expectedCurrent + " first, but it is " + booking.getStatus());
        }
    }

    /** The booking's family party (the user who made it, or anyone co-managing its elder
     *  profile via FamilyElderLink) or ADMIN/SUPER_ADMIN. */
    private boolean isFamilyPartyOrAdmin(Booking booking, User caller) {
        if (ADMIN_TIER.contains(caller.getUserType())) {
            return true;
        }
        if (Objects.equals(booking.getFamilyUserId(), caller.getId())) {
            return true;
        }
        return familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(booking.getElderProfileId(), caller.getId());
    }

    private boolean isAssignedCaretaker(Booking booking, User caller) {
        return caretakerProfileRepository.findByUserId(caller.getId())
                .map(profile -> Objects.equals(profile.getId(), booking.getCaretakerId()))
                .orElse(false);
    }

    private void assertCanView(Booking booking, User caller) throws GenericException {
        if (isFamilyPartyOrAdmin(booking, caller) || isAssignedCaretaker(booking, caller)) {
            return;
        }
        throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to this booking");
    }

    private void assertAssignedCaretakerOrAdmin(Booking booking, User caller) throws GenericException {
        if (ADMIN_TIER.contains(caller.getUserType()) || isAssignedCaretaker(booking, caller)) {
            return;
        }
        throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "Only the assigned caretaker may do this");
    }

    /** While PENDING, either party may cancel (nothing is committed yet); once CONFIRMED
     *  or IN_PROGRESS, only the family party may - a caretaker backing out of a
     *  commitment they already confirmed needs an admin's involvement, not a unilateral
     *  self-service cancel. ADMIN/SUPER_ADMIN may always cancel regardless of status. */
    private void assertCanCancel(Booking booking, User caller) throws GenericException {
        if (ADMIN_TIER.contains(caller.getUserType())) {
            return;
        }
        boolean allowed = booking.getStatus() == BookingStatusEnum.PENDING
                ? (isFamilyPartyOrAdmin(booking, caller) || isAssignedCaretaker(booking, caller))
                : isFamilyPartyOrAdmin(booking, caller);
        if (!allowed) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to cancel this booking");
        }
    }

    private void assertFamilyPartyOrAdmin(ElderProfile elderProfile, User caller) throws GenericException {
        if (ADMIN_TIER.contains(caller.getUserType())) {
            return;
        }
        boolean isOwner = Objects.equals(elderProfile.getFamilyUserId(), caller.getId());
        boolean isCoManaging = familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(elderProfile.getId(), caller.getId());
        if (!isOwner && !isCoManaging) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You do not have access to book for this elder profile");
        }
    }

    /** BOOKING_CANCELLED notifies whichever party didn't initiate the cancellation - the
     *  one who might not already know. A caretaker-initiated cancel notifies the family;
     *  a family-initiated one (the original owner or any co-manager - "family" is treated
     *  as one party, not per-individual) notifies the caretaker. An ADMIN override
     *  initiated as neither party, so both get notified. */
    private void notifyCancellation(Booking booking, User caller) throws GenericException {
        Long caretakerUserId = caretakerProfileRepository.findById(booking.getCaretakerId())
                .map(CaretakerProfile::getUserId).orElse(null);

        if (ADMIN_TIER.contains(caller.getUserType())) {
            notify("BOOKING_CANCELLED", booking, booking.getFamilyUserId());
            if (caretakerUserId != null) {
                notify("BOOKING_CANCELLED", booking, caretakerUserId);
            }
        } else if (isAssignedCaretaker(booking, caller)) {
            notify("BOOKING_CANCELLED", booking, booking.getFamilyUserId());
        } else if (caretakerUserId != null) {
            notify("BOOKING_CANCELLED", booking, caretakerUserId);
        }
    }

    private void notify(String templateKey, Booking booking, Long targetUserId) throws GenericException {
        Map<String, String> variables = new HashMap<>();
        variables.put("elderName", elderProfileRepository.findById(booking.getElderProfileId()).map(ElderProfile::getFullName).orElse("your elder"));
        variables.put("serviceName", serviceOfferingRepository.findById(booking.getServiceOfferingId()).map(ServiceOffering::getName).orElse("your service"));
        variables.put("bookingDate", String.valueOf(booking.getScheduledDate()));
        notificationService.send(templateKey, variables, targetUserId);
    }

    private BookingResponse toResponse(Booking booking) {
        ElderProfile elderProfile = elderProfileRepository.findById(booking.getElderProfileId()).orElse(null);
        CaretakerProfile caretaker = caretakerProfileRepository.findById(booking.getCaretakerId()).orElse(null);
        ServiceOffering offering = serviceOfferingRepository.findById(booking.getServiceOfferingId()).orElse(null);
        return toResponse(booking, elderProfile, caretaker, offering);
    }

    private BookingResponse toResponse(Booking booking, ElderProfile elderProfile, CaretakerProfile caretaker, ServiceOffering offering) {
        String caretakerName = caretaker != null
                ? userRepository.findById(caretaker.getUserId()).map(User::getFullName).orElse(null)
                : null;
        return BookingResponse.builder()
                .id(booking.getId())
                .familyUserId(booking.getFamilyUserId())
                .elderProfileId(booking.getElderProfileId())
                .elderName(elderProfile != null ? elderProfile.getFullName() : null)
                .caretakerId(booking.getCaretakerId())
                .caretakerName(caretakerName)
                .serviceOfferingId(booking.getServiceOfferingId())
                .serviceName(offering != null ? offering.getName() : null)
                .scheduledDate(booking.getScheduledDate())
                .scheduledTime(booking.getScheduledTime())
                .status(booking.getStatus())
                .cost(booking.getCost())
                .notes(booking.getNotes())
                .recurringGroupId(booking.getRecurringGroupId())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
