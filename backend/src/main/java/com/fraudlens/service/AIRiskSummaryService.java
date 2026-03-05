package com.fraudlens.service;

import com.fraudlens.domain.Event;
import com.fraudlens.domain.Session;
import com.fraudlens.exception.AIServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AIRiskSummaryService {

    private final ChatClient chatClient;
    private final RiskScoringService riskScoringService;

    public AIRiskSummaryService(ChatClient.Builder builder, RiskScoringService riskScoringService) {
        this.chatClient = builder.build();
        this.riskScoringService = riskScoringService;
    }

    public String generateRiskSummary(Session session, List<Event> events) {
        int riskScore = riskScoringService.compute(session, events);
        String prompt = buildPrompt(session, events, riskScore);
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.error("AI risk summary generation failed for session {}: {}", session.getId(), ex.getMessage());
            throw new AIServiceException("AI risk assessment is currently unavailable. Please try again later.");
        }
    }

    private String buildPrompt(Session session, List<Event> events, int riskScore) {
        return """
                Analyze the following user session for fraud risk and provide a concise 2-3 sentence
                natural language assessment. End with "Risk level: LOW | MEDIUM | HIGH".

                Session:
                - Country: %s
                - Device: %s
                - Status: %s
                - Risk Score: %d/100

                Events (chronological):
                %s
                """.formatted(
                session.getCountry(),
                session.getDevice(),
                session.getStatus(),
                riskScore,
                formatEvents(events)
        );
    }

    private String formatEvents(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return "No events recorded.";
        }
        return events.stream()
                .map(e -> "- [%s] URL: %s, Duration: %dms%s".formatted(
                        e.getType(),
                        e.getUrl(),
                        e.getDurationMs(),
                        e.getMetadata() != null ? ", Metadata: " + e.getMetadata() : ""))
                .collect(Collectors.joining("\n"));
    }
}
