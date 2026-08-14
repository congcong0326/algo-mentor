package org.congcong.algomentor.mentor.application.practice;

import java.util.Objects;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentPreparedStream;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;

/**
 * 训练聊天单轮编排器：把已完成业务前检的 Practice 输入交给统一 Runtime。
 */
public class PracticeTurnOrchestrator {

  private final AgentRuntime agentRuntime;

  public PracticeTurnOrchestrator(AgentRuntime agentRuntime) {
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime must not be null");
  }

  public Flow.Publisher<AgentStreamEvent> stream(PracticeChatAgentInput input) {
    return agentRuntime.stream(invocation(input));
  }

  public AgentPreparedStream prepareStream(PracticeChatAgentInput input) {
    return agentRuntime.prepareStream(invocation(input));
  }

  private AgentInvocation<PracticeChatAgentInput> invocation(PracticeChatAgentInput input) {
    PracticeChatAgentInput candidate = Objects.requireNonNull(input, "Practice chat input must not be null");
    return new AgentInvocation<>(
        PracticeChatAgentDefinition.KEY,
        candidate,
        new AgentInvocationContext(
            candidate.userId(),
            AgentInvocationMode.USER_ENTRY,
            candidate.idempotencyKey(),
            null,
            null,
            candidate.requestSize(),
            true));
  }
}
