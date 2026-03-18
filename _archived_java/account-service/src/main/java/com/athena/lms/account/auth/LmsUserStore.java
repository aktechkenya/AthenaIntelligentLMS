package com.athena.lms.account.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * In-memory user store for the AthenaLMS portal.
 * Users are configured via application.yml / environment variables.
 * Replace with a proper user-service / LDAP in production.
 */
@Component
public class LmsUserStore {

    public record LmsUser(
        String username,
        String passwordHash,   // bcrypt or plain for dev
        String name,
        String email,
        String tenantId,
        List<String> roles
    ) {}

    // Default system users — override via ADMIN_PASSWORD env var
    private final Map<String, LmsUser> users = new LinkedHashMap<>();

    public LmsUserStore(
        @Value("${lms.auth.admin-password:admin123}") String adminPwd,
        @Value("${lms.auth.manager-password:manager123}") String managerPwd,
        @Value("${lms.auth.officer-password:officer123}") String officerPwd,
        @Value("${lms.auth.tenant-id:admin}") String tenantId
    ) {
        users.put("admin", new LmsUser("admin", adminPwd,
            "System Administrator", "admin@athena.com",
            tenantId, List.of("ADMIN", "USER")));

        users.put("admin@athena.com", new LmsUser("admin@athena.com", adminPwd,
            "System Administrator", "admin@athena.com",
            tenantId, List.of("ADMIN", "USER")));

        users.put("manager", new LmsUser("manager", managerPwd,
            "Branch Manager", "manager@athena.com",
            tenantId, List.of("MANAGER", "USER")));

        users.put("manager@athena.com", new LmsUser("manager@athena.com", managerPwd,
            "Branch Manager", "manager@athena.com",
            tenantId, List.of("MANAGER", "USER")));

        users.put("officer", new LmsUser("officer", officerPwd,
            "Loan Officer", "officer@athena.com",
            tenantId, List.of("OFFICER", "USER")));

        users.put("officer@athena.com", new LmsUser("officer@athena.com", officerPwd,
            "Loan Officer", "officer@athena.com",
            tenantId, List.of("OFFICER", "USER")));

        users.put("teller@athena.com", new LmsUser("teller@athena.com", "teller123",
            "Senior Teller", "teller@athena.com",
            tenantId, List.of("TELLER", "USER")));
    }

    public Optional<LmsUser> authenticate(String username, String password) {
        LmsUser user = users.get(username.toLowerCase());
        if (user != null && user.passwordHash().equals(password)) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
