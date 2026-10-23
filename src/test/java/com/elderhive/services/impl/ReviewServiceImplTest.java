package com.elderhive.services.impl;

import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.dtos.Review.ReviewReplyRequest;
import com.elderhive.dtos.Review.ReviewReplyResponse;
import com.elderhive.dtos.Review.ReviewRequest;
import com.elderhive.dtos.Review.ReviewResponse;
import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.Review;
import com.elderhive.entities.ReviewReply;
import com.elderhive.entities.User;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.ReviewReplyRepository;
import com.elderhive.repositories.ReviewRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.CaretakerProfileService;
import com.elderhive.services.FileAssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReplyRepository reviewReplyRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;

    @Mock
    private FamilyElderLinkRepository familyElderLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileAssetService fileAssetService;

    @Mock
    private CaretakerProfileService caretakerProfileService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private Booking booking(Long id, Long familyUserId, Long elderProfileId, Long caretakerId, BookingStatusEnum status) {
        return Booking.builder().id(id).familyUserId(familyUserId).elderProfileId(elderProfileId)
                .caretakerId(caretakerId).serviceOfferingId(1L).status(status).build();
    }

    private ReviewRequest request(Long bookingId, Integer overallRating) {
        ReviewRequest request = new ReviewRequest();
        request.setBookingId(bookingId);
        request.setOverallRating(overallRating);
        request.setComment("Great care");
        return request;
    }

    @Test
    void theBookingsOwningFamilyMemberCanReviewACompletedBookingAndSubRatingsDefaultToOverallRating() throws GenericException {
        User familyMember = user(1L, UserTypeEnum.FAMILY_MEMBER);
        Booking booking = booking(10L, 1L, 20L, 30L, BookingStatusEnum.COMPLETED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(10L)).thenReturn(false);
        when(reviewRepository.save(any())).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        ReviewResponse response = reviewService.create(familyMember, request(10L, 5));

        assertThat(response.getOverallRating()).isEqualTo(5);
        assertThat(response.getPunctualityRating()).isEqualTo(5);
        assertThat(response.getCareQualityRating()).isEqualTo(5);
        assertThat(response.getCommunicationRating()).isEqualTo(5);
        assertThat(response.getCaretakerId()).isEqualTo(30L);
        verify(caretakerProfileService).recomputeRatingAverage(30L);
    }

    @Test
    void aFamilyElderLinkCoManagerCanAlsoReviewABookingTheyDidNotPersonallyBook() throws GenericException {
        User coManager = user(2L, UserTypeEnum.FAMILY_MEMBER);
        Booking booking = booking(10L, 1L, 20L, 30L, BookingStatusEnum.COMPLETED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(20L, 2L)).thenReturn(true);
        when(reviewRepository.existsByBookingId(10L)).thenReturn(false);
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = reviewService.create(coManager, request(10L, 4));

        assertThat(response.getOverallRating()).isEqualTo(4);
    }

    @Test
    void anUnrelatedFamilyMemberCannotReviewSomeoneElsesBooking() {
        User stranger = user(3L, UserTypeEnum.FAMILY_MEMBER);
        Booking booking = booking(10L, 1L, 20L, 30L, BookingStatusEnum.COMPLETED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(20L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.create(stranger, request(10L, 5)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aBookingThatIsNotYetCompletedCannotBeReviewed() {
        User familyMember = user(1L, UserTypeEnum.FAMILY_MEMBER);
        Booking booking = booking(10L, 1L, 20L, 30L, BookingStatusEnum.CONFIRMED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewService.create(familyMember, request(10L, 5)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_BOOKING_STATUS_TRANSITION);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aBookingThatHasAlreadyBeenReviewedCannotBeReviewedAgain() {
        User familyMember = user(1L, UserTypeEnum.FAMILY_MEMBER);
        Booking booking = booking(10L, 1L, 20L, 30L, BookingStatusEnum.COMPLETED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(10L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(familyMember, request(10L, 5)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DUPLICATE_REVIEW);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void reviewingAnUnknownBookingThrowsBookingNotFound() {
        User familyMember = user(1L, UserTypeEnum.FAMILY_MEMBER);
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(familyMember, request(999L, 5)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BOOKING_NOT_FOUND);
    }

    @Test
    void getByCaretakerReturnsEveryReviewForThatCaretaker() {
        Review review = Review.builder().id(100L).bookingId(10L).reviewerUserId(1L).caretakerId(30L).overallRating(5).build();
        when(reviewRepository.findByCaretakerId(30L, PageRequest.of(0, 10))).thenReturn(new PageImpl<>(java.util.List.of(review)));

        var page = reviewService.getByCaretaker(30L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getOverallRating()).isEqualTo(5);
    }

    @Test
    void getByBookingIdReturnsTheReviewIncludingItsPhotoUrlAndReply() throws GenericException {
        Review review = Review.builder().id(100L).bookingId(10L).reviewerUserId(1L).caretakerId(30L)
                .overallRating(5).photoFileAssetId(77L).build();
        when(reviewRepository.findByBookingId(10L)).thenReturn(Optional.of(review));
        when(fileAssetService.getById(77L)).thenReturn(FileAssetResponse.builder().id(77L).url("https://files/77").build());
        ReviewReply reply = ReviewReply.builder().id(200L).reviewId(100L).caretakerId(30L).content("Thank you!").build();
        when(reviewReplyRepository.findByReviewId(100L)).thenReturn(Optional.of(reply));

        ReviewResponse response = reviewService.getByBookingId(10L);

        assertThat(response.getPhotoUrl()).isEqualTo("https://files/77");
        assertThat(response.getReply().getContent()).isEqualTo("Thank you!");
    }

    @Test
    void getByBookingIdThrowsReviewNotFoundWhenTheBookingHasNotBeenReviewedYet() {
        when(reviewRepository.findByBookingId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getByBookingId(10L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.REVIEW_NOT_FOUND);
    }

    @Test
    void theReviewedCaretakerCanPostAFirstReply() throws GenericException {
        User caretakerUser = user(5L, UserTypeEnum.CARETAKER);
        Review review = Review.builder().id(100L).bookingId(10L).reviewerUserId(1L).caretakerId(30L).overallRating(5).build();
        CaretakerProfile caretakerProfile = CaretakerProfile.builder().id(30L).userId(5L).build();
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(caretakerProfileRepository.findById(30L)).thenReturn(Optional.of(caretakerProfile));
        when(reviewReplyRepository.findByReviewId(100L)).thenReturn(Optional.empty());
        when(reviewReplyRepository.save(any())).thenAnswer(inv -> {
            ReviewReply r = inv.getArgument(0);
            r.setId(200L);
            return r;
        });

        ReviewReplyRequest request = new ReviewReplyRequest();
        request.setContent("Thanks for the kind words!");

        ReviewReplyResponse response = reviewService.upsertReply(caretakerUser, 100L, request);

        assertThat(response.getContent()).isEqualTo("Thanks for the kind words!");
        assertThat(response.getReviewId()).isEqualTo(100L);
    }

    @Test
    void postingASecondReplyUpdatesTheExistingOneInPlaceRatherThanInsertingANewRow() throws GenericException {
        User caretakerUser = user(5L, UserTypeEnum.CARETAKER);
        Review review = Review.builder().id(100L).bookingId(10L).reviewerUserId(1L).caretakerId(30L).overallRating(5).build();
        CaretakerProfile caretakerProfile = CaretakerProfile.builder().id(30L).userId(5L).build();
        ReviewReply existingReply = ReviewReply.builder().id(200L).reviewId(100L).caretakerId(30L).content("Old reply").build();
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(caretakerProfileRepository.findById(30L)).thenReturn(Optional.of(caretakerProfile));
        when(reviewReplyRepository.findByReviewId(100L)).thenReturn(Optional.of(existingReply));
        when(reviewReplyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewReplyRequest request = new ReviewReplyRequest();
        request.setContent("Updated reply");

        ReviewReplyResponse response = reviewService.upsertReply(caretakerUser, 100L, request);

        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getContent()).isEqualTo("Updated reply");
        verify(reviewReplyRepository, times(1)).save(any());
    }

    @Test
    void aDifferentCaretakerCannotReplyToSomeoneElsesReview() {
        User otherCaretaker = user(6L, UserTypeEnum.CARETAKER);
        Review review = Review.builder().id(100L).bookingId(10L).reviewerUserId(1L).caretakerId(30L).overallRating(5).build();
        CaretakerProfile caretakerProfile = CaretakerProfile.builder().id(30L).userId(5L).build();
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));
        when(caretakerProfileRepository.findById(30L)).thenReturn(Optional.of(caretakerProfile));

        ReviewReplyRequest request = new ReviewReplyRequest();
        request.setContent("Not mine to reply to");

        assertThatThrownBy(() -> reviewService.upsertReply(otherCaretaker, 100L, request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(reviewReplyRepository, never()).save(any());
    }

    @Test
    void replyingToAnUnknownReviewThrowsReviewNotFound() {
        User caretakerUser = user(5L, UserTypeEnum.CARETAKER);
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        ReviewReplyRequest request = new ReviewReplyRequest();
        request.setContent("Thanks!");

        assertThatThrownBy(() -> reviewService.upsertReply(caretakerUser, 999L, request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.REVIEW_NOT_FOUND);
    }
}
