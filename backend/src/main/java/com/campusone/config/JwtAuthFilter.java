package com.campusone.config;

import com.campusone.domain.User;
import com.campusone.repo.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtService.parse(token);
                userRepository.findByEmailIgnoreCase(claims.getSubject())
                        .filter(User::isActive)
                        .ifPresent(user -> {
                            AuthenticatedUser principal = new AuthenticatedUser(
                                    user.getId(), user.getEmail(), user.getName(), user.getRole(),
                                    user.getCampus().getId(), user.getCampus().getName(), user.getTitle());
                            var auth = new UsernamePasswordAuthenticationToken(
                                    principal, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            } catch (Exception ex) {
                log.debug("Rejected bearer token: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
