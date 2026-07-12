package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.request.LoginRequest;
import org.settlementservice.settlementservice.dtos.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
