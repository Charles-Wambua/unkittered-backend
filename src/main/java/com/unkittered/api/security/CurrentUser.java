package com.unkittered.api.security;

import com.unkittered.api.common.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Convenience accessor for the authenticated user id held in the security context. */
public final class CurrentUser {

    private CurrentUser() { }

    public static UUID id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) {
            throw ApiException.unauthorized("Not authenticated");
        }
        return userId;
    }
}
