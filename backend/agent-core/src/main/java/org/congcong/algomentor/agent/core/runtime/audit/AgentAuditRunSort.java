package org.congcong.algomentor.agent.core.runtime.audit;

import java.util.Locale;

/** 审计 run 列表允许的排序字段；用于限制持久层排序表达式。 */
public enum AgentAuditRunSort {
  REQUESTED_AT,
  OVER_BUDGET,
  CACHE_RATIO;

  public static AgentAuditRunSort fromQueryValue(String value) {
    if (value == null || value.isBlank()) {
      return REQUESTED_AT;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "requestedat", "requested_at" -> REQUESTED_AT;
      case "overbudget", "over_budget" -> OVER_BUDGET;
      case "cacheratio", "cache_ratio" -> CACHE_RATIO;
      default -> throw new IllegalArgumentException("Audit sort field is invalid");
    };
  }
}
