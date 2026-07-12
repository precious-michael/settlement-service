package org.settlementservice.settlementservice.services.impl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.request.LoginRequest;
import org.settlementservice.settlementservice.dtos.response.LoginResponse;
import org.settlementservice.settlementservice.security.JwtService;
import org.settlementservice.settlementservice.services.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${jwt.expiration-minutes}")
    private long expirationTime;

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        String token = jwtService.generateToken(authentication);
        return new LoginResponse(token, expirationTime);
    }
}
