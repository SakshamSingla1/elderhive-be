package com.elderhive.services.impl;

import com.elderhive.dtos.Role.RoleRequest;
import com.elderhive.dtos.Role.RoleResponse;
import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.entities.RolePermission;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.RoleStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    private RoleRequest validRequest() {
        return RoleRequest.builder().name("Support").description("Support staff").status(RoleStatusEnum.ACTIVE).build();
    }

    @Test
    void createsARole() throws GenericException {
        RoleRequest request = validRequest();
        when(roleRepository.existsByName("Support")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of());

        RoleResponse response = roleService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Support");
        assertThat(response.getStatus()).isEqualTo(RoleStatusEnum.ACTIVE);
        assertThat(response.getPermissionCodes()).isEmpty();
    }

    @Test
    void rejectsADuplicateRoleNameOnCreate() {
        RoleRequest request = validRequest();
        when(roleRepository.existsByName("Support")).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateAllowsKeepingTheSameNameWithoutTriggeringAConflict() throws GenericException {
        Role existing = Role.builder().id(1L).name("Support").description("old").status(RoleStatusEnum.ACTIVE).build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of());

        RoleRequest request = RoleRequest.builder().name("Support").description("updated").status(RoleStatusEnum.ACTIVE).build();

        RoleResponse response = roleService.update(1L, request);

        assertThat(response.getDescription()).isEqualTo("updated");
        verify(roleRepository, never()).existsByName(any());
    }

    @Test
    void rejectsRenamingToAnAlreadyTakenName() {
        Role existing = Role.builder().id(1L).name("Support").status(RoleStatusEnum.ACTIVE).build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.existsByName("Operations")).thenReturn(true);

        RoleRequest request = RoleRequest.builder().name("Operations").status(RoleStatusEnum.ACTIVE).build();

        assertThatThrownBy(() -> roleService.update(1L, request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void getByIdThrowsWhenTheRoleDoesNotExist() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getById(99L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);
    }

    @Test
    void deleteThrowsWhenTheRoleDoesNotExistAndNeverCallsDelete() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.delete(99L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);

        verify(roleRepository, never()).delete(any());
    }

    @Test
    void getByIdResolvesGrantedPermissionCodesFromRolePermissionRows() throws GenericException {
        Role role = Role.builder().id(1L).name("Support").status(RoleStatusEnum.ACTIVE).build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of(
                RolePermission.builder().roleId(1L).permissionId(10L).build(),
                RolePermission.builder().roleId(1L).permissionId(11L).build()));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(Permission.builder().id(10L).code("USER_MANAGE").build()));
        when(permissionRepository.findById(11L)).thenReturn(Optional.of(Permission.builder().id(11L).code("DASHBOARD_VIEW").build()));

        RoleResponse response = roleService.getById(1L);

        assertThat(response.getPermissionCodes()).containsExactlyInAnyOrder("USER_MANAGE", "DASHBOARD_VIEW");
    }
}
