package org.congcong.algomentor.mentor.application.profile.ai;

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
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;

/** 使用受管理 Prompt 和严格 JSON Schema 的学习者自述画像子 Agent Definition。 */
public final class DeclaredProfileUpdateAgentDefinition implements AgentDefinition<DeclaredProfileUpdateAgentInput> {

  public static final int MAX_STEPS = 1;
  public static final AgentKey<DeclaredProfileUpdateAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.LEARNER_DECLARED_PROFILE_UPDATE.code(), DeclaredProfileUpdateAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final DeclaredProfileUpdatePromptBuilder promptBuilder;

  public DeclaredProfileUpdateAgentDefinition(DeclaredProfileUpdatePromptBuilder promptBuilder) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Declared profile prompt builder must not be null");
  }

  @Override
  public AgentKey<DeclaredProfileUpdateAgentInput> key() {
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
  public AgentPreparedRequest prepare(DeclaredProfileUpdateAgentInput input, AgentInvocationContext context) {
    DeclaredProfileUpdateAgentInput candidate = Objects.requireNonNull(input, "Declared profile input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Declared profile input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Declared profile input idempotency key does not match the invocation");
    }
    ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(candidate.userId());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AgentRuntimeMetadataKeys.TITLE, LearnerDeclaredProfileToolContracts.AGENT_TITLE);
    metadata.put(LearnerDeclaredProfileToolContracts.METADATA_DIMENSION_COUNT, candidate.candidates().size());
    metadata.put(AgentRuntimeMetadataKeys.SCHEMA_VERSION, LearnerDeclaredProfileToolContracts.SCHEMA_VERSION);
    metadata.putAll(SystemPromptMetadataKeys.from(snapshot));
    return new AgentPreparedRequest(
        promptBuilder.build(candidate.candidates().stream().map(item -> new DeclaredProfileUpdatePromptBuilder.Candidate(
            item.dimension(), item.statement(), item.intent(), item.activeClaims().stream()
                .map(claim -> new DeclaredProfileUpdatePromptBuilder.ActiveClaim(
                    claim.revisionId(), claim.claimText()))
                .toList())).toList(), snapshot),
        Map.copyOf(metadata),
        executionOptions(candidate),
        null,
        false,
        null,
        candidate.retryOfRunId());
  }

  private AgentExecutionOptions executionOptions(DeclaredProfileUpdateAgentInput candidate) {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            DeclaredProfileUpdateJsonSchema.SCHEMA_NAME,
            DeclaredProfileUpdateJsonSchema.schema(candidate.candidates()),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            DeclaredProfileUpdateJsonSchema.SCHEMA_NAME,
            LearnerDeclaredProfileToolContracts.SCHEMA_VERSION,
            true));
  }
}
