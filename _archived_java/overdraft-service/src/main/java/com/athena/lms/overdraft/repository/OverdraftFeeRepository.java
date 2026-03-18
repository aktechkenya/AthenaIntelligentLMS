package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.OverdraftFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OverdraftFeeRepository extends JpaRepository<OverdraftFee, UUID> {
    List<OverdraftFee> findByFacilityIdOrderByCreatedAtDesc(UUID facilityId);
    List<OverdraftFee> findByFacilityIdAndStatus(UUID facilityId, String status);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM OverdraftFee f WHERE f.facilityId = :facilityId AND f.status = 'CHARGED'")
    BigDecimal sumChargedFeesByFacilityId(@Param("facilityId") UUID facilityId);
}
