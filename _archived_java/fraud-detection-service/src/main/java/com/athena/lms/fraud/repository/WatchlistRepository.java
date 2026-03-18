package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.enums.WatchlistType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, UUID> {

    @Query("SELECT w FROM WatchlistEntry w WHERE w.active = true AND w.expiresAt IS NOT NULL AND w.expiresAt < :now")
    List<WatchlistEntry> findExpiredEntries(OffsetDateTime now);


    Page<WatchlistEntry> findByTenantIdAndActive(String tenantId, Boolean active, Pageable pageable);

    List<WatchlistEntry> findByTenantIdAndListTypeAndActive(String tenantId, WatchlistType type, Boolean active);

    @Query("SELECT w FROM WatchlistEntry w WHERE w.tenantId = :tenantId AND w.active = :active")
    List<WatchlistEntry> findAllByTenantIdAndActive(String tenantId, boolean active);

    @Query("SELECT w FROM WatchlistEntry w WHERE (w.tenantId = :tenantId OR w.tenantId = '*') " +
           "AND w.active = true AND (w.expiresAt IS NULL OR w.expiresAt > CURRENT_TIMESTAMP) " +
           "AND (w.nationalId = :nationalId OR w.name = :name OR w.phone = :phone)")
    List<WatchlistEntry> findMatches(String tenantId, String nationalId, String name, String phone);
}
