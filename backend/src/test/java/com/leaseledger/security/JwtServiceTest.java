package com.leaseledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leaseledger.user.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Plain unit test: no Spring context, runs in milliseconds. */
class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("test-secret-that-is-at-least-32-bytes-long".getBytes());
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("a-different-secret-also-32-bytes-or-more!".getBytes());
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static JwtService serviceAt(String secret, Instant instant) {
        return new JwtService(secret, Duration.ofMinutes(60), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void tokenRoundTripsUserIdAndRole() {
        JwtService jwt = serviceAt(SECRET, NOW);

        AuthenticatedUser caller = jwt.verify(jwt.issue(42L, Role.MANAGER));

        assertThat(caller).isEqualTo(new AuthenticatedUser(42L, Role.MANAGER));
    }

    @Test
    void tokenIsRejectedAfterItExpires() {
        String token = serviceAt(SECRET, NOW).issue(42L, Role.TENANT);
        JwtService sixtyOneMinutesLater = serviceAt(SECRET, NOW.plus(Duration.ofMinutes(61)));

        assertThatThrownBy(() -> sixtyOneMinutesLater.verify(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = serviceAt(OTHER_SECRET, NOW).issue(42L, Role.ADMIN);

        assertThatThrownBy(() -> serviceAt(SECRET, NOW).verify(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithEditedPayloadIsRejected() {
        JwtService jwt = serviceAt(SECRET, NOW);
        String[] parts = jwt.issue(42L, Role.TENANT).split("\\.");
        // Attacker rewrites the payload to promote themselves, keeping the old signature.
        String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("TENANT", "ADMIN");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "."
                + parts[2];

        assertThatThrownBy(() -> jwt.verify(tampered)).isInstanceOf(JwtException.class);
    }
}
