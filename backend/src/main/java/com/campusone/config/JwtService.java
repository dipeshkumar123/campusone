package com.campusone.config;

import com.campusone.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.ttl-minutes:720}") long ttlMinutes,
                      @Value("${app.jwt.issuer:campusone}") String issuer,
                      Clock clock) {
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.issuer = issuer;
        if (secret == null || secret.isBlank()) {
            byte[] generated = new byte[64];
            new SecureRandom().nextBytes(generated);
            this.key = Keys.hmacShaKeyFor(generated);
            log.warn("APP_JWT_SECRET is not set. A random signing key was generated; existing sessions end on restart.");
        } else {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("APP_JWT_SECRET must be at least 32 characters.");
            }
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String issue(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getEmail())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
