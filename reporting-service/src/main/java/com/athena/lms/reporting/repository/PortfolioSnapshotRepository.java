package com.athena.lms.reporting.repository;

import com.athena.lms.reporting.entity.PortfolioSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    Optional<PortfolioSnapshot> findTopByTenantIdOrderBySnapshotDateDesc(String tenantId);

    Page<PortfolioSnapshot> findByTenantIdOrderBySnapshotDateDesc(String tenantId, Pageable pageable);
}
