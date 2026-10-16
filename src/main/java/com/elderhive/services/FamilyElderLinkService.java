package com.elderhive.services;

import com.elderhive.dtos.FamilyElderLink.FamilyElderLinkRequest;
import com.elderhive.dtos.FamilyElderLink.FamilyMemberSummaryResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface FamilyElderLinkService {

    /** Owner (ElderProfile.familyUserId) or ADMIN/SUPER_ADMIN only. Throws INVALID_ROLE if
     *  the target user isn't a FAMILY_MEMBER, CONFLICT if they're already the owner or
     *  already linked. */
    FamilyMemberSummaryResponse addFamilyMember(Long elderProfileId, FamilyElderLinkRequest request, User caller)
            throws GenericException;

    /** Same access rule as addFamilyMember(). Never removes the original owner — there's no
     *  FamilyElderLink row for them to delete in the first place. */
    void removeFamilyMember(Long elderProfileId, Long targetUserId, User caller) throws GenericException;

    /**
     * Every family member with standing to manage elderProfileId — the original owner
     * (ElderProfile.familyUserId, isOwner=true) plus every accepted co-managing member
     * (FamilyElderLink rows, isOwner=false) — in one flat list. A service-layer merge
     * (owner lookup + {@link com.elderhive.repositories.FamilyElderLinkRepository
     * #findByElderProfileId}), not a UNION SQL query: simpler to read and test, and this
     * table is small per profile (a handful of family members at most).
     *
     * <p><b>Deliberately takes no caller/access-check parameter</b> — unlike every other
     * method here, this is meant to be called unconditionally by trusted internal code (the
     * controller's own GET endpoint pre-checks access via ElderProfileService#getById
     * before calling this; Phase 11's emergency fan-out calls this directly with no HTTP
     * caller at all). This is the one method Phase 11 depends on being complete — if this
     * entity's shape or this method's contract ever changes, double-check Phase 11's
     * emergency-alert fan-out still gets every family member, not just co-managers.
     */
    List<FamilyMemberSummaryResponse> findAllByElderProfileId(Long elderProfileId) throws GenericException;
}
