package com.customersupport.backend.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Slows down password guessing on the public login page: after {@value #MAX_FAILURES} failed logins in a row,
 * every login is refused for {@link #LOCKOUT}. There is only one account, so the count is global rather than per
 * client (client IPs behind ngrok and the Vite proxy come from forwarded headers, which can be faked). Trade-off:
 * someone guessing can also keep the admin out for a while; restarting the backend clears it.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final Clock clock;
    private int failures;
    private Instant lockedUntil = Instant.EPOCH;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** How long until logins are allowed again, or empty if they are allowed now. */
    public synchronized Optional<Duration> lockedFor() {
        Duration remaining = Duration.between(clock.instant(), lockedUntil);
        return remaining.isPositive() ? Optional.of(remaining) : Optional.empty();
    }

    public synchronized void recordFailure() {
        failures++;
        if (failures >= MAX_FAILURES) {
            lockedUntil = clock.instant().plus(LOCKOUT);
            failures = 0;
        }
    }

    public synchronized void recordSuccess() {
        failures = 0;
    }
}
