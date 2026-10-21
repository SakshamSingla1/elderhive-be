package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.enums.DayOfWeekEnum;
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

import java.time.LocalTime;

/**
 * Mirrors ElderSphere's CaretakerAvailability field-for-field - a simple weekly recurring
 * schedule, one row per (caretaker, day-of-week, time-range) slot. Date-specific overrides
 * (a one-off unavailable day, a holiday closure, etc.) are explicitly out of scope per this
 * prompt's own text - a future phase's job, not modeled here.
 */
@Entity
@Table(name = "caretaker_availability")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaretakerAvailability extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caretaker_id", nullable = false)
    private Long caretakerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeekEnum dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
