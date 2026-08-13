package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentStructuredOutputOptions;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentLoopPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentOutputContract;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;

/** 使用受管理 Prompt 与严格 JSON Schema 的 Practice Code Review 子 Agent Definition。 */
public final class PracticeCodeReviewAgentDefinition implements AgentDefinition<PracticeCodeReviewAgentInput> {

  public static final int MAX_STEPS = 1;
  public static final AgentKey<PracticeCodeReviewAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.PRACTICE_CODE_REVIEW.code(), PracticeCodeReviewAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final PracticeCodeReviewPromptBuilder promptBuilder;

  public PracticeCodeReviewAgentDefinition(PracticeCodeReviewPromptBuilder promptBuilder) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Practice code review prompt builder must not be null");
  }

  @Override
  public AgentKey<PracticeCodeReviewAgentInput> key() {
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
    return List.of();
  }

  @Override
  public AgentOutputContract outputContract() {
    return OUTPUT_CONTRACT;
  }

  @Override
  public AgentPreparedRequest prepare(PracticeCodeReviewAgentInput input, AgentInvocationContext context) {
    PracticeCodeReviewAgentInput candidate = Objects.requireNonNull(input, "Practice code review input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.context().userId() != invocation.userId()) {
      throw new IllegalArgumentException("Practice code review input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Practice code review input idempotency key does not match the invocation");
    }
    ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(candidate.context().userId());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AgentRuntimeMetadataKeys.TITLE, PracticeCodeReviewConstants.AGENT_TITLE);
    metadata.put(PracticeCodeReviewConstants.METADATA_REVIEW_CANDIDATE, true);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, candidate.context().sessionId());
    metadata.put(AgentRuntimeMetadataKeys.SCHEMA_VERSION, PracticeCodeReviewConstants.SCHEMA_VERSION);
    metadata.putAll(SystemPromptMetadataKeys.from(snapshot));
    return new AgentPreparedRequest(
        promptBuilder.build(candidate.context(), snapshot),
        Map.copyOf(metadata),
        executionOptions());
  }

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            PracticeCodeReviewConstants.SCHEMA_NAME,
            PracticeCodeReviewJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            PracticeCodeReviewConstants.SCHEMA_NAME,
            PracticeCodeReviewConstants.SCHEMA_VERSION,
            true));
  }
}
