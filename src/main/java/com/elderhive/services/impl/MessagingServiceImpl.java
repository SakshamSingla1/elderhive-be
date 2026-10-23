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
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ConversationRepository;
import com.elderhive.repositories.MessageRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.FileAssetService;
import com.elderhive.services.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingServiceImpl implements MessagingService {

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FileAssetService fileAssetService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public ConversationResponse getOrCreateConversation(Long callerId, ConversationCreateRequest request) throws GenericException {
        Long otherUserId = request.getOtherUserId();
        if (otherUserId.equals(callerId)) {
            throw new GenericException(ExceptionCodeEnum.BAD_REQUEST, "Cannot start a conversation with yourself");
        }
        if (userRepository.findById(otherUserId).isEmpty()) {
            throw new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found");
        }

        Conversation conversation = conversationRepository.findBetweenUsers(callerId, otherUserId, request.getBookingId())
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .userAId(callerId)
                        .userBId(otherUserId)
                        .bookingId(request.getBookingId())
                        .build()));

        return toResponse(conversation, callerId);
    }

    @Override
    public Page<ConversationResponse> getConversationsForUser(Long callerId, Pageable pageable) {
        return conversationRepository.findForUser(callerId, pageable).map(c -> toResponse(c, callerId));
    }

    @Override
    public Page<MessageResponse> getMessages(Long callerId, Long conversationId, Pageable pageable) throws GenericException {
        Conversation conversation = getConversationOrThrow(conversationId);
        assertAccess(conversation, callerId);
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Long callerId, Long conversationId, MessageRequest request) throws GenericException {
        Conversation conversation = getConversationOrThrow(conversationId);
        assertAccess(conversation, callerId);

        boolean hasContent = request.getContent() != null && !request.getContent().isBlank();
        if (!hasContent && request.getFileAssetId() == null) {
            throw new GenericException(ExceptionCodeEnum.BAD_REQUEST, "Message must have content or an attachment");
        }

        Long fileAssetId = null;
        String fileUrl = null;
        if (request.getFileAssetId() != null) {
            FileAssetResponse fileAsset = fileAssetService.getById(request.getFileAssetId());
            fileAssetId = fileAsset.getId();
            fileUrl = fileAsset.getUrl();
        }

        LocalDateTime now = LocalDateTime.now();
        Message message = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .senderId(callerId)
                .content(request.getContent())
                .sentAt(now)
                .fileAssetId(fileAssetId)
                .fileUrl(fileUrl)
                .build());

        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);

        MessageResponse response = toResponse(message);

        // Best-effort - a client that never subscribed (or has since disconnected) simply
        // misses the live push; the REST history endpoint remains the authoritative source
        // of truth either way, same precedent EmergencyAlertServiceImpl's own broadcast uses.
        try {
            messagingTemplate.convertAndSend(CONVERSATION_TOPIC_PREFIX + conversationId, response);
        } catch (Exception e) {
            log.warn("Failed to broadcast message {} on conversation {}: {}", message.getId(), conversationId, e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public void markRead(Long callerId, Long conversationId) throws GenericException {
        Conversation conversation = getConversationOrThrow(conversationId);
        assertAccess(conversation, callerId);
        messageRepository.markConversationRead(conversationId, callerId, LocalDateTime.now());
    }

    @Override
    public boolean hasAccess(Long callerId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) {
            return false;
        }
        try {
            assertAccess(conversation, callerId);
            return true;
        } catch (GenericException e) {
            return false;
        }
    }

    private Conversation getConversationOrThrow(Long conversationId) throws GenericException {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CONVERSATION_NOT_FOUND, "Conversation not found"));
    }

    private void assertAccess(Conversation conversation, Long callerId) throws GenericException {
        if (conversation.getUserAId().equals(callerId) || conversation.getUserBId().equals(callerId)) {
            return;
        }
        User caller = userRepository.findById(callerId).orElse(null);
        boolean isAdmin = caller != null
                && (caller.getUserType() == UserTypeEnum.ADMIN || caller.getUserType() == UserTypeEnum.SUPER_ADMIN);
        if (!isAdmin) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "You are not a participant of this conversation");
        }
    }

    private ConversationResponse toResponse(Conversation conversation, Long callerId) {
        Long otherUserId = conversation.getUserAId().equals(callerId) ? conversation.getUserBId() : conversation.getUserAId();
        User other = userRepository.findById(otherUserId).orElse(null);

        return ConversationResponse.builder()
                .id(conversation.getId())
                .userAId(conversation.getUserAId())
                .userBId(conversation.getUserBId())
                .otherUserId(otherUserId)
                .otherUserName(other != null ? other.getFullName() : null)
                .bookingId(conversation.getBookingId())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), callerId))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageResponse toResponse(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(sender != null ? sender.getFullName() : null)
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .readAt(message.getReadAt())
                .fileAssetId(message.getFileAssetId())
                .fileUrl(message.getFileUrl())
                .build();
    }
}
