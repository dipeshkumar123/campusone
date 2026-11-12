package com.campusone.support;

import com.campusone.config.AuthenticatedUser;
import com.campusone.domain.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public final class TestSecurity {
    private TestSecurity() {
    }

    public static void signInAs(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getCampus().getId(),
                user.getCampus().getName(),
                user.getTitle()
        );
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
