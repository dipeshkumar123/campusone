package com.campusone.config;

import com.campusone.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String email) {
        Deque<Instant> recent = recentAttempts(email);
        if (recent.size() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many sign-in attempts. Please wait 10 minutes and try again.");
        }
    }

    public void recordFailure(String email) {
        Deque<Instant> recent = recentAttempts(email);
        recent.addLast(clock.instant());
        attempts.put(key(email), recent);
    }

    public void clear(String email) {
        attempts.remove(key(email));
    }

    private Deque<Instant> recentAttempts(String email) {
        Instant cutoff = clock.instant().minus(WINDOW);
        Deque<Instant> existing = attempts.computeIfAbsent(key(email), k -> new ArrayDeque<>());
        synchronized (existing) {
            while (!existing.isEmpty() && existing.peekFirst().isBefore(cutoff)) {
                existing.removeFirst();
            }
            return existing;
        }
    }

    private String key(String email) {
        return email == null ? "unknown" : email.trim().toLowerCase(Locale.ROOT);
    }
}
