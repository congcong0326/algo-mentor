package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.Set;
import org.congcong.algomentor.agent.core.AgentErrorCode;

/** 首次 AI 草案生成协调服务使用的稳定错误码与安全文案。 */
public final class LearningPlanDraftGenerationConstants {

  public static final String IDEMPOTENCY_CONFLICT_CODE = "LEARNING_PLAN_GENERATION_IDEMPOTENCY_CONFLICT";
  public static final String IDEMPOTENCY_KEY_INVALID_CODE = "LEARNING_PLAN_GENERATION_IDEMPOTENCY_KEY_INVALID";
  public static final String GENERATION_FAILED_CODE = "LEARNING_PLAN_GENERATION_FAILED";
  public static final String GENERATION_FAILED_MESSAGE = "学习计划生成失败，请稍后重试。";
  public static final String GENERATION_INTERRUPTED_CODE = "LEARNING_PLAN_GENERATION_INTERRUPTED";
  public static final String GENERATION_INTERRUPTED_MESSAGE = "学习计划生成因服务重启中断，请重新生成。";
  public static final String GENERATED_MESSAGE = "已生成学习计划草案。";
  public static final String GENERATING_MESSAGE = "正在生成学习计划草案。";
  public static final String WORK_STARTED_MESSAGE = "开始生成学习计划";
  public static final String WORK_PROGRESS_MESSAGE = "正在规划学习计划";
  public static final Set<String> PUBLIC_TOOL_NAMES = Set.of(
      LearningPlanAgentToolNames.LIST_PROBLEM_FILTERS,
      LearningPlanAgentToolNames.SEARCH_PROBLEMS);
  public static final Set<String> PUBLIC_FAILURE_CODES = Set.of(
      GENERATION_FAILED_CODE,
      GENERATION_INTERRUPTED_CODE,
      AgentErrorCode.AGENT_EXECUTOR_OVERLOADED.name());

  private LearningPlanDraftGenerationConstants() {
  }
}
