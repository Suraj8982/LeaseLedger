package com.leaseledger.security;

import com.leaseledger.user.Role;

/**
 * Who is making the current request. Built from the JWT alone, with no database
 * lookup, and injected into controllers with {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(Long id, Role role) {
}
