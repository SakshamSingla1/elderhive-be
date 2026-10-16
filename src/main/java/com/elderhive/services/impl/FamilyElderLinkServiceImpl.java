package com.elderhive.services.impl;

import com.elderhive.dtos.FamilyElderLink.FamilyElderLinkRequest;
import com.elderhive.dtos.FamilyElderLink.FamilyMemberSummaryResponse;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.FamilyElderLink;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.FamilyElderLinkRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.FamilyElderLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FamilyElderLinkServiceImpl implements FamilyElderLinkService {

    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final FamilyElderLinkRepository familyElderLinkRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FamilyMemberSummaryResponse addFamilyMember(Long elderProfileId, FamilyElderLinkRequest request, User caller)
            throws GenericException {
        ElderProfile profile = findProfileOrThrow(elderProfileId);
        assertIsOwnerOrAdmin(profile, caller);

        User target = userRepository.findById(request.getFamilyUserId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found"));
        if (target.getUserType() != UserTypeEnum.FAMILY_MEMBER) {
            throw new GenericException(ExceptionCodeEnum.INVALID_ROLE, "Only a FAMILY_MEMBER account can co-manage an elder profile");
        }
        if (Objects.equals(profile.getFamilyUserId(), target.getId())) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "That user already owns this elder profile");
        }
        if (familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(elderProfileId, target.getId())) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "That user already co-manages this elder profile");
        }

        FamilyElderLink link = familyElderLinkRepository.save(FamilyElderLink.builder()
                .elderProfileId(elderProfileId)
                .familyUserId(target.getId())
                .relationshipLabel(request.getRelationshipLabel())
                .build());

        return FamilyMemberSummaryResponse.builder()
                .userId(target.getId())
                .fullName(target.getFullName())
                .relationshipLabel(link.getRelationshipLabel())
                .isOwner(false)
                .build();
    }

    @Override
    @Transactional
    public void removeFamilyMember(Long elderProfileId, Long targetUserId, User caller) throws GenericException {
        ElderProfile profile = findProfileOrThrow(elderProfileId);
        assertIsOwnerOrAdmin(profile, caller);

        if (!familyElderLinkRepository.existsByElderProfileIdAndFamilyUserId(elderProfileId, targetUserId)) {
            throw new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "That user is not a linked family member of this profile");
        }
        familyElderLinkRepository.deleteByElderProfileIdAndFamilyUserId(elderProfileId, targetUserId);
    }

    @Override
    public List<FamilyMemberSummaryResponse> findAllByElderProfileId(Long elderProfileId) throws GenericException {
        ElderProfile profile = findProfileOrThrow(elderProfileId);
        List<FamilyElderLink> links = familyElderLinkRepository.findByElderProfileId(elderProfileId);

        List<Long> userIds = new ArrayList<>(links.stream().map(FamilyElderLink::getFamilyUserId).toList());
        if (profile.getFamilyUserId() != null) {
            userIds.add(profile.getFamilyUserId());
        }
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<FamilyMemberSummaryResponse> members = new ArrayList<>();
        if (profile.getFamilyUserId() != null) {
            User owner = usersById.get(profile.getFamilyUserId());
            members.add(FamilyMemberSummaryResponse.builder()
                    .userId(profile.getFamilyUserId())
                    .fullName(owner != null ? owner.getFullName() : null)
                    .isOwner(true)
                    .build());
        }
        for (FamilyElderLink link : links) {
            User member = usersById.get(link.getFamilyUserId());
            members.add(FamilyMemberSummaryResponse.builder()
                    .userId(link.getFamilyUserId())
                    .fullName(member != null ? member.getFullName() : null)
                    .relationshipLabel(link.getRelationshipLabel())
                    .isOwner(false)
                    .build());
        }
        return members;
    }

    private ElderProfile findProfileOrThrow(Long elderProfileId) throws GenericException {
        return elderProfileRepository.findById(elderProfileId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.ELDER_PROFILE_NOT_FOUND, "Elder profile not found"));
    }

    private void assertIsOwnerOrAdmin(ElderProfile profile, User caller) throws GenericException {
        if (ADMIN_TIER.contains(caller.getUserType())) {
            return;
        }
        boolean isOwner = profile.getFamilyUserId() != null && Objects.equals(profile.getFamilyUserId(), caller.getId());
        if (!isOwner) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN, "Only the profile owner can manage co-managing family members");
        }
    }
}
