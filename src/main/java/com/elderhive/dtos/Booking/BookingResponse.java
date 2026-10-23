package com.elderhive.dtos.Booking;

import com.elderhive.enums.BookingStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private Long familyUserId;
    private Long elderProfileId;
    // Denormalized from ElderProfile/CaretakerProfile+User/ServiceOffering for browsing
    // convenience, same precedent as CaretakerProfileResponse's fullName/email/phone and
    // ServiceOfferingResponse's categoryName.
    private String elderName;
    private Long caretakerId;
    private String caretakerName;
    // Added Prompt 92 - lets a booking's caretaker be matched against
    // presence.PresenceService (keyed by user id, not caretakerId/CaretakerProfile's own
    // id) and used to start a Messaging conversation (ConversationCreateRequest.otherUserId
    // is also a user id) without a second round trip.
    private Long caretakerUserId;
    private Long serviceOfferingId;
    private String serviceName;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private BookingStatusEnum status;
    private BigDecimal cost;
    private String notes;
    private String recurringGroupId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
