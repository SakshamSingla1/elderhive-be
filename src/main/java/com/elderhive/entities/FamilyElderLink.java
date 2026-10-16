package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * An active co-management link between a FAMILY_MEMBER user and an ElderProfile they did
 * not originally create — ElderProfile.familyUserId remains the profile's original owner;
 * rows here are ADDITIONAL family members sharing management of the same elder. Mirrors
 * ElderSphere's entity minus {@code invitedByUserId}: this prompt's own field list is
 * exactly {@code elderProfileId, familyUserId, relationshipLabel}, and there's no invite
 * flow yet to populate that column meaningfully (Prompt 63). Removal is a hard delete —
 * there's no "revoked" state to track for an already-active link.
 */
@Entity
@Table(name = "family_elder_links")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyElderLink extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_profile_id", nullable = false)
    private Long elderProfileId;

    @Column(name = "family_user_id", nullable = false)
    private Long familyUserId;

    @Column(name = "relationship_label")
    private String relationshipLabel;
}
