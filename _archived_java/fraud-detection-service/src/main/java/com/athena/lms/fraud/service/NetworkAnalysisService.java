package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.response.NetworkNodeResponse;
import com.athena.lms.fraud.entity.CustomerRiskProfile;
import com.athena.lms.fraud.entity.NetworkLink;
import com.athena.lms.fraud.enums.RiskLevel;
import com.athena.lms.fraud.repository.CustomerRiskProfileRepository;
import com.athena.lms.fraud.repository.NetworkLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class NetworkAnalysisService {

    private final NetworkLinkRepository linkRepository;
    private final CustomerRiskProfileRepository riskProfileRepository;

    public void recordLink(String tenantId, String customerIdA, String customerIdB,
                            String linkType, String linkValue) {
        // Normalize order so A < B to avoid duplicates
        String a = customerIdA.compareTo(customerIdB) <= 0 ? customerIdA : customerIdB;
        String b = customerIdA.compareTo(customerIdB) <= 0 ? customerIdB : customerIdA;

        if (linkRepository.existsByTenantIdAndCustomerIdAAndCustomerIdBAndLinkType(tenantId, a, b, linkType)) {
            // Update strength
            List<NetworkLink> existing = linkRepository.findByLink(tenantId, linkType, linkValue);
            for (NetworkLink link : existing) {
                if (link.getCustomerIdA().equals(a) && link.getCustomerIdB().equals(b)) {
                    link.setStrength(link.getStrength() + 1);
                    linkRepository.save(link);
                    return;
                }
            }
        }

        NetworkLink link = NetworkLink.builder()
            .tenantId(tenantId)
            .customerIdA(a)
            .customerIdB(b)
            .linkType(linkType)
            .linkValue(linkValue)
            .build();
        linkRepository.save(link);
        log.debug("Recorded network link: {} <-> {} via {}={}", a, b, linkType, linkValue);
    }

    @Transactional(readOnly = true)
    public NetworkNodeResponse getCustomerNetwork(String tenantId, String customerId) {
        List<NetworkLink> links = linkRepository.findByCustomer(tenantId, customerId);

        NetworkNodeResponse node = new NetworkNodeResponse();
        node.setCustomerId(customerId);
        node.setLinkCount(links.size());

        // Get risk level
        riskProfileRepository.findByTenantIdAndCustomerId(tenantId, customerId)
            .ifPresentOrElse(
                p -> node.setRiskLevel(p.getRiskLevel().name()),
                () -> node.setRiskLevel("LOW")
            );

        List<NetworkNodeResponse.LinkResponse> linkResponses = links.stream().map(l -> {
            NetworkNodeResponse.LinkResponse lr = new NetworkNodeResponse.LinkResponse();
            lr.setLinkedCustomerId(l.getCustomerIdA().equals(customerId) ? l.getCustomerIdB() : l.getCustomerIdA());
            lr.setLinkType(l.getLinkType());
            lr.setLinkValue(l.getLinkValue());
            lr.setStrength(l.getStrength());
            lr.setFlagged(l.getFlagged());
            return lr;
        }).toList();

        node.setLinks(linkResponses);
        return node;
    }

    @Transactional(readOnly = true)
    public List<NetworkNodeResponse> getFlaggedClusters(String tenantId) {
        List<NetworkLink> flagged = linkRepository.findFlaggedLinks(tenantId);

        // Group by unique customers
        Set<String> customerIds = new HashSet<>();
        for (NetworkLink link : flagged) {
            customerIds.add(link.getCustomerIdA());
            customerIds.add(link.getCustomerIdB());
        }

        return customerIds.stream()
            .map(cid -> getCustomerNetwork(tenantId, cid))
            .filter(n -> n.getLinkCount() > 0)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<NetworkLink> findByLinkValue(String tenantId, String linkType, String linkValue) {
        return linkRepository.findByLink(tenantId, linkType, linkValue);
    }

    public void flagLink(String tenantId, UUID linkId) {
        NetworkLink link = linkRepository.findById(linkId)
            .orElseThrow(() -> new com.athena.lms.common.exception.ResourceNotFoundException("Link not found: " + linkId));
        link.setFlagged(true);
        linkRepository.save(link);
    }
}
