package org.congcong.algomentor.api.learningplan.policy;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyResolver;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;

/** 使用通用策略底座解析指定用户的学习计划创建容量。 */
public class PolicyBackedLearningPlanCreationPolicyResolver implements LearningPlanCreationPolicyResolver {

  private final GenericPolicyQueryService queryService;
  private final GenericPolicyType<LearningPlanCreationPolicy> policyType;

  public PolicyBackedLearningPlanCreationPolicyResolver(
      GenericPolicyQueryService queryService,
      GenericPolicyType<LearningPlanCreationPolicy> policyType
  ) {
    this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    this.policyType = Objects.requireNonNull(policyType, "policyType must not be null");
  }

  @Override
  public LearningPlanCreationPolicy resolve(long userId) {
    try {
      return queryService.resolve(policyType, userId)
          .map(resolved -> resolved.content())
          .orElseGet(LearningPlanCreationPolicy::defaults);
    } catch (LearningPlanException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new LearningPlanException(
          LearningPlanCreationPolicyConstants.POLICY_UNAVAILABLE_CODE,
          "api.error." + LearningPlanCreationPolicyConstants.POLICY_UNAVAILABLE_CODE,
          "学习计划创建策略暂不可用，请稍后重试。");
    }
  }
}
