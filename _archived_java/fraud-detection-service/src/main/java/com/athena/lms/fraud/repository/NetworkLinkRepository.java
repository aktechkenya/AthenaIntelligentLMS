package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.NetworkLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NetworkLinkRepository extends JpaRepository<NetworkLink, UUID> {
    @Query("SELECT n FROM NetworkLink n WHERE n.tenantId = :tenantId AND (n.customerIdA = :customerId OR n.customerIdB = :customerId)")
    List<NetworkLink> findByCustomer(String tenantId, String customerId);

    @Query("SELECT n FROM NetworkLink n WHERE n.tenantId = :tenantId AND n.linkType = :linkType AND n.linkValue = :linkValue")
    List<NetworkLink> findByLink(String tenantId, String linkType, String linkValue);

    @Query("SELECT n FROM NetworkLink n WHERE n.tenantId = :tenantId AND n.flagged = true")
    List<NetworkLink> findFlaggedLinks(String tenantId);

    boolean existsByTenantIdAndCustomerIdAAndCustomerIdBAndLinkType(
        String tenantId, String customerIdA, String customerIdB, String linkType);
}
