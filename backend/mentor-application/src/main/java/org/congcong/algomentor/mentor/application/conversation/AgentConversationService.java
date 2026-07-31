package org.congcong.algomentor.mentor.application.conversation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptAssembler;
import org.congcong.algomentor.agent.core.prompt.PromptAssembler;
import org.congcong.algomentor.agent.core.prompt.PromptAssembly;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.runtime.context.AssembledContext;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssemblyPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeChatContext;
import org.congcong.algomentor.mentor.application.practice.PracticeChatAgentDefinition;
import org.congcong.algomentor.mentor.application.practice.PracticeChatAgentInput;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptProfileResolver;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptSectionProvider;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;
import org.congcong.algomentor.mentor.application.practice.PracticeResponseLanguage;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinition;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallContracts;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallPromptSectionProvider;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;

public class AgentConversationService {

  private final AgentConversationRepository conversationRepository;
  private final ContextAssembler contextAssembler;
  private final ContextAssemblyPolicy contextPolicy;
  private final LearningPlanRepository learningPlanRepository;
  private final PracticeChatProblemCatalog practiceProblemCatalog;
  private final PromptAssembler practicePromptAssembler;
  private final LearnerMemoryRecallService learnerMemoryRecallService;
  private final LearnerMemoryRecallPromptSectionProvider learnerMemoryRecallPromptSectionProvider;
  private final ManagedSystemPromptResolver systemPromptResolver;

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler
  ) {
    this(conversationRepository, contextAssembler, ContextAssemblyPolicy.defaultPolicy());
  }

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ContextAssemblyPolicy contextPolicy
  ) {
    this(
        conversationRepository,
        contextAssembler,
        contextPolicy,
        null,
        null,
        defaultPracticePromptAssembler());
  }

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog practiceProblemCatalog
  ) {
    this(
        conversationRepository,
        contextAssembler,
        ContextAssemblyPolicy.defaultPolicy(),
        learningPlanRepository,
        practiceProblemCatalog,
        defaultPracticePromptAssembler());
  }

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ContextAssemblyPolicy contextPolicy,
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog practiceProblemCatalog,
      PromptAssembler practicePromptAssembler
  ) {
    this(
        conversationRepository,
        contextAssembler,
        contextPolicy,
        learningPlanRepository,
        practiceProblemCatalog,
        practicePromptAssembler,
        null,
        defaultLearnerMemoryRecallPromptSectionProvider(),
        ManagedSystemPrompts.defaultResolver());
  }

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ContextAssemblyPolicy contextPolicy,
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog practiceProblemCatalog,
      PromptAssembler practicePromptAssembler,
      LearnerMemoryRecallService learnerMemoryRecallService,
      LearnerMemoryRecallPromptSectionProvider learnerMemoryRecallPromptSectionProvider
  ) {
    this(
        conversationRepository,
        contextAssembler,
        contextPolicy,
        learningPlanRepository,
        practiceProblemCatalog,
        practicePromptAssembler,
        learnerMemoryRecallService,
        learnerMemoryRecallPromptSectionProvider,
        ManagedSystemPrompts.defaultResolver());
  }

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ContextAssemblyPolicy contextPolicy,
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog practiceProblemCatalog,
      PromptAssembler practicePromptAssembler,
      LearnerMemoryRecallService learnerMemoryRecallService,
      LearnerMemoryRecallPromptSectionProvider learnerMemoryRecallPromptSectionProvider,
      ManagedSystemPromptResolver systemPromptResolver
  ) {
    this.conversationRepository = conversationRepository;
    this.contextAssembler = contextAssembler;
    this.contextPolicy = contextPolicy == null ? ContextAssemblyPolicy.defaultPolicy() : contextPolicy;
    this.learningPlanRepository = learningPlanRepository;
    this.practiceProblemCatalog = practiceProblemCatalog;
    this.practicePromptAssembler = practicePromptAssembler == null
        ? defaultPracticePromptAssembler()
        : practicePromptAssembler;
    this.learnerMemoryRecallService = learnerMemoryRecallService;
    this.learnerMemoryRecallPromptSectionProvider = learnerMemoryRecallPromptSectionProvider == null
        ? defaultLearnerMemoryRecallPromptSectionProvider()
        : learnerMemoryRecallPromptSectionProvider;
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  /** 为 Practice Chat 复用 session task，并准备新的 turn、run 与完整受信上下文。 */
  public AgentConversationRun preparePracticeRun(PracticeChatAgentInput input) {
    PracticeChatAgentInput candidate = requirePracticeInput(input);
    AgentConversationCommand command = practiceCommand(candidate);
    PreparedAgentRun draft = conversationRepository.createOrReuseRun(toPracticePreparationRequest(candidate));
    return toConversationRun(draft, command);
  }

  /** 查询 Practice Chat 幂等 replay，避免重复写入 session 消息或再次执行模型。 */
  public Optional<AgentConversationRun> findPracticeRunByIdempotencyKey(PracticeChatAgentInput input) {
    PracticeChatAgentInput candidate = requirePracticeInput(input);
    AgentConversationCommand command = practiceCommand(candidate);
    return conversationRepository.findRunByIdempotencyKey(candidate.idempotencyKey())
        .map(draft -> toConversationRun(draft, command));
  }

  private AgentConversationRun toConversationRun(
      PreparedAgentRun draft,
      AgentConversationCommand command
  ) {
    PracticeChatContextAssembly practiceAssembly = assemblePracticeChatContext(draft, command);
    AssembledContext context = practiceAssembly.context();

    Map<String, Object> metadata = new HashMap<>(draft.metadata());
    metadata.putAll(context.metadata());
    metadata.putAll(command.governanceMetadata());
    metadata.put(AgentRuntimeMetadataKeys.TASK_ID, draft.taskId());
    metadata.put(AgentRuntimeMetadataKeys.TURN_ID, draft.turnId());
    metadata.put(AgentRuntimeMetadataKeys.RUN_DB_ID, draft.runId());
    metadata.put(AgentRuntimeMetadataKeys.AGENT_RUN_ID, draft.runUuid());
    metadata.put(AgentRuntimeMetadataKeys.USER_ID, command.userId());
    metadata.put(AgentRuntimeMetadataKeys.TITLE, "task-" + draft.taskId());

    AgentRequest request = new AgentRequest(
        draft.runUuid(),
        draft.requestId(),
        context.messages(),
        metadata);
    return new AgentConversationRun(
        draft.taskId(), draft.turnId(), draft.runId(), draft.runUuid(), request, draft, practiceAssembly.runResource());
  }

  private PracticeChatAgentInput requirePracticeInput(PracticeChatAgentInput input) {
    if (input == null) {
      throw new IllegalArgumentException("Practice chat input must not be null");
    }
    return input;
  }

  private AgentConversationCommand practiceCommand(PracticeChatAgentInput input) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, input.practiceSessionId());
    metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, input.planId());
    metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, input.phaseIndex());
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, input.problemSlug());
    metadata.put(PracticeChatPromptConstants.METADATA_LOCALE, input.locale());
    metadata.put(PracticeChatPromptConstants.METADATA_COACH_STYLE, input.coachStyle().name());
    metadata.put(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE, input.responseLanguage().name());
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT);
    return new AgentConversationCommand(
        input.agentTaskId(),
        input.userId(),
        input.userMessage(),
        input.idempotencyKey(),
        Map.copyOf(metadata),
        new PracticeChatReference(input.planId(), input.phaseIndex(), input.problemSlug(), input.locale()));
  }

  private AgentRunPreparationRequest toPracticePreparationRequest(PracticeChatAgentInput input) {
    ResolvedSystemPromptSnapshot promptSnapshot = systemPromptResolver.resolve(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT, input.userId());
    return new AgentRunPreparationRequest(
        input.agentTaskId(),
        input.userId(),
        input.userMessage(),
        input.idempotencyKey(),
        promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_BASE_IDENTITY).text(),
        practicePreparationMetadata(input, promptSnapshot),
        practiceUserMessageMetadata(input),
        PracticeChatAgentDefinition.KEY.value(),
        AgentInvocationMode.USER_ENTRY,
        null,
        null,
        null,
        PracticeChatAgentDefinition.MAX_STEPS);
  }

  private Map<String, Object> practicePreparationMetadata(
      PracticeChatAgentInput input,
      ResolvedSystemPromptSnapshot promptSnapshot
  ) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("triggerType", "user_request");
    metadata.putAll(SystemPromptMetadataKeys.from(promptSnapshot));
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, input.practiceSessionId());
    metadata.putAll(practiceReferenceMetadata(new PracticeChatReference(
        input.planId(), input.phaseIndex(), input.problemSlug(), input.locale())));
    metadata.put(AgentRuntimeMetadataKeys.ASSISTANT_MESSAGE_METADATA, practiceAssistantMessageMetadata(input));
    metadata.put(PracticeChatPromptConstants.METADATA_COACH_STYLE, input.coachStyle().name());
    metadata.put(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE, input.responseLanguage().name());
    return Map.copyOf(metadata);
  }

  private Map<String, Object> practiceAssistantMessageMetadata(PracticeChatAgentInput input) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT);
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, input.practiceSessionId());
    metadata.putAll(practiceReferenceMetadata(new PracticeChatReference(
        input.planId(), input.phaseIndex(), input.problemSlug(), input.locale())));
    return Map.copyOf(metadata);
  }

  private Map<String, Object> practiceUserMessageMetadata(PracticeChatAgentInput input) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.putAll(practiceReferenceMetadata(new PracticeChatReference(
        input.planId(), input.phaseIndex(), input.problemSlug(), input.locale())));
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT);
    return Map.copyOf(metadata);
  }

  private Map<String, Object> practiceReferenceMetadata(PracticeChatReference reference) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, reference.planId());
    metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, reference.phaseIndex());
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, reference.problemSlug());
    metadata.put(PracticeChatPromptConstants.METADATA_LOCALE, reference.locale());
    return Map.copyOf(metadata);
  }

  private PracticeChatContextAssembly assemblePracticeChatContext(
      PreparedAgentRun draft,
      AgentConversationCommand command
  ) {
    AgentRunResource recallLease = AgentRunResource.none();
    LearnerMemoryRecallSnapshot learnerMemorySnapshot = null;
    try {
    PracticeChatContext practiceContext = practiceChatContext(command.practiceChat(), command.userId());
    List<AgentMessage> history = conversationRepository.recentMessages(draft.taskId(), contextPolicy.recentTurns() * 2);
    PracticeCoachStyle coachStyle = PracticeCoachStyle.from(
        command.governanceMetadata().get(PracticeChatPromptConstants.METADATA_COACH_STYLE));
    PracticeResponseLanguage responseLanguage = PracticeResponseLanguage.from(
        command.governanceMetadata().get(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE));
    boolean idempotentReplay = Boolean.TRUE.equals(
        draft.metadata().get(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY));
    if (!idempotentReplay && learnerMemoryRecallService != null) {
      LearnerMemoryRecallService.OpenedSnapshot openedSnapshot = learnerMemoryRecallService.openSnapshot(
          command.userId(),
          command.userMessage(),
          command.practiceChat().problemSlug(),
          command.practiceChat().locale());
      learnerMemorySnapshot = openedSnapshot.snapshot();
      recallLease = openedSnapshot.lease();
    }
    ResolvedSystemPromptSnapshot promptSnapshot = systemPromptResolver.resolve(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT, command.userId());
    Map<String, Object> variables = new HashMap<>();
    variables.put(PracticeChatPromptConstants.VARIABLE_CONTEXT, practiceContext);
    variables.put(PracticeChatPromptConstants.VARIABLE_ACTIVE_SUMMARY,
        draft.activeSummary() == null ? "" : draft.activeSummary());
    variables.put(PracticeChatPromptConstants.VARIABLE_HISTORY, history);
    variables.put(PracticeChatPromptConstants.VARIABLE_CURRENT_USER_MESSAGE, command.userMessage());
    variables.put(PracticeChatPromptConstants.VARIABLE_COACH_STYLE, coachStyle);
    variables.put(PracticeChatPromptConstants.VARIABLE_RESPONSE_LANGUAGE, responseLanguage);
    if (learnerMemorySnapshot != null) {
      variables.put(LearnerMemoryRecallContracts.VARIABLE_SNAPSHOT, learnerMemorySnapshot);
    }
    variables.put(PracticeChatPromptConstants.VARIABLE_SYSTEM_PROMPT_SNAPSHOT, promptSnapshot);
    PromptAssembly assembly = practicePromptAssembler.assemble(new PromptAssemblyRequest(
        PracticeChatPromptConstants.SCENARIO,
        PracticeChatPromptConstants.PROFILE_ID,
        contextPolicy.tokenBudget(),
        variables,
        Map.of(
            PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
            PracticeChatPromptConstants.METADATA_PLAN_ID, command.practiceChat().planId(),
            PracticeChatPromptConstants.METADATA_PHASE_INDEX, command.practiceChat().phaseIndex(),
            PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, command.practiceChat().problemSlug(),
            PracticeChatPromptConstants.METADATA_LOCALE, command.practiceChat().locale(),
            PracticeChatPromptConstants.METADATA_COACH_STYLE, coachStyle.name(),
            PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE, responseLanguage.name())));
    Map<String, Object> metadata = new HashMap<>(assembly.metadata());
    metadata.putAll(SystemPromptMetadataKeys.from(promptSnapshot));
    metadata.putAll(learnerMemoryRecallPromptSectionProvider.metadata(
        learnerMemorySnapshot, assembly, promptSnapshot));
    return new PracticeChatContextAssembly(
        new AssembledContext(assembly.canonicalMessages(), Map.copyOf(metadata), assembly.tokenEstimate()), recallLease);
    } catch (RuntimeException failure) {
      recallLease.release();
      throw failure;
    }
  }

  private PracticeChatContext practiceChatContext(PracticeChatReference reference, long userId) {
    if (learningPlanRepository == null) {
      throw new LearningPlanException("PRACTICE_CHAT_PLAN_REPOSITORY_UNAVAILABLE", "学习计划仓库不可用。");
    }
    if (practiceProblemCatalog == null) {
      throw new LearningPlanException("PRACTICE_CHAT_PROBLEM_CATALOG_UNAVAILABLE", "题库仓库不可用。");
    }
    LearningPlan plan = learningPlanRepository.findPlanByIdForUser(reference.planId(), userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PLAN_NOT_FOUND", "学习计划不存在。"));
    LearningPlanPhaseDraft phase = plan.plan().phases().stream()
        .filter(candidate -> candidate.phaseIndex() == reference.phaseIndex())
        .findFirst()
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PHASE_NOT_FOUND", "学习计划阶段不存在。"));
    LearningPlanProblemDraft planProblem = phase.problems().stream()
        .filter(candidate -> reference.problemSlug().equals(candidate.slug()))
        .findFirst()
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PROBLEM_NOT_FOUND", "学习计划题目不存在。"));
    PracticeChatProblemDetail problemDetail = practiceProblemCatalog
        .findProblemBySlug(reference.problemSlug(), reference.locale())
        .orElse(null);
    return new PracticeChatContext(plan, phase, planProblem, problemDetail, reference.locale());
  }

  private static PromptAssembler defaultPracticePromptAssembler() {
    return new DefaultPromptAssembler(
        new PracticeChatPromptProfileResolver(),
        List.of(
            new PracticeChatPromptSectionProvider(),
            defaultLearnerMemoryRecallPromptSectionProvider()));
  }

  private static LearnerMemoryRecallPromptSectionProvider defaultLearnerMemoryRecallPromptSectionProvider() {
    return new LearnerMemoryRecallPromptSectionProvider(
        new org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallBootstrapBuilder(
            LearnerMemoryRecallContracts.DEFAULT_BOOTSTRAP_TOKEN_BUDGET),
        ManagedSystemPrompts.defaultResolver());
  }

  private record PracticeChatContextAssembly(AssembledContext context, AgentRunResource runResource) {
  }
}
