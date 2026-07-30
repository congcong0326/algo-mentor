package org.congcong.algomentor.mentor.application.conversation;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentLoopPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentOutputContract;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;

/** 使用受管理 Mentor Prompt 的普通会话 Definition。 */
public final class MentorConversationAgentDefinition implements AgentDefinition<MentorConversationAgentInput> {

  public static final int MAX_STEPS = 1;
  public static final AgentKey<MentorConversationAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.MENTOR_CONVERSATION.code(), MentorConversationAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final MentorConversationRunAdapter runAdapter;

  public MentorConversationAgentDefinition(MentorConversationRunAdapter runAdapter) {
    this.runAdapter = Objects.requireNonNull(runAdapter, "Mentor conversation run adapter must not be null");
  }

  @Override
  public AgentKey<MentorConversationAgentInput> key() {
    return KEY;
  }

  @Override
  public AgentLoopPolicy loopPolicy() {
    return LOOP_POLICY;
  }

  @Override
  public List<String> allowedToolNames() {
    return List.of();
  }

  @Override
  public AgentOutputContract outputContract() {
    return OUTPUT_CONTRACT;
  }

  @Override
  public AgentPreparedRequest prepare(MentorConversationAgentInput input, AgentInvocationContext context) {
    MentorConversationAgentInput candidate = Objects.requireNonNull(input, "Mentor conversation input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Mentor conversation input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Mentor conversation input idempotency key does not match the invocation");
    }
    if (candidate.requestSize() != invocation.requestSize()) {
      throw new IllegalArgumentException("Mentor conversation input size does not match the invocation");
    }
    return runAdapter.prepare(candidate);
  }
}
