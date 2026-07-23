package org.congcong.algomentor.policy.service;

import java.util.Optional;
import org.congcong.algomentor.identity.group.relation.UserRelationProvider;
import org.congcong.algomentor.policy.cache.GenericPolicyCompiler;
import org.congcong.algomentor.policy.cache.PolicySetCache;
import org.congcong.algomentor.policy.metrics.GenericPolicyMetrics;
import org.congcong.algomentor.policy.model.EffectiveGenericPolicy;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;

/** 仅供 HTTP 等需要原始 JSON 的受控入口调用。 */
public final class DefaultEffectiveGenericPolicyQueryService implements EffectiveGenericPolicyQueryService {

  private final GenericPolicyTypeRegistry typeRegistry;
  private final PolicyResolutionEngine resolutionEngine;

  public DefaultEffectiveGenericPolicyQueryService(
      GenericPolicyTypeRegistry typeRegistry,
      PolicySetCache policySetCache,
      GenericPolicyCompiler compiler,
      UserRelationProvider userRelationProvider,
      GenericPolicyMetrics metrics
  ) {
    this.typeRegistry = typeRegistry;
    this.resolutionEngine = new PolicyResolutionEngine(
        typeRegistry, policySetCache, compiler, userRelationProvider, metrics);
  }

  @Override
  public Optional<EffectiveGenericPolicy> resolveEffective(String typeCode, long userId) {
    GenericPolicyType<?> policyType = typeRegistry.require(typeCode);
    return resolveRegistered(policyType, userId).map(match -> new EffectiveGenericPolicy(
        match.policy().id(),
        match.policy().typeCode(),
        match.policy().priority(),
        match.policy().rawContent().deepCopy()));
  }

  private <T> Optional<PolicyResolutionEngine.MatchedCompiledPolicy<T>> resolveRegistered(
      GenericPolicyType<T> policyType,
      long userId
  ) {
    return resolutionEngine.resolve(policyType, userId);
  }
}
