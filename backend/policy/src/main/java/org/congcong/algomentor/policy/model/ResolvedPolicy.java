package org.congcong.algomentor.policy.model;

import java.util.Objects;

/** 面向业务调用方的单条强类型策略解析结果。 */
public record ResolvedPolicy<T>(
    long policyId,
    String typeCode,
    String name,
    int priority,
    T content,
    PolicyMatchSource matchSource,
    Long matchedSubjectId,
    long version
) {

  public ResolvedPolicy {
    if (policyId < 1 || priority < 1 || version < 1) {
      throw new IllegalArgumentException("resolved policy identifiers, priority and version must be positive");
    }
    typeCode = Objects.requireNonNull(typeCode, "typeCode must not be null");
    name = Objects.requireNonNull(name, "name must not be null");
    matchSource = Objects.requireNonNull(matchSource, "matchSource must not be null");
  }
}
