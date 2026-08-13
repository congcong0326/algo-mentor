package org.congcong.algomentor.mentor.application.profile.review;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentStructuredOutputOptions;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
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
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;

/** 使用 BATCH 受管理 Prompt 和严格 JSON Schema 的 Code Review 画像后台 Agent。 */
public final class LearnerMemoryCodeReviewUpdateAgentDefinition implements AgentDefinition<LearnerMemoryCodeReviewUpdateAgentInput> {

  public static final int MAX_STEPS = 4;
  public static final AgentKey<LearnerMemoryCodeReviewUpdateAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE.code(), LearnerMemoryCodeReviewUpdateAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final LearnerMemoryCodeReviewPromptBuilder promptBuilder;
  private final LearnerMemoryRunScopeRegistry scopeRegistry;

  public LearnerMemoryCodeReviewUpdateAgentDefinition(
      LearnerMemoryCodeReviewPromptBuilder promptBuilder,
      LearnerMemoryRunScopeRegistry scopeRegistry
  ) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Code review profile prompt builder must not be null");
    this.scopeRegistry = Objects.requireNonNull(scopeRegistry, "Code review memory scope registry must not be null");
  }

  @Override
  public AgentKey<LearnerMemoryCodeReviewUpdateAgentInput> key() {
    return KEY;
  }

  @Override
  public AgentExecutionGroup executionGroup() {
    return AgentExecutionGroup.LEARNER_PROFILE_BACKGROUND;
  }

  @Override
  public AgentLoopPolicy loopPolicy() {
    return LOOP_POLICY;
  }

  @Override
  public List<String> allowedToolNames() {
    return List.of(
        LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
        LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
        LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS);
  }

  @Override
  public AgentOutputContract outputContract() {
    return OUTPUT_CONTRACT;
  }

  @Override
  public AgentPreparedRequest prepare(LearnerMemoryCodeReviewUpdateAgentInput input, AgentInvocationContext context) {
    LearnerMemoryCodeReviewUpdateAgentInput candidate = Objects.requireNonNull(input,
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
    LearnerMemoryRunScopeRegistry.ScopeLease lease = candidate.openScope(scopeRegistry);
    try {
      ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(candidate.userId());
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put(AgentRuntimeMetadataKeys.TITLE, LearnerMemoryCodeReviewConsumerConstants.AGENT_TITLE);
      metadata.put(LearnerMemoryCodeReviewConsumerConstants.METADATA_WINDOW_PROBLEM_COUNT, candidate.windowFacts().size());
      metadata.put(LearnerMemoryCodeReviewConsumerConstants.METADATA_INITIAL_ACTIVE_CLAIM_COUNT, candidate.activeClaims().size());
      metadata.put(LearnerMemoryCodeReviewConsumerConstants.METADATA_CAPACITY_STATE, candidate.capacityState().name());
      metadata.putAll(scopeRegistry.initialRequestMetadata(lease));
      metadata.put(AgentRuntimeMetadataKeys.SCHEMA_VERSION, LearnerMemoryCodeReviewConsumerConstants.SCHEMA_VERSION);
      metadata.putAll(SystemPromptMetadataKeys.from(snapshot));
      return new AgentPreparedRequest(
          promptBuilder.build(candidate, snapshot),
          Map.copyOf(metadata),
          executionOptions(),
          null,
          false,
          lease,
          candidate.retryOfRunId());
    } catch (RuntimeException exception) {
      lease.release();
      throw exception;
    }
  }

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            LearnerMemoryCodeReviewJsonSchema.SCHEMA_NAME,
            LearnerMemoryCodeReviewJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            LearnerMemoryCodeReviewJsonSchema.SCHEMA_NAME,
            LearnerMemoryCodeReviewConsumerConstants.SCHEMA_VERSION,
            true));
  }
}
