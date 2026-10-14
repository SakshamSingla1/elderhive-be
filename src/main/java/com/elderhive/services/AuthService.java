package com.elderhive.services;

import com.elderhive.dtos.Authentication.RegisterRequest;
import com.elderhive.dtos.Authentication.RegisterResponse;
import com.elderhive.exceptions.GenericException;

public interface AuthService {

    RegisterResponse register(RegisterRequest request) throws GenericException;
}
