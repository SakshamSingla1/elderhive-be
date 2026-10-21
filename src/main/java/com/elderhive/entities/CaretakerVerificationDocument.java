package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.DocumentReviewStatusEnum;
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
 * Join row linking a CaretakerProfile (Prompt 64, {@code caretakerId} FKs to its {@code id})
 * to a FileAsset the caretaker submitted as verification evidence. Adds {@code reviewStatus}
 * on top of ElderSphere's own shape - that reference entity has no per-document review
 * state at all, only a profile-level verification status - so an admin can approve/reject
 * each submitted document individually here. {@code docType} is a plain string (ID proof,
 * certification, background check, ...), not an enum - this prompt doesn't ask for one and
 * a fixed picklist belongs in the frontend, not a hardcoded Java enum. No separate
 * {@code uploadedBy} column either (ElderSphere has one) - the inherited
 * {@code Auditable.createdBy} already captures that, matching Prompt 63's precedent of not
 * duplicating what auditing already provides.
 */
@Entity
@Table(name = "caretaker_verification_documents")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaretakerVerificationDocument extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caretaker_id", nullable = false)
    private Long caretakerId;

    @Column(name = "file_asset_id", nullable = false)
    private Long fileAssetId;

    @Column(name = "doc_type")
    private String docType;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private DocumentReviewStatusEnum reviewStatus;
}
