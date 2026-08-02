package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

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
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanAgentToolNames;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;

/** 使用受管理 Prompt 和最小题库工具集的学习计划扩展 Definition。 */
public final class LearningPlanExtensionAgentDefinition implements AgentDefinition<LearningPlanExtensionAgentInput> {

  /** list filters、搜索候选和最终结构化扩展输出预留的最大 Agent step 数。 */
  public static final int MAX_STEPS = 24;
  public static final AgentKey<LearningPlanExtensionAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.LEARNING_PLAN_EXTENSION.code(), LearningPlanExtensionAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final LearningPlanProposalPromptBuilder promptBuilder;

  public LearningPlanExtensionAgentDefinition(LearningPlanProposalPromptBuilder promptBuilder) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Learning plan proposal prompt builder must not be null");
  }

  @Override
  public AgentKey<LearningPlanExtensionAgentInput> key() {
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
  public AgentPreparedRequest prepare(LearningPlanExtensionAgentInput input, AgentInvocationContext context) {
    LearningPlanExtensionAgentInput candidate = Objects.requireNonNull(input, "Learning plan extension input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Learning plan extension input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Learning plan extension idempotency key does not match the invocation");
    }
    ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(
        ManagedSystemPromptDefinitions.LEARNING_PLAN_EXTENSION, candidate.userId());
    Map<String, Object> metadata = new LinkedHashMap<>(SystemPromptMetadataKeys.from(snapshot));
    metadata.put(AgentRuntimeMetadataKeys.TITLE, LearningPlanStreamConstants.EXTENSION_AGENT_TITLE);
    metadata.put(
        LearningPlanDraftMetadataKeys.CONTENT_LOCALE,
        candidate.plan().plan().contentLocale().languageTag());
    return new AgentPreparedRequest(
        candidate.previousExtension() == null
            ? promptBuilder.buildExtensionPrompt(
                candidate.instruction(), candidate.plan(), candidate.progress(), candidate.userId())
            : promptBuilder.buildExtensionRevisionPrompt(
                candidate.instruction(), candidate.plan(), candidate.progress(), candidate.previousExtension(), candidate.userId()),
        Map.copyOf(metadata),
        executionOptions());
  }

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            LearningPlanStreamConstants.EXTENSION_SCHEMA_NAME,
            LearningPlanExtensionJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            LearningPlanStreamConstants.EXTENSION_SCHEMA_NAME,
            LearningPlanStreamConstants.EXTENSION_SCHEMA_VERSION,
            true));
  }
}
