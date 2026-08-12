package org.congcong.algomentor.api.learningplan.policy;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionCapabilities;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicyResolver;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;

public final class PolicyBackedLearningPlanAiRevisionPolicyResolver
    implements LearningPlanAiRevisionPolicyResolver {
  private final GenericPolicyQueryService queryService;
  private final GenericPolicyType<LearningPlanAiRevisionPolicy> policyType;

  public PolicyBackedLearningPlanAiRevisionPolicyResolver(
      GenericPolicyQueryService queryService,
      GenericPolicyType<LearningPlanAiRevisionPolicy> policyType) {
    this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    this.policyType = Objects.requireNonNull(policyType, "policyType must not be null");
  }

  @Override
  public LearningPlanAiRevisionCapabilities resolve(long userId) {
    try {
      return queryService.resolve(policyType, userId)
          .map(resolved -> resolved.content().capabilities())
          .orElseGet(() -> LearningPlanAiRevisionPolicy.defaults().capabilities());
    } catch (RuntimeException exception) {
      throw new LearningPlanException(
          LearningPlanAiRevisionPolicyConstants.POLICY_UNAVAILABLE_CODE,
          "api.error." + LearningPlanAiRevisionPolicyConstants.POLICY_UNAVAILABLE_CODE,
          "学习计划 AI 修订策略暂不可用，请稍后重试。");
    }
  }
}
