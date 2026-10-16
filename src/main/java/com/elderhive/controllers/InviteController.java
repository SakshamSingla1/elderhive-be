package com.elderhive.controllers;

import com.elderhive.dtos.ElderProfileLinkInvite.InviteResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token-addressed, not invite-id-addressed (ElderSphere's own InviteController is id-based) -
 * an invitee without an account yet only ever has the token from their emailed link, never an
 * invite id. The caller must already be authenticated (register first, via the normal
 * /auth/register endpoint, then accept) - InviteServiceImpl itself checks the caller's own
 * email/phone matches what the invite was sent to.
 */
@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/accept/{token}")
    public ResponseEntity<ResponseModel<InviteResponse>> accept(@PathVariable String token) throws GenericException {
        return ApiResponse.successResponse(inviteService.accept(token, currentUser()), "Invite accepted successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/decline/{token}")
    public ResponseEntity<ResponseModel<InviteResponse>> decline(@PathVariable String token) throws GenericException {
        return ApiResponse.successResponse(inviteService.decline(token, currentUser()), "Invite declined successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
