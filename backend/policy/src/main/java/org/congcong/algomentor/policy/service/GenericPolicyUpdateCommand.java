package org.congcong.algomentor.policy.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/** 管理员编辑策略的完整可编辑字段载荷。 */
public record GenericPolicyUpdateCommand(
    long version,
    String name,
    String description,
    GenericPolicyStatus status,
    PolicySubjectRange subjectRange,
    JsonNode content,
    long operatorUserId
) {
}
