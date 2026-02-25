package com.athena.lms.compliance.repository;

import com.athena.lms.compliance.entity.SarFiling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SarRepository extends JpaRepository<SarFiling, UUID> {

    Optional<SarFiling> findByAlertId(UUID alertId);

    List<SarFiling> findByTenantId(String tenantId);
}
