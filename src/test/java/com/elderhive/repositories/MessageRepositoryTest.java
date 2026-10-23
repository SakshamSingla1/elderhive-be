package com.elderhive.repositories;

import com.elderhive.entities.Conversation;
import com.elderhive.entities.FileAsset;
import com.elderhive.entities.Message;
import com.elderhive.entities.User;
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
 * Runs against the real local Postgres - confirms V33's migration matches the entity
 * mapping, the conversation_id/sender_id CASCADE and file_asset_id SET-NULL FK behavior, and
 * the history/unread-count/mark-read queries MessagingServiceImpl relies on.
 */
@SpringBootTest
@Transactional
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileAssetRepository fileAssetRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private Conversation persistConversation(Long userAId, Long userBId) {
        return conversationRepository.save(Conversation.builder().userAId(userAId).userBId(userBId).build());
    }

    private Message.MessageBuilder newMessage(Long conversationId, Long senderId) {
        return Message.builder().conversationId(conversationId).senderId(senderId).sentAt(LocalDateTime.now());
    }

    @Test
    void persistingAndReloadingAMessagePopulatesAuditFieldsAndEveryColumn() {
        User userA = persistUser("msg-roundtrip-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-roundtrip-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());
        FileAsset asset = fileAssetRepository.save(FileAsset.builder().storageKey("messages/photo.jpg").build());
        LocalDateTime sentAt = LocalDateTime.now();

        Message saved = messageRepository.save(newMessage(conversation.getId(), userA.getId())
                .content("Hello!").sentAt(sentAt).fileAssetId(asset.getId()).fileUrl("https://files/photo.jpg").build());
        entityManager.flush();
        entityManager.clear();

        Message reloaded = messageRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getConversationId()).isEqualTo(conversation.getId());
        assertThat(reloaded.getSenderId()).isEqualTo(userA.getId());
        assertThat(reloaded.getContent()).isEqualTo("Hello!");
        assertThat(reloaded.getSentAt()).isEqualToIgnoringNanos(sentAt);
        assertThat(reloaded.getReadAt()).isNull();
        assertThat(reloaded.getFileAssetId()).isEqualTo(asset.getId());
        assertThat(reloaded.getFileUrl()).isEqualTo("https://files/photo.jpg");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void contentIsOptionalForAnAttachmentOnlyMessage() {
        User userA = persistUser("msg-noattach-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-noattach-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());

        Message saved = messageRepository.save(newMessage(conversation.getId(), userA.getId()).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.findById(saved.getId()).orElseThrow().getContent()).isNull();
    }

    @Test
    void deletingTheConversationCascadesToItsMessages() {
        User userA = persistUser("msg-cascade-convo-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-cascade-convo-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());
        Message saved = messageRepository.save(newMessage(conversation.getId(), userA.getId()).content("Hi").build());
        entityManager.flush();

        conversationRepository.delete(conversation);
        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheSenderCascadesToTheirMessages() {
        User userA = persistUser("msg-cascade-sender-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-cascade-sender-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());
        Message saved = messageRepository.save(newMessage(conversation.getId(), userA.getId()).content("Hi").build());
        entityManager.flush();

        userRepository.delete(userA);
        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheFileAssetJustClearsTheReferenceRatherThanDeletingTheMessage() {
        User userA = persistUser("msg-setnull-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-setnull-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());
        FileAsset asset = fileAssetRepository.save(FileAsset.builder().storageKey("messages/to-delete.jpg").build());
        Message saved = messageRepository.save(newMessage(conversation.getId(), userA.getId())
                .fileAssetId(asset.getId()).fileUrl("https://files/to-delete.jpg").build());
        entityManager.flush();

        fileAssetRepository.delete(asset);
        entityManager.flush();
        entityManager.clear();

        Message reloaded = messageRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFileAssetId()).isNull();
        assertThat(reloaded.getFileUrl()).isEqualTo("https://files/to-delete.jpg");
    }

    @Test
    void findByConversationIdOrderBySentAtAscReturnsOnlyThatConversationsMessagesOldestFirst() {
        User userA = persistUser("msg-history-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-history-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());
        Conversation otherConversation = persistConversation(userA.getId(), userB.getId());

        messageRepository.save(newMessage(conversation.getId(), userA.getId()).content("First").sentAt(LocalDateTime.now().minusMinutes(5)).build());
        messageRepository.save(newMessage(conversation.getId(), userB.getId()).content("Second").sentAt(LocalDateTime.now()).build());
        messageRepository.save(newMessage(otherConversation.getId(), userA.getId()).content("Elsewhere").build());
        entityManager.flush();

        var page = messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId(), PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Message::getContent).containsExactly("First", "Second");
    }

    @Test
    void countByConversationIdAndSenderIdNotAndReadAtIsNullCountsOnlyTheOtherPersonsUnreadMessages() {
        User userA = persistUser("msg-unread-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-unread-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());

        messageRepository.save(newMessage(conversation.getId(), userB.getId()).content("Unread 1").build());
        messageRepository.save(newMessage(conversation.getId(), userB.getId()).content("Unread 2").build());
        messageRepository.save(newMessage(conversation.getId(), userA.getId()).content("My own message").build());
        entityManager.flush();

        assertThat(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), userA.getId())).isEqualTo(2);
        assertThat(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), userB.getId())).isEqualTo(1);
    }

    @Test
    void markConversationReadSetsReadAtOnlyForTheOtherPersonsUnreadMessages() {
        User userA = persistUser("msg-markread-a@example.com", UserTypeEnum.FAMILY_MEMBER);
        User userB = persistUser("msg-markread-b@example.com", UserTypeEnum.CARETAKER);
        Conversation conversation = persistConversation(userA.getId(), userB.getId());
        Message fromB = messageRepository.save(newMessage(conversation.getId(), userB.getId()).content("From B").build());
        Message fromA = messageRepository.save(newMessage(conversation.getId(), userA.getId()).content("From A").build());
        entityManager.flush();

        int updated = messageRepository.markConversationRead(conversation.getId(), userA.getId(), LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(messageRepository.findById(fromB.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(messageRepository.findById(fromA.getId()).orElseThrow().getReadAt()).isNull();
    }
}
