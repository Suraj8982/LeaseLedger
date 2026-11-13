package com.leaseledger.user;

/**
 * What the API returns when it describes a user. A separate type from the
 * {@link User} entity so that fields such as the password hash can never
 * leak into a JSON response.
 */
public record UserResponse(Long id, String email, String fullName, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
