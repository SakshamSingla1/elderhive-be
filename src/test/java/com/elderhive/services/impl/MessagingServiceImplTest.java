package com.elderhive.services.impl;

import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.dtos.Messaging.ConversationCreateRequest;
import com.elderhive.dtos.Messaging.ConversationResponse;
import com.elderhive.dtos.Messaging.MessageRequest;
import com.elderhive.dtos.Messaging.MessageResponse;
import com.elderhive.entities.Conversation;
import com.elderhive.entities.Message;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ConversationRepository;
import com.elderhive.repositories.MessageRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.FileAssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileAssetService fileAssetService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessagingServiceImpl messagingService;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    @Test
    void getOrCreateReusesAnExistingConversationBetweenTheSameTwoUsers() throws GenericException {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserTypeEnum.CARETAKER)));
        Conversation existing = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findBetweenUsers(1L, 2L, null)).thenReturn(Optional.of(existing));

        ConversationResponse response = messagingService.getOrCreateConversation(1L,
                ConversationCreateRequest.builder().otherUserId(2L).build());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOtherUserId()).isEqualTo(2L);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreateCreatesANewConversationWhenNoneExistsYet() throws GenericException {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserTypeEnum.CARETAKER)));
        when(conversationRepository.findBetweenUsers(1L, 2L, 50L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        ConversationResponse response = messagingService.getOrCreateConversation(1L,
                ConversationCreateRequest.builder().otherUserId(2L).bookingId(50L).build());

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getBookingId()).isEqualTo(50L);
    }

    @Test
    void aUserCannotStartAConversationWithThemselves() {
        assertThatThrownBy(() -> messagingService.getOrCreateConversation(1L,
                ConversationCreateRequest.builder().otherUserId(1L).build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BAD_REQUEST);
    }

    @Test
    void startingAConversationWithAnUnknownUserThrowsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messagingService.getOrCreateConversation(1L,
                ConversationCreateRequest.builder().otherUserId(99L).build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.USER_NOT_FOUND);
    }

    @Test
    void getConversationsForUserPagesEveryConversationForThatUser() {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findForUser(1L, PageRequest.of(0, 10))).thenReturn(new PageImpl<>(List.of(conversation)));

        var page = messagingService.getConversationsForUser(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getOtherUserId()).isEqualTo(2L);
    }

    @Test
    void aParticipantCanReadMessageHistory() throws GenericException {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        Message message = Message.builder().id(100L).conversationId(10L).senderId(2L).content("Hi").sentAt(java.time.LocalDateTime.now()).build();
        when(messageRepository.findByConversationIdOrderBySentAtAsc(10L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(message)));

        var page = messagingService.getMessages(1L, 10L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getContent()).isEqualTo("Hi");
    }

    @Test
    void aNonParticipantCannotReadMessageHistory() {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, UserTypeEnum.FAMILY_MEMBER)));

        assertThatThrownBy(() -> messagingService.getMessages(7L, 10L, PageRequest.of(0, 10)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void anAdminCanReadAnyConversationsMessageHistory() throws GenericException {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L, UserTypeEnum.SUPER_ADMIN)));
        when(messageRepository.findByConversationIdOrderBySentAtAsc(10L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));

        var page = messagingService.getMessages(9L, 10L, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void readingHistoryForAnUnknownConversationThrowsConversationNotFound() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messagingService.getMessages(1L, 999L, PageRequest.of(0, 10)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONVERSATION_NOT_FOUND);
    }

    @Test
    void sendingAMessageSavesItUpdatesLastMessageAtAndBroadcastsIt() throws GenericException {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        MessageResponse response = messagingService.sendMessage(1L, 10L, MessageRequest.builder().content("Hello there").build());

        assertThat(response.getContent()).isEqualTo("Hello there");
        assertThat(response.getSenderId()).isEqualTo(1L);
        assertThat(conversation.getLastMessageAt()).isNotNull();
        verify(conversationRepository).save(conversation);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/10"), any(MessageResponse.class));
    }

    @Test
    void sendingAMessageWithAnAttachmentResolvesAndPersistsItsUrl() throws GenericException {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(fileAssetService.getById(77L)).thenReturn(FileAssetResponse.builder().id(77L).url("https://files/77").build());
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messagingService.sendMessage(1L, 10L, MessageRequest.builder().fileAssetId(77L).build());

        assertThat(response.getFileAssetId()).isEqualTo(77L);
        assertThat(response.getFileUrl()).isEqualTo("https://files/77");
    }

    @Test
    void sendingAMessageWithNeitherContentNorAttachmentIsRejected() {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> messagingService.sendMessage(1L, 10L, MessageRequest.builder().build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BAD_REQUEST);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void aNonParticipantCannotSendAMessage() {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, UserTypeEnum.FAMILY_MEMBER)));

        assertThatThrownBy(() -> messagingService.sendMessage(7L, 10L, MessageRequest.builder().content("Hi").build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void markReadDelegatesToTheRepositoryWithTheCallerExcluded() throws GenericException {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        messagingService.markRead(1L, 10L);

        verify(messageRepository).markConversationRead(eq(10L), eq(1L), any());
    }

    @Test
    void hasAccessReturnsTrueForAParticipantAndFalseForAStranger() {
        Conversation conversation = Conversation.builder().id(10L).userAId(1L).userBId(2L).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, UserTypeEnum.FAMILY_MEMBER)));

        assertThat(messagingService.hasAccess(1L, 10L)).isTrue();
        assertThat(messagingService.hasAccess(7L, 10L)).isFalse();
    }

    @Test
    void hasAccessReturnsFalseForAnUnknownConversation() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(messagingService.hasAccess(1L, 999L)).isFalse();
    }
}
