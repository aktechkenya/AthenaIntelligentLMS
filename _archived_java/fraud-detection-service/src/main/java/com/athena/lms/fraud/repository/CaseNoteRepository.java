package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseNoteRepository extends JpaRepository<CaseNote, UUID> {
    List<CaseNote> findByCaseIdAndTenantIdOrderByCreatedAtDesc(UUID caseId, String tenantId);
}
