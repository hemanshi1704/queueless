package com.queueless.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueless.domain.Owner;
import com.queueless.repo.OwnerRepository;
import com.queueless.security.JwtService;
import com.queueless.web.ApiException;
import com.queueless.web.Dtos.AuthResponse;
import com.queueless.web.Dtos.LoginRequest;
import com.queueless.web.Dtos.RegisterRequest;

@Service
public class AuthService {

    private final OwnerRepository owners;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(OwnerRepository owners, PasswordEncoder encoder, JwtService jwt) {
        this.owners = owners;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (owners.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        Owner owner = owners.save(new Owner(email, encoder.encode(req.password()), req.name().trim()));
        return new AuthResponse(jwt.issue(owner), owner.getName(), owner.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        Owner owner = owners.findByEmail(email)
                .filter(o -> encoder.matches(req.password(), o.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        return new AuthResponse(jwt.issue(owner), owner.getName(), owner.getEmail());
    }
}
