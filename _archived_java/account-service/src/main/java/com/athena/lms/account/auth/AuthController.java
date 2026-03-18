package com.athena.lms.account.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LmsUserStore userStore;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final long EXPIRY_MS = 24L * 60 * 60 * 1000; // 24 hours

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        return userStore.authenticate(req.getUsername(), req.getPassword())
            .map(user -> {
                String token = generateToken(user);
                log.info("Successful login for user: {}", user.username());
                return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .username(user.username())
                    .name(user.name())
                    .email(user.email())
                    .role(user.roles().get(0))
                    .roles(user.roles())
                    .tenantId(user.tenantId())
                    .expiresIn(EXPIRY_MS / 1000)
                    .build());
            })
            .orElseGet(() -> {
                log.warn("Failed login attempt for user: {}", req.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder().build());
            });
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            org.springframework.security.core.Authentication auth) {
        Map<String, Object> info = new HashMap<>();
        info.put("username", auth.getName());
        info.put("authorities", auth.getAuthorities());
        info.put("tenantId", auth.getCredentials());
        return ResponseEntity.ok(info);
    }

    private String generateToken(LmsUserStore.LmsUser user) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRY_MS);

        return Jwts.builder()
            .setSubject(user.username())
            .claim("roles", user.roles())
            .claim("tenantId", user.tenantId())
            .claim("name", user.name())
            .claim("email", user.email())
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }
}
