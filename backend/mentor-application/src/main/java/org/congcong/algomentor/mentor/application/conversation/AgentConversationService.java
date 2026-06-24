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
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeChatContext;
import org.congcong.algomentor.mentor.application.practice.PracticeChatMessageIntentClassifier;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptProfileResolver;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptSectionProvider;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;

public class AgentConversationService {

  private static final String DEFAULT_MENTOR_SYSTEM_PROMPT =
      "You are an algorithm learning mentor. Explain clearly, ask guiding questions when useful, and prefer Java examples.";

  private final AgentConversationRepository conversationRepository;
  private final ContextAssembler contextAssembler;
  private final ContextAssemblyPolicy contextPolicy;
  private final LearningPlanRepository learningPlanRepository;
  private final PracticeChatProblemCatalog practiceProblemCatalog;
  private final PromptAssembler practicePromptAssembler;

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
    this.conversationRepository = conversationRepository;
    this.contextAssembler = contextAssembler;
    this.contextPolicy = contextPolicy == null ? ContextAssemblyPolicy.defaultPolicy() : contextPolicy;
    this.learningPlanRepository = learningPlanRepository;
    this.practiceProblemCatalog = practiceProblemCatalog;
    this.practicePromptAssembler = practicePromptAssembler == null
        ? defaultPracticePromptAssembler()
        : practicePromptAssembler;
  }

  public AgentConversationRun prepareRun(AgentConversationCommand command) {
    PreparedAgentRun draft = conversationRepository.createOrReuseRun(toPreparationRequest(command));
    return toConversationRun(draft, command);
  }

  public Optional<AgentConversationRun> findRunByIdempotencyKey(String idempotencyKey, String userMessage) {
    return conversationRepository.findRunByIdempotencyKey(idempotencyKey)
        .map(draft -> toConversationRun(draft, new AgentConversationCommand(null, 1L, userMessage, idempotencyKey)));
  }

  public Optional<AgentConversationRun> findRunByIdempotencyKey(AgentConversationCommand command) {
    return conversationRepository.findRunByIdempotencyKey(command.idempotencyKey())
        .map(draft -> toConversationRun(draft, command));
  }

  private AgentConversationRun toConversationRun(PreparedAgentRun draft, AgentConversationCommand command) {
    AssembledContext context = command.practiceChatEnabled()
        ? assemblePracticeChatContext(draft, command)
        : contextAssembler.assemble(
            draft.systemPrompt(),
            draft.activeSummary(),
            conversationRepository.recentMessages(draft.taskId(), contextPolicy.recentTurns() * 2),
            command.userMessage(),
            contextPolicy);

    Map<String, Object> metadata = new HashMap<>(draft.metadata());
    metadata.putAll(context.metadata());
    metadata.put(AgentRuntimeMetadataKeys.TASK_ID, draft.taskId());
    metadata.put(AgentRuntimeMetadataKeys.TURN_ID, draft.turnId());
    metadata.put(AgentRuntimeMetadataKeys.RUN_DB_ID, draft.runId());
    metadata.put(AgentRuntimeMetadataKeys.AGENT_RUN_ID, draft.runUuid());
    metadata.put(AgentRuntimeMetadataKeys.TITLE, "task-" + draft.taskId());
    metadata.putAll(command.governanceMetadata());

    AgentRequest request = new AgentRequest(
        draft.runUuid(),
        draft.requestId(),
        context.messages(),
        metadata);
    return new AgentConversationRun(draft.taskId(), draft.turnId(), draft.runId(), draft.runUuid(), request);
  }

  private AgentRunPreparationRequest toPreparationRequest(AgentConversationCommand command) {
    return new AgentRunPreparationRequest(
        command.taskId(),
        command.userId(),
        command.userMessage(),
        command.idempotencyKey(),
        DEFAULT_MENTOR_SYSTEM_PROMPT,
        preparationMetadata(command));
  }

  private Map<String, Object> preparationMetadata(AgentConversationCommand command) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("triggerType", "user_request");
    if (command.practiceChatEnabled()) {
      PracticeChatReference reference = command.practiceChat();
      metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
      metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, reference.planId());
      metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, reference.phaseIndex());
      metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, reference.problemSlug());
      metadata.put(PracticeChatPromptConstants.METADATA_LOCALE, reference.locale());
    }
    return Map.copyOf(metadata);
  }

  private AssembledContext assemblePracticeChatContext(PreparedAgentRun draft, AgentConversationCommand command) {
    PracticeChatContext practiceContext = practiceChatContext(command.practiceChat(), command.userId());
    List<AgentMessage> history = conversationRepository.recentMessages(draft.taskId(), contextPolicy.recentTurns() * 2);
    PromptAssembly assembly = practicePromptAssembler.assemble(new PromptAssemblyRequest(
        PracticeChatPromptConstants.SCENARIO,
        PracticeChatPromptConstants.PROFILE_ID,
        contextPolicy.tokenBudget(),
        Map.of(
            PracticeChatPromptConstants.VARIABLE_CONTEXT, practiceContext,
            PracticeChatPromptConstants.VARIABLE_ACTIVE_SUMMARY, draft.activeSummary() == null ? "" : draft.activeSummary(),
            PracticeChatPromptConstants.VARIABLE_HISTORY, history,
            PracticeChatPromptConstants.VARIABLE_CURRENT_USER_MESSAGE, command.userMessage()),
        Map.of(
            PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
            PracticeChatPromptConstants.METADATA_PLAN_ID, command.practiceChat().planId(),
            PracticeChatPromptConstants.METADATA_PHASE_INDEX, command.practiceChat().phaseIndex(),
            PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, command.practiceChat().problemSlug(),
            PracticeChatPromptConstants.METADATA_LOCALE, command.practiceChat().locale(),
            PracticeChatPromptConstants.METADATA_MESSAGE_INTENT,
            PracticeChatMessageIntentClassifier.classify(command.userMessage()).name())));
    return new AssembledContext(assembly.canonicalMessages(), assembly.metadata(), assembly.tokenEstimate());
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
        List.of(new PracticeChatPromptSectionProvider()));
  }
}
