package org.congcong.algomentor.policy.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.Set;

/** 运行时快照中的已验证策略，主体集合与业务内容均在加载时编译。 */
public record CompiledPolicy<T>(
    long id,
    String typeCode,
    String name,
    int priority,
    boolean allSubject,
    Set<Long> userIds,
    Set<Long> groupIds,
    JsonNode rawContent,
    T content,
    long version
) {

  public CompiledPolicy {
    if (id < 1 || priority < 1 || version < 1) {
      throw new IllegalArgumentException("compiled policy identifiers, priority and version must be positive");
    }
    typeCode = Objects.requireNonNull(typeCode, "typeCode must not be null");
    name = Objects.requireNonNull(name, "name must not be null");
    userIds = Set.copyOf(Objects.requireNonNull(userIds, "userIds must not be null"));
    groupIds = Set.copyOf(Objects.requireNonNull(groupIds, "groupIds must not be null"));
    rawContent = Objects.requireNonNull(rawContent, "rawContent must not be null").deepCopy();
  }

  @Override
  public JsonNode rawContent() {
    return rawContent.deepCopy();
  }
}
