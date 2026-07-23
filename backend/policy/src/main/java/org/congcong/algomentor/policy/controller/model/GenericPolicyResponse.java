package org.congcong.algomentor.policy.controller.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.congcong.algomentor.policy.model.GenericPolicy;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/** 管理端策略响应，content 保持原始 JSON 节点。 */
public record GenericPolicyResponse(
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

  public static GenericPolicyResponse from(GenericPolicy policy) {
    return new GenericPolicyResponse(
        policy.id(),
        policy.typeCode(),
        policy.name(),
        policy.description(),
        policy.status(),
        policy.priority(),
        policy.subjectRange(),
        policy.content().deepCopy(),
        policy.version(),
        policy.createdBy(),
        policy.createdAt(),
        policy.updatedBy(),
        policy.updatedAt());
  }
}
