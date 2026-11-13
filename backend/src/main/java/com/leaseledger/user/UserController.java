package com.leaseledger.user;

import com.leaseledger.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    /** Returns the profile of whoever owns the token on this request. */
    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal AuthenticatedUser caller) {
        User user = users.findById(caller.id())
                // A valid token for a user that no longer exists: treat as not logged in.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return UserResponse.from(user);
    }
}
