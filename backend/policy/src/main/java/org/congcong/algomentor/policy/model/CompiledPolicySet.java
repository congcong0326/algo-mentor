package org.congcong.algomentor.policy.model;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.policy.type.GenericPolicyType;

/** 一个 typeCode 的已启用策略不可变缓存快照。 */
public record CompiledPolicySet(
    String typeCode,
    GenericPolicyType<?> policyType,
    List<CompiledPolicy<?>> sortedPolicies
) {

  public CompiledPolicySet {
    typeCode = Objects.requireNonNull(typeCode, "typeCode must not be null");
    policyType = Objects.requireNonNull(policyType, "policyType must not be null");
    sortedPolicies = List.copyOf(Objects.requireNonNull(sortedPolicies, "sortedPolicies must not be null"));
  }
}
