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
 * a user can additionally hold other roles at once via UserRoleMapping. No activeThemeId
 * here — ElderHive tracks a user's active color theme via a reverse-lookup
 * UserThemeMapping table instead (see IMPLEMENTATION_PLAN.md section 2, point 9).
 *
 * <p><b>roleId is a SEPARATE concept from userType/UserRoleMapping</b> — see the field
 * javadoc below and the "UserType vs. Role/Permission" section of the root CLAUDE.md for
 * the full picture.
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

    /**
     * Nullable FK to the fine-grained Role table (Prompt 20) — only ever meaningful for an
     * ADMIN. userType decides WHICH of the 5 portals a user can log into at all; roleId
     * further restricts WHAT an ADMIN specifically can do once inside the admin portal, by
     * way of that Role's granted Permissions. The two are independent axes, not a hierarchy:
     * an ELDER/FAMILY_MEMBER/CARETAKER/SUPER_ADMIN's roleId (if any) is never consulted —
     * only an ADMIN's is (enforced by the permission guard built in Prompt 23).
     *
     * <p>Null means "no fine-grained role assigned yet" — an ADMIN in that state keeps full
     * admin-portal access (fail-open for today's default, same as ElderSphere's equivalent
     * guard), not no access. SUPER_ADMIN always has full access regardless of roleId — it
     * never goes through the permission guard at all — but still gets assigned a Role row
     * by the seeder (Prompt 24) purely so the admin UI has something consistent to display.
     */
    @Column(name = "role_id")
    private Long roleId;
}
