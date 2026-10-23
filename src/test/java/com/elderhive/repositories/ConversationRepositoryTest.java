package com.elderhive.repositories;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.Conversation;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres - confirms V32's migration matches the entity
 * mapping, the user_a/user_b CASCADE and booking_id SET-NULL FK behavior, and the
 * participant-pair/ordering queries MessagingServiceImpl relies on.
 */
@SpringBootTest
@Transactional
class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;

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

    private Booking persistBooking(String namePrefix, Long familyUserId, Long caretakerProfileUserId) {
        CaretakerProfile caretaker = caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(caretakerProfileUserId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).ratingAverage(0.0).build());
        ElderProfile elder = elderProfileRepository.save(ElderProfile.builder().familyUserId(familyUserId).fullName("Robert").build());
        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                .name(namePrefix + "-Temple Visit").status(ServiceCategoryStatusEnum.ACTIVE).build());
        ServiceOffering offering = serviceOfferingRepository.save(ServiceOffering.builder()
                .name(namePrefix + "-Weekly Temple Visit").categoryId(category.getId()).status(ServiceOfferingStatusEnum.ACTIVE).build());
        return bookingRepository.save(Booking.builder()
                .familyUserId(familyUserId).elderProfileId(elder.getId()).caretakerId(caretaker.getId())
                .serviceOfferingId(offering.getId()).status(BookingStatusEnum.COMPLETED).build());
    }

    @Test
    void persistingAndReloadingAConversationPopulatesAuditFieldsAndEveryColumn() {
        User userA = persistUser("convo-roundtrip-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("convo-roundtrip-b@example.com", UserTypeEnum.CARETAKER);
        Booking booking = persistBooking("convo-roundtrip", userA.getId(), userB.getId());
        LocalDateTime lastMessageAt = LocalDateTime.now();

        Conversation saved = conversationRepository.save(Conversation.builder()
                .userAId(userA.getId()).userBId(userB.getId()).bookingId(booking.getId()).lastMessageAt(lastMessageAt).build());
        entityManager.flush();
        entityManager.clear();

        Conversation reloaded = conversationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getUserAId()).isEqualTo(userA.getId());
        assertThat(reloaded.getUserBId()).isEqualTo(userB.getId());
        assertThat(reloaded.getBookingId()).isEqualTo(booking.getId());
        assertThat(reloaded.getLastMessageAt()).isEqualToIgnoringNanos(lastMessageAt);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void bookingIdIsOptional() {
        User userA = persistUser("convo-nobooking-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("convo-nobooking-b@example.com", UserTypeEnum.CARETAKER);

        Conversation saved = conversationRepository.save(Conversation.builder().userAId(userA.getId()).userBId(userB.getId()).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(conversationRepository.findById(saved.getId()).orElseThrow().getBookingId()).isNull();
    }

    @Test
    void deletingEitherParticipantCascadesToTheConversation() {
        User userA = persistUser("convo-cascade-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("convo-cascade-b@example.com", UserTypeEnum.CARETAKER);
        Conversation saved = conversationRepository.save(Conversation.builder().userAId(userA.getId()).userBId(userB.getId()).build());
        entityManager.flush();

        userRepository.delete(userB);
        entityManager.flush();
        entityManager.clear();

        assertThat(conversationRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheLinkedBookingJustClearsTheReferenceRatherThanDeletingTheConversation() {
        User userA = persistUser("convo-setnull-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("convo-setnull-b@example.com", UserTypeEnum.CARETAKER);
        Booking booking = persistBooking("convo-setnull", userA.getId(), userB.getId());
        Conversation saved = conversationRepository.save(Conversation.builder()
                .userAId(userA.getId()).userBId(userB.getId()).bookingId(booking.getId()).build());
        entityManager.flush();

        bookingRepository.delete(booking);
        entityManager.flush();
        entityManager.clear();

        assertThat(conversationRepository.findById(saved.getId()).orElseThrow().getBookingId()).isNull();
    }

    @Test
    void findBetweenUsersMatchesEitherColumnOrderAndOptionallyScopesByBooking() {
        User userA = persistUser("convo-between-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("convo-between-b@example.com", UserTypeEnum.CARETAKER);
        Conversation saved = conversationRepository.save(Conversation.builder().userAId(userA.getId()).userBId(userB.getId()).build());
        entityManager.flush();

        assertThat(conversationRepository.findBetweenUsers(userA.getId(), userB.getId(), null)).contains(saved);
        assertThat(conversationRepository.findBetweenUsers(userB.getId(), userA.getId(), null)).contains(saved);
        assertThat(conversationRepository.findBetweenUsers(userA.getId(), userB.getId(), 12345L)).isEmpty();
    }

    @Test
    void findForUserOrdersByLastMessageAtFallingBackToCreatedAt() throws InterruptedException {
        User userA = persistUser("convo-order-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("convo-order-b@example.com", UserTypeEnum.CARETAKER);
        User userC = persistUser("convo-order-c@example.com", UserTypeEnum.CARETAKER);

        Conversation older = conversationRepository.save(Conversation.builder().userAId(userA.getId()).userBId(userB.getId()).build());
        Conversation newer = conversationRepository.save(Conversation.builder().userAId(userA.getId()).userBId(userC.getId())
                .lastMessageAt(LocalDateTime.now().plusMinutes(5)).build());
        entityManager.flush();

        var page = conversationRepository.findForUser(userA.getId(), PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Conversation::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test
    void findForUserDoesNotReturnConversationsTheUserIsNotPartOf() {
        User userA = persistUser("convo-notmine-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("convo-notmine-b@example.com", UserTypeEnum.CARETAKER);
        User stranger = persistUser("convo-notmine-stranger@example.com", UserTypeEnum.FAMILY_MEMBER);
        conversationRepository.save(Conversation.builder().userAId(userA.getId()).userBId(userB.getId()).build());
        entityManager.flush();

        assertThat(conversationRepository.findForUser(stranger.getId(), PageRequest.of(0, 10)).getContent()).isEmpty();
    }
}
