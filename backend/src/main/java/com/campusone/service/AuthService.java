package com.campusone.service;

import com.campusone.api.dto.LoginRequest;
import com.campusone.api.dto.LoginResponse;
import com.campusone.api.dto.UserView;
import com.campusone.common.ApiException;
import com.campusone.config.AuthenticatedUser;
import com.campusone.config.CurrentUserService;
import com.campusone.config.JwtService;
import com.campusone.config.LoginAttemptService;
import com.campusone.domain.User;
import com.campusone.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5J7uRrYf2J6m1yC7yZ3nQ0Hn0Q5sY1K";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final CurrentUserService currentUserService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptService loginAttemptService, CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        loginAttemptService.checkAllowed(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            loginAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        loginAttemptService.clear(email);
        String token = jwtService.issue(user);
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.getCampus().getId(), user.getCampus().getName(), user.getTitle());
        return new LoginResponse(token, UserView.from(principal));
    }

    public UserView me() {
        return UserView.from(currentUserService.current());
    }
}
