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

import java.time.LocalDateTime;

/**
 * Mirrors ~/ElderSphere/eldersphere-be's Conversation field-for-field, including the
 * denormalized {@code lastMessageAt} (set by MessagingServiceImpl#sendMessage on every new
 * message) - not mentioned explicitly in Prompt 89's own field list, but needed to order a
 * "my conversations" listing by recency without a join/subquery against messages, same
 * precedent as Review.caretakerId's own denormalization. All FKs are plain scalar fields, no
 * {@code @ManyToOne}, matching this project's established cross-entity-reference convention.
 */
@Entity
@Table(name = "conversations")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_a_id", nullable = false)
    private Long userAId;

    @Column(name = "user_b_id", nullable = false)
    private Long userBId;

    // Optional - links this conversation to the booking it's about, when it started from
    // one (e.g. a family member messaging the caretaker they just booked). Null for a
    // conversation started independent of any booking.
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
}
