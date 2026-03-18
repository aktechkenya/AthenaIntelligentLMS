package com.athena.lms.fraud.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.fraud.dto.request.CreateWatchlistEntryRequest;
import com.athena.lms.fraud.dto.response.WatchlistEntryResponse;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.enums.WatchlistType;
import com.athena.lms.fraud.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final CaseManagementService caseManagementService;

    public WatchlistEntryResponse createEntry(CreateWatchlistEntryRequest req, String tenantId) {
        WatchlistEntry entry = WatchlistEntry.builder()
                .tenantId(tenantId)
                .listType(WatchlistType.valueOf(req.getListType()))
                .entryType(req.getEntryType())
                .name(req.getName())
                .nationalId(req.getNationalId())
                .phone(req.getPhone())
                .reason(req.getReason())
                .source(req.getSource())
                .expiresAt(req.getExpiresAt())
                .build();

        entry = watchlistRepository.save(entry);

        caseManagementService.audit(tenantId, "WATCHLIST_ENTRY_CREATED", "WATCHLIST", entry.getId(),
                "system", "Watchlist entry created: " + req.getName(), null);

        log.info("Created watchlist entry {} for tenant={}", entry.getId(), tenantId);
        return mapToResponse(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<WatchlistEntryResponse> listEntries(String tenantId, Boolean active, Pageable pageable) {
        Page<WatchlistEntry> page;
        if (active != null) {
            page = watchlistRepository.findByTenantIdAndActive(tenantId, active, pageable);
        } else {
            page = watchlistRepository.findByTenantIdAndActive(tenantId, true, pageable);
        }
        return PageResponse.from(page.map(this::mapToResponse));
    }

    public WatchlistEntryResponse deactivateEntry(UUID id, String tenantId) {
        WatchlistEntry entry = watchlistRepository.findById(id)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist entry not found: " + id));

        entry.setActive(false);
        entry = watchlistRepository.save(entry);

        caseManagementService.audit(tenantId, "WATCHLIST_ENTRY_DEACTIVATED", "WATCHLIST", entry.getId(),
                "system", "Watchlist entry deactivated: " + entry.getName(), null);

        log.info("Deactivated watchlist entry {} for tenant={}", id, tenantId);
        return mapToResponse(entry);
    }

    private WatchlistEntryResponse mapToResponse(WatchlistEntry e) {
        WatchlistEntryResponse resp = new WatchlistEntryResponse();
        resp.setId(e.getId());
        resp.setTenantId(e.getTenantId());
        resp.setListType(e.getListType());
        resp.setEntryType(e.getEntryType());
        resp.setName(e.getName());
        resp.setNationalId(e.getNationalId());
        resp.setPhone(e.getPhone());
        resp.setReason(e.getReason());
        resp.setSource(e.getSource());
        resp.setActive(e.getActive());
        resp.setExpiresAt(e.getExpiresAt());
        resp.setCreatedAt(e.getCreatedAt());
        resp.setUpdatedAt(e.getUpdatedAt());
        return resp;
    }
}
