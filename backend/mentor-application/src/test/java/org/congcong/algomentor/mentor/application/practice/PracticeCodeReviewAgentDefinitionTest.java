package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewAgentDefinitionTest {

  @Test
  void preparesOneStepNoToolStrictJsonReviewRequest() {
    PracticeCodeReviewAgentDefinition definition = definition();
    PracticeCodeReviewAgentInput input = new PracticeCodeReviewAgentInput(context(), "practice-code-review:50:701");

    var prepared = definition.prepare(input, invocation(7L, "practice-code-review:50:701"));

    assertThat(definition.key().value()).isEqualTo(AiBusinessScenario.PRACTICE_CODE_REVIEW.code());
    assertThat(definition.allowedToolNames()).isEmpty();
    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(PracticeCodeReviewAgentDefinition.MAX_STEPS);
    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
    assertThat(prepared.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TITLE, PracticeCodeReviewConstants.AGENT_TITLE)
        .containsEntry(PracticeCodeReviewConstants.METADATA_REVIEW_CANDIDATE, true)
        .containsEntry(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L)
        .containsEntry(AgentRuntimeMetadataKeys.SCHEMA_VERSION, PracticeCodeReviewConstants.SCHEMA_VERSION)
        .containsKey(SystemPromptMetadataKeys.TYPE_CODE);
    assertThat(prepared.executionOptions().responseFormat())
        .isInstanceOfSatisfying(LlmResponseFormat.JsonSchema.class, schema -> {
          assertThat(schema.name()).isEqualTo(PracticeCodeReviewConstants.SCHEMA_NAME);
          assertThat(schema.strict()).isTrue();
        });
    assertThat(prepared.executionOptions().structuredOutput())
        .extracting(options -> options.strategy(), options -> options.schemaName(), options -> options.schemaVersion(),
            options -> options.required())
        .containsExactly(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            PracticeCodeReviewConstants.SCHEMA_NAME,
            PracticeCodeReviewConstants.SCHEMA_VERSION,
            true);
  }

  @Test
  void rejectsMismatchedTrustedInvocationIdentity() {
    PracticeCodeReviewAgentDefinition definition = definition();
    PracticeCodeReviewAgentInput input = new PracticeCodeReviewAgentInput(context(), "practice-code-review:50:701");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, invocation(8L, "practice-code-review:50:701")))
        .withMessage("Practice code review input user does not match the invocation user");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, invocation(7L, "other")))
        .withMessage("Practice code review input idempotency key does not match the invocation");
  }

  private PracticeCodeReviewAgentDefinition definition() {
    return new PracticeCodeReviewAgentDefinition(new PracticeCodeReviewPromptBuilder());
  }

  private PracticeTurnContext context() {
    return new PracticeTurnContext(
        7L, 12L, 1, "climbing-stairs", 50L, 701L, 702L, 501L,
        "题目事实", "计划事实", "class Solution {}", "class Solution {}", "", "zh-CN");
  }

  private AgentInvocationContext invocation(long userId, String idempotencyKey) {
    return new AgentInvocationContext(
        userId,
        AgentInvocationMode.CHILD,
        idempotencyKey,
        "501",
        1,
        18,
        false);
  }
}
