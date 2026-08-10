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
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaseSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionModelViewProjector;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionToolContracts;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/** 保留既有草案 Prompt 上下文顺序的学习计划修订 Definition。 */
public final class LearningPlanDraftRevisionAgentDefinition
    implements AgentDefinition<LearningPlanDraftRevisionAgentInput> {

  /** 修订沿用草案题库查询 Prompt，保留其既有工具循环步数。 */
  public static final int MAX_STEPS = 24;
  public static final AgentKey<LearningPlanDraftRevisionAgentInput> KEY = new AgentKey<>(
      AiBusinessScenario.LEARNING_PLAN_REVISION.code(), LearningPlanDraftRevisionAgentInput.class);
  private static final AgentLoopPolicy LOOP_POLICY = new AgentLoopPolicy(MAX_STEPS);
  private static final AgentOutputContract OUTPUT_CONTRACT = AgentOutputContract.defaults();

  private final LearningPlanProposalPromptBuilder promptBuilder;
  private final ObjectMapper objectMapper;
  private final LearningPlanRevisionModelViewProjector modelViewProjector;

  public LearningPlanDraftRevisionAgentDefinition(
      LearningPlanProposalPromptBuilder promptBuilder,
      ObjectMapper objectMapper
  ) {
    this(promptBuilder, objectMapper, new LearningPlanRevisionModelViewProjector(objectMapper));
  }

  public LearningPlanDraftRevisionAgentDefinition(
      LearningPlanProposalPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      LearningPlanRevisionModelViewProjector modelViewProjector
  ) {
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "Learning plan proposal prompt builder must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper must not be null");
    this.modelViewProjector = Objects.requireNonNull(modelViewProjector, "Model view projector must not be null");
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
    return LearningPlanRevisionToolContracts.AGENT_TOOLS;
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
    ResolvedSystemPromptSnapshot snapshot = promptBuilder.snapshot(
        ManagedSystemPromptDefinitions.LEARNING_PLAN_REVISION, candidate.userId());
    Map<String, Object> metadata = new LinkedHashMap<>(SystemPromptMetadataKeys.from(snapshot));
    metadata.put(AgentRuntimeMetadataKeys.TITLE, LearningPlanStreamConstants.DRAFT_REVISION_AGENT_TITLE);
    metadata.put(AgentRuntimeMetadataKeys.USER_ID, candidate.userId());
    metadata.put(LearningPlanRevisionToolContracts.METADATA_SCENARIO, LearningPlanRevisionToolContracts.SCENARIO);
    metadata.put(LearningPlanRevisionToolContracts.METADATA_REVISION_ID, candidate.revisionId());
    metadata.put(
        LearningPlanDraftMetadataKeys.CONTENT_LOCALE,
        candidate.baseBrief().contentLocale().languageTag());
    metadata.put(LearningPlanPersonalizationMetadataKeys.ENABLED, candidate.personalizationSnapshot().enabled());
    metadata.put(
        LearningPlanPersonalizationMetadataKeys.SOURCE_OUTCOMES,
        candidate.personalizationSnapshot().sourceOutcomes().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().name(),
                entry -> entry.getValue().name(),
                (left, right) -> left,
                LinkedHashMap::new)));
    metadata.put(LearningPlanPersonalizationMetadataKeys.ENTRY_COUNT, personalizationEntryCount(candidate));
    metadata.put(
        LearningPlanPersonalizationMetadataKeys.TOKEN_ESTIMATE,
        candidate.personalizationSnapshot().tokenEstimate());
    metadata.put(LearningPlanPersonalizationMetadataKeys.TRIMMED, candidate.personalizationSnapshot().trimmed());
    return new AgentPreparedRequest(messages(candidate, snapshot), Map.copyOf(metadata), executionOptions());
  }

  private List<LlmMessage> messages(
      LearningPlanDraftRevisionAgentInput input,
      ResolvedSystemPromptSnapshot snapshot
  ) {
    List<LlmMessage> messages = new ArrayList<>();
    messages.add(ManagedSystemMessageFactory.system(snapshot, SystemPromptSectionKeys.LEARNING_PLAN_REVISION_BASE));
    if (!input.personalizationSnapshot().promptText().isBlank()) {
      messages.add(LlmMessage.system(input.personalizationSnapshot().promptText()));
    }
    messages.add(LlmMessage.assistant("""
        当前学习计划修订模型视图 JSON：
        %s
        """.formatted(toJson(modelViewProjector.project(
        new LearningPlanRevisionBaseSnapshot(input.baseBrief(), input.basePlan()))))));
    messages.add(LlmMessage.user("""
        请基于当前模型视图和用户修订要求完成修订。
        projectionMode=SUMMARY_WITH_TOOLS 时，只查询完成本次修改所需的阶段或题目；不要遍历无关阶段。
        所有变化必须通过 compile_learning_plan_revision 提交。编译返回 NEEDS_REVISION 时按诊断修正 Patch 后重试；
        返回 PASS 后，最终只输出 status=COMPILED 与 Tool 返回的 artifactRef，不要输出完整 Brief 或计划。

        用户修订要求：
        %s
        """.formatted(input.instruction())));
    return List.copyOf(messages);
  }

  private int personalizationEntryCount(LearningPlanDraftRevisionAgentInput input) {
    var context = input.personalizationSnapshot().context();
    return context.declaredFacts().size()
        + context.generalObservations().size()
        + context.weakTags().size()
        + context.strongTags().size()
        + (context.activePlan() == null ? 0 : 1)
        + (context.reviewLoad() == null ? 0 : 1);
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
            LearningPlanStreamConstants.DRAFT_REVISION_SCHEMA_NAME,
            LearningPlanDraftRevisionJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            LearningPlanStreamConstants.DRAFT_REVISION_SCHEMA_NAME,
            LearningPlanStreamConstants.DRAFT_REVISION_SCHEMA_VERSION,
            true));
  }
}
