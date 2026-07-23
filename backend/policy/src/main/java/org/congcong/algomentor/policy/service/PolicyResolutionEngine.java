package org.congcong.algomentor.policy.service;

import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.identity.group.relation.UserRelationProvider;
import org.congcong.algomentor.identity.group.relation.UserRelations;
import org.congcong.algomentor.policy.cache.GenericPolicyCompiler;
import org.congcong.algomentor.policy.cache.PolicySetCache;
import org.congcong.algomentor.policy.metrics.GenericPolicyMetrics;
import org.congcong.algomentor.policy.model.CompiledPolicy;
import org.congcong.algomentor.policy.model.CompiledPolicySet;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;

/** 统一处理缓存加载、用户关系读取与优先级命中，供强类型和原始 JSON 查询共用。 */
final class PolicyResolutionEngine {

  private final GenericPolicyTypeRegistry typeRegistry;
  private final PolicySetCache policySetCache;
  private final GenericPolicyCompiler compiler;
  private final UserRelationProvider userRelationProvider;
  private final GenericPolicyMetrics metrics;

  PolicyResolutionEngine(
      GenericPolicyTypeRegistry typeRegistry,
      PolicySetCache policySetCache,
      GenericPolicyCompiler compiler,
      UserRelationProvider userRelationProvider,
      GenericPolicyMetrics metrics
  ) {
    this.typeRegistry = typeRegistry;
    this.policySetCache = policySetCache;
    this.compiler = compiler;
    this.userRelationProvider = userRelationProvider;
    this.metrics = metrics;
  }

  <T> Optional<MatchedCompiledPolicy<T>> resolve(GenericPolicyType<T> requestedType, long userId) {
    if (userId < 1) {
      throw new GenericPolicyException(GenericPolicyErrorCode.POLICY_INVALID_REQUEST, "用户 ID 必须为正整数。");
    }
    GenericPolicyType<T> policyType = typeRegistry.requireRegisteredInstance(requestedType);
    try {
      CompiledPolicySet policySet = policySetCache.get(policyType.typeCode(), () -> compiler.compile(policyType.typeCode()));
      UserRelations relations = userRelationProvider.getRelations(userId);
      for (CompiledPolicy<?> policy : policySet.sortedPolicies()) {
        Match match = match(policy, userId, relations.activeGroupIds());
        if (match != null) {
          @SuppressWarnings("unchecked")
          CompiledPolicy<T> typedPolicy = (CompiledPolicy<T>) policy;
          metrics.recordResolve(policyType.typeCode(), "hit");
          return Optional.of(new MatchedCompiledPolicy<>(typedPolicy, match.source(), match.subjectId()));
        }
      }
      metrics.recordResolve(policyType.typeCode(), "miss");
      return Optional.empty();
    } catch (PolicyResolutionException exception) {
      metrics.recordResolve(policyType.typeCode(), "failure");
      throw exception;
    } catch (RuntimeException exception) {
      metrics.recordResolve(policyType.typeCode(), "failure");
      throw new PolicyResolutionException("通用策略运行时解析失败，typeCode=" + policyType.typeCode(), exception);
    }
  }

  private Match match(CompiledPolicy<?> policy, long userId, Set<Long> activeGroupIds) {
    if (policy.userIds().contains(userId)) {
      return new Match(PolicyMatchSource.USER, userId);
    }
    for (Long groupId : policy.groupIds()) {
      if (activeGroupIds.contains(groupId)) {
        return new Match(PolicyMatchSource.GROUP, groupId);
      }
    }
    if (policy.allSubject()) {
      return new Match(PolicyMatchSource.ALL, null);
    }
    return null;
  }

  record MatchedCompiledPolicy<T>(CompiledPolicy<T> policy, PolicyMatchSource matchSource, Long matchedSubjectId) {
  }

  private record Match(PolicyMatchSource source, Long subjectId) {
  }
}
