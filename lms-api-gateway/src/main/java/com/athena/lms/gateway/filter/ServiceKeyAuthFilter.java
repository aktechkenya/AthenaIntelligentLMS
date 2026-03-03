package com.athena.lms.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class ServiceKeyAuthFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyAuthFilter.class);

    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final List<SimpleGrantedAuthority> SERVICE_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("ROLE_SERVICE"),
            new SimpleGrantedAuthority("ROLE_ADMIN")
    );

    @Value("${lms.internal.service-key}")
    private String expectedServiceKey;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public int getOrder() {
        return -100; // Run before other filters
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip actuator endpoints
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // Try X-Service-Key auth first
        String serviceKey = request.getHeaders().getFirst(SERVICE_KEY_HEADER);
        if (serviceKey != null && serviceKey.equals(expectedServiceKey)) {
            String serviceUser = request.getHeaders().getFirst("X-Service-User");
            String principal = serviceUser != null ? serviceUser : "service-client";

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, SERVICE_AUTHORITIES);
            SecurityContextImpl securityContext = new SecurityContextImpl(auth);

            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
        }

        // Try JWT Bearer token auth
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                SecretKey key = Keys.hmacShaKeyFor(
                        Base64.getDecoder().decode(jwtSecret.getBytes(StandardCharsets.UTF_8)));
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String username = claims.getSubject();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null, SERVICE_AUTHORITIES);
                SecurityContextImpl securityContext = new SecurityContextImpl(auth);

                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
            } catch (Exception e) {
                log.warn("Invalid JWT token from {}: {}", request.getRemoteAddress(), e.getMessage());
            }
        }

        // No valid auth — reject
        log.warn("Unauthorized request to {} from {}", path, request.getRemoteAddress());
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }
}
