package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.junit.jupiter.api.Test;

class LearningPlanDraftAgentDefinitionTest {

  @Test
  void preparesManagedPromptSchemaAndMinimalProblemTools() {
    LearningPlanDraftAgentDefinition definition = definition();
    LearningPlanDraftAgentInput input = new LearningPlanDraftAgentInput(7L, command(), "draft-1");

    var prepared = definition.prepare(input, context(7L, "draft-1"));

    assertThat(definition.key().value()).isEqualTo(AiBusinessScenario.LEARNING_PLAN_DRAFT.code());
    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(LearningPlanDraftAgentDefinition.MAX_STEPS);
    assertThat(definition.allowedToolNames()).containsExactly(
        LearningPlanAgentToolNames.LIST_PROBLEM_FILTERS,
        LearningPlanAgentToolNames.SEARCH_PROBLEMS,
        ReadToolResultTool.NAME);
    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
    assertThat(prepared.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TITLE, LearningPlanStreamConstants.DRAFT_AGENT_TITLE)
        .containsEntry(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US")
        .containsKey(SystemPromptMetadataKeys.TYPE_CODE);
    assertThat(prepared.executionOptions().structuredOutput().schemaName())
        .isEqualTo(LearningPlanStreamConstants.SCHEMA_NAME);
  }

  @Test
  void rejectsInvocationIdentityMismatch() {
    LearningPlanDraftAgentDefinition definition = definition();
    LearningPlanDraftAgentInput input = new LearningPlanDraftAgentInput(7L, command(), "draft-1");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(8L, "draft-1")))
        .withMessage("Learning plan draft input user does not match the invocation user");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(7L, "other")))
        .withMessage("Learning plan draft input idempotency key does not match the invocation");
  }

  private LearningPlanDraftAgentDefinition definition() {
    return new LearningPlanDraftAgentDefinition(new LearningPlanDraftPromptBuilder(
        new LearningPlanLoadService(), ManagedSystemPrompts.defaultResolver()));
  }

  private LearningPlanDraftCommand command() {
    return new LearningPlanDraftCommand(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array"),
        LearningPlanContentLocale.EN_US);
  }

  private AgentInvocationContext context(long userId, String idempotencyKey) {
    return new AgentInvocationContext(
        userId,
        AgentInvocationMode.USER_ENTRY,
        idempotencyKey,
        null,
        null,
        24,
        true);
  }
}
