package com.fraudlens.service;

import com.fraudlens.domain.Event;
import com.fraudlens.domain.EventType;
import com.fraudlens.domain.Session;
import com.fraudlens.domain.SessionStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class RiskScoringService {

    private static final int MAX_SCORE = 100;

    // Rule weights
    static final int WEIGHT_UNUSUAL_COUNTRY         = 15;
    static final int WEIGHT_LOGIN_THEN_FORM_FAST    = 25;
    static final int WEIGHT_SENSITIVE_FORM_FIELDS   = 20;
    static final int WEIGHT_MULTIPLE_LOGIN_ATTEMPTS = 15;
    static final int WEIGHT_BOT_SPEED_SUBMISSION    = 10;
    static final int WEIGHT_DANGEROUS_STATUS        = 10;
    static final int WEIGHT_SUSPICIOUS_STATUS       = 5;

    // Countries flagged as elevated-risk for demo purposes
    private static final Set<String> UNUSUAL_COUNTRIES = Set.of("CN", "KP", "RU", "IR");

    // Threshold constants
    private static final long LOGIN_FAST_THRESHOLD_MS  = 5_000L;
    private static final long BOT_SPEED_THRESHOLD_MS   = 500L;
    private static final long MULTIPLE_LOGIN_THRESHOLD = 2L;

    public int compute(Session session, List<Event> events) {
        int score = 0;

        // Rule: session status contributes a base weight
        if (session.getStatus() == SessionStatus.DANGEROUS) {
            score += WEIGHT_DANGEROUS_STATUS;
        } else if (session.getStatus() == SessionStatus.SUSPICIOUS) {
            score += WEIGHT_SUSPICIOUS_STATUS;
        }

        // Rule: unusual country
        if (UNUSUAL_COUNTRIES.contains(session.getCountry())) {
            score += WEIGHT_UNUSUAL_COUNTRY;
        }

        if (events == null || events.isEmpty()) {
            return Math.min(score, MAX_SCORE);
        }

        // Rule: more than 2 LOGIN_ATTEMPT events
        long loginAttempts = events.stream()
                .filter(e -> e.getType() == EventType.LOGIN_ATTEMPT)
                .count();
        if (loginAttempts > MULTIPLE_LOGIN_THRESHOLD) {
            score += WEIGHT_MULTIPLE_LOGIN_ATTEMPTS;
        }

        // Rule: any event completed in under 500ms (bot-speed interaction)
        boolean hasBotSpeed = events.stream()
                .anyMatch(e -> e.getDurationMs() != null && e.getDurationMs() < BOT_SPEED_THRESHOLD_MS);
        if (hasBotSpeed) {
            score += WEIGHT_BOT_SPEED_SUBMISSION;
        }

        // Rule: LOGIN_ATTEMPT immediately followed by FORM_SUBMIT where login took < 5s
        for (int i = 0; i < events.size() - 1; i++) {
            Event current = events.get(i);
            Event next    = events.get(i + 1);
            if (current.getType() == EventType.LOGIN_ATTEMPT
                    && next.getType() == EventType.FORM_SUBMIT
                    && current.getDurationMs() != null
                    && current.getDurationMs() < LOGIN_FAST_THRESHOLD_MS) {
                score += WEIGHT_LOGIN_THEN_FORM_FAST;
                break; // apply once per session
            }
        }

        // Rule: FORM_SUBMIT with sensitive fields (card_number or cvv) in metadata
        boolean hasSensitiveFields = events.stream()
                .filter(e -> e.getType() == EventType.FORM_SUBMIT && e.getMetadata() != null)
                .anyMatch(e -> e.getMetadata().contains("card_number") || e.getMetadata().contains("cvv"));
        if (hasSensitiveFields) {
            score += WEIGHT_SENSITIVE_FORM_FIELDS;
        }

        return Math.min(score, MAX_SCORE);
    }
}
