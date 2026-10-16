package com.elderhive.repositories;

import com.elderhive.entities.ElderProfileLinkInvite;
import com.elderhive.enums.LinkInviteStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElderProfileLinkInviteRepository extends JpaRepository<ElderProfileLinkInvite, Long> {

    Optional<ElderProfileLinkInvite> findByToken(String token);

    List<ElderProfileLinkInvite> findByElderProfileIdOrderByCreatedAtDesc(Long elderProfileId);

    boolean existsByElderProfileIdAndInvitedEmailAndInvitedRoleAndStatus(
            Long elderProfileId, String invitedEmail, UserTypeEnum invitedRole, LinkInviteStatusEnum status);

    boolean existsByElderProfileIdAndInvitedPhoneAndInvitedRoleAndStatus(
            Long elderProfileId, String invitedPhone, UserTypeEnum invitedRole, LinkInviteStatusEnum status);
}
