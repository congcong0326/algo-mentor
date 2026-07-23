package org.congcong.algomentor.policy.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

/** 通用策略的持久化领域模型，内容以原始 JSON 保存。 */
public record GenericPolicy(
    long id,
    String typeCode,
    String name,
    String description,
    GenericPolicyStatus status,
    int priority,
    PolicySubjectRange subjectRange,
    JsonNode content,
    long version,
    long createdBy,
    Instant createdAt,
    long updatedBy,
    Instant updatedAt
) {

  public GenericPolicy {
    if (id < 1 || version < 1 || priority < 1 || createdBy < 1 || updatedBy < 1) {
      throw new IllegalArgumentException("policy numeric identifiers, version and priority must be positive");
    }
    typeCode = Objects.requireNonNull(typeCode, "typeCode must not be null");
    name = Objects.requireNonNull(name, "name must not be null");
    status = Objects.requireNonNull(status, "status must not be null");
    subjectRange = Objects.requireNonNull(subjectRange, "subjectRange must not be null");
    content = Objects.requireNonNull(content, "content must not be null").deepCopy();
    createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  @Override
  public JsonNode content() {
    return content.deepCopy();
  }
}
