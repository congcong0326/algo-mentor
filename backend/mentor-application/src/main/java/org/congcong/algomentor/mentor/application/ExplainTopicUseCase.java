package org.congcong.algomentor.mentor.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExplainTopicUseCase {

  private static final long LEGACY_DEFAULT_USER_ID = 1L;

  private final AgentRunner agentRunner;
  private final AgentLoopRunner agentLoopRunner;
  private final ManagedSystemPromptResolver systemPromptResolver;

  public ExplainTopicUseCase(AgentRunner agentRunner, AgentLoopRunner agentLoopRunner) {
    this(agentRunner, agentLoopRunner, ManagedSystemPrompts.defaultResolver());
  }

  @Autowired
  public ExplainTopicUseCase(
      AgentRunner agentRunner,
      AgentLoopRunner agentLoopRunner,
      ManagedSystemPromptResolver systemPromptResolver
  ) {
    this.agentRunner = agentRunner;
    this.agentLoopRunner = agentLoopRunner;
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public String explain(String topic) {
    return explain(LEGACY_DEFAULT_USER_ID, topic);
  }

  public String explain(long userId, String topic) {
    return agentRunner.run(toAgentRequest(LearningTopic.of(topic), userId, Map.of())).content();
  }

  public Flow.Publisher<AgentStreamEvent> stream(String topic) {
    return stream(topic, LEGACY_DEFAULT_USER_ID, Map.of());
  }

  public Flow.Publisher<AgentStreamEvent> stream(String topic, Map<String, Object> governanceMetadata) {
    return stream(topic, LEGACY_DEFAULT_USER_ID, governanceMetadata);
  }

  public Flow.Publisher<AgentStreamEvent> stream(
      String topic,
      long userId,
      Map<String, Object> governanceMetadata
  ) {
    return agentLoopRunner.stream(toAgentRequest(LearningTopic.of(topic), userId, governanceMetadata));
  }

  private AgentRequest toAgentRequest(
      LearningTopic topic,
      long userId,
      Map<String, Object> governanceMetadata
  ) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    ResolvedSystemPromptSnapshot promptSnapshot = systemPromptResolver.resolve(
        ManagedSystemPromptDefinitions.TOPIC_EXPLANATION, userId);
    String prompt = "Explain the learning topic for an algorithm student: " + topic.title();
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AgentRuntimeMetadataKeys.TITLE, topic.title());
    metadata.put(AgentRuntimeMetadataKeys.TOPIC_TITLE, topic.title());
    metadata.put(AgentRuntimeMetadataKeys.ADAPTER, MentorApplicationConstants.TOPIC_EXPLANATION);
    metadata.putAll(SystemPromptMetadataKeys.from(promptSnapshot));
    metadata.putAll(governanceMetadata == null ? Map.of() : governanceMetadata);
    return new AgentRequest(
        null,
        null,
        List.of(
            LlmMessage.system(promptSnapshot.requireSection(SystemPromptSectionKeys.TOPIC_EXPLANATION_BASE).text()),
            LlmMessage.user(prompt)),
        metadata);
  }
}
