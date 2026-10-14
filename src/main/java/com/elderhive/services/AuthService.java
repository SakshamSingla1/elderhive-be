package com.elderhive.services;

import com.elderhive.dtos.Authentication.LoginRequest;
import com.elderhive.dtos.Authentication.LoginResult;
import com.elderhive.dtos.Authentication.RefreshResult;
import com.elderhive.dtos.Authentication.RegisterRequest;
import com.elderhive.dtos.Authentication.RegisterResponse;
import com.elderhive.exceptions.GenericException;

public interface AuthService {

    RegisterResponse register(RegisterRequest request) throws GenericException;

    LoginResult login(LoginRequest request) throws GenericException;

    /** Validates rawRefreshToken, rotates it (revokes the old one, issues a new one),
     *  and returns a fresh access token alongside it. */
    RefreshResult refresh(String rawRefreshToken) throws GenericException;

    /** Revokes rawRefreshToken server-side if present — never throws; logging out with
     *  no cookie, or an already-invalid one, is still a successful logout. */
    void logout(String rawRefreshToken);
}
