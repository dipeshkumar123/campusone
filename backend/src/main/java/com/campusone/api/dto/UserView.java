package com.campusone.api.dto;

import com.campusone.config.AuthenticatedUser;
import com.campusone.domain.User;

public record UserView(
        Long id,
        String name,
        String email,
        User.Role role,
        String roleLabel,
        String title,
        Long campusId,
        String campusName
) {
    public static UserView from(AuthenticatedUser user) {
        return new UserView(
                user.id(),
                user.name(),
                user.email(),
                user.role(),
                user.role() == User.Role.ADMIN ? "Office admin" : "Faculty",
                user.title(),
                user.campusId(),
                user.campusName()
        );
    }
}
