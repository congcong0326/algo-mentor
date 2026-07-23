package org.congcong.algomentor.policy.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicySubjectRange;

/** 普通编辑使用的乐观锁写入载荷，不能修改 typeCode 与 priority。 */
public record GenericPolicyUpdate(
    long policyId,
    long version,
    String name,
    String description,
    GenericPolicyStatus status,
    PolicySubjectRange subjectRange,
    JsonNode content,
    long operatorUserId
) {
}
