package org.congcong.algomentor.policy.service;

import java.util.Optional;
import org.congcong.algomentor.identity.group.relation.UserRelationProvider;
import org.congcong.algomentor.policy.cache.GenericPolicyCompiler;
import org.congcong.algomentor.policy.cache.PolicySetCache;
import org.congcong.algomentor.policy.metrics.GenericPolicyMetrics;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;

/** 默认强类型运行时查询实现。 */
public final class DefaultGenericPolicyQueryService implements GenericPolicyQueryService {

  private final PolicyResolutionEngine resolutionEngine;

  public DefaultGenericPolicyQueryService(
      GenericPolicyTypeRegistry typeRegistry,
      PolicySetCache policySetCache,
      GenericPolicyCompiler compiler,
      UserRelationProvider userRelationProvider,
      GenericPolicyMetrics metrics
  ) {
    this.resolutionEngine = new PolicyResolutionEngine(
        typeRegistry, policySetCache, compiler, userRelationProvider, metrics);
  }

  @Override
  public <T> Optional<ResolvedPolicy<T>> resolve(GenericPolicyType<T> policyType, long userId) {
    return resolutionEngine.resolve(policyType, userId).map(match -> new ResolvedPolicy<>(
        match.policy().id(),
        match.policy().typeCode(),
        match.policy().name(),
        match.policy().priority(),
        match.policy().content(),
        match.matchSource(),
        match.matchedSubjectId(),
        match.policy().version()));
  }
}
