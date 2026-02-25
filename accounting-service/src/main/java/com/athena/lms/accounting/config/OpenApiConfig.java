package com.athena.lms.accounting.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Accounting Service API",
        version = "v1",
        description = "Athena LMS — General ledger, journal entries, trial balance. Channel partners (mobile banking, device finance) must authenticate using a JWT Bearer token obtained from the Account Service login endpoint.",
        contact = @Contact(name = "Athena LMS", email = "api@athena.lms")
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT token from POST /api/auth/login on Account Service (port 8086)"
)
public class OpenApiConfig {}
