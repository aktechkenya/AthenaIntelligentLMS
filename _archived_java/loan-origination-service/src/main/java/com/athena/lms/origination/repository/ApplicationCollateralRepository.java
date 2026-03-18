package com.athena.lms.origination.repository;

import com.athena.lms.origination.entity.ApplicationCollateral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationCollateralRepository extends JpaRepository<ApplicationCollateral, UUID> {
}
