package com.athena.lms.account.repository;

import com.athena.lms.account.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, String> {
}
