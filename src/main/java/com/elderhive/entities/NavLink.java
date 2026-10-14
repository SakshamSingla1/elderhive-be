package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.NavLinkStatusEnum;
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
 * One sidebar entry. Compared against both Portfolio's NavLink (flat, no fine-grained
 * gating) and ElderSphere's (adds requiredPermission + superAdminOnly on top of the same
 * navGroup/navIndex/name/path/icon/status shape) — ElderSphere's two gating fields are kept
 * here because ElderHive needs the same fine-grained admin RBAC this project's Permission
 * catalog (Prompt 20) exists for.
 *
 * <p><b>How requiredPermission and superAdminOnly interact during resolution</b> (the nav
 * list a given caller actually sees — built in Prompt 25, mirroring
 * ElderSphereNavLinkServiceImpl#getMine): the two gates are independent and both must pass.
 * <ul>
 *   <li>{@code superAdminOnly = true} hides the link from everyone except a SUPER_ADMIN
 *       caller, regardless of {@code requiredPermission} — a non-SUPER_ADMIN never sees it
 *       even if their role happens to carry the required permission.</li>
 *   <li>{@code requiredPermission}, when non-null, is checked only for an ADMIN caller who
 *       has a {@code roleId} assigned — resolved by looking up that role's granted
 *       permissions and testing whether {@code requiredPermission} (matched against
 *       Permission.code) is among them. It is a no-op (always passes) for SUPER_ADMIN, for
 *       any non-ADMIN-tier caller, and for an ADMIN with no roleId assigned yet (today's
 *       default - nobody loses access before fine-grained roles are actually assigned).</li>
 *   <li>A link with both fields set therefore reads as "visible only to SUPER_ADMIN" in
 *       practice - the superAdminOnly gate already excludes every caller the permission gate
 *       would otherwise evaluate for real (ADMIN-with-roleId), since a SUPER_ADMIN passes
 *       requiredPermission unconditionally too. Setting both is redundant, not conflicting.</li>
 *   <li>A link with neither field set is visible to every caller whose userType/portal scope
 *       otherwise includes it - no fine-grained gate at all.</li>
 * </ul>
 */
@Entity
@Table(name = "nav_links")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavLink extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nav_group")
    private String navGroup;

    @Column(name = "nav_index", nullable = false)
    @Builder.Default
    private Integer navIndex = 0;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    private String icon;

    // Soft reference (matched by value, no FK) to Permission.code - see the class-level
    // javadoc above for exactly how this interacts with superAdminOnly during resolution.
    @Column(name = "required_permission")
    private String requiredPermission;

    @Column(name = "super_admin_only", nullable = false)
    @Builder.Default
    private Boolean superAdminOnly = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NavLinkStatusEnum status = NavLinkStatusEnum.ACTIVE;
}
