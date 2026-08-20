package com.study.security.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** JWT 工具类纯单元测试：不启动 SecurityFilterChain。 */
class JwtUtilUnitTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3_600_000L);
    }

    @Test
    void generatedTokenShouldContainUsernameAndBeValid() {
        String token = jwtUtil.generateToken("alice");

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void tamperedTokenShouldBeInvalid() {
        String token = jwtUtil.generateToken("alice");
        String[] parts = token.split("\\.");
        String replacement = parts[2].charAt(0) == 'a' ? "b" : "a";
        String tampered = parts[0] + "." + parts[1] + "."
                + replacement + parts[2].substring(1);

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void expiredTokenShouldBeInvalid() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1L);

        String token = jwtUtil.generateToken("alice");

        assertThat(jwtUtil.isValid(token)).isFalse();
    }
}
