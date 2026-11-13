package com.leaseledger.user;

import com.leaseledger.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    public record MeResponse(Long id, String email, String fullName, Role role) {
    }

    /** Returns the profile of whoever owns the token on this request. */
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        User user = users.findById(caller.id())
                // A valid token for a user that no longer exists: treat as not logged in.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
