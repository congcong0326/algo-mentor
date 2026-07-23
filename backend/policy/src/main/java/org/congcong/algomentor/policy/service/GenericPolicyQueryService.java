package org.congcong.algomentor.policy.service;

import java.util.Optional;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.type.GenericPolicyType;

/** 业务模块使用的强类型运行时策略查询门面。 */
public interface GenericPolicyQueryService {

  <T> Optional<ResolvedPolicy<T>> resolve(GenericPolicyType<T> policyType, long userId);
}
