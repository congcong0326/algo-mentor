package org.congcong.algomentor.policy.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/** 新建策略时已经完成基础校验和主体校验的写入载荷。 */
public record GenericPolicyDraft(
    String typeCode,
    String name,
    String description,
    GenericPolicyStatus status,
    PolicySubjectRange subjectRange,
    JsonNode content,
    long operatorUserId
) {
}
