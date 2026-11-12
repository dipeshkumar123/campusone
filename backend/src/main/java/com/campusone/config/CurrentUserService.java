package com.campusone.config;

import com.campusone.common.ApiException;
import com.campusone.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    public AuthenticatedUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in to continue.");
    }

    public boolean isAdmin() {
        return current().role() == User.Role.ADMIN;
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw ApiException.forbidden("This action is limited to office administrators.");
        }
    }
}
