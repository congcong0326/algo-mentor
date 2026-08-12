package org.congcong.algomentor.mentor.application.learningplan.policy;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftSource;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;

/** 统一提供能力快照和 AI 发起前门禁。 */
public class LearningPlanAiRevisionAccessService {
  private final LearningPlanAiRevisionPolicyResolver resolver;
  private final LearningPlanAiRevisionAccessMetrics metrics;

  public LearningPlanAiRevisionAccessService(LearningPlanAiRevisionPolicyResolver resolver) {
    this(resolver, LearningPlanAiRevisionAccessMetrics.NOOP);
  }

  public LearningPlanAiRevisionAccessService(
      LearningPlanAiRevisionPolicyResolver resolver,
      LearningPlanAiRevisionAccessMetrics metrics) {
    this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    this.metrics = metrics == null ? LearningPlanAiRevisionAccessMetrics.NOOP : metrics;
  }

  public LearningPlanAiRevisionCapabilities capabilities(long userId) {
    try {
      LearningPlanAiRevisionCapabilities capabilities = resolver.resolve(userId);
      for (LearningPlanAiRevisionAction action : LearningPlanAiRevisionAction.values()) {
        metrics.record(action, capabilities.allows(action)
            ? LearningPlanAiRevisionAccessMetrics.Outcome.ALLOWED
            : LearningPlanAiRevisionAccessMetrics.Outcome.DENIED);
      }
      return capabilities;
    } catch (RuntimeException exception) {
      recordUnavailable();
      throw exception;
    }
  }

  public void requireDraftRevision(long userId, LearningPlanDraftSource source) {
    require(userId, source == LearningPlanDraftSource.TEMPLATE
        ? LearningPlanAiRevisionAction.TEMPLATE_DRAFT_REVISION
        : LearningPlanAiRevisionAction.PERSONALIZED_DRAFT_REVISION);
  }

  public void requireSavedPlanRevision(long userId) {
    require(userId, LearningPlanAiRevisionAction.SAVED_PLAN_REVISION);
  }

  public void require(long userId, LearningPlanAiRevisionAction action) {
    LearningPlanAiRevisionCapabilities capabilities;
    try {
      capabilities = resolver.resolve(userId);
    } catch (RuntimeException exception) {
      metrics.record(action, LearningPlanAiRevisionAccessMetrics.Outcome.POLICY_UNAVAILABLE);
      throw exception;
    }
    if (!capabilities.allows(action)) {
      metrics.record(action, LearningPlanAiRevisionAccessMetrics.Outcome.DENIED);
      throw new LearningPlanException(
          LearningPlanAiRevisionPolicyConstants.NOT_ENABLED_CODE,
          "api.error." + LearningPlanAiRevisionPolicyConstants.NOT_ENABLED_CODE,
          "当前用户未获准使用该学习计划 AI 修订能力。");
    }
    metrics.record(action, LearningPlanAiRevisionAccessMetrics.Outcome.ALLOWED);
  }

  private void recordUnavailable() {
    for (LearningPlanAiRevisionAction action : LearningPlanAiRevisionAction.values()) {
      metrics.record(action, LearningPlanAiRevisionAccessMetrics.Outcome.POLICY_UNAVAILABLE);
    }
  }
}
