package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanAgentToolNames;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftJsonSchema;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;

/** 保留既有草案 Prompt 上下文顺序的学习计划修订 Definition。 */
public final class LearningPlanDraftRevisionAgentDefinition
    implements AgentDefinition<LearningPlanDraftRevisionAgentInput> {

  /** 修订沿用草案题库查询 Prompt，保留其既有工具循环步数。 */
  public static final int MAX_STEPS = 24;
  public static final AgentKey<LearningPlanDraftRevisionAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.LEARNING_PLAN_REVISION.code(), LearningPlanDraftRevisionAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final LearningPlanDraftPromptBuilder promptBuilder;
  private final ObjectMapper objectMapper;

  public LearningPlanDraftRevisionAgentDefinition(
      LearningPlanDraftPromptBuilder promptBuilder,
      ObjectMapper objectMapper
  ) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Learning plan draft prompt builder must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper must not be null");
  }

  @Override
  public AgentKey<LearningPlanDraftRevisionAgentInput> key() {
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
  public AgentPreparedRequest prepare(LearningPlanDraftRevisionAgentInput input, AgentInvocationContext context) {
    LearningPlanDraftRevisionAgentInput candidate = Objects.requireNonNull(input, "Learning plan draft revision input must not be null");
    AgentInvocationContext invocation = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (candidate.userId() != invocation.userId()) {
      throw new IllegalArgumentException("Learning plan draft revision input user does not match the invocation user");
    }
    if (!candidate.idempotencyKey().equals(invocation.idempotencyKey())) {
      throw new IllegalArgumentException("Learning plan draft revision idempotency key does not match the invocation");
    }
    ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(candidate.userId());
    Map<String, Object> metadata = new LinkedHashMap<>(SystemPromptMetadataKeys.from(snapshot));
    metadata.put(AgentRuntimeMetadataKeys.TITLE, LearningPlanStreamConstants.DRAFT_REVISION_AGENT_TITLE);
    metadata.put(
        LearningPlanDraftMetadataKeys.CONTENT_LOCALE,
        candidate.command().contentLocale().languageTag());
    return new AgentPreparedRequest(messages(candidate, snapshot), Map.copyOf(metadata), executionOptions());
  }

  private List<LlmMessage> messages(
      LearningPlanDraftRevisionAgentInput input,
      ResolvedSystemPromptSnapshot snapshot
  ) {
    List<LlmMessage> messages = new ArrayList<>(promptBuilder.build(input.command(), snapshot));
    messages.add(LlmMessage.assistant("""
        当前学习计划草案 JSON：
        %s
        """.formatted(toJson(input.currentPlan()))));
    messages.add(LlmMessage.user("""
        请基于当前学习计划草案和用户修订要求，输出一份完整的新学习计划草案 JSON。

        用户修订要求：
        %s
        """.formatted(input.instruction())));
    return List.copyOf(messages);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_PLAN_INVALID", "学习计划草案内容无法序列化。");
    }
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
