package com.elderhive.services.impl;

import com.elderhive.dtos.ElderProfile.ElderProfileRequest;
import com.elderhive.dtos.ElderProfile.ElderProfileResponse;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElderProfileServiceImplTest {

    private static final Long FAMILY_USER_ID = 10L;
    private static final Long ELDER_USER_ID = 20L;
    private static final Long STRANGER_USER_ID = 30L;

    @Mock
    private ElderProfileRepository elderProfileRepository;

    @InjectMocks
    private ElderProfileServiceImpl elderProfileService;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("user" + id + "@example.com").passwordHash("hashed")
                .userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private ElderProfileRequest request(String fullName) {
        ElderProfileRequest request = new ElderProfileRequest();
        request.setFullName(fullName);
        return request;
    }

    private ElderProfile profile(Long familyUserId, Long elderUserId) {
        return ElderProfile.builder().id(1L).familyUserId(familyUserId).elderUserId(elderUserId).fullName("Robert").build();
    }

    @Test
    void createPersistsExactlyWhatWasPassedIn() throws GenericException {
        ElderProfileRequest request = request("Robert Smith");
        request.setFamilyUserId(FAMILY_USER_ID);
        when(elderProfileRepository.save(any(ElderProfile.class))).thenAnswer(inv -> {
            ElderProfile p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ElderProfileResponse response = elderProfileService.create(request);

        ArgumentCaptor<ElderProfile> captor = ArgumentCaptor.forClass(ElderProfile.class);
        verify(elderProfileRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getFamilyUserId()).isEqualTo(FAMILY_USER_ID);
        assertThat(captor.getValue().getFullName()).isEqualTo("Robert Smith");
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void theFamilyOwnerCanGetUpdateAndDelete() throws GenericException {
        ElderProfile profile = profile(FAMILY_USER_ID, null);
        when(elderProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(elderProfileRepository.save(any())).thenReturn(profile);
        User caller = user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThat(elderProfileService.getById(1L, caller).getId()).isEqualTo(1L);
        elderProfileService.update(1L, request("Updated Name"), caller);
        elderProfileService.delete(1L, caller);

        verify(elderProfileRepository, times(1)).deleteById(1L);
    }

    @Test
    void theLinkedElderCanGetUpdateAndDelete() throws GenericException {
        ElderProfile profile = profile(FAMILY_USER_ID, ELDER_USER_ID);
        when(elderProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(elderProfileRepository.save(any())).thenReturn(profile);
        User caller = user(ELDER_USER_ID, UserTypeEnum.ELDER);

        assertThat(elderProfileService.getById(1L, caller).getId()).isEqualTo(1L);
        elderProfileService.update(1L, request("Updated Name"), caller);
        elderProfileService.delete(1L, caller);

        verify(elderProfileRepository, times(1)).deleteById(1L);
    }

    @Test
    void anAdminCanAccessAnyProfileRegardlessOfOwnership() throws GenericException {
        ElderProfile profile = profile(FAMILY_USER_ID, ELDER_USER_ID);
        when(elderProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        User admin = user(999L, UserTypeEnum.SUPER_ADMIN);

        assertThat(elderProfileService.getById(1L, admin).getId()).isEqualTo(1L);
    }

    @Test
    void aStrangerCannotAccessSomeoneElsesProfile() {
        ElderProfile profile = profile(FAMILY_USER_ID, ELDER_USER_ID);
        when(elderProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        User stranger = user(STRANGER_USER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> elderProfileService.getById(1L, stranger))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownId() {
        when(elderProfileRepository.findById(1L)).thenReturn(Optional.empty());
        User caller = user(FAMILY_USER_ID, UserTypeEnum.FAMILY_MEMBER);

        assertThatThrownBy(() -> elderProfileService.getById(1L, caller))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND);
    }

    @Test
    void getByElderUserIdThrowsNotFoundWhenNoSelfManagedProfileExists() {
        when(elderProfileRepository.findByElderUserId(ELDER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> elderProfileService.getByElderUserId(ELDER_USER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND);
    }

    @Test
    void getByFamilyUserIdListReturnsOnlyThatFamilysProfiles() {
        when(elderProfileRepository.findByFamilyUserId(FAMILY_USER_ID))
                .thenReturn(List.of(profile(FAMILY_USER_ID, null)));

        List<ElderProfileResponse> results = elderProfileService.getByFamilyUserId(FAMILY_USER_ID);

        assertThat(results).hasSize(1);
    }

    @Test
    void getAllDelegatesStraightToTheRepository() {
        Page<ElderProfile> page = new PageImpl<>(List.of(profile(FAMILY_USER_ID, null)));
        when(elderProfileRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<ElderProfileResponse> results = elderProfileService.getAll(PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
    }
}
