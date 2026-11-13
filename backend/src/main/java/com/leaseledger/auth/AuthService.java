package com.leaseledger.auth;

import com.leaseledger.auth.AuthDtos.AuthResponse;
import com.leaseledger.auth.AuthDtos.LoginRequest;
import com.leaseledger.auth.AuthDtos.RegisterRequest;
import com.leaseledger.security.JwtService;
import com.leaseledger.user.Role;
import com.leaseledger.user.User;
import com.leaseledger.user.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be MANAGER or TENANT");
        }
        User user = new User(
                normalize(request.email()),
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.role());
        try {
            // No "does this email exist?" query first: two simultaneous requests could both
            // pass that check. The unique constraint is the only race-free guard, so we
            // insert and translate its violation.
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        return tokenFor(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                // Same error for "no such email" and "wrong password", so an attacker
                // cannot use this endpoint to find out which emails are registered.
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        return new AuthResponse(
                jwtService.issue(user.getId(), user.getRole()),
                "Bearer",
                jwtService.ttlSeconds(),
                user.getId(),
                user.getRole());
    }

    /** Emails are case-insensitive in practice: Suraj@x.com and suraj@x.com are one account. */
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
