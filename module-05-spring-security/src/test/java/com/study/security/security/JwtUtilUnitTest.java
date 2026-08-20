package com.study.security.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("生成的 token 包含用户名且校验通过")
    void generatedTokenShouldContainUsernameAndBeValid() {
        String token = jwtUtil.generateToken("alice");

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("篡改签名的 token 校验不通过")
    void tamperedTokenShouldBeInvalid() {
        String token = jwtUtil.generateToken("alice");
        String[] parts = token.split("\\.");
        String replacement = parts[2].charAt(0) == 'a' ? "b" : "a";
        String tampered = parts[0] + "." + parts[1] + "."
                + replacement + parts[2].substring(1);

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("过期的 token 校验不通过（expirationMs 设为负值）")
    void expiredTokenShouldBeInvalid() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1L);

        String token = jwtUtil.generateToken("alice");

        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("垃圾字符串/空值 token 校验不通过且解析抛异常")
    void malformedTokenShouldBeInvalid() {
        assertThat(jwtUtil.isValid("not-a-jwt")).isFalse();
        assertThat(jwtUtil.isValid("")).isFalse();
        assertThat(jwtUtil.isValid(null)).isFalse();
        assertThatThrownBy(() -> jwtUtil.extractUsername("garbage"))
                .isInstanceOf(Exception.class);
    }
}
