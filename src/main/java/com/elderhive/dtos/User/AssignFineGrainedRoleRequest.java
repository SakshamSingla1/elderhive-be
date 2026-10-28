package com.elderhive.dtos.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code roleId} is deliberately nullable - a null value clears the target's fine-grained
 * role (back to the fail-closed "no role assigned yet" state), rather than being a
 * validation error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignFineGrainedRoleRequest {

    private Long roleId;
}
