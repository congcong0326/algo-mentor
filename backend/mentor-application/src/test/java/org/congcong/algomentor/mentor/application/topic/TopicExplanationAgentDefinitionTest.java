package org.congcong.algomentor.mentor.application.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.mentor.application.MentorApplicationConstants;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.junit.jupiter.api.Test;

class TopicExplanationAgentDefinitionTest {

  @Test
  void preparesTheManagedTopicPromptAsAOneStepNoToolTextRequest() {
    TopicExplanationAgentDefinition definition = new TopicExplanationAgentDefinition(
        ManagedSystemPrompts.defaultResolver());
    TopicExplanationAgentInput input = new TopicExplanationAgentInput(
        7L,
        LearningTopic.of("binary search"),
        "topic-1",
        Map.of("origin", "topic-page"));

    var prepared = definition.prepare(input, context(7L, "topic-1"));

    assertThat(definition.key().value()).isEqualTo(AiBusinessScenario.TOPIC_EXPLANATION.code());
    assertThat(definition.allowedToolNames()).isEmpty();
    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(1);
    assertThat(prepared.messages()).containsExactly(
        LlmMessage.system(ManagedSystemPrompts.defaultResolver()
            .resolve(ManagedSystemPromptDefinitions.TOPIC_EXPLANATION, 7L)
            .requireSection(SystemPromptSectionKeys.TOPIC_EXPLANATION_BASE)
            .text()),
        LlmMessage.user("Explain the learning topic for an algorithm student: binary search"));
    assertThat(prepared.metadata()).containsEntry("origin", "topic-page")
        .containsEntry(AgentRuntimeMetadataKeys.TITLE, "binary search")
        .containsEntry(AgentRuntimeMetadataKeys.TOPIC_TITLE, "binary search")
        .containsEntry(AgentRuntimeMetadataKeys.ADAPTER, MentorApplicationConstants.TOPIC_EXPLANATION)
        .containsKey(SystemPromptMetadataKeys.TYPE_CODE)
        .doesNotContainKey("systemPrompt");
  }

  @Test
  void rejectsInvocationIdentityThatDoesNotMatchItsTrustedInput() {
    TopicExplanationAgentDefinition definition = new TopicExplanationAgentDefinition(
        ManagedSystemPrompts.defaultResolver());
    TopicExplanationAgentInput input = new TopicExplanationAgentInput(
        7L, LearningTopic.of("binary search"), "topic-1", Map.of());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(8L, "topic-1")))
        .withMessage("Topic explanation input user does not match the invocation user");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(7L, "topic-2")))
        .withMessage("Topic explanation input idempotency key does not match the invocation");
  }

  private static AgentInvocationContext context(long userId, String idempotencyKey) {
    return new AgentInvocationContext(
        userId,
        AgentInvocationMode.USER_ENTRY,
        idempotencyKey,
        null,
        null,
        13,
        false);
  }
}
