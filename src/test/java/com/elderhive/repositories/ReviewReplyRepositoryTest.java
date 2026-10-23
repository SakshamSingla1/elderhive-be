package com.elderhive.repositories;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.Review;
import com.elderhive.entities.ReviewReply;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres - confirms V31's migration matches the entity
 * mapping, both FKs' CASCADE behavior, and the unique-review constraint.
 */
@SpringBootTest
@Transactional
class ReviewReplyRepositoryTest {

    @Autowired
    private ReviewReplyRepository reviewReplyRepository;

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
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private CaretakerProfile persistCaretakerProfile(Long userId) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).ratingAverage(0.0).build());
    }

    private Review persistReview(String namePrefix, Long familyUserId, Long caretakerId) {
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUserId).fullName("Robert").build());
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name(namePrefix + "-Temple Visit").status(ServiceCategoryStatusEnum.ACTIVE).build());
        ServiceOffering offering = serviceOfferingRepository.save(ServiceOffering.builder()
                .name(namePrefix + "-Weekly Temple Visit").categoryId(category.getId()).status(ServiceOfferingStatusEnum.ACTIVE).build());
        Booking booking = bookingRepository.save(Booking.builder()
                .familyUserId(familyUserId).elderProfileId(elder.getId()).caretakerId(caretakerId)
                .serviceOfferingId(offering.getId()).status(BookingStatusEnum.COMPLETED).build());
        return reviewRepository.save(Review.builder()
                .bookingId(booking.getId()).reviewerUserId(familyUserId).caretakerId(caretakerId).overallRating(5).build());
    }

    @Test
    void persistingAndReloadingAReplyPopulatesAuditFieldsAndEveryColumn() {
        User family = persistUser("reply-roundtrip-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("reply-roundtrip-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Review review = persistReview("reply-roundtrip", family.getId(), caretaker.getId());

        ReviewReply saved = reviewReplyRepository.save(ReviewReply.builder()
                .reviewId(review.getId()).caretakerId(caretaker.getId()).content("Thank you so much!").build());
        entityManager.flush();
        entityManager.clear();

        ReviewReply reloaded = reviewReplyRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReviewId()).isEqualTo(review.getId());
        assertThat(reloaded.getCaretakerId()).isEqualTo(caretaker.getId());
        assertThat(reloaded.getContent()).isEqualTo("Thank you so much!");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void aSecondReplyForTheSameReviewViolatesTheUniqueConstraint() {
        User family = persistUser("reply-dup-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("reply-dup-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Review review = persistReview("reply-dup", family.getId(), caretaker.getId());
        reviewReplyRepository.save(ReviewReply.builder().reviewId(review.getId()).caretakerId(caretaker.getId()).content("First").build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            reviewReplyRepository.save(ReviewReply.builder().reviewId(review.getId()).caretakerId(caretaker.getId()).content("Second").build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheReviewCascadesToItsReply() {
        User family = persistUser("reply-cascade-review-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("reply-cascade-review-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Review review = persistReview("reply-cascade-review", family.getId(), caretaker.getId());
        ReviewReply saved = reviewReplyRepository.save(ReviewReply.builder()
                .reviewId(review.getId()).caretakerId(caretaker.getId()).content("Thanks!").build());
        entityManager.flush();

        reviewRepository.delete(review);
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewReplyRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheCaretakerProfileCascadesToTheirReply() {
        User family = persistUser("reply-cascade-caretaker-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("reply-cascade-caretaker-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Review review = persistReview("reply-cascade-caretaker", family.getId(), caretaker.getId());
        ReviewReply saved = reviewReplyRepository.save(ReviewReply.builder()
                .reviewId(review.getId()).caretakerId(caretaker.getId()).content("Thanks!").build());
        entityManager.flush();

        caretakerProfileRepository.delete(caretaker);
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewReplyRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findByReviewIdReturnsEmptyUntilAReplyExists() {
        User family = persistUser("reply-find-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretakerUser = persistUser("reply-find-caretaker@example.com", UserTypeEnum.CARETAKER);
        CaretakerProfile caretaker = persistCaretakerProfile(caretakerUser.getId());
        Review review = persistReview("reply-find", family.getId(), caretaker.getId());

        assertThat(reviewReplyRepository.findByReviewId(review.getId())).isEmpty();

        reviewReplyRepository.save(ReviewReply.builder().reviewId(review.getId()).caretakerId(caretaker.getId()).content("Thanks!").build());
        entityManager.flush();

        assertThat(reviewReplyRepository.findByReviewId(review.getId())).isPresent();
    }
}
