package com.elderhive.controllers;

import com.elderhive.dtos.Messaging.ConversationCreateRequest;
import com.elderhive.dtos.Messaging.ConversationResponse;
import com.elderhive.dtos.Messaging.MessageResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The REST side of Prompt 89's messaging feature - realtime send happens over STOMP
 * (ChatWebSocketController), but a conversation still needs to be started/listed somehow,
 * and message history needs to be readable without an open socket (a freshly opened chat
 * thread, or a page reload).
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final MessagingService messagingService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ResponseModel<ConversationResponse>> getOrCreate(@Valid @RequestBody ConversationCreateRequest request) throws GenericException {
        return ApiResponse.createSuccess(messagingService.getOrCreateConversation(currentUser().getId(), request), "Conversation ready");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<ConversationResponse>>> getMyConversations(Pageable pageable) throws GenericException {
        return ApiResponse.successResponse(messagingService.getConversationsForUser(currentUser().getId(), pageable), "Conversations fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/messages")
    public ResponseEntity<ResponseModel<Page<MessageResponse>>> getMessages(@PathVariable Long id, Pageable pageable) throws GenericException {
        return ApiResponse.successResponse(messagingService.getMessages(currentUser().getId(), id, pageable), "Message history fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/read")
    public ResponseEntity<ResponseModel<Void>> markRead(@PathVariable Long id) throws GenericException {
        messagingService.markRead(currentUser().getId(), id);
        return ApiResponse.successResponse();
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
