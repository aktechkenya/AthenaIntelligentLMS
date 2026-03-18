package com.athena.lms.account.auth;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String username;
    private String name;
    private String email;
    private String role;
    private List<String> roles;
    private String tenantId;
    private long expiresIn;
}
