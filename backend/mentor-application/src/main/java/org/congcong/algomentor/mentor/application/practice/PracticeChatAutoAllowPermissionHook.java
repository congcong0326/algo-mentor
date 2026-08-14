package org.congcong.algomentor.mentor.application.practice;

import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHook;
import org.congcong.algomentor.agent.core.permission.ToolNamePermissionHook;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;

/**
 * Practice Chat 正式代码 Review 工具的自动授权策略。
 *
 * <p>只信任 Runtime 写入的 agentKey 元数据。此策略不放宽同一场景的其他工具；工具白名单与
 * 工具自身的参数、归属校验仍由既有边界负责。</p>
 */
public final class PracticeChatAutoAllowPermissionHook implements AgentToolPermissionHook {

  public static final int DEFAULT_ORDER = ToolNamePermissionHook.DEFAULT_ORDER - 100;
  public static final String POLICY_SOURCE = "practice-chat-code-review-auto-allow";

  @Override
  public int order() {
    return DEFAULT_ORDER;
  }

  @Override
  public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
    if (check == null) {
      throw new IllegalArgumentException("Agent tool permission check must not be null");
    }
    Object agentKey = check.trustedMetadata().get(AgentRuntimeMetadataKeys.AGENT_KEY);
    if (PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW.equals(check.toolCall().name())
        && PracticeChatAgentDefinition.KEY.value().equals(agentKey)) {
      return AgentToolPermissionDecisionPlan.allow(POLICY_SOURCE);
    }
    return AgentToolPermissionDecisionPlan.passthrough();
  }
}
