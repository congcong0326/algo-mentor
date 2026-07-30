package org.congcong.algomentor.mentor.application.topic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentLoopPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentOutputContract;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.mentor.application.MentorApplicationConstants;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;

/** 使用受管理 Topic system prompt 的无工具单步 Agent Definition。 */
public final class TopicExplanationAgentDefinition implements AgentDefinition<TopicExplanationAgentInput> {

  public static final AgentKey<TopicExplanationAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.TOPIC_EXPLANATION.code(), TopicExplanationAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(1);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final ManagedSystemPromptResolver systemPromptResolver;

  public TopicExplanationAgentDefinition(ManagedSystemPromptResolver systemPromptResolver) {
    this.systemPromptResolver = Objects.requireNonNull(systemPromptResolver, "System prompt resolver must not be null");
  }

  @Override
  public AgentKey<TopicExplanationAgentInput> key() {
    return KEY;
  }

  @Override
  public AgentLoopPolicy loopPolicy() {
    return LOOP_POLICY;
  }

  @Override
  public AgentOutputContract outputContract() {
    return OUTPUT_CONTRACT;
  }

  @Override
  public AgentPreparedRequest prepare(TopicExplanationAgentInput input, AgentInvocationContext context) {
    TopicExplanationAgentInput candidate = Objects.requireNonNull(input, "Topic explanation input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Topic explanation input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Topic explanation input idempotency key does not match the invocation");
    }
    ResolvedSystemPromptSnapshot snapshot = systemPromptResolver.resolve(
        ManagedSystemPromptDefinitions.TOPIC_EXPLANATION, candidate.userId());
    Map<String, Object> metadata = new LinkedHashMap<>(candidate.displayMetadata());
    metadata.put(AgentRuntimeMetadataKeys.TITLE, candidate.topic().title());
    metadata.put(AgentRuntimeMetadataKeys.TOPIC_TITLE, candidate.topic().title());
    metadata.put(AgentRuntimeMetadataKeys.ADAPTER, MentorApplicationConstants.TOPIC_EXPLANATION);
    metadata.putAll(SystemPromptMetadataKeys.from(snapshot));
    String request = "Explain the learning topic for an algorithm student: " + candidate.topic().title();
    return new AgentPreparedRequest(
        List.of(
            LlmMessage.system(snapshot.requireSection(SystemPromptSectionKeys.TOPIC_EXPLANATION_BASE).text()),
            LlmMessage.user(request)),
        Map.copyOf(metadata),
        AgentExecutionOptions.defaults());
  }
}
