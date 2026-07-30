package org.congcong.algomentor.mentor.application.profile.review;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentStructuredOutputOptions;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
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

/** 使用 BATCH 受管理 Prompt 和严格 JSON Schema 的 Code Review 画像后台 Agent。 */
public final class CodeReviewProfileUpdateAgentDefinition implements AgentDefinition<CodeReviewProfileUpdateAgentInput> {

  public static final int MAX_STEPS = 1;
  public static final AgentKey<CodeReviewProfileUpdateAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE.code(), CodeReviewProfileUpdateAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final CodeReviewProfilePromptBuilder promptBuilder;

  public CodeReviewProfileUpdateAgentDefinition(CodeReviewProfilePromptBuilder promptBuilder) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Code review profile prompt builder must not be null");
  }

  @Override
  public AgentKey<CodeReviewProfileUpdateAgentInput> key() {
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
  public AgentPreparedRequest prepare(CodeReviewProfileUpdateAgentInput input, AgentInvocationContext context) {
    CodeReviewProfileUpdateAgentInput candidate = Objects.requireNonNull(input,
        "Code review profile Agent input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context,
        "Code review profile Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Code review profile Agent input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Code review profile Agent input idempotency key does not match the invocation");
    }
    if (invocation.mode() != AgentInvocationMode.BACKGROUND
        || invocation.parentRunId() != null || invocation.parentStepIndex() != null) {
      throw new IllegalArgumentException("Code review profile Agent requires a parentless background invocation");
    }
    ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(candidate.userId());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AgentRuntimeMetadataKeys.TITLE, CodeReviewProfileConsumerConstants.AGENT_TITLE);
    metadata.put(CodeReviewProfileConsumerConstants.METADATA_WINDOW_PROBLEM_COUNT, candidate.facts().size());
    metadata.put(CodeReviewProfileConsumerConstants.METADATA_CANDIDATE_COUNT, candidate.candidates().size());
    metadata.put(AgentRuntimeMetadataKeys.SCHEMA_VERSION, CodeReviewProfileConsumerConstants.SCHEMA_VERSION);
    metadata.putAll(SystemPromptMetadataKeys.from(snapshot));
    return new AgentPreparedRequest(
        promptBuilder.build(candidate.facts(), candidate.candidates(), snapshot),
        Map.copyOf(metadata),
        executionOptions(),
        null,
        false,
        null,
        candidate.retryOfRunId());
  }

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            CodeReviewProfileJsonSchema.SCHEMA_NAME,
            CodeReviewProfileJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            CodeReviewProfileJsonSchema.SCHEMA_NAME,
            CodeReviewProfileConsumerConstants.SCHEMA_VERSION,
            true));
  }
}
