package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.GenderEnum;
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

import java.time.LocalDate;

/**
 * Mirrors ElderSphere's ElderProfile field-for-field, renaming its {@code name} to
 * {@code fullName} (matching ElderHive's own User.fullName naming) per this prompt's own
 * text. Both owner columns are nullable by design, not just by omission:
 * {@code familyUserId} is set at creation when a family member builds the profile on an
 * elder's behalf; {@code elderUserId} starts null and is set later, once the elder in
 * question links their own login (a later phase's job — this prompt only models the
 * column). A profile can have either, both, or (immediately after self-creation by an
 * ELDER caller) just {@code elderUserId}.
 *
 * <p>Co-management by additional family members (ElderSphere's FamilyElderLink) is
 * explicitly out of scope here — that's Prompt 62. This entity only models the single
 * owning family member and the optionally-linked elder.
 */
@Entity
@Table(name = "elder_profiles")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElderProfile extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_user_id")
    private Long familyUserId;

    @Column(name = "elder_user_id")
    private Long elderUserId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private GenderEnum gender;

    @Column(name = "medical_conditions", columnDefinition = "TEXT")
    private String medicalConditions;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;
}
