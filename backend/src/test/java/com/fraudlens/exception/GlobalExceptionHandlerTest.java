package com.fraudlens.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void handleResourceNotFoundException_returns404() throws Exception {
        mockMvc.perform(get("/throw/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("resource not found"))
                .andExpect(jsonPath("$.path").value("/throw/not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void handleAIServiceException_returns503() throws Exception {
        mockMvc.perform(get("/throw/ai-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }

    @Test
    void handleValidationException_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/throw/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void handleHttpMessageNotReadable_returns400() throws Exception {
        mockMvc.perform(post("/throw/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void handleDataIntegrityViolation_returns409() throws Exception {
        mockMvc.perform(get("/throw/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void handleAuthenticationException_returns401() throws Exception {
        mockMvc.perform(get("/throw/auth-failure"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void handleGenericException_returns500WithoutDetails() throws Exception {
        mockMvc.perform(get("/throw/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    // ── Inner test controller ─────────────────────────────────────────────────

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("resource not found");
        }

        @GetMapping("/throw/ai-unavailable")
        public void aiUnavailable() {
            throw new AIServiceException("AI is down");
        }

        @PostMapping("/throw/validation")
        public void validation(@RequestBody @Valid ValidatedBody body) {
        }

        @GetMapping("/throw/conflict")
        public void conflict() {
            throw new DataIntegrityViolationException("duplicate key");
        }

        @GetMapping("/throw/auth-failure")
        public void authFailure() {
            throw new BadCredentialsException("bad credentials");
        }

        @GetMapping("/throw/generic")
        public void generic() throws Exception {
            throw new RuntimeException("something went wrong");
        }

        record ValidatedBody(@NotBlank String name) {
        }
    }
}
