package org.congcong.algomentor.policy.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/** 管理员新建策略的应用层请求。 */
public record GenericPolicyCreateCommand(
    String typeCode,
    String name,
    String description,
    GenericPolicyStatus status,
    PolicySubjectRange subjectRange,
    JsonNode content,
    long operatorUserId
) {
}
