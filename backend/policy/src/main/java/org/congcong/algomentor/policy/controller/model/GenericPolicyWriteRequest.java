package org.congcong.algomentor.policy.controller.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/** 新建策略的管理 API 请求。 */
public record GenericPolicyWriteRequest(
    String typeCode,
    String name,
    String description,
    GenericPolicyStatus status,
    PolicySubjectRange subjectRange,
    JsonNode content
) {
}
