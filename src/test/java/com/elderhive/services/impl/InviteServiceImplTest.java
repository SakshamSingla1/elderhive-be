package com.elderhive.services.impl;

import com.elderhive.dtos.ElderProfileLinkInvite.CreateInviteRequest;
import com.elderhive.dtos.ElderProfileLinkInvite.InviteResponse;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ElderProfileLinkInvite;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.LinkInviteStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileLinkInviteRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.services.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceImplTest {

    private static final Long PROFILE_ID = 1L;
    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";

    @Mock
    private ElderProfileLinkInviteRepository inviteRepository;

    @Mock
    private ElderProfileRepository elderProfileRepository;

    @Mock
    private FamilyElderLinkRepository familyElderLinkRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private InviteServiceImpl inviteService;

    private User user(Long id, UserTypeEnum type, String email, String phone) {
        return User.builder().id(id).email(email).phone(phone).fullName("User " + id)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private ElderProfile profile(Long elderUserId) {
        return ElderProfile.builder().id(PROFILE_ID).familyUserId(10L).elderUserId(elderUserId).fullName("Robert").build();
    }

    private CreateInviteRequest request(String email, String phone, UserTypeEnum role, String label) {
        CreateInviteRequest request = new CreateInviteRequest();
        request.setInvitedEmail(email);
        request.setInvitedPhone(phone);
        request.setInvitedRole(role);
        request.setRelationshipLabel(label);
        return request;
    }

    private ElderProfileLinkInvite invite(String email, String phone, UserTypeEnum role, LinkInviteStatusEnum status) {
        return ElderProfileLinkInvite.builder()
                .id(5L).elderProfileId(PROFILE_ID).invitedEmail(email).invitedPhone(phone)
                .invitedRole(role).relationshipLabel("Son").status(status).token(TOKEN).build();
    }

    @Test
    void createInviteByEmailSavesAPendingInviteAndSendsASimulatedEmail() throws GenericException {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));
        when(inviteRepository.existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatus(
                PROFILE_ID, "elder@example.com", UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)).thenReturn(false);
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.createInvite(PROFILE_ID,
                request("elder@example.com", null, UserTypeEnum.ELDER, null), user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null));

        assertThat(response.getStatus()).isEqualTo(LinkInviteStatusEnum.PENDING);
        assertThat(response.getToken()).isNotBlank();
        verify(emailService, times(1)).sendEmail(eq("elder@example.com"), anyString(), anyString());
    }

    @Test
    void createInviteByPhoneDoesNotSendAnEmail() throws GenericException {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));
        when(inviteRepository.existsByElderProfileIdAndInvitedPhoneAndInvitedRoleAndStatus(
                PROFILE_ID, "+1555000", UserTypeEnum.FAMILY_MEMBER, LinkInviteStatusEnum.PENDING)).thenReturn(false);
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inviteService.createInvite(PROFILE_ID, request(null, "+1555000", UserTypeEnum.FAMILY_MEMBER, "Son"),
                user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null));

        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void createInviteWithBothEmailAndPhoneThrowsValidationFailed() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));

        assertThatThrownBy(() -> inviteService.createInvite(PROFILE_ID,
                request("e@example.com", "+1555000", UserTypeEnum.ELDER, null), user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.VALIDATION_FAILED);
    }

    @Test
    void createInviteWithNeitherEmailNorPhoneThrowsValidationFailed() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));

        assertThatThrownBy(() -> inviteService.createInvite(PROFILE_ID,
                request(null, null, UserTypeEnum.ELDER, null), user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.VALIDATION_FAILED);
    }

    @Test
    void createInviteWithAnInvalidRoleThrowsInvalidRole() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));

        assertThatThrownBy(() -> inviteService.createInvite(PROFILE_ID,
                request("a@example.com", null, UserTypeEnum.ADMIN, null), user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_ROLE);
    }

    @Test
    void createInviteForAnAlreadyLinkedElderRoleThrowsConflict() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(99L)));

        assertThatThrownBy(() -> inviteService.createInvite(PROFILE_ID,
                request("e@example.com", null, UserTypeEnum.ELDER, null), user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);
    }

    @Test
    void createInviteWithAnExistingPendingInviteThrowsConflict() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));
        when(inviteRepository.existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatus(
                PROFILE_ID, "e@example.com", UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> inviteService.createInvite(PROFILE_ID,
                request("e@example.com", null, UserTypeEnum.ELDER, null), user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);
    }

    @Test
    void createInviteForAnUnknownProfileThrowsElderProfileNotFound() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.createInvite(PROFILE_ID,
                request("e@example.com", null, UserTypeEnum.ELDER, null), user(10L, UserTypeEnum.FAMILY_MEMBER, "f@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND);
    }

    @Test
    void acceptingAnElderInviteLinksTheElderUserIdAndMarksAccepted() throws GenericException {
        ElderProfileLinkInvite pendingInvite = invite("elder@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING);
        when(inviteRepository.findByToken(TOKEN)).thenReturn(Optional.of(pendingInvite));
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));
        when(elderProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User elderCaller = user(50L, UserTypeEnum.ELDER, "elder@example.com", null);

        InviteResponse response = inviteService.accept(TOKEN, elderCaller);

        assertThat(response.getStatus()).isEqualTo(LinkInviteStatusEnum.ACCEPTED);
        ArgumentCaptor<ElderProfile> captor = ArgumentCaptor.forClass(ElderProfile.class);
        verify(elderProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getElderUserId()).isEqualTo(50L);
        verify(familyElderLinkRepository, never()).save(any());
    }

    @Test
    void acceptingAFamilyMemberInviteCreatesAFamilyElderLinkAndMarksAccepted() throws GenericException {
        ElderProfileLinkInvite pendingInvite = invite(null, "+1555000", UserTypeEnum.FAMILY_MEMBER, LinkInviteStatusEnum.PENDING);
        when(inviteRepository.findByToken(TOKEN)).thenReturn(Optional.of(pendingInvite));
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(PROFILE_ID, 60L)).thenReturn(false);
        when(familyElderLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User familyCaller = user(60L, UserTypeEnum.FAMILY_MEMBER, "f2@example.com", "+1555000");

        InviteResponse response = inviteService.accept(TOKEN, familyCaller);

        assertThat(response.getStatus()).isEqualTo(LinkInviteStatusEnum.ACCEPTED);
        ArgumentCaptor<FamilyElderLink> captor = ArgumentCaptor.forClass(FamilyElderLink.class);
        verify(familyElderLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getFamilyUserId()).isEqualTo(60L);
        assertThat(captor.getValue().getRelationshipLabel()).isEqualTo("Son");
        verify(elderProfileRepository, never()).save(any());
    }

    @Test
    void acceptingAnUnknownTokenThrowsInviteNotFound() {
        when(inviteRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteService.accept(TOKEN, user(1L, UserTypeEnum.ELDER, "e@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_LINK_INVITE_NOT_FOUND);
    }

    @Test
    void acceptingANonPendingInviteThrowsConflict() {
        when(inviteRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(invite("e@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.ACCEPTED)));

        assertThatThrownBy(() -> inviteService.accept(TOKEN, user(50L, UserTypeEnum.ELDER, "e@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);
    }

    @Test
    void acceptingWithAMismatchedEmailAndPhoneThrowsForbidden() {
        when(inviteRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(invite("e@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)));

        assertThatThrownBy(() -> inviteService.accept(TOKEN, user(50L, UserTypeEnum.ELDER, "different@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void acceptingWithAMismatchedAccountRoleThrowsInvalidRole() {
        when(inviteRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(invite("e@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)));

        assertThatThrownBy(() -> inviteService.accept(TOKEN, user(50L, UserTypeEnum.FAMILY_MEMBER, "e@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_ROLE);
    }

    @Test
    void acceptingAnElderInviteForAnAlreadyLinkedProfileThrowsConflict() {
        when(inviteRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(invite("e@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)));
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(99L)));

        assertThatThrownBy(() -> inviteService.accept(TOKEN, user(50L, UserTypeEnum.ELDER, "e@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);
    }

    @Test
    void acceptingAFamilyMemberInviteWhenAlreadyLinkedThrowsConflict() {
        when(inviteRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(invite(null, "+1555000", UserTypeEnum.FAMILY_MEMBER, LinkInviteStatusEnum.PENDING)));
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile(null)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(PROFILE_ID, 60L)).thenReturn(true);

        assertThatThrownBy(() -> inviteService.accept(TOKEN, user(60L, UserTypeEnum.FAMILY_MEMBER, "f2@example.com", "+1555000")))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);
    }

    @Test
    void decliningAPendingInviteMarksItDeclined() throws GenericException {
        when(inviteRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(invite("e@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)));
        when(inviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = inviteService.decline(TOKEN, user(50L, UserTypeEnum.ELDER, "e@example.com", null));

        assertThat(response.getStatus()).isEqualTo(LinkInviteStatusEnum.DECLINED);
        verify(elderProfileRepository, never()).findById(any());
    }

    @Test
    void decliningWithAMismatchedContactThrowsForbidden() {
        when(inviteRepository.findByToken(TOKEN))
                .thenReturn(Optional.of(invite("e@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING)));

        assertThatThrownBy(() -> inviteService.decline(TOKEN, user(50L, UserTypeEnum.ELDER, "different@example.com", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void getInvitesForProfileReturnsEveryInviteForThatProfile() {
        when(inviteRepository.findByElderProfileIdOrderByCreatedAtDesc(PROFILE_ID)).thenReturn(List.of(
                invite("a@example.com", null, UserTypeEnum.ELDER, LinkInviteStatusEnum.PENDING),
                invite(null, "+1555111", UserTypeEnum.FAMILY_MEMBER, LinkInviteStatusEnum.DECLINED)
        ));

        List<InviteResponse> responses = inviteService.getInvitesForProfile(PROFILE_ID);

        assertThat(responses).hasSize(2);
    }
}
