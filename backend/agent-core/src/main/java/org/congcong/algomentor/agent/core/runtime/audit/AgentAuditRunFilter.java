package org.congcong.algomentor.agent.core.runtime.audit;

import java.time.Instant;

/** 列表审计筛选条件，时间边界采用 [from, to) 语义。 */
public record AgentAuditRunFilter(
    Instant from,
    Instant to,
    int page,
    int pageSize,
    Long userId,
    String scenario,
    String purpose,
    String source,
    Long taskId,
    Long turnId,
    Long runId,
    String provider,
    String model,
    String status,
    String finishReason,
    Boolean hasTools,
    Boolean hasCompaction,
    Boolean overBudget,
    Boolean providerError,
    Long minCachedTokens,
    Long maxCachedTokens,
    Double minCacheRatio,
    Double maxCacheRatio,
    AgentAuditRunSort sort,
    AgentAuditSortDirection direction
) {

  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;

  public AgentAuditRunFilter(
      Instant from,
      Instant to,
      int page,
      int pageSize,
      Long userId,
      String scenario,
      String purpose,
      String source,
      Long taskId,
      Long turnId,
      Long runId,
      String provider,
      String model,
      String status,
      String finishReason,
      Boolean hasTools,
      Boolean hasCompaction,
      Boolean overBudget,
      Boolean providerError,
      Long minCachedTokens,
      Long maxCachedTokens,
      Double minCacheRatio,
      Double maxCacheRatio
  ) {
    this(from, to, page, pageSize, userId, scenario, purpose, source, taskId, turnId, runId, provider, model,
        status, finishReason, hasTools, hasCompaction, overBudget, providerError, minCachedTokens, maxCachedTokens,
        minCacheRatio, maxCacheRatio, AgentAuditRunSort.REQUESTED_AT, AgentAuditSortDirection.DESC);
  }

  public AgentAuditRunFilter {
    sort = sort == null ? AgentAuditRunSort.REQUESTED_AT : sort;
    direction = direction == null ? AgentAuditSortDirection.DESC : direction;
    if (from != null && to != null && !from.isBefore(to)) {
      throw new IllegalArgumentException("Audit query from must be before to");
    }
    if (page < 1) {
      throw new IllegalArgumentException("Audit query page must be positive");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("Audit query page size is out of range");
    }
    if (minCachedTokens != null && minCachedTokens < 0) {
      throw new IllegalArgumentException("Minimum cached tokens must not be negative");
    }
    if (maxCachedTokens != null && maxCachedTokens < 0) {
      throw new IllegalArgumentException("Maximum cached tokens must not be negative");
    }
    if (minCachedTokens != null && maxCachedTokens != null && minCachedTokens > maxCachedTokens) {
      throw new IllegalArgumentException("Cached token range is invalid");
    }
    validateCacheRatio(minCacheRatio, "Minimum cache ratio");
    validateCacheRatio(maxCacheRatio, "Maximum cache ratio");
    if (minCacheRatio != null && maxCacheRatio != null && minCacheRatio > maxCacheRatio) {
      throw new IllegalArgumentException("Cache ratio range is invalid");
    }
  }

  public int offset() {
    return (page - 1) * pageSize;
  }

  private void validateCacheRatio(Double value, String field) {
    if (value != null && (!Double.isFinite(value) || value < 0D || value > 1D)) {
      throw new IllegalArgumentException(field + " must be between zero and one");
    }
  }
}
