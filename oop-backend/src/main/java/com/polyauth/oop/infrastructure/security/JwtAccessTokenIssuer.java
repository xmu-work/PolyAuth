package com.polyauth.oop.infrastructure.security;

import com.polyauth.oop.application.port.AccessTokenExpiredException;
import com.polyauth.oop.application.port.AccessTokenInvalidException;
import com.polyauth.oop.application.port.AccessTokenIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * OOP 后端独立 JWT 签发与校验实现。
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final Clock clock;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expire:7200}")
    private long accessTokenExpire;

    @Value("${jwt.issuer:polyauth}")
    private String issuer;

    /**
     * 注入统一时钟，便于令牌测试和过期判断。
     */
    public JwtAccessTokenIssuer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(String userNo, List<String> systems) {
        long now = clock.millis();
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userNo)
                .claim("userNo", userNo)
                .claim("aud", systems)
                .issuer(issuer)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpire * 1000))
                .signWith(signingKey())
                .compact();
        return new IssuedToken(token, accessTokenExpire);
    }

    @Override
    public AccessTokenClaims read(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AccessTokenClaims(claims.getSubject(),
                    List.copyOf(claims.getAudience()));
        } catch (ExpiredJwtException exception) {
            throw new AccessTokenExpiredException();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessTokenInvalidException(exception);
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
