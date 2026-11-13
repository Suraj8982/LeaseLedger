package com.leaseledger.security;

import com.leaseledger.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates and verifies access tokens.
 *
 * <p>A token carries the user id (subject) and role, and is signed with HMAC-SHA256
 * using a secret only this server knows. Anyone can read a token, but nobody can
 * change it or forge one without the secret.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${leaseledger.jwt.secret}") String base64Secret,
                      @Value("${leaseledger.jwt.ttl-minutes}") long ttlMinutes) {
        this(base64Secret, Duration.ofMinutes(ttlMinutes), Clock.systemUTC());
    }

    /** The clock is a parameter so tests can check expiry without sleeping. */
    JwtService(String base64Secret, Duration ttl, Clock clock) {
        // hmacShaKeyFor rejects keys shorter than 256 bits, so a weak secret fails at startup.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(Long userId, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws JwtException if the signature is wrong, the token is expired or it is malformed
     */
    public AuthenticatedUser verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    Role.valueOf(claims.get("role", String.class)));
        } catch (RuntimeException e) {
            throw new JwtException("Token has a valid signature but unexpected claims", e);
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
