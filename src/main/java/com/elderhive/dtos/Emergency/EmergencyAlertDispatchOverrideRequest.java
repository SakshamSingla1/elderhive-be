package com.elderhive.dtos.Emergency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Exactly one of the two fields below must be set (checked in
 * EmergencyAlertServiceImpl#dispatchOverride, not Bean Validation - a cross-field rule):
 * {@code respondingCaretakerId} reassigns (or newly assigns) who's responding, without
 * resolving the alert; {@code forceResolve=true} resolves it immediately regardless of
 * whether anyone has claimed it, for the "nothing gets claimed" case prompt 84 calls out.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlertDispatchOverrideRequest {

    private Long respondingCaretakerId;
    private boolean forceResolve;
}
