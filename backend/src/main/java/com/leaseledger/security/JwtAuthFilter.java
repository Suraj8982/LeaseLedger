package com.leaseledger.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once on every request, before the controllers.
 *
 * <p>If the request has a valid "Authorization: Bearer &lt;token&gt;" header, it records
 * who the caller is in the SecurityContext. If the header is missing or the token is
 * bad, it does nothing: the request continues as anonymous and Spring Security's
 * authorization rules decide whether that is allowed (401 for protected URLs).
 *
 * <p>Deliberately not a {@code @Component}: Spring Boot would then also register it
 * as a plain servlet filter and it would run twice. SecurityConfig creates it.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            try {
                AuthenticatedUser caller = jwtService.verify(header.substring(BEARER.length()));
                var authentication = new UsernamePasswordAuthenticationToken(
                        caller,
                        null,
                        // Spring's hasRole("MANAGER") looks for the authority "ROLE_MANAGER".
                        List.of(new SimpleGrantedAuthority("ROLE_" + caller.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
