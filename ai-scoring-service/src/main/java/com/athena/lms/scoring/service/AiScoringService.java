package com.athena.lms.scoring.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.scoring.client.AthenaScoreClient;
import com.athena.lms.scoring.dto.external.ExternalScoreResponse;
import com.athena.lms.scoring.dto.request.ManualScoringRequest;
import com.athena.lms.scoring.dto.response.ScoringRequestResponse;
import com.athena.lms.scoring.dto.response.ScoringResultResponse;
import com.athena.lms.scoring.entity.ScoringRequest;
import com.athena.lms.scoring.entity.ScoringResult;
import com.athena.lms.scoring.enums.ScoringStatus;
import com.athena.lms.scoring.event.ScoringEventPublisher;
import com.athena.lms.scoring.repository.ScoringRequestRepository;
import com.athena.lms.scoring.repository.ScoringResultRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AiScoringService {

    private final ScoringRequestRepository requestRepository;
    private final ScoringResultRepository resultRepository;
    private final AthenaScoreClient athenaScoreClient;
    private final ScoringEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public void triggerScoring(UUID loanApplicationId, Long customerId,
                               String triggerEvent, String tenantId) {
        // Idempotency: skip if already COMPLETED
        Optional<ScoringRequest> existing = requestRepository.findTopByLoanApplicationIdOrderByCreatedAtDesc(loanApplicationId);
        if (existing.isPresent() && existing.get().getStatus() == ScoringStatus.COMPLETED) {
            log.info("Scoring already COMPLETED for loanApplicationId={}, skipping.", loanApplicationId);
            return;
        }

        ScoringRequest request = existing.orElseGet(() -> ScoringRequest.builder()
                .tenantId(tenantId)
                .loanApplicationId(loanApplicationId)
                .customerId(customerId)
                .triggerEvent(triggerEvent)
                .build());

        request.setStatus(ScoringStatus.IN_PROGRESS);
        request = requestRepository.save(request);

        Optional<ExternalScoreResponse> scoreOpt = athenaScoreClient.getScore(customerId);

        if (scoreOpt.isPresent()) {
            ExternalScoreResponse score = scoreOpt.get();
            String rawResponse = serializeToJson(score);
            String reasoningJson = serializeToJson(score.getReasoning());

            ScoringResult result = ScoringResult.builder()
                    .tenantId(tenantId)
                    .requestId(request.getId())
                    .loanApplicationId(loanApplicationId)
                    .customerId(customerId)
                    .baseScore(score.getBaseScore())
                    .crbContribution(score.getCrbContribution())
                    .llmAdjustment(score.getLlmAdjustment())
                    .pdProbability(score.getPdProbability())
                    .finalScore(score.getFinalScore())
                    .scoreBand(score.getScoreBand())
                    .reasoning(reasoningJson)
                    .llmProvider(score.getLlmProvider())
                    .llmModel(score.getLlmModel())
                    .rawResponse(rawResponse)
                    .scoredAt(score.getScoredAt() != null ? parseInstant(score.getScoredAt()) : Instant.now())
                    .build();

            resultRepository.save(result);

            request.setStatus(ScoringStatus.COMPLETED);
            request.setCompletedAt(Instant.now());
            requestRepository.save(request);

            eventPublisher.publishCreditAssessed(
                    loanApplicationId, customerId,
                    score.getFinalScore(), score.getScoreBand(),
                    score.getPdProbability(), tenantId
            );
            log.info("Scoring COMPLETED for loanApplicationId={} finalScore={} scoreBand={}",
                    loanApplicationId, score.getFinalScore(), score.getScoreBand());
        } else {
            request.setStatus(ScoringStatus.FAILED);
            request.setErrorMessage("Failed to retrieve score from AthenaCreditScore API");
            requestRepository.save(request);
            log.warn("Scoring FAILED for loanApplicationId={} customerId={}", loanApplicationId, customerId);
        }
    }

    @Transactional(readOnly = true)
    public ScoringRequestResponse getRequest(UUID id, String tenantId) {
        ScoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScoringRequest", id.toString()));
        return toRequestResponse(request);
    }

    @Transactional(readOnly = true)
    public ScoringRequestResponse getRequestByApplication(UUID loanApplicationId, String tenantId) {
        ScoringRequest request = requestRepository.findTopByLoanApplicationIdOrderByCreatedAtDesc(loanApplicationId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoringRequest", loanApplicationId.toString()));
        return toRequestResponse(request);
    }

    @Transactional(readOnly = true)
    public ScoringResultResponse getResultByApplication(UUID loanApplicationId, String tenantId) {
        ScoringResult result = resultRepository.findTopByLoanApplicationIdOrderByCreatedAtDesc(loanApplicationId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoringResult", loanApplicationId.toString()));
        return toResultResponse(result);
    }

    @Transactional(readOnly = true)
    public ScoringResultResponse getLatestResultByCustomer(Long customerId, String tenantId) {
        ScoringResult result = resultRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoringResult", customerId.toString()));
        return toResultResponse(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScoringRequestResponse> listRequests(String tenantId, Pageable pageable) {
        Page<ScoringRequest> page = requestRepository.findByTenantId(tenantId, pageable);
        Page<ScoringRequestResponse> responsePage = page.map(this::toRequestResponse);
        return PageResponse.from(responsePage);
    }

    public ScoringRequestResponse manualScore(ManualScoringRequest req, String tenantId) {
        ScoringRequest request = ScoringRequest.builder()
                .tenantId(tenantId)
                .loanApplicationId(req.getLoanApplicationId())
                .customerId(req.getCustomerId())
                .status(ScoringStatus.PENDING)
                .triggerEvent(req.getTriggerEvent() != null ? req.getTriggerEvent() : "MANUAL")
                .build();
        request = requestRepository.save(request);
        triggerScoring(req.getLoanApplicationId(), req.getCustomerId(),
                request.getTriggerEvent(), tenantId);
        return toRequestResponse(requestRepository.findById(request.getId()).orElse(request));
    }

    private ScoringRequestResponse toRequestResponse(ScoringRequest req) {
        ScoringRequestResponse resp = new ScoringRequestResponse();
        resp.setId(req.getId());
        resp.setTenantId(req.getTenantId());
        resp.setLoanApplicationId(req.getLoanApplicationId());
        resp.setCustomerId(req.getCustomerId());
        resp.setStatus(req.getStatus());
        resp.setTriggerEvent(req.getTriggerEvent());
        resp.setRequestedAt(req.getRequestedAt());
        resp.setCompletedAt(req.getCompletedAt());
        resp.setErrorMessage(req.getErrorMessage());
        resp.setCreatedAt(req.getCreatedAt());
        return resp;
    }

    private ScoringResultResponse toResultResponse(ScoringResult res) {
        ScoringResultResponse resp = new ScoringResultResponse();
        resp.setId(res.getId());
        resp.setRequestId(res.getRequestId());
        resp.setLoanApplicationId(res.getLoanApplicationId());
        resp.setCustomerId(res.getCustomerId());
        resp.setBaseScore(res.getBaseScore());
        resp.setCrbContribution(res.getCrbContribution());
        resp.setLlmAdjustment(res.getLlmAdjustment());
        resp.setPdProbability(res.getPdProbability());
        resp.setFinalScore(res.getFinalScore());
        resp.setScoreBand(res.getScoreBand());
        resp.setLlmProvider(res.getLlmProvider());
        resp.setLlmModel(res.getLlmModel());
        resp.setScoredAt(res.getScoredAt());
        resp.setCreatedAt(res.getCreatedAt());
        resp.setReasoning(parseReasoningList(res.getReasoning()));
        return resp;
    }

    private List<String> parseReasoningList(String reasoningJson) {
        if (reasoningJson == null || reasoningJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(reasoningJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse reasoning JSON: {}", e.getMessage());
            return Collections.singletonList(reasoningJson);
        }
    }

    private String serializeToJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private Instant parseInstant(String scoredAt) {
        try {
            return Instant.parse(scoredAt);
        } catch (Exception e) {
            try {
                return LocalDate.parse(scoredAt).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }
}
