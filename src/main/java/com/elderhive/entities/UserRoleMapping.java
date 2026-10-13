package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A single role held by a user. A user can hold several of these at once (e.g.
 * FAMILY_MEMBER + CARETAKER); exactly one of them has isPrimary = true, whose
 * userType must always match User.userType.
 *
 * NOT related to the fine-grained Role/Permission/RolePermission RBAC system that
 * gates the admin panel (that lands in Phase 3) — this table is named "mapping"
 * specifically to avoid being confused with that system.
 */
@Entity
@Table(name = "user_role_mappings")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleMapping extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserTypeEnum userType;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "granted_by_user_id")
    private Long grantedByUserId;
}
