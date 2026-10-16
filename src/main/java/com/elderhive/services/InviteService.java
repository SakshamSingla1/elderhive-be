package com.elderhive.services;

import com.elderhive.dtos.ElderProfileLinkInvite.CreateInviteRequest;
import com.elderhive.dtos.ElderProfileLinkInvite.InviteResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface InviteService {

    InviteResponse createInvite(Long elderProfileId, CreateInviteRequest request, User caller) throws GenericException;

    List<InviteResponse> getInvitesForProfile(Long elderProfileId);

    InviteResponse accept(String token, User caller) throws GenericException;

    InviteResponse decline(String token, User caller) throws GenericException;
}
