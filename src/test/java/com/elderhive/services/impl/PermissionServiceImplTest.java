package com.elderhive.services.impl;

import com.elderhive.dtos.Permission.PermissionResponse;
import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.entities.RolePermission;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Test
    void listsEveryPermissionInTheCatalog() {
        when(permissionRepository.findAll()).thenReturn(List.of(
                Permission.builder().id(1L).code("USER_MANAGE").description("d1").build(),
                Permission.builder().id(2L).code("ROLE_MANAGE").description("d2").build()));

        List<PermissionResponse> result = permissionService.getAll();

        assertThat(result).extracting(PermissionResponse::getCode).containsExactly("USER_MANAGE", "ROLE_MANAGE");
    }

    @Test
    void assignsAPermissionToARole() throws GenericException {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(Role.builder().id(1L).name("Support").build()));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(Permission.builder().id(10L).code("USER_MANAGE").build()));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(1L, 10L)).thenReturn(false);

        String message = permissionService.assignToRole(1L, 10L);

        assertThat(message).isNotBlank();
        ArgumentCaptor<RolePermission> captor = ArgumentCaptor.forClass(RolePermission.class);
        verify(rolePermissionRepository).save(captor.capture());
        assertThat(captor.getValue().getRoleId()).isEqualTo(1L);
        assertThat(captor.getValue().getPermissionId()).isEqualTo(10L);
    }

    @Test
    void rejectsAssigningAnAlreadyAssignedPermission() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(Role.builder().id(1L).name("Support").build()));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(Permission.builder().id(10L).code("USER_MANAGE").build()));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> permissionService.assignToRole(1L, 10L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);

        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    void assignThrowsWhenTheRoleDoesNotExist() {
        when(roleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.assignToRole(1L, 10L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);

        verify(permissionRepository, never()).findById(any());
    }

    @Test
    void assignThrowsWhenThePermissionDoesNotExist() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(Role.builder().id(1L).name("Support").build()));
        when(permissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.assignToRole(1L, 99L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);

        verify(rolePermissionRepository, never()).existsByRoleIdAndPermissionId(any(), any());
    }

    @Test
    void unassignsAnExistingAssignment() throws GenericException {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(Role.builder().id(1L).name("Support").build()));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(Permission.builder().id(10L).code("USER_MANAGE").build()));
        RolePermission existing = RolePermission.builder().id(5L).roleId(1L).permissionId(10L).build();
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of(existing));

        String message = permissionService.unassignFromRole(1L, 10L);

        assertThat(message).isNotBlank();
        verify(rolePermissionRepository).delete(existing);
    }

    @Test
    void rejectsUnassigningAPermissionThatIsNotCurrentlyAssigned() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(Role.builder().id(1L).name("Support").build()));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(Permission.builder().id(10L).code("USER_MANAGE").build()));
        when(rolePermissionRepository.findByRoleId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> permissionService.unassignFromRole(1L, 10L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);

        verify(rolePermissionRepository, never()).delete(any());
    }
}
