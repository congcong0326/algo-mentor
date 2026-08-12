package org.congcong.algomentor.agent.core.runtime.audit;

import java.util.Locale;

/** 审计 run 列表允许的排序方向。 */
public enum AgentAuditSortDirection {
  ASC,
  DESC;

  public static AgentAuditSortDirection fromQueryValue(String value) {
    if (value == null || value.isBlank()) {
      return DESC;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Audit sort direction is invalid", exception);
    }
  }
}
