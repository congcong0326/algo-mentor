package org.congcong.algomentor.policy.controller.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.policy.model.EffectiveGenericPolicy;

/** 当前用户有效策略查询响应；未命中仍返回成功。 */
public record EffectiveGenericPolicyResponse(
    boolean matched,
    Long policyId,
    String typeCode,
    Integer priority,
    JsonNode content
) {

  public static EffectiveGenericPolicyResponse matched(EffectiveGenericPolicy policy) {
    return new EffectiveGenericPolicyResponse(
        true, policy.policyId(), policy.typeCode(), policy.priority(), policy.content().deepCopy());
  }

  public static EffectiveGenericPolicyResponse unmatched(String typeCode) {
    return new EffectiveGenericPolicyResponse(false, null, typeCode, null, null);
  }
}
