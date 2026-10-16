package com.elderhive.dtos.FamilyElderLink;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FamilyElderLinkRequest {

    @NotNull(message = "Family user ID is required")
    private Long familyUserId;

    private String relationshipLabel;
}
