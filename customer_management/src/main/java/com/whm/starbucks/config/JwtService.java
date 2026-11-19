package com.whm.starbucks.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Getter
    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Getter
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 签发 Access Token（不含 refresh 的 jti）
    public String generateAccessToken(Long userId, Integer memberLevel) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("memberLevel", memberLevel)
                .claim("type","access")
                .id(jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getSignKey())
                .compact();
    }

    // 签发 Refresh Token，返回 token 和 jti（jti 需存入 Redis）
    public Map.Entry<String, String> generateRefreshToken(Long userId) {
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .id(jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSignKey())
                .compact();
        return new AbstractMap.SimpleEntry<>(token, jti);
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(getSignKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type"));
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type"));
    }

    public long remainingMillis(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null ? 0L : expiration.getTime() - System.currentTimeMillis();
    }

}