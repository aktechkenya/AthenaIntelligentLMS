package com.athena.lms.collections.repository;

import com.athena.lms.collections.entity.PromiseToPay;
import com.athena.lms.collections.enums.PtpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PtpRepository extends JpaRepository<PromiseToPay, UUID> {

    List<PromiseToPay> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    List<PromiseToPay> findByStatusAndPromiseDateBefore(PtpStatus status, LocalDate date);
}
