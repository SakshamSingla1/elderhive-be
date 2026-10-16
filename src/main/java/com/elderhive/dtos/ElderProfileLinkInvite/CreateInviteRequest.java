package com.elderhive.dtos.ElderProfileLinkInvite;

import com.elderhive.enums.UserTypeEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Exactly one of invitedEmail/invitedPhone is expected ("invitedEmail or invitedPhone" per
 * this prompt's own text) - enforced in the service layer (both blank, or both present, is
 * rejected there) rather than via a custom cross-field bean-validation annotation, matching
 * this project's existing preference for simple manual checks over new annotations.
 */
@Data
public class CreateInviteRequest {

    private String invitedEmail;

    private String invitedPhone;

    @NotNull(message = "Invited role is required")
    private UserTypeEnum invitedRole;

    private String relationshipLabel;
}
