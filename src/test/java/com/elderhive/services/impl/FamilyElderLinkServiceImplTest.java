package com.elderhive.services.impl;

import com.elderhive.dtos.FamilyElderLink.FamilyElderLinkRequest;
import com.elderhive.dtos.FamilyElderLink.FamilyMemberSummaryResponse;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyElderLinkServiceImplTest {

    private static final Long PROFILE_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long TARGET_ID = 20L;

    @Mock
    private FamilyElderLinkRepository familyElderLinkRepository;

    @Mock
    private ElderProfileRepository elderProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FamilyElderLinkServiceImpl familyElderLinkService;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private ElderProfile profile() {
        return ElderProfile.builder().id(PROFILE_ID).familyUserId(OWNER_ID).fullName("Robert").build();
    }

    private FamilyElderLinkRequest request(Long familyUserId, String label) {
        FamilyElderLinkRequest request = new FamilyElderLinkRequest();
        request.setFamilyUserId(familyUserId);
        request.setRelationshipLabel(label);
        return request;
    }

    @Test
    void theOwnerCanAddAFamilyMemberCoManager() throws GenericException {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, UserTypeEnum.FAMILY_MEMBER)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(PROFILE_ID, TARGET_ID)).thenReturn(false);
        when(familyElderLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User owner = user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER);

        FamilyMemberSummaryResponse response = familyElderLinkService.addFamilyMember(PROFILE_ID, request(TARGET_ID, "Daughter"), owner);

        assertThat(response.getUserId()).isEqualTo(TARGET_ID);
        assertThat(response.getRelationshipLabel()).isEqualTo("Daughter");
        assertThat(response.isOwner()).isFalse();
    }

    @Test
    void anAdminCanAddAFamilyMemberCoManager() throws GenericException {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, UserTypeEnum.FAMILY_MEMBER)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(PROFILE_ID, TARGET_ID)).thenReturn(false);
        when(familyElderLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User admin = user(999L, UserTypeEnum.SUPER_ADMIN);

        FamilyMemberSummaryResponse response = familyElderLinkService.addFamilyMember(PROFILE_ID, request(TARGET_ID, null), admin);

        assertThat(response.getUserId()).isEqualTo(TARGET_ID);
    }

    @Test
    void aNonOwnerNonAdminCannotAddAFamilyMember() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        User stranger = user(30L, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> familyElderLinkService.addFamilyMember(PROFILE_ID, request(TARGET_ID, null), stranger))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(familyElderLinkRepository, never()).save(any());
    }

    @Test
    void addingANonFamilyMemberUserThrowsInvalidRole() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, UserTypeEnum.CARETAKER)));
        User owner = user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> familyElderLinkService.addFamilyMember(PROFILE_ID, request(TARGET_ID, null), owner))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_ROLE);
    }

    @Test
    void addingTheExistingOwnerAsACoManagerThrowsConflict() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER)));
        User owner = user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> familyElderLinkService.addFamilyMember(PROFILE_ID, request(OWNER_ID, null), owner))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);
    }

    @Test
    void addingAnAlreadyLinkedCoManagerAgainThrowsConflict() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, UserTypeEnum.FAMILY_MEMBER)));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(PROFILE_ID, TARGET_ID)).thenReturn(true);
        User owner = user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> familyElderLinkService.addFamilyMember(PROFILE_ID, request(TARGET_ID, null), owner))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);
    }

    @Test
    void theOwnerCanRemoveALinkedCoManager() throws GenericException {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(PROFILE_ID, TARGET_ID)).thenReturn(true);
        User owner = user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER);

        familyElderLinkService.removeFamilyMember(PROFILE_ID, TARGET_ID, owner);

        verify(familyElderLinkRepository, times(1)).deleteByElderProfileIdAndFamilyUserId(PROFILE_ID, TARGET_ID);
    }

    @Test
    void removingAUserThatIsNotLinkedThrowsDataNotFound() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(PROFILE_ID, TARGET_ID)).thenReturn(false);
        User owner = user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> familyElderLinkService.removeFamilyMember(PROFILE_ID, TARGET_ID, owner))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);
    }

    @Test
    void aNonOwnerNonAdminCannotRemoveAFamilyMember() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        User stranger = user(30L, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> familyElderLinkService.removeFamilyMember(PROFILE_ID, TARGET_ID, stranger))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(familyElderLinkRepository, never()).deleteByElderProfileIdAndFamilyUserId(any(), any());
    }

    @Test
    void addOrRemoveThrowsElderProfileNotFoundForAnUnknownProfile() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.empty());
        User someone = user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> familyElderLinkService.addFamilyMember(PROFILE_ID, request(TARGET_ID, null), someone))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND);
    }

    @Test
    void findAllByElderProfileIdReturnsTheOwnerFirstThenEveryCoManagerWithNoCallerCheckAtAll() throws GenericException {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(familyElderLinkRepository.findByElderProfileId(PROFILE_ID)).thenReturn(List.of(
                FamilyElderLink.builder().elderProfileId(PROFILE_ID).familyUserId(TARGET_ID).relationshipLabel("Son").build()
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(OWNER_ID, UserTypeEnum.FAMILY_MEMBER), user(TARGET_ID, UserTypeEnum.FAMILY_MEMBER)
        ));

        List<FamilyMemberSummaryResponse> members = familyElderLinkService.findAllByElderProfileId(PROFILE_ID);

        assertThat(members).hasSize(2);
        assertThat(members.get(0).isOwner()).isTrue();
        assertThat(members.get(0).getUserId()).isEqualTo(OWNER_ID);
        assertThat(members.get(1).isOwner()).isFalse();
        assertThat(members.get(1).getUserId()).isEqualTo(TARGET_ID);
        assertThat(members.get(1).getRelationshipLabel()).isEqualTo("Son");
    }

    @Test
    void findAllByElderProfileIdThrowsNotFoundForAnUnknownProfile() {
        when(elderProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyElderLinkService.findAllByElderProfileId(PROFILE_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND);
    }
}
