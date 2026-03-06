package com.fraudlens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudlens.config.SecurityConfig;
import com.fraudlens.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthenticationManager authenticationManager;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    // ── AC-AUTH-01: Valid credentials → 200 + token ───────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        var authToken = new UsernamePasswordAuthenticationToken("admin", null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(jwtService.generateToken("admin")).thenReturn("mocked-jwt-token");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked-jwt-token"));
    }

    // ── AC-AUTH-02: Invalid credentials → 401 ─────────────────────────────────

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    // ── AC-AUTH-03: Missing required field → 400 ──────────────────────────────

    @Test
    void login_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingUsername_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"admin123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── AC-AUTH-03: No Authorization header on protected endpoint → 401 ───────

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
