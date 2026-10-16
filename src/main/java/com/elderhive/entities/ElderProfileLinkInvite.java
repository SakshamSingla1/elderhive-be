package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.LinkInviteStatusEnum;
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
 * A material departure from ElderSphere's own ElderProfileLinkInvite, which targets an
 * already-registered {@code invitedUserId} with no email/phone/token at all. This prompt's
 * own field list explicitly adds {@code invitedEmail}/{@code invitedPhone} + {@code token}
 * so an invite can reach someone who hasn't registered yet (resolved by email/phone match at
 * accept time, not by a foreign key to an existing user). {@code invitedByUserId} is
 * deliberately not modeled as its own column either — it's already captured by the inherited
 * {@code Auditable.createdBy}, matching Prompt 62's own precedent of not porting a field
 * ElderSphere has but this prompt's text doesn't ask for. {@code invitedRole} reuses the
 * existing UserTypeEnum rather than a new narrower enum (validated at the service layer to be
 * ELDER or FAMILY_MEMBER only, per this prompt's own text). There is no REVOKED status —
 * ElderSphere's 4th status is not in this prompt's explicit PENDING/ACCEPTED/DECLINED list,
 * and no revoke action was asked for.
 */
@Entity
@Table(name = "elder_profile_link_invites")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElderProfileLinkInvite extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_profile_id", nullable = false)
    private Long elderProfileId;

    @Column(name = "invited_email")
    private String invitedEmail;

    @Column(name = "invited_phone")
    private String invitedPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "invited_role", nullable = false)
    private UserTypeEnum invitedRole;

    @Column(name = "relationship_label")
    private String relationshipLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LinkInviteStatusEnum status;

    @Column(unique = true, nullable = false)
    private String token;
}
