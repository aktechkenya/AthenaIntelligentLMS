package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.OverdraftInterestCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OverdraftInterestChargeRepository extends JpaRepository<OverdraftInterestCharge, UUID> {
    List<OverdraftInterestCharge> findByFacilityIdOrderByChargeDateDesc(UUID facilityId);
    boolean existsByFacilityIdAndChargeDate(UUID facilityId, java.time.LocalDate date);
}
