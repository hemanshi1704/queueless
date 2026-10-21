package com.queueless.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.service.AuthService;
import com.queueless.web.Dtos.AuthResponse;
import com.queueless.web.Dtos.LoginRequest;
import com.queueless.web.Dtos.RegisterRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }
}
