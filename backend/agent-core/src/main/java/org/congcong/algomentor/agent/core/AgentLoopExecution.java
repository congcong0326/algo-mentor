package org.congcong.algomentor.agent.core;

import java.util.Collection;
import java.util.Objects;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;

/** 单次同步 Agent loop 的工具集合与执行边界。 */
public final class AgentLoopExecution {

  private final AgentToolRegistry registeredTools;
  private final AgentToolRegistry allowedTools;
  private final int maxSteps;
  private final LlmToolChoice toolChoice;

  private AgentLoopExecution(
      AgentToolRegistry registeredTools,
      AgentToolRegistry allowedTools,
      int maxSteps,
      LlmToolChoice toolChoice
  ) {
    this.registeredTools = Objects.requireNonNull(
        registeredTools, "Agent loop registered tool registry must not be null");
    this.allowedTools = Objects.requireNonNull(
        allowedTools, "Agent loop allowed tool registry must not be null");
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    this.maxSteps = maxSteps;
    this.toolChoice = Objects.requireNonNull(toolChoice, "Agent loop tool choice must not be null");
  }

  /** Definition 驱动的 run 固定空工具为 NONE + 单 step，有工具为 AUTO。 */
  public static AgentLoopExecution forRuntime(
      AgentToolRegistry registeredTools,
      Collection<String> allowedToolNames,
      int maxSteps
  ) {
    AgentToolRegistry globalTools = Objects.requireNonNull(
        registeredTools, "Agent loop registered tool registry must not be null");
    AgentToolRegistry selectedTools = globalTools.select(allowedToolNames);
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    if (selectedTools.isEmpty()) {
      return new AgentLoopExecution(globalTools, selectedTools, 1, LlmToolChoice.none());
    }
    return new AgentLoopExecution(globalTools, selectedTools, maxSteps, LlmToolChoice.auto());
  }

  /** 旧 Runner 的兼容入口保留既有全量工具和工具选择配置。 */
  static AgentLoopExecution legacy(
      AgentToolRegistry toolRegistry,
      int maxSteps,
      LlmToolChoice toolChoice
  ) {
    AgentToolRegistry globalTools = Objects.requireNonNull(
        toolRegistry, "Agent loop registered tool registry must not be null");
    LlmToolChoice resolvedChoice = globalTools.isEmpty()
        ? LlmToolChoice.none()
        : toolChoice == null ? LlmToolChoice.auto() : toolChoice;
    return new AgentLoopExecution(globalTools, globalTools, maxSteps, resolvedChoice);
  }

  /** 验证 Definition 的 step 上限是否可被全局硬上限接受。 */
  public static void validateMaxSteps(int maxSteps, int hardLimit) {
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    if (hardLimit < 1) {
      throw new IllegalArgumentException("Agent loop hard limit must be positive");
    }
    if (maxSteps > hardLimit) {
      throw new IllegalArgumentException("Agent loop max steps exceeds hard limit");
    }
  }

  public AgentToolRegistry toolRegistry() {
    return allowedTools;
  }

  /** 是否为当前运行全局注册但未获 Definition 允许的工具。 */
  public boolean isRegisteredTool(String toolName) {
    return registeredTools.find(toolName).isPresent();
  }

  public int maxSteps() {
    return maxSteps;
  }

  public LlmToolChoice toolChoice() {
    return toolChoice;
  }
}
