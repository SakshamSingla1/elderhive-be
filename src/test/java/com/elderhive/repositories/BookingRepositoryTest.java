package com.elderhive.repositories;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.entities.User;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V28's migration matches the entity
 * mapping, every FK's CASCADE/RESTRICT behavior, the finder/exists methods the future
 * BookingService will rely on, and JPA auditing.
 */
@SpringBootTest
@Transactional
class BookingRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ElderProfileRepository elderProfileRepository;

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private ElderProfile persistElderProfile(Long familyUserId) {
        return elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUserId).fullName("Robert").build());
    }

    private CaretakerProfile persistCaretakerProfile(Long userId) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
    }

    private ServiceOffering persistServiceOffering(String namePrefix) {
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name(namePrefix + "-Temple Visit").status(ServiceCategoryStatusEnum.ACTIVE).build());
        return serviceOfferingRepository.save(ServiceOffering.builder()
                .name(namePrefix + "-Weekly Temple Visit").categoryId(category.getId()).status(ServiceOfferingStatusEnum.ACTIVE).build());
    }

    private Booking.BookingBuilder newBooking(Long familyUserId, Long elderProfileId, Long caretakerId, Long serviceOfferingId) {
        return Booking.builder()
                .familyUserId(familyUserId).elderProfileId(elderProfileId)
                .caretakerId(caretakerId).serviceOfferingId(serviceOfferingId)
                .status(BookingStatusEnum.PENDING);
    }

    @Test
    void persistingAndReloadingABookingPopulatesAuditFieldsAndEveryColumn() {
        User family = persistUser("booking-roundtrip-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-roundtrip-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-roundtrip");
        String recurringGroupId = UUID.randomUUID().toString();

        Booking saved = bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId())
                .scheduledDate(LocalDate.of(2026, 1, 15)).scheduledTime(LocalTime.of(10, 0))
                .cost(new BigDecimal("40.00")).notes("Bring wheelchair").recurringGroupId(recurringGroupId)
                .build());
        entityManager.flush();
        entityManager.clear();

        Booking reloaded = bookingRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFamilyUserId()).isEqualTo(family.getId());
        assertThat(reloaded.getElderProfileId()).isEqualTo(elder.getId());
        assertThat(reloaded.getCaretakerId()).isEqualTo(caretaker.getId());
        assertThat(reloaded.getServiceOfferingId()).isEqualTo(offering.getId());
        assertThat(reloaded.getScheduledDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(reloaded.getScheduledTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatusEnum.PENDING);
        assertThat(reloaded.getCost()).isEqualByComparingTo("40.00");
        assertThat(reloaded.getNotes()).isEqualTo("Bring wheelchair");
        assertThat(reloaded.getRecurringGroupId()).isEqualTo(recurringGroupId);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void recurringGroupIdIsNullForAOneOffBooking() {
        User family = persistUser("booking-oneoff-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-oneoff-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-oneoff");

        Booking saved = bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId()).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(bookingRepository.findById(saved.getId()).orElseThrow().getRecurringGroupId()).isNull();
    }

    @Test
    void deletingTheFamilyUserCascadesToTheirBookings() {
        User family = persistUser("booking-cascade-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-cascade-family-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-cascade-family");

        Booking saved = bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId()).build());
        entityManager.flush();

        userRepository.delete(family);
        entityManager.flush();
        entityManager.clear();

        assertThat(bookingRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheElderProfileCascadesToItsBookings() {
        User family = persistUser("booking-cascade-elder@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-cascade-elder-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-cascade-elder");

        Booking saved = bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId()).build());
        entityManager.flush();

        elderProfileRepository.delete(elder);
        entityManager.flush();
        entityManager.clear();

        assertThat(bookingRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheCaretakerProfileCascadesToItsBookings() {
        User family = persistUser("booking-cascade-caretaker@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-cascade-caretaker-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-cascade-caretaker");

        Booking saved = bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId()).build());
        entityManager.flush();

        caretakerProfileRepository.deleteById(caretaker.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(bookingRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingAServiceOfferingThatStillHasBookingsIsRejected() {
        User family = persistUser("booking-restrict-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-restrict-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-restrict");

        bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId()).build());
        entityManager.flush();

        // Same RESTRICT-FK nuance as ServiceOfferingRepositoryTest's own
        // deletingACategoryThatStillHasOfferingsIsRejected: the violating SQL only runs at
        // this explicit flush() (outside the repository-method-wrapped exception-
        // translation aspect), so the raw Hibernate exception surfaces, not Spring's
        // translated DataIntegrityViolationException.
        assertThatThrownBy(() -> {
            serviceOfferingRepository.delete(offering);
            entityManager.flush();
        }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void findByFamilyUserIdAndFindByCaretakerIdPageTheirOwnBookingsOnly() {
        User family = persistUser("booking-byuser-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User otherFamily = persistUser("booking-byuser-other-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-byuser-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        ElderProfile otherElder = persistElderProfile(otherFamily.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-byuser");

        bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId()).build());
        bookingRepository.save(newBooking(otherFamily.getId(), otherElder.getId(), caretaker.getId(), offering.getId()).build());
        entityManager.flush();

        assertThat(bookingRepository.findByFamilyUserId(family.getId(), PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(bookingRepository.findByCaretakerId(caretaker.getId(), PageRequest.of(0, 10)).getContent()).hasSize(2);
    }

    @Test
    void existsByCaretakerIdAndScheduledDateAndScheduledTimeAndStatusInDetectsAConflictingSlotButIgnoresTerminalBookings() {
        User family = persistUser("booking-slot-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-slot-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-slot");
        LocalDate date = LocalDate.of(2026, 2, 1);
        LocalTime time = LocalTime.of(14, 0);
        List<BookingStatusEnum> nonTerminal = List.of(BookingStatusEnum.PENDING, BookingStatusEnum.CONFIRMED, BookingStatusEnum.IN_PROGRESS);

        bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId())
                .scheduledDate(date).scheduledTime(time).status(BookingStatusEnum.CONFIRMED).build());
        entityManager.flush();

        assertThat(bookingRepository.existsByCaretakerIdAndScheduledDateAndScheduledTimeAndStatusIn(
                caretaker.getId(), date, time, nonTerminal)).isTrue();
        assertThat(bookingRepository.existsByCaretakerIdAndScheduledDateAndScheduledTimeAndStatusIn(
                caretaker.getId(), date, time.plusHours(1), nonTerminal)).isFalse();
        assertThat(bookingRepository.existsByCaretakerIdAndScheduledDateAndScheduledTimeAndStatusIn(
                caretaker.getId(), date, time, List.of(BookingStatusEnum.CANCELLED))).isFalse();
    }

    @Test
    void findByRecurringGroupIdOrdersTheSeriesByScheduledDate() {
        User family = persistUser("booking-recurring-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-recurring-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        ServiceOffering offering = persistServiceOffering("booking-recurring");
        String groupId = UUID.randomUUID().toString();
        String otherGroupId = UUID.randomUUID().toString();

        bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId())
                .scheduledDate(LocalDate.of(2026, 3, 15)).recurringGroupId(groupId).build());
        bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId())
                .scheduledDate(LocalDate.of(2026, 3, 1)).recurringGroupId(groupId).build());
        bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), offering.getId())
                .scheduledDate(LocalDate.of(2026, 3, 8)).recurringGroupId(otherGroupId).build());
        entityManager.flush();

        List<Booking> series = bookingRepository.findByRecurringGroupIdOrderByScheduledDateAsc(groupId);
        assertThat(series).extracting(Booking::getScheduledDate)
                .containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15));
    }

    @Test
    void findByCriteriaFiltersByEveryCombinationOfFamilyUserIdCaretakerIdAndStatus() {
        User familyA = persistUser("booking-criteria-familyA@example.com", UserTypeEnum.FAMILY_MEMBER);
        User familyB = persistUser("booking-criteria-familyB@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUserA = persistUser("booking-criteria-caretakerA@example.com", UserTypeEnum.CARETAKER);
        User caretakerUserB = persistUser("booking-criteria-caretakerB@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elderA = persistElderProfile(familyA.getId());
        ElderProfile elderB = persistElderProfile(familyB.getId());
        CaretakerProfile caretakerA = persistCaretakerProfile(caretakerUserA.getId());
        CaretakerProfile caretakerB = persistCaretakerProfile(caretakerUserB.getId());
        ServiceOffering offering = persistServiceOffering("booking-criteria");

        bookingRepository.save(newBooking(familyA.getId(), elderA.getId(), caretakerA.getId(), offering.getId())
                .status(BookingStatusEnum.PENDING).build());
        bookingRepository.save(newBooking(familyB.getId(), elderB.getId(), caretakerB.getId(), offering.getId())
                .status(BookingStatusEnum.CONFIRMED).build());
        entityManager.flush();

        assertThat(bookingRepository.findByCriteria(familyA.getId(), null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Booking::getFamilyUserId).containsExactly(familyA.getId());
        assertThat(bookingRepository.findByCriteria(null, caretakerB.getId(), null, PageRequest.of(0, 10)).getContent())
                .extracting(Booking::getCaretakerId).containsExactly(caretakerB.getId());
        assertThat(bookingRepository.findByCriteria(null, null, BookingStatusEnum.PENDING, PageRequest.of(0, 10)).getContent())
                .extracting(Booking::getFamilyUserId).containsExactly(familyA.getId());
        assertThat(bookingRepository.findByCriteria(null, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Booking::getFamilyUserId).containsExactlyInAnyOrder(familyA.getId(), familyB.getId());
    }

    @Test
    void savingABookingWithAnUnknownServiceOfferingFailsTheForeignKey() {
        User family = persistUser("booking-badoffering-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("booking-badoffering-caretaker@example.com", UserTypeEnum.CARETAKER);
        ElderProfile elder = persistElderProfile(family.getId());
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());

        assertThatThrownBy(() -> {
            bookingRepository.save(newBooking(family.getId(), elder.getId(), caretaker.getId(), 999999999L).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
