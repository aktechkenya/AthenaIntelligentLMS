package com.athena.lms.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stateless JWT filter for LMS services.
 * Authenticates purely from JWT claims — no UserDetailsService dependency.
 * The token is trusted once signature is validated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LmsJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String token = authHeader.substring(7);

            if (!jwtUtil.isTokenExpired(token)) {
                final String username = jwtUtil.extractUsername(token);
                final List<String> roles = jwtUtil.extractRoles(token);
                final String tenantId = jwtUtil.extractTenantId(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .collect(Collectors.toList());

                    var authToken = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Propagate tenant context for multi-tenancy
                    if (tenantId != null) {
                        TenantContextHolder.setTenantId(tenantId);
                        request.setAttribute("tenantId", tenantId);
                    }

                    // Propagate customerId for downstream use
                    Long customerId = jwtUtil.extractCustomerId(token);
                    if (customerId != null) {
                        request.setAttribute("customerId", customerId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
