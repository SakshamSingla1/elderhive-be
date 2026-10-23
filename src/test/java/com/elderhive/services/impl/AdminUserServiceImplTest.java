package com.elderhive.services.impl;

import com.elderhive.dtos.User.AdminUserResponse;
import com.elderhive.entities.User;
import com.elderhive.entities.UserRoleMapping;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.UserRepository;
import com.elderhive.repositories.UserRoleMappingRepository;
import com.elderhive.services.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleMappingRepository userRoleMappingRepository;
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private User user(Long id, UserTypeEnum userType) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .userType(userType).status(UserStatusEnum.ACTIVE).build();
    }

    private User superAdminCaller() {
        return user(1L, UserTypeEnum.SUPER_ADMIN);
    }

    private User adminCaller() {
        return user(2L, UserTypeEnum.ADMIN);
    }

    @Test
    void listUsersBatchesRoleMappingsInOneQueryRatherThanOnePerRow() {
        User a = user(10L, UserTypeEnum.FAMILY_MEMBER);
        User b = user(11L, UserTypeEnum.CARETAKER);
        Page<User> page = new PageImpl<>(List.of(a, b), PageRequest.of(0, 10), 2);
        when(userRepository.findByCriteria(any(), any(), any(), any())).thenReturn(page);
        when(userRoleMappingRepository.findByUserIdIn(List.of(10L, 11L))).thenReturn(List.of(
                UserRoleMapping.builder().userId(10L).userType(UserTypeEnum.FAMILY_MEMBER).isPrimary(true).build(),
                UserRoleMapping.builder().userId(11L).userType(UserTypeEnum.CARETAKER).isPrimary(true).build()
        ));

        Page<AdminUserResponse> result = adminUserService.listUsers("sam", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getRoles()).containsExactly(UserTypeEnum.FAMILY_MEMBER);
        assertThat(result.getContent().get(1).getRoles()).containsExactly(UserTypeEnum.CARETAKER);
        verify(userRoleMappingRepository, times(1)).findByUserIdIn(any());
        verify(userRoleMappingRepository, never()).findByUserId(any());
    }

    @Test
    void listUsersLowercasesAndWrapsTheSearchTermIntoALikePattern() {
        when(userRepository.findByCriteria(anyString(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        adminUserService.listUsers("  Saksham  ", null, null, PageRequest.of(0, 10));

        verify(userRepository).findByCriteria(eq("%saksham%"), isNull(), isNull(), any());
    }

    @Test
    void listUsersPassesNullPatternWhenSearchIsBlank() {
        when(userRepository.findByCriteria(isNull(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        adminUserService.listUsers("   ", UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE, PageRequest.of(0, 10));

        verify(userRepository).findByCriteria(isNull(), eq(UserTypeEnum.CARETAKER), eq(UserStatusEnum.ACTIVE), any());
    }

    @Test
    void suspendUserSetsStatusToSuspended() throws GenericException {
        User target = user(5L, UserTypeEnum.FAMILY_MEMBER);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRoleMappingRepository.findByUserId(5L)).thenReturn(List.of());

        AdminUserResponse response = adminUserService.suspendUser(5L);

        assertThat(response.getStatus()).isEqualTo(UserStatusEnum.SUSPENDED);
        assertThat(target.getStatus()).isEqualTo(UserStatusEnum.SUSPENDED);
    }

    @Test
    void suspendUserThrowsWhenTargetDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.suspendUser(99L))
                .isInstanceOf(GenericException.class)
                .extracting("exceptionCode").isEqualTo(ExceptionCodeEnum.USER_NOT_FOUND);
    }

    @Test
    void grantRoleOfAnOrdinaryTypeDoesNotRequireSuperAdminCaller() throws GenericException {
        User target = user(6L, UserTypeEnum.FAMILY_MEMBER);
        when(userRepository.findById(6L)).thenReturn(Optional.of(target));
        when(userRoleMappingRepository.existsByUserIdAndUserType(6L, UserTypeEnum.CARETAKER)).thenReturn(false);
        when(userRoleMappingRepository.findByUserId(6L)).thenReturn(List.of(
                UserRoleMapping.builder().userId(6L).userType(UserTypeEnum.FAMILY_MEMBER).isPrimary(true).build()
        ));

        AdminUserResponse response = adminUserService.grantRole(6L, UserTypeEnum.CARETAKER, adminCaller());

        assertThat(response).isNotNull();
        ArgumentCaptor<UserRoleMapping> captor = ArgumentCaptor.forClass(UserRoleMapping.class);
        verify(userRoleMappingRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserTypeEnum.CARETAKER);
        assertThat(captor.getValue().isPrimary()).isFalse();
        assertThat(captor.getValue().getGrantedByUserId()).isEqualTo(adminCaller().getId());
        verify(auditLogService).record(eq(EntityTypeEnum.USER), eq("6"), eq(ActionType.GRANT), eq(adminCaller().getId()), anyString());
    }

    @Test
    void grantingAdminRoleByAPlainAdminCallerIsForbidden() {
        User target = user(7L, UserTypeEnum.FAMILY_MEMBER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> adminUserService.grantRole(7L, UserTypeEnum.ADMIN, adminCaller()))
                .isInstanceOf(GenericException.class)
                .extracting("exceptionCode").isEqualTo(ExceptionCodeEnum.FORBIDDEN);
        verify(userRoleMappingRepository, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void grantingSuperAdminRoleByASuperAdminCallerSucceeds() throws GenericException {
        User target = user(8L, UserTypeEnum.ADMIN);
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(userRoleMappingRepository.existsByUserIdAndUserType(8L, UserTypeEnum.SUPER_ADMIN)).thenReturn(false);
        when(userRoleMappingRepository.findByUserId(8L)).thenReturn(List.of());

        adminUserService.grantRole(8L, UserTypeEnum.SUPER_ADMIN, superAdminCaller());

        verify(userRoleMappingRepository).save(any());
    }

    @Test
    void grantRoleRejectsARoleTheUserAlreadyHolds() {
        User target = user(9L, UserTypeEnum.FAMILY_MEMBER);
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));
        when(userRoleMappingRepository.existsByUserIdAndUserType(9L, UserTypeEnum.CARETAKER)).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.grantRole(9L, UserTypeEnum.CARETAKER, adminCaller()))
                .isInstanceOf(GenericException.class)
                .extracting("exceptionCode").isEqualTo(ExceptionCodeEnum.USER_ROLE_ALREADY_ASSIGNED);
        verify(userRoleMappingRepository, never()).save(any());
    }

    @Test
    void revokeRoleOfAnOrdinaryTypeDeletesTheMappingAndAudits() throws GenericException {
        User target = user(12L, UserTypeEnum.FAMILY_MEMBER);
        UserRoleMapping mapping = UserRoleMapping.builder().id(100L).userId(12L).userType(UserTypeEnum.CARETAKER).isPrimary(false).build();
        when(userRepository.findById(12L)).thenReturn(Optional.of(target));
        when(userRoleMappingRepository.findByUserIdAndUserType(12L, UserTypeEnum.CARETAKER)).thenReturn(Optional.of(mapping));
        when(userRoleMappingRepository.findByUserId(12L)).thenReturn(List.of(
                UserRoleMapping.builder().userId(12L).userType(UserTypeEnum.FAMILY_MEMBER).isPrimary(true).build()
        ));

        adminUserService.revokeRole(12L, UserTypeEnum.CARETAKER, adminCaller());

        verify(userRoleMappingRepository).delete(mapping);
        verify(auditLogService).record(eq(EntityTypeEnum.USER), eq("12"), eq(ActionType.REVOKE), eq(adminCaller().getId()), anyString());
    }

    @Test
    void revokeRoleRejectsRevokingTheUsersPrimaryRole() {
        User target = user(13L, UserTypeEnum.FAMILY_MEMBER);
        UserRoleMapping mapping = UserRoleMapping.builder().id(101L).userId(13L).userType(UserTypeEnum.FAMILY_MEMBER).isPrimary(true).build();
        when(userRepository.findById(13L)).thenReturn(Optional.of(target));
        when(userRoleMappingRepository.findByUserIdAndUserType(13L, UserTypeEnum.FAMILY_MEMBER)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> adminUserService.revokeRole(13L, UserTypeEnum.FAMILY_MEMBER, adminCaller()))
                .isInstanceOf(GenericException.class)
                .extracting("exceptionCode").isEqualTo(ExceptionCodeEnum.PRIMARY_USER_ROLE_CANNOT_BE_REVOKED);
        verify(userRoleMappingRepository, never()).delete(any());
    }

    @Test
    void revokeRoleRejectsARoleTheUserDoesNotHold() {
        User target = user(14L, UserTypeEnum.FAMILY_MEMBER);
        when(userRepository.findById(14L)).thenReturn(Optional.of(target));
        when(userRoleMappingRepository.findByUserIdAndUserType(14L, UserTypeEnum.CARETAKER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.revokeRole(14L, UserTypeEnum.CARETAKER, adminCaller()))
                .isInstanceOf(GenericException.class)
                .extracting("exceptionCode").isEqualTo(ExceptionCodeEnum.USER_ROLE_NOT_ASSIGNED);
    }

    @Test
    void revokingAdminRoleByAPlainAdminCallerIsForbidden() {
        User target = user(15L, UserTypeEnum.ADMIN);
        when(userRepository.findById(15L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> adminUserService.revokeRole(15L, UserTypeEnum.ADMIN, adminCaller()))
                .isInstanceOf(GenericException.class)
                .extracting("exceptionCode").isEqualTo(ExceptionCodeEnum.FORBIDDEN);
        verify(userRoleMappingRepository, never()).delete(any());
    }
}
