package com.unkittered.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.unkittered.api.profile.PresenceService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Reads a {@code Bearer} token, validates it, and populates the security context. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PresenceService presence;

    public JwtAuthFilter(JwtService jwtService, PresenceService presence) {
        this.jwtService = jwtService;
        this.presence = presence;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            try {
                UUID userId = jwtService.parseUserId(token);
                var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                // Refresh activity for presence (throttled, best-effort).
                presence.touch(userId);
            } catch (Exception ignored) {
                // Invalid/expired token -> remains unauthenticated; protected routes 401.
            }
        }
        chain.doFilter(request, response);
    }
}
