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

/** Mirrors ElderSphere's FavoriteCaretaker field-for-field - a simple bookmark join row, a
 *  family member saving a caretaker for quick re-booking. Unique on (familyUserId,
 *  caretakerId) so the same pair can't be favorited twice. */
@Entity
@Table(name = "favorite_caretakers")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteCaretaker extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_user_id", nullable = false)
    private Long familyUserId;

    @Column(name = "caretaker_id", nullable = false)
    private Long caretakerId;
}
