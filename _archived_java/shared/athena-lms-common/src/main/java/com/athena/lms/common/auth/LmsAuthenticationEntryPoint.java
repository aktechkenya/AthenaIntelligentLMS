package com.athena.lms.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns a proper 401 JSON response when no valid credentials are provided.
 * Without this, Spring Security defaults to 403 for unauthenticated requests.
 */
@Component
public class LmsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 401);
        body.put("error", "Unauthorized");
        body.put("message", "Authentication required. Provide a valid Bearer token or service key.");
        body.put("path", request.getRequestURI());
        body.put("timestamp", OffsetDateTime.now().toString());

        String requestId = MDC.get("requestId");
        if (requestId != null) {
            body.put("requestId", requestId);
        }

        MAPPER.writeValue(response.getOutputStream(), body);
    }
}
