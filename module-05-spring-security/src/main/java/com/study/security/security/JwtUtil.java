package com.study.security.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT（JSON Web Token）工具类
 *
 * JWT 结构：Header.Payload.Signature（三段，用 . 分隔）
 *   Header    算法信息（HS256）
 *   Payload   用户信息（subject=用户名, iat=签发时间, exp=过期时间）
 *   Signature 签名（用密钥对前两段签名，防止篡改）
 *
 * 特点：
 *   - 无状态：服务端不保存 token，验证签名即可
 *   - 自包含：用户信息在 token 里
 *   - 必须设置过期时间（exp），否则 token 永久有效
 */
@Component
public class JwtUtil {

    /** 密钥：HS256 要求至少 32 字节（256 bit） */
    @Value("${jwt.secret}")
    private String secret;

    /** token 有效期（毫秒），默认 1 小时 */
    @Value("${jwt.expiration-ms:3600000}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 token */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)          // 主题：用户名
                .issuedAt(now)              // 签发时间
                .expiration(expiry)         // 过期时间
                .signWith(key())            // 签名
                .compact();
    }

    /** 解析 token，返回用户名；无效/过期会抛 JwtException */
    public String extractUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /** 校验 token 是否有效 */
    public boolean isValid(String token) {
        try {
            extractUsername(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
