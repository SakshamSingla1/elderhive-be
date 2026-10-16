package com.elderhive.dtos.ElderProfileLinkInvite;

import com.elderhive.enums.LinkInviteStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * token is deliberately included here (not just logged by the simulated EmailService) - with
 * no real SMTP wiring yet (EmailServiceImpl's own documented MVP placeholder), the invite
 * creator has no other way to actually hand the invite link to anyone in this phase.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteResponse {

    private Long id;
    private Long elderProfileId;
    private String invitedEmail;
    private String invitedPhone;
    private UserTypeEnum invitedRole;
    private String relationshipLabel;
    private LinkInviteStatusEnum status;
    private String token;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
