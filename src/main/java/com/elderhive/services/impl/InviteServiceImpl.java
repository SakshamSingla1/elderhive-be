package com.elderhive.services.impl;

import com.elderhive.dtos.ElderProfileLinkInvite.CreateInviteRequest;
import com.elderhive.dtos.ElderProfileLinkInvite.InviteResponse;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ElderProfileLinkInvite;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.LinkInviteStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileLinkInviteRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.services.EmailService;
import com.elderhive.services.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapted from ~/ElderSphere's InviteServiceImpl's own create/accept/decline flow shape, but
 * resolving the accepting user by invitedEmail/invitedPhone match against the authenticated
 * caller rather than by an invitedUserId foreign key - see ElderProfileLinkInvite's own
 * javadoc for why that departure exists. Scoped to exactly what this prompt asks: create,
 * accept, decline - no revoke, no accept/decline notification back to the inviter (narrower
 * than ElderSphere's full reference flow, which this prompt's own text doesn't ask for).
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private static final Set<UserTypeEnum> INVITABLE_ROLES = Set.of(UserTypeEnum.ELDER, UserTypeEnum.FAMILY_MEMBER);

    private final ElderProfileLinkInviteRepository inviteRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final FamilyElderLinkRepository familyElderLinkRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public InviteResponse createInvite(Long elderProfileId, CreateInviteRequest request, User caller) throws GenericException {
        ElderProfile profile = elderProfileRepository.findById(elderProfileId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND, "Elder profile not found"));

        if (!INVITABLE_ROLES.contains(request.getInvitedRole())) {
            throw new GenericException(ExceptionCodeEnum.INVALID_ROLE, "invitedRole must be ELDER or FAMILY_MEMBER");
        }

        boolean hasEmail = request.getInvitedEmail() != null && !request.getInvitedEmail().isBlank();
        boolean hasPhone = request.getInvitedPhone() != null && !request.getInvitedPhone().isBlank();
        if (hasEmail == hasPhone) {
            throw new GenericException(ExceptionCodeEnum.VALIDATION_FAILED,
                    "Exactly one of invitedEmail or invitedPhone is required");
        }

        if (request.getInvitedRole() == UserTypeEnum.ELDER && profile.getElderUserId() != null) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "Elder profile is already linked to an elder user");
        }

        boolean alreadyPending = hasEmail
                ? inviteRepository.existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatus(
                        elderProfileId, request.getInvitedEmail(), request.getInvitedRole(), LinkInviteStatusEnum.PENDING)
                : inviteRepository.existsByElderProfileIdAndInvitedPhoneAndInvitedRoleAndStatus(
                        elderProfileId, request.getInvitedPhone(), request.getInvitedRole(), LinkInviteStatusEnum.PENDING);
        if (alreadyPending) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "A pending invite already exists for this contact and role");
        }

        ElderProfileLinkInvite invite = ElderProfileLinkInvite.builder()
                .elderProfileId(elderProfileId)
                .invitedEmail(request.getInvitedEmail())
                .invitedPhone(request.getInvitedPhone())
                .invitedRole(request.getInvitedRole())
                .relationshipLabel(request.getRelationshipLabel())
                .status(LinkInviteStatusEnum.PENDING)
                .token(UUID.randomUUID().toString())
                .build();
        invite = inviteRepository.save(invite);

        if (hasEmail) {
            emailService.sendEmail(invite.getInvitedEmail(), "You've been invited on ElderHive",
                    "You've been invited to manage " + profile.getFullName() + "'s profile on ElderHive. "
                            + "Use invite token: " + invite.getToken());
        }

        return toResponse(invite);
    }

    @Override
    public List<InviteResponse> getInvitesForProfile(Long elderProfileId) {
        return inviteRepository.findByElderProfileIdOrderByCreatedAtDesc(elderProfileId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public InviteResponse accept(String token, User caller) throws GenericException {
        ElderProfileLinkInvite invite = findByTokenOrThrow(token);
        assertPending(invite);
        assertCallerMatchesInvite(invite, caller);

        if (caller.getUserType() != invite.getInvitedRole()) {
            throw new GenericException(ExceptionCodeEnum.INVALID_ROLE,
                    "Your account type does not match the role this invite was sent for");
        }

        ElderProfile profile = elderProfileRepository.findById(invite.getElderProfileId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND, "Elder profile not found"));

        if (invite.getInvitedRole() == UserTypeEnum.ELDER) {
            if (profile.getElderUserId() != null) {
                throw new GenericException(ExceptionCodeEnum.CONFLICT, "Elder profile is already linked to an elder user");
            }
            profile.setElderUserId(caller.getId());
            elderProfileRepository.save(profile);
        } else {
            if (familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(invite.getElderProfileId(), caller.getId())) {
                throw new GenericException(ExceptionCodeEnum.CONFLICT, "You are already linked to this elder profile");
            }
            familyElderLinkRepository.save(FamilyElderLink.builder()
                    .elderProfileId(invite.getElderProfileId())
                    .familyUserId(caller.getId())
                    .relationshipLabel(invite.getRelationshipLabel())
                    .build());
        }

        invite.setStatus(LinkInviteStatusEnum.ACCEPTED);
        return toResponse(inviteRepository.save(invite));
    }

    @Override
    @Transactional
    public InviteResponse decline(String token, User caller) throws GenericException {
        ElderProfileLinkInvite invite = findByTokenOrThrow(token);
        assertPending(invite);
        assertCallerMatchesInvite(invite, caller);

        invite.setStatus(LinkInviteStatusEnum.DECLINED);
        return toResponse(inviteRepository.save(invite));
    }

    private ElderProfileLinkInvite findByTokenOrThrow(String token) throws GenericException {
        return inviteRepository.findByToken(token)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_LINK_INVITE_NOT_FOUND, "Invite not found"));
    }

    private void assertPending(ElderProfileLinkInvite invite) throws GenericException {
        if (invite.getStatus() != LinkInviteStatusEnum.PENDING) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "Invite is no longer pending");
        }
    }

    /** The invite is addressed to a contact, not an account - only the caller whose own email
     *  or phone matches what the invite was sent to may accept or decline it. */
    private void assertCallerMatchesInvite(ElderProfileLinkInvite invite, User caller) throws GenericException {
        boolean emailMatch = invite.getInvitedEmail() != null && invite.getInvitedEmail().equalsIgnoreCase(caller.getEmail());
        boolean phoneMatch = invite.getInvitedPhone() != null && invite.getInvitedPhone().equals(caller.getPhone());
        if (!emailMatch && !phoneMatch) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "This invite was not sent to you");
        }
    }

    private InviteResponse toResponse(ElderProfileLinkInvite invite) {
        return InviteResponse.builder()
                .id(invite.getId())
                .elderProfileId(invite.getElderProfileId())
                .invitedEmail(invite.getInvitedEmail())
                .invitedPhone(invite.getInvitedPhone())
                .invitedRole(invite.getInvitedRole())
                .relationshipLabel(invite.getRelationshipLabel())
                .status(invite.getStatus())
                .token(invite.getToken())
                .createdAt(invite.getCreatedAt())
                .updatedAt(invite.getUpdatedAt())
                .createdBy(invite.getCreatedBy())
                .updatedBy(invite.getUpdatedBy())
                .build();
    }
}
