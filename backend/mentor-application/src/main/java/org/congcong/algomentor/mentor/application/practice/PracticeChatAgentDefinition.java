package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentLoopPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentOutputContract;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;

/** 题目训练聊天的 Runtime Definition，工具仅按当前已启用能力白名单暴露。 */
public final class PracticeChatAgentDefinition implements AgentDefinition<PracticeChatAgentInput> {

  public static final int MAX_STEPS = 8;
  public static final AgentKey<PracticeChatAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.PRACTICE_CHAT.code(), PracticeChatAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final PracticeChatRunAdapter runAdapter;
  private final List<String> allowedToolNames;

  public PracticeChatAgentDefinition(PracticeChatRunAdapter runAdapter, List<String> allowedToolNames) {
    this.runAdapter = Objects.requireNonNull(runAdapter, "Practice chat run adapter must not be null");
    LinkedHashSet<String> toolNames = new LinkedHashSet<>(
        allowedToolNames == null ? List.of() : allowedToolNames);
    toolNames.removeIf(toolName -> toolName == null || toolName.isBlank());
    if (toolNames.isEmpty()) {
      throw new IllegalArgumentException("Practice chat requires at least one enabled Agent tool");
    }
    this.allowedToolNames = List.copyOf(toolNames);
  }

  @Override
  public AgentKey<PracticeChatAgentInput> key() {
    return KEY;
  }

  @Override
  public AgentExecutionGroup executionGroup() {
    return AgentExecutionGroup.PRACTICE;
  }

  @Override
  public AgentLoopPolicy loopPolicy() {
    return LOOP_POLICY;
  }

  @Override
  public List<String> allowedToolNames() {
    return allowedToolNames;
  }

  @Override
  public AgentOutputContract outputContract() {
    return OUTPUT_CONTRACT;
  }

  @Override
  public AgentPreparedRequest prepare(PracticeChatAgentInput input, AgentInvocationContext context) {
    PracticeChatAgentInput candidate = Objects.requireNonNull(input, "Practice chat input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Practice chat input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Practice chat input idempotency key does not match the invocation");
    }
    if (candidate.requestSize() != invocation.requestSize()) {
      throw new IllegalArgumentException("Practice chat input size does not match the invocation");
    }
    return runAdapter.prepare(candidate);
  }
}
