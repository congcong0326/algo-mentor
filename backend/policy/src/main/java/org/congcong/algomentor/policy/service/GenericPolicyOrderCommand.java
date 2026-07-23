package org.congcong.algomentor.policy.service;

import java.util.List;
import java.util.Map;

/** 完整排序请求，版本映射用于确保管理员没有覆盖并发修改。 */
public record GenericPolicyOrderCommand(List<Long> policyIds, Map<Long, Long> versions, long operatorUserId) {

  public GenericPolicyOrderCommand {
    policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
    versions = versions == null ? Map.of() : Map.copyOf(versions);
  }
}
