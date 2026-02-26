package com.athena.lms.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object roles = claims.get("roles");
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }

    public Long extractCustomerId(String token) {
        Claims claims = extractAllClaims(token);
        Object cid = claims.get("customerId");
        if (cid == null) return null;
        try {
            return Long.valueOf(cid.toString());
        } catch (NumberFormatException e) {
            // Mobile wallet tokens use string customer IDs (e.g. "MOB-C88EE444")
            return null;
        }
    }

    public String extractCustomerIdAsString(String token) {
        Claims claims = extractAllClaims(token);
        Object cid = claims.get("customerId");
        return cid != null ? cid.toString() : null;
    }

    public String extractTenantId(String token) {
        Claims claims = extractAllClaims(token);
        Object tid = claims.get("tenantId");
        // Fall back to subject (username) as tenant for single-tenant deployments
        return tid != null ? tid.toString() : claims.getSubject();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
