package com.elderhive.repositories;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FileAsset;
import com.elderhive.entities.Review;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Runs against the real local Postgres - confirms V30's migration matches the entity
 * mapping, every FK's CASCADE/SET-NULL behavior, the unique-booking constraint, and the
 * AVG() query CaretakerProfileServiceImpl#recomputeRatingAverage relies on.
 */
@SpringBootTest
@Transactional
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

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
    private BookingRepository bookingRepository;

    @Autowired
    private FileAssetRepository fileAssetRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private CaretakerProfile persistCaretakerProfile(Long userId) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).ratingAverage(0.0).build());
    }

    private Booking persistCompletedBooking(String namePrefix, Long familyUserId, Long caretakerId) {
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUserId).fullName("Robert").build());
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name(namePrefix + "-Temple Visit").status(ServiceCategoryStatusEnum.ACTIVE).build());
        ServiceOffering offering = serviceOfferingRepository.save(ServiceOffering.builder()
                .name(namePrefix + "-Weekly Temple Visit").categoryId(category.getId()).status(ServiceOfferingStatusEnum.ACTIVE).build());
        return bookingRepository.save(Booking.builder()
                .familyUserId(familyUserId).elderProfileId(elder.getId()).caretakerId(caretakerId)
                .serviceOfferingId(offering.getId()).status(BookingStatusEnum.COMPLETED).build());
    }

    private Review.ReviewBuilder newReview(Long bookingId, Long reviewerUserId, Long caretakerId, Integer overallRating) {
        return Review.builder().bookingId(bookingId).reviewerUserId(reviewerUserId).caretakerId(caretakerId).overallRating(overallRating);
    }

    @Test
    void persistingAndReloadingAReviewPopulatesAuditFieldsAndEveryColumn() {
        User family = persistUser("review-roundtrip-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-roundtrip-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Booking booking = persistCompletedBooking("review-roundtrip", family.getId(), caretaker.getId());
        FileAsset photo = fileAssetRepository.save(FileAsset.builder().storageKey("reviews/photo.jpg").build());

        Review saved = reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 5)
                .comment("Wonderful care").punctualityRating(5).careQualityRating(4).communicationRating(5)
                .photoFileAssetId(photo.getId()).build());
        entityManager.flush();
        entityManager.clear();

        Review reloaded = reviewRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBookingId()).isEqualTo(booking.getId());
        assertThat(reloaded.getReviewerUserId()).isEqualTo(family.getId());
        assertThat(reloaded.getCaretakerId()).isEqualTo(caretaker.getId());
        assertThat(reloaded.getOverallRating()).isEqualTo(5);
        assertThat(reloaded.getComment()).isEqualTo("Wonderful care");
        assertThat(reloaded.getPunctualityRating()).isEqualTo(5);
        assertThat(reloaded.getCareQualityRating()).isEqualTo(4);
        assertThat(reloaded.getCommunicationRating()).isEqualTo(5);
        assertThat(reloaded.getPhotoFileAssetId()).isEqualTo(photo.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void aSecondReviewForTheSameBookingViolatesTheUniqueConstraint() {
        User family = persistUser("review-dup-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-dup-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Booking booking = persistCompletedBooking("review-dup", family.getId(), caretaker.getId());
        reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 5).build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 3).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheBookingCascadesToItsReview() {
        User family = persistUser("review-cascade-booking-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-cascade-booking-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Booking booking = persistCompletedBooking("review-cascade-booking", family.getId(), caretaker.getId());
        Review saved = reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 5).build());
        entityManager.flush();

        bookingRepository.delete(booking);
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheCaretakerProfileCascadesToItsReviews() {
        User family = persistUser("review-cascade-caretaker-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-cascade-caretaker-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Booking booking = persistCompletedBooking("review-cascade-caretaker", family.getId(), caretaker.getId());
        Review saved = reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 5).build());
        entityManager.flush();

        caretakerProfileRepository.delete(caretaker);
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheReviewingUserCascadesToTheirReview() {
        User family = persistUser("review-cascade-user-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-cascade-user-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Booking booking = persistCompletedBooking("review-cascade-user", family.getId(), caretaker.getId());
        Review saved = reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 5).build());
        entityManager.flush();

        userRepository.delete(family);
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingThePhotoFileAssetJustClearsTheReferenceRatherThanDeletingTheReview() {
        User family = persistUser("review-setnull-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-setnull-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Booking booking = persistCompletedBooking("review-setnull", family.getId(), caretaker.getId());
        FileAsset photo = fileAssetRepository.save(FileAsset.builder().storageKey("reviews/to-delete.jpg").build());
        Review saved = reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 5)
                .photoFileAssetId(photo.getId()).build());
        entityManager.flush();

        fileAssetRepository.delete(photo);
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewRepository.findById(saved.getId()).orElseThrow().getPhotoFileAssetId()).isNull();
    }

    @Test
    void findByCaretakerIdPagesOnlyThatCaretakersReviews() {
        User familyA = persistUser("review-bycaretaker-familyA@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUserA = persistUser("review-bycaretaker-caretakerA@example.com", UserTypeEnum.CARETAKER);
        User caretakerUserB = persistUser("review-bycaretaker-caretakerB@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretakerA = persistCaretakerProfile(caretakerUserA.getId());
        CaretakerProfile caretakerB = persistCaretakerProfile(caretakerUserB.getId());
        Booking bookingA = persistCompletedBooking("review-bycaretaker-a", familyA.getId(), caretakerA.getId());
        Booking bookingB = persistCompletedBooking("review-bycaretaker-b", familyA.getId(), caretakerB.getId());
        reviewRepository.save(newReview(bookingA.getId(), familyA.getId(), caretakerA.getId(), 5).build());
        reviewRepository.save(newReview(bookingB.getId(), familyA.getId(), caretakerB.getId(), 3).build());
        entityManager.flush();

        assertThat(reviewRepository.findByCaretakerId(caretakerA.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Review::getOverallRating).containsExactly(5);
    }

    @Test
    void findAverageOverallRatingByCaretakerIdAveragesAcrossAllTheirReviewsAndIsNullForNone() {
        User familyA = persistUser("review-avg-familyA@example.com", UserTypeEnum.FAMILY_MEMBER);
        User familyB = persistUser("review-avg-familyB@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-avg-caretaker@example.com", UserTypeEnum.CARETAKER);
        User unreviewedCaretakerUser = persistUser("review-avg-unreviewed-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        CaretakerProfile unreviewedCaretaker = persistCaretakerProfile(unreviewedCaretakerUser.getId());
        Booking bookingA = persistCompletedBooking("review-avg-a", familyA.getId(), caretaker.getId());
        Booking bookingB = persistCompletedBooking("review-avg-b", familyB.getId(), caretaker.getId());
        reviewRepository.save(newReview(bookingA.getId(), familyA.getId(), caretaker.getId(), 5).build());
        reviewRepository.save(newReview(bookingB.getId(), familyB.getId(), caretaker.getId(), 3).build());
        entityManager.flush();

        assertThat(reviewRepository.findAverageOverallRatingByCaretakerId(caretaker.getId())).isCloseTo(4.0, within(0.0001));
        assertThat(reviewRepository.findAverageOverallRatingByCaretakerId(unreviewedCaretaker.getId())).isNull();
    }

    @Test
    void existsByBookingIdAndFindByBookingIdReflectWhetherABookingHasBeenReviewed() {
        User family = persistUser("review-exists-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("review-exists-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Booking booking = persistCompletedBooking("review-exists", family.getId(), caretaker.getId());

        assertThat(reviewRepository.existsByBookingId(booking.getId())).isFalse();
        assertThat(reviewRepository.findByBookingId(booking.getId())).isEmpty();

        reviewRepository.save(newReview(booking.getId(), family.getId(), caretaker.getId(), 4).build());
        entityManager.flush();

        assertThat(reviewRepository.existsByBookingId(booking.getId())).isTrue();
        assertThat(reviewRepository.findByBookingId(booking.getId())).isPresent();
    }
}
