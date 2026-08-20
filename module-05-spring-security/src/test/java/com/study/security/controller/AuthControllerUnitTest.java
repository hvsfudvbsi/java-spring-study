package com.study.security.controller;

import com.study.security.model.LoginRequest;
import com.study.security.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AuthController 纯单元测试：不经过 HTTP 和 SecurityFilterChain。 */
class AuthControllerUnitTest {

    private AuthenticationManager authenticationManager;
    private JwtUtil jwtUtil;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        jwtUtil = mock(JwtUtil.class);
        authController = new AuthController(authenticationManager, jwtUtil);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginShouldAuthenticateAndBuildTokenResponse() {
        Authentication authentication = mock(Authentication.class);
        UserDetails user = User.withUsername("alice")
                .password("ignored")
                .roles("USER")
                .build();
        when(authentication.getPrincipal()).thenReturn(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.generateToken("alice")).thenReturn("jwt-token");

        var response = authController.login(new LoginRequest("alice", "password"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("token")).isEqualTo("jwt-token");
        assertThat(body.get("username")).isEqualTo("alice");
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(authentication);
        verify(authenticationManager).authenticate(any());
        verify(jwtUtil).generateToken("alice");
    }
}
