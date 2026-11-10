package com.unkittered.api.community;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Notes which app version a signed-in member runs (the app sends
 * {@code X-App-Version: 1.0.4+5}). Runs after Spring Security, so the member
 * is known. Never fails a request.
 */
@Component
public class AppVersionFilter extends OncePerRequestFilter {

    static final String HEADER = "X-App-Version";
    private static final Logger log = LoggerFactory.getLogger(AppVersionFilter.class);

    private final AppReleaseService releases;

    public AppVersionFilter(AppReleaseService releases) {
        this.releases = releases;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String version = request.getHeader(HEADER);
        if (version != null) {
            try {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof UUID userId) releases.reported(userId, version);
            } catch (Exception e) {
                log.debug("App version not recorded: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
