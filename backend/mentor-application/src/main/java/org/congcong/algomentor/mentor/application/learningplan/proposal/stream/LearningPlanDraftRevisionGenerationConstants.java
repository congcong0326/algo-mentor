package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import java.util.Set;
import org.congcong.algomentor.agent.core.AgentErrorCode;

/** 草案修订异步生成使用的稳定错误码和公开进度文案。 */
public final class LearningPlanDraftRevisionGenerationConstants {

  public static final String IDEMPOTENCY_KEY_INVALID_CODE = "LEARNING_PLAN_DRAFT_REVISION_IDEMPOTENCY_KEY_INVALID";
  public static final String IDEMPOTENCY_CONFLICT_CODE = "LEARNING_PLAN_DRAFT_REVISION_IDEMPOTENCY_CONFLICT";
  public static final String GENERATION_FAILED_CODE = "LEARNING_PLAN_DRAFT_REVISION_FAILED";
  public static final String GENERATION_FAILED_MESSAGE = "学习计划修订失败，请稍后重试。";
  public static final String GENERATION_INTERRUPTED_CODE = "LEARNING_PLAN_DRAFT_REVISION_INTERRUPTED";
  public static final String GENERATION_INTERRUPTED_MESSAGE = "学习计划修订因服务重启中断，请重新提交。";
  public static final String SUPERSEDED_CODE = "LEARNING_PLAN_DRAFT_REVISION_SUPERSEDED";
  public static final String SUPERSEDED_MESSAGE = "学习计划修订结果已被更新的请求取代。";
  public static final String WORK_STARTED_MESSAGE = "开始修订学习计划";
  public static final String WORK_PROGRESS_MESSAGE = "正在修订学习计划";
  public static final Set<String> PUBLIC_FAILURE_CODES = Set.of(
      GENERATION_FAILED_CODE,
      GENERATION_INTERRUPTED_CODE,
      SUPERSEDED_CODE,
      AgentErrorCode.AGENT_EXECUTOR_OVERLOADED.name());

  private LearningPlanDraftRevisionGenerationConstants() {
  }
}
