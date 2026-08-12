package org.congcong.algomentor.api.controller.admin.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditQuery;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunFilter;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunSort;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditSortDirection;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditRunDetailResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditRunPageResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditStepDetailResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditToolResultResponse;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 管理员 Agent 请求审计的只读 HTTP 边界。 */
@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH)
public class AdminAiAuditController {

  private static final int MAX_TOOL_RESULT_LIMIT = 16_000;

  private final AgentAuditQuery auditQuery;
  private final AdminAiAuditResponseMapper responseMapper;
  private final AdminOperationAuditRecorder auditRecorder;

  public AdminAiAuditController(
      AgentAuditQuery auditQuery,
      IdentityUserRepository identityUserRepository,
      @Nullable AdminOperationAuditRecorder auditRecorder
  ) {
    this.auditQuery = auditQuery;
    this.responseMapper = new AdminAiAuditResponseMapper(identityUserRepository);
    this.auditRecorder = auditRecorder == null ? new NoopAdminOperationAuditRecorder() : auditRecorder;
  }

  @GetMapping(AdminAiApiContractConstants.AUDIT_RUNS_PATH)
  @PreAuthorize("hasAuthority('ai-run:read')")
  public ApiResponse<AdminAiAuditRunPageResponse> runs(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String scenario,
      @RequestParam(required = false) String purpose,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Long taskId,
      @RequestParam(required = false) Long turnId,
      @RequestParam(required = false) Long runId,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String finishReason,
      @RequestParam(required = false) Boolean hasTools,
      @RequestParam(required = false) Boolean hasCompaction,
      @RequestParam(required = false) Boolean overBudget,
      @RequestParam(required = false) Boolean providerError,
      @RequestParam(required = false) Long minCachedTokens,
      @RequestParam(required = false) Long maxCachedTokens,
      @RequestParam(required = false) Double minCacheRatio,
      @RequestParam(required = false) Double maxCacheRatio,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction
  ) {
    try {
      return ApiResponse.success(responseMapper.page(auditQuery.findRuns(new AgentAuditRunFilter(
          parseInstant(from, false), parseInstant(to, true), page, pageSize, userId, clean(scenario), clean(purpose),
          clean(source), taskId, turnId,
          runId, clean(provider), clean(model), clean(status), clean(finishReason), hasTools, hasCompaction, overBudget,
          providerError, minCachedTokens, maxCachedTokens, minCacheRatio, maxCacheRatio,
          AgentAuditRunSort.fromQueryValue(sort), AgentAuditSortDirection.fromQueryValue(direction)))));
    } catch (IllegalArgumentException exception) {
      throw badRequest("Audit query is invalid.", exception);
    }
  }

  @GetMapping(AdminAiApiContractConstants.AUDIT_RUN_PATH)
  @PreAuthorize("hasAuthority('ai-run:read')")
  public ApiResponse<AdminAiAuditRunDetailResponse> run(
      @PathVariable long runId,
      Authentication authentication
  ) {
    AdminAiAuditRunDetailResponse response;
    try {
      response = auditQuery.findRun(runId)
          .map(responseMapper::detail)
          .orElseThrow(() -> notFound("Audit run was not found."));
    } catch (IllegalArgumentException exception) {
      throw badRequest("Audit run id is invalid.", exception);
    }
    recordView(authentication, runId, "summary");
    return ApiResponse.success(response);
  }

  @GetMapping(AdminAiApiContractConstants.AUDIT_STEP_PATH)
  @PreAuthorize("hasAuthority('ai-run:read')")
  public ApiResponse<AdminAiAuditStepDetailResponse> step(
      @PathVariable long runId,
      @PathVariable int stepIndex,
      @RequestParam(defaultValue = "false") boolean raw,
      Authentication authentication
  ) {
    AdminAiAuditStepDetailResponse response;
    try {
      response = auditQuery.findStep(runId, stepIndex, raw)
          .map(responseMapper::stepDetail)
          .orElseThrow(() -> notFound("Audit step was not found."));
    } catch (IllegalArgumentException exception) {
      throw badRequest("Audit step is invalid.", exception);
    }
    recordView(authentication, runId, raw ? "raw" : "detail");
    return ApiResponse.success(response);
  }

  @GetMapping(AdminAiApiContractConstants.AUDIT_TOOL_RESULT_PATH)
  @PreAuthorize("hasAuthority('ai-run:read')")
  public ApiResponse<AdminAiAuditToolResultResponse> toolResult(
      @PathVariable long runId,
      @PathVariable String toolCallId,
      @RequestParam(defaultValue = "false") boolean includeContent,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "2000") int limit,
      Authentication authentication
  ) {
    if (offset < 0 || limit < 1 || limit > MAX_TOOL_RESULT_LIMIT) {
      throw badRequest("Tool result read range is invalid.", null);
    }
    AdminAiAuditToolResultResponse response;
    try {
      response = auditQuery.findToolResult(runId, toolCallId, includeContent, offset, limit)
          .map(responseMapper::toolResult)
          .orElseThrow(() -> notFound("Audit tool result was not found."));
    } catch (IllegalArgumentException exception) {
      throw badRequest("Audit tool result is invalid.", exception);
    }
    recordView(authentication, runId, includeContent ? "content" : "detail");
    return ApiResponse.success(response);
  }

  private void recordView(Authentication authentication, long runId, String viewType) {
    long operatorUserId = AdminAiRequestSupport.requireOperatorId(authentication);
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.AI_RUN_TRACE_VIEW,
        AdminAuditTargetType.AI_RUN,
        String.valueOf(runId),
        Map.of(AdminAuditMetadataKey.USER_ID, operatorUserId,
            AdminAuditMetadataKey.APPLICATION_VERSION, viewType)));
  }

  private Instant parseInstant(String value, boolean upperBound) {
    String candidate = clean(value);
    if (candidate == null) {
      return null;
    }
    try {
      return Instant.parse(candidate);
    } catch (RuntimeException ignored) {
      try {
        LocalDate date = LocalDate.parse(candidate);
        return (upperBound ? date.plusDays(1) : date).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (RuntimeException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit time range is invalid.", exception);
      }
    }
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private ResponseStatusException badRequest(String message, Throwable cause) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, cause);
  }
}
