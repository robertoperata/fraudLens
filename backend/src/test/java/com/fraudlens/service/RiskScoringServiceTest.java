package com.fraudlens.service;

import com.fraudlens.domain.Event;
import com.fraudlens.domain.EventType;
import com.fraudlens.domain.Session;
import com.fraudlens.domain.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fraudlens.service.RiskScoringService.*;
import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringServiceTest {

    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        service = new RiskScoringService();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Session session(String country, SessionStatus status) {
        return Session.builder().id("s1").userId("u1").ip("1.1.1.1")
                .country(country).device("web").timestamp("2024-01-01T00:00:00Z")
                .status(status).build();
    }

    private Event event(EventType type, long durationMs, String metadata) {
        return Event.builder().id("e1").type(type)
                .url("https://example.com").durationMs(durationMs)
                .metadata(metadata).build();
    }

    // ── Status rules ─────────────────────────────────────────────────────────

    @Test
    void safeStatus_addsZero() {
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of())).isZero();
    }

    @Test
    void suspiciousStatus_addsFive() {
        assertThat(service.compute(session("US", SessionStatus.SUSPICIOUS), List.of()))
                .isEqualTo(WEIGHT_SUSPICIOUS_STATUS);
    }

    @Test
    void dangerousStatus_addsTen() {
        assertThat(service.compute(session("US", SessionStatus.DANGEROUS), List.of()))
                .isEqualTo(WEIGHT_DANGEROUS_STATUS);
    }

    // ── Country rule ─────────────────────────────────────────────────────────

    @Test
    void unusualCountry_addsFifteen() {
        assertThat(service.compute(session("CN", SessionStatus.SAFE), List.of()))
                .isEqualTo(WEIGHT_UNUSUAL_COUNTRY);
    }

    @Test
    void unusualCountry_RU_addsFifteen() {
        assertThat(service.compute(session("RU", SessionStatus.SAFE), List.of()))
                .isEqualTo(WEIGHT_UNUSUAL_COUNTRY);
    }

    @Test
    void unusualCountry_KP_addsFifteen() {
        assertThat(service.compute(session("KP", SessionStatus.SAFE), List.of()))
                .isEqualTo(WEIGHT_UNUSUAL_COUNTRY);
    }

    @Test
    void unusualCountry_IR_addsFifteen() {
        assertThat(service.compute(session("IR", SessionStatus.SAFE), List.of()))
                .isEqualTo(WEIGHT_UNUSUAL_COUNTRY);
    }

    @Test
    void usualCountry_addsZero() {
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of())).isZero();
    }

    // ── Null / empty events early return ─────────────────────────────────────

    @Test
    void nullEvents_returnsStatusScore() {
        assertThat(service.compute(session("US", SessionStatus.SUSPICIOUS), null))
                .isEqualTo(WEIGHT_SUSPICIOUS_STATUS);
    }

    @Test
    void emptyEvents_returnsStatusScore() {
        assertThat(service.compute(session("US", SessionStatus.SUSPICIOUS), List.of()))
                .isEqualTo(WEIGHT_SUSPICIOUS_STATUS);
    }

    // ── Multiple login attempts rule ──────────────────────────────────────────

    @Test
    void moreThanTwoLoginAttempts_addsWeight() {
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 1000, null),
                event(EventType.LOGIN_ATTEMPT, 1000, null),
                event(EventType.LOGIN_ATTEMPT, 1000, null)
        );
        assertThat(service.compute(session("US", SessionStatus.SAFE), events))
                .isEqualTo(WEIGHT_MULTIPLE_LOGIN_ATTEMPTS);
    }

    @Test
    void exactlyTwoLoginAttempts_doesNotAddWeight() {
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 1000, null),
                event(EventType.LOGIN_ATTEMPT, 1000, null)
        );
        assertThat(service.compute(session("US", SessionStatus.SAFE), events)).isZero();
    }

    @Test
    void oneLoginAttempt_doesNotAddWeight() {
        assertThat(service.compute(session("US", SessionStatus.SAFE),
                List.of(event(EventType.LOGIN_ATTEMPT, 1000, null)))).isZero();
    }

    // ── Bot speed rule ────────────────────────────────────────────────────────

    @Test
    void eventUnder500ms_addsBotSpeedWeight() {
        assertThat(service.compute(session("US", SessionStatus.SAFE),
                List.of(event(EventType.PAGE_VISIT, 499, null))))
                .isEqualTo(WEIGHT_BOT_SPEED_SUBMISSION);
    }

    @Test
    void eventExactly500ms_doesNotAddBotSpeedWeight() {
        assertThat(service.compute(session("US", SessionStatus.SAFE),
                List.of(event(EventType.PAGE_VISIT, 500, null)))).isZero();
    }

    @Test
    void eventOver500ms_doesNotAddBotSpeedWeight() {
        assertThat(service.compute(session("US", SessionStatus.SAFE),
                List.of(event(EventType.PAGE_VISIT, 2000, null)))).isZero();
    }

    @Test
    void nullDuration_doesNotAddBotSpeedWeight() {
        Event e = Event.builder().id("e1").type(EventType.PAGE_VISIT)
                .url("https://example.com").durationMs(null).build();
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of(e))).isZero();
    }

    // ── Login→FormSubmit fast rule ────────────────────────────────────────────

    @Test
    void loginFollowedByFormSubmitFast_addsWeight() {
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 4999, null),
                event(EventType.FORM_SUBMIT, 1000, null)
        );
        assertThat(service.compute(session("US", SessionStatus.SAFE), events))
                .isEqualTo(WEIGHT_LOGIN_THEN_FORM_FAST);
    }

    @Test
    void loginFollowedByFormSubmitSlow_doesNotAddWeight() {
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 5000, null),
                event(EventType.FORM_SUBMIT, 1000, null)
        );
        assertThat(service.compute(session("US", SessionStatus.SAFE), events)).isZero();
    }

    @Test
    void loginNotFollowedByFormSubmit_doesNotAddWeight() {
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 1000, null),
                event(EventType.PAGE_VISIT, 1000, null)
        );
        assertThat(service.compute(session("US", SessionStatus.SAFE), events)).isZero();
    }

    @Test
    void loginAtEnd_doesNotAddWeight() {
        // LOGIN_ATTEMPT is last — no next event
        assertThat(service.compute(session("US", SessionStatus.SAFE),
                List.of(event(EventType.LOGIN_ATTEMPT, 1000, null)))).isZero();
    }

    @Test
    void loginFastRule_appliedOnlyOnce() {
        // Two qualifying pairs — weight applied only once
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 100, null),
                event(EventType.FORM_SUBMIT, 100, null),
                event(EventType.LOGIN_ATTEMPT, 100, null),
                event(EventType.FORM_SUBMIT, 100, null)
        );
        // Also triggers bot-speed for all 4 events, but login-fast rule fires once
        int expected = WEIGHT_LOGIN_THEN_FORM_FAST + WEIGHT_BOT_SPEED_SUBMISSION;
        assertThat(service.compute(session("US", SessionStatus.SAFE), events))
                .isEqualTo(expected);
    }

    // ── Sensitive form fields rule ────────────────────────────────────────────

    @Test
    void formSubmitWithCardNumber_addsWeight() {
        Event e = event(EventType.FORM_SUBMIT, 1000, "{\"formFields\":{\"card_number\":\"4111\"}}");
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of(e)))
                .isEqualTo(WEIGHT_SENSITIVE_FORM_FIELDS);
    }

    @Test
    void formSubmitWithCvv_addsWeight() {
        Event e = event(EventType.FORM_SUBMIT, 1000, "{\"formFields\":{\"cvv\":\"123\"}}");
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of(e)))
                .isEqualTo(WEIGHT_SENSITIVE_FORM_FIELDS);
    }

    @Test
    void formSubmitWithoutSensitiveFields_doesNotAddWeight() {
        Event e = event(EventType.FORM_SUBMIT, 1000, "{\"formFields\":{\"name\":\"John\"}}");
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of(e))).isZero();
    }

    @Test
    void formSubmitWithNullMetadata_doesNotAddWeight() {
        Event e = event(EventType.FORM_SUBMIT, 1000, null);
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of(e))).isZero();
    }

    @Test
    void pageVisitWithCardNumber_doesNotAddSensitiveFieldWeight() {
        // Sensitive-field rule only applies to FORM_SUBMIT
        Event e = event(EventType.PAGE_VISIT, 1000, "{\"card_number\":\"4111\"}");
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of(e))).isZero();
    }

    // ── Cap at 100 ────────────────────────────────────────────────────────────

    @Test
    void allRulesActive_maxScoreIs95() {
        // All rules active simultaneously:
        //   DANGEROUS(10) + CN(15) + botSpeed(10) + loginFast(25) + sensitive(20) + multipleLogins(15) = 95
        // Math.min(95, 100) = 95 — cap does not alter the result but is still exercised
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 100, null),  // bot speed + login fast trigger
                event(EventType.FORM_SUBMIT, 100, "{\"card_number\":\"4111\"}"),  // sensitive
                event(EventType.LOGIN_ATTEMPT, 100, null),  // 2nd login
                event(EventType.LOGIN_ATTEMPT, 100, null)   // 3rd login → >2 → multipleLogins
        );
        int score = service.compute(session("CN", SessionStatus.DANGEROUS), events);
        assertThat(score).isEqualTo(95);
    }

    @Test
    void scoreIsAlwaysNonNegative() {
        assertThat(service.compute(session("US", SessionStatus.SAFE), List.of())).isGreaterThanOrEqualTo(0);
    }

    @Test
    void scoreIsAlwaysBetween0And100() {
        List<Event> events = List.of(
                event(EventType.LOGIN_ATTEMPT, 100, null),
                event(EventType.FORM_SUBMIT, 100, "{\"card_number\":\"4111\",\"cvv\":\"123\"}")
        );
        int score = service.compute(session("CN", SessionStatus.DANGEROUS), events);
        assertThat(score).isBetween(0, 100);
    }

    // ── Combined rules ────────────────────────────────────────────────────────

    @Test
    void combinedRules_accumulateCorrectly() {
        // SUSPICIOUS(5) + CN(15) = 20
        assertThat(service.compute(session("CN", SessionStatus.SUSPICIOUS), List.of()))
                .isEqualTo(WEIGHT_SUSPICIOUS_STATUS + WEIGHT_UNUSUAL_COUNTRY);
    }
}
