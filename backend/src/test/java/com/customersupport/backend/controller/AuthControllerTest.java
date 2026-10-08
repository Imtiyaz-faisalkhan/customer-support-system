package com.customersupport.backend.controller;

import com.customersupport.backend.dto.LoginRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Mock
    private Authentication authentication;

    @AfterEach
    void clearSecurityContext() {
        // login() sets the thread-local SecurityContextHolder; clear it so the mock Authentication
        // doesn't leak into later tests (e.g. MockMvc integration tests) that run on the same thread.
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_withValidCredentials_returns200AndUsername() {
        AuthController controller = new AuthController(
                authenticationManager, securityContextRepository, sessionAuthenticationStrategy);
        when(authentication.getName()).thenReturn("admin");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        ResponseEntity<Map<String, String>> response = controller.login(
                new LoginRequest("admin", "changeme123"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("username", "admin");
        verify(sessionAuthenticationStrategy).onAuthentication(eq(authentication), any(), any());
        verify(securityContextRepository).saveContext(any(), any(), any());
    }

    @Test
    void login_withInvalidCredentials_returns401WithError() {
        AuthController controller = new AuthController(
                authenticationManager, securityContextRepository, sessionAuthenticationStrategy);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        ResponseEntity<Map<String, String>> response = controller.login(
                new LoginRequest("admin", "wrong"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Invalid username or password");
    }
}
