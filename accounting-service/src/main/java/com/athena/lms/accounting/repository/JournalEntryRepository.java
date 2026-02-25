package com.athena.lms.accounting.repository;

import com.athena.lms.accounting.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Optional<JournalEntry> findByIdAndTenantId(UUID id, String tenantId);
    Page<JournalEntry> findByTenantId(String tenantId, Pageable pageable);
    Page<JournalEntry> findByTenantIdAndEntryDateBetween(String tenantId, LocalDate from, LocalDate to, Pageable pageable);
    boolean existsBySourceEventAndSourceId(String sourceEvent, String sourceId);
}
