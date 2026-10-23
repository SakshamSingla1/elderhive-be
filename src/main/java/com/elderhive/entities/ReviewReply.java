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
 * Mirrors ~/ElderSphere/eldersphere-be's ReviewReply field-for-field. caretakerId is
 * denormalized from the parent Review (same precedent as Review.caretakerId itself) -
 * mostly for symmetry/future querying convenience, since the owning review is always
 * loaded first anyway to authorize a reply (only the reviewed caretaker may reply, Prompt
 * 88's own documented rule).
 */
@Entity
@Table(name = "review_replies")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReply extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique - one reply per review (Prompt 88's own documented rule). ReviewServiceImpl's
    // upsertReply does exactly that: update-in-place if a reply already exists, insert
    // otherwise - never a second row for the same review.
    @Column(name = "review_id", nullable = false, unique = true)
    private Long reviewId;

    @Column(name = "caretaker_id", nullable = false)
    private Long caretakerId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
}
