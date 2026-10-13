package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.UserStatusEnum;
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
 * userType is the single PRIMARY role (which of the 5 portals this account logs into) —
 * a user can additionally hold other roles at once via UserRoleMapping. Deliberately
 * lean compared to ElderSphere's User: no roleId column here yet (added in a later
 * prompt once the fine-grained Role/Permission system exists) and no activeThemeId at
 * all — ElderHive tracks a user's active color theme via a reverse-lookup
 * UserThemeMapping table instead (see IMPLEMENTATION_PLAN.md section 2, point 9).
 */
@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserTypeEnum userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatusEnum status;
}
