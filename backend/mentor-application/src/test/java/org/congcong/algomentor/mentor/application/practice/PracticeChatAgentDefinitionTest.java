package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.junit.jupiter.api.Test;

class PracticeChatAgentDefinitionTest {

  @Test
  void exposesOnlyTheEnabledPracticeToolsWithTheScenarioLoopPolicy() {
    PracticeChatAgentDefinition definition = definition(List.of(
        PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
        LearnerDeclaredProfileToolContracts.TOOL_NAME));

    assertThat(definition.key().value()).isEqualTo(AiBusinessScenario.PRACTICE_CHAT.code());
    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(PracticeChatAgentDefinition.MAX_STEPS);
    assertThat(definition.allowedToolNames()).containsExactly(
        PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
        LearnerDeclaredProfileToolContracts.TOOL_NAME);
    assertThat(definition.outputContract().executionOptions().responseFormat())
        .isInstanceOf(LlmResponseFormat.Text.class);
    assertThat(definition.outputContract().executionOptions().structuredOutput().strategy())
        .isEqualTo(StructuredOutputStrategy.NONE);
  }

  @Test
  void rejectsAnEmptyToolWhitelist() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition(List.of()))
        .withMessage("Practice chat requires at least one enabled Agent tool");
  }

  private PracticeChatAgentDefinition definition(List<String> toolNames) {
    AgentConversationService conversationService = new AgentConversationService(null, new ContextAssembler());
    return new PracticeChatAgentDefinition(
        new PracticeChatRunAdapter(
            conversationService,
            new InMemoryAgentRunLockManager(),
            new LocalAgentRunLockOwnerProvider("test-owner")),
        toolNames);
  }
}
