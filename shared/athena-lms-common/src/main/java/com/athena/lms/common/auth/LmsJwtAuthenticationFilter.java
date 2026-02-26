package com.athena.lms.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${lms.internal.service-key:}")
    private String internalServiceKey;

    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String SERVICE_TENANT_HEADER = "X-Service-Tenant";
    private static final String SERVICE_USER_HEADER = "X-Service-User";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Fallback: check for internal service key (service-to-service calls)
            if (!tryServiceKeyAuth(request)) {
                filterChain.doFilter(request, response);
                return;
            }
        } else {
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
                            MDC.put("tenantId", tenantId);
                        }

                        // Set userId in MDC for log correlation
                        MDC.put("userId", username);

                        // Propagate customerId for downstream use (supports both Long and String IDs)
                        String customerIdStr = jwtUtil.extractCustomerIdAsString(token);
                        if (customerIdStr != null) {
                            request.setAttribute("customerIdStr", customerIdStr);
                            Long customerId = jwtUtil.extractCustomerId(token);
                            if (customerId != null) {
                                request.setAttribute("customerId", customerId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            // Note: MDC.clear() is handled by MdcLoggingFilter (higher precedence)
        }
    }

    /**
     * Authenticates internal service-to-service calls using a shared secret key.
     * Wallet microservices use this to call LMS endpoints without a user JWT.
     * The caller can pass X-Service-Tenant and X-Service-User headers for context.
     */
    private boolean tryServiceKeyAuth(HttpServletRequest request) {
        if (internalServiceKey == null || internalServiceKey.isBlank()) {
            return false;
        }

        String serviceKey = request.getHeader(SERVICE_KEY_HEADER);
        if (serviceKey == null || !serviceKey.equals(internalServiceKey)) {
            return false;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return true;
        }

        String tenantId = request.getHeader(SERVICE_TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }
        String serviceUser = request.getHeader(SERVICE_USER_HEADER);
        if (serviceUser == null || serviceUser.isBlank()) {
            serviceUser = "internal-service";
        }

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_SERVICE"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );

        var authToken = new UsernamePasswordAuthenticationToken(serviceUser, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        TenantContextHolder.setTenantId(tenantId);
        request.setAttribute("tenantId", tenantId);
        MDC.put("tenantId", tenantId);
        MDC.put("userId", serviceUser);

        log.debug("Authenticated internal service call from [{}] for tenant [{}]", serviceUser, tenantId);
        return true;
    }
}
