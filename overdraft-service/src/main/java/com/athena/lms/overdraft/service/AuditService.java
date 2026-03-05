package com.athena.lms.overdraft.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.overdraft.dto.response.AuditLogResponse;
import com.athena.lms.overdraft.entity.OverdraftAuditLog;
import com.athena.lms.overdraft.repository.OverdraftAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final OverdraftAuditLogRepository auditLogRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String tenantId, String entityType, UUID entityId, String action,
                      Map<String, Object> before, Map<String, Object> after, Map<String, Object> metadata) {
        OverdraftAuditLog entry = new OverdraftAuditLog();
        entry.setTenantId(tenantId);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setActor(resolveActor());
        entry.setBeforeSnapshot(before);
        entry.setAfterSnapshot(after);
        entry.setMetadata(metadata);
        auditLogRepo.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLog(String tenantId, String entityType, UUID entityId, Pageable pageable) {
        Page<OverdraftAuditLog> page;
        if (entityId != null && entityType != null) {
            page = auditLogRepo.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                tenantId, entityType, entityId, pageable);
        } else if (entityType != null) {
            page = auditLogRepo.findByTenantIdAndEntityTypeOrderByCreatedAtDesc(tenantId, entityType, pageable);
        } else {
            page = auditLogRepo.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    private String resolveActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "SYSTEM";
    }

    private AuditLogResponse toResponse(OverdraftAuditLog e) {
        AuditLogResponse r = new AuditLogResponse();
        r.setId(e.getId());
        r.setTenantId(e.getTenantId());
        r.setEntityType(e.getEntityType());
        r.setEntityId(e.getEntityId());
        r.setAction(e.getAction());
        r.setActor(e.getActor());
        r.setBeforeSnapshot(e.getBeforeSnapshot());
        r.setAfterSnapshot(e.getAfterSnapshot());
        r.setMetadata(e.getMetadata());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }
}
