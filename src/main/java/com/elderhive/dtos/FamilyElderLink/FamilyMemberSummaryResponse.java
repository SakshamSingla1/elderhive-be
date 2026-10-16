package com.elderhive.dtos.FamilyElderLink;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One entry in FamilyElderLinkService#findAllByElderProfileId's merged result — either the
 *  profile's original owner (isOwner=true, relationshipLabel=null) or an additional
 *  co-managing family member linked via FamilyElderLink. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberSummaryResponse {

    private Long userId;
    private String fullName;
    private String relationshipLabel;
    // See NotificationResponse.isRead's own comment - Lombok's isOwner() getter for this
    // already-"is"-prefixed field gets its "is" stripped AGAIN by Jackson's default bean
    // naming, serializing as "owner" instead of "isOwner" unless pinned explicitly.
    @Getter(onMethod_ = @__(@JsonProperty("isOwner")))
    private boolean isOwner;
}
