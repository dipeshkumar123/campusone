package com.campusone.config;

import com.campusone.domain.User;

public record AuthenticatedUser(
        Long id,
        String email,
        String name,
        User.Role role,
        Long campusId,
        String campusName,
        String title
) {
    public boolean isAdmin() {
        return role == User.Role.ADMIN;
    }
}
