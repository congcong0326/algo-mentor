package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentStructuredOutputOptions;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
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
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;

/** 使用受管理 Prompt 和题库工具的学习计划草案 Definition。 */
public final class LearningPlanDraftAgentDefinition implements AgentDefinition<LearningPlanDraftAgentInput> {

  /** list filters、搜索候选和最终结构化输出预留的最大 Agent step 数。 */
  public static final int MAX_STEPS = 24;
  public static final AgentKey<LearningPlanDraftAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.LEARNING_PLAN_DRAFT.code(), LearningPlanDraftAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final LearningPlanDraftPromptBuilder promptBuilder;

  public LearningPlanDraftAgentDefinition(LearningPlanDraftPromptBuilder promptBuilder) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Learning plan draft prompt builder must not be null");
  }

  @Override
  public AgentKey<LearningPlanDraftAgentInput> key() {
    return KEY;
  }

  @Override
  public AgentLoopPolicy loopPolicy() {
    return LOOP_POLICY;
  }

  @Override
  public List<String> allowedToolNames() {
    return LearningPlanAgentToolNames.PLANNING_TOOLS;
  }

  @Override
  public AgentOutputContract outputContract() {
    return OUTPUT_CONTRACT;
  }

  @Override
  public AgentPreparedRequest prepare(LearningPlanDraftAgentInput input, AgentInvocationContext context) {
    LearningPlanDraftAgentInput candidate = Objects.requireNonNull(input, "Learning plan draft input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Learning plan draft input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Learning plan draft input idempotency key does not match the invocation");
    }
    ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(candidate.userId());
    Map<String, Object> metadata = new LinkedHashMap<>(SystemPromptMetadataKeys.from(snapshot));
    metadata.put(AgentRuntimeMetadataKeys.TITLE, LearningPlanStreamConstants.DRAFT_AGENT_TITLE);
    metadata.put(
        LearningPlanDraftMetadataKeys.CONTENT_LOCALE,
        candidate.command().contentLocale().languageTag());
    return new AgentPreparedRequest(
        promptBuilder.build(candidate.command(), snapshot),
        Map.copyOf(metadata),
        executionOptions());
  }

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            LearningPlanStreamConstants.SCHEMA_NAME,
            LearningPlanDraftJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            LearningPlanStreamConstants.SCHEMA_NAME,
            LearningPlanStreamConstants.SCHEMA_VERSION,
            true));
  }
}
