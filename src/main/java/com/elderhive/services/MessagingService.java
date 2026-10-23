package com.elderhive.services;

import com.elderhive.dtos.Messaging.ConversationCreateRequest;
import com.elderhive.dtos.Messaging.ConversationResponse;
import com.elderhive.dtos.Messaging.MessageRequest;
import com.elderhive.dtos.Messaging.MessageResponse;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MessagingService {

    /** Reuses the existing 1:1 conversation between the caller and otherUserId (matched on
     *  bookingId too, so the same two users get a separate thread per booking context) or
     *  creates a new one. */
    ConversationResponse getOrCreateConversation(Long callerId, ConversationCreateRequest request) throws GenericException;

    /** Every conversation the caller is a participant of, most recently active first. */
    Page<ConversationResponse> getConversationsForUser(Long callerId, Pageable pageable);

    /** Paginated message history for a conversation, oldest first - CONVERSATION_NOT_FOUND
     *  if it doesn't exist, FORBIDDEN if the caller isn't one of its two participants (an
     *  ADMIN/SUPER_ADMIN may always read, same oversight precedent used elsewhere). */
    Page<MessageResponse> getMessages(Long callerId, Long conversationId, Pageable pageable) throws GenericException;

    /**
     * Persists a new message, updates the conversation's lastMessageAt, and broadcasts it
     * over {@code /topic/conversations/conversationId} - called from both
     * ChatWebSocketController's realtime send and nowhere else, so REST and WS can never
     * diverge on what "sending a message" means.
     */
    MessageResponse sendMessage(Long callerId, Long conversationId, MessageRequest request) throws GenericException;

    /** Marks every message the OTHER participant sent as read. A no-op if none are unread. */
    void markRead(Long callerId, Long conversationId) throws GenericException;

    /** Used by ChatWebSocketController to reuse the same access rule for a STOMP-only
     *  interaction, without throwing - a rejected send should just be dropped, not surface
     *  a GenericException with nowhere to deliver it back to the client. */
    boolean hasAccess(Long callerId, Long conversationId);
}
