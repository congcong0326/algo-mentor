package org.congcong.algomentor.policy.controller.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/** 编辑策略的管理 API 请求；typeCode 与 priority 不可通过该接口修改。 */
public record GenericPolicyUpdateRequest(
    long version,
    String name,
    String description,
    GenericPolicyStatus status,
    PolicySubjectRange subjectRange,
    JsonNode content
) {
}
