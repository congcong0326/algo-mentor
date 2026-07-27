package org.congcong.algomentor.mentor.application.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.prompt.PromptBudgetPolicy;
import org.congcong.algomentor.agent.core.prompt.PromptCachePolicy;
import org.congcong.algomentor.agent.core.prompt.PromptProfile;
import org.congcong.algomentor.agent.core.prompt.PromptRenderMode;
import org.congcong.algomentor.agent.core.prompt.PromptSection;
import org.congcong.algomentor.agent.core.prompt.PromptSectionProvider;
import org.congcong.algomentor.agent.core.prompt.PromptSensitivity;
import org.congcong.algomentor.agent.core.prompt.PromptSlot;
import org.congcong.algomentor.agent.core.prompt.PromptSourceRef;
import org.congcong.algomentor.agent.core.prompt.PromptTrustLevel;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptSectionFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptResolutionSource;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

public class PracticeChatPromptSectionProvider implements PromptSectionProvider {

  private static final String TEXT = "text";
  private final ManagedSystemPromptResolver systemPromptResolver;

  public PracticeChatPromptSectionProvider() {
    this(ManagedSystemPrompts.defaultResolver());
  }

  public PracticeChatPromptSectionProvider(ManagedSystemPromptResolver systemPromptResolver) {
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  @Override
  public List<PromptSection> sections(PromptAssemblyRequest request, PromptProfile profile) {
    PracticeChatContext context = context(request);
    String currentUserMessage = stringVariable(request, PracticeChatPromptConstants.VARIABLE_CURRENT_USER_MESSAGE);
    PracticeCoachStyle coachStyle = coachStyle(request);
    PracticeResponseLanguage responseLanguage = responseLanguage(request);
    ResolvedSystemPromptSnapshot promptSnapshot = promptSnapshot(request);
    List<PromptSection> sections = new ArrayList<>();

    sections.add(baseInstruction(promptSnapshot));
    sections.add(coachStyle(promptSnapshot, coachStyle));
    sections.add(responseLanguage(promptSnapshot, responseLanguage));
    sections.add(scenarioPolicy(promptSnapshot));
    sections.add(runtimeContext(context));
    activeSummary(request, promptSnapshot).ifPresent(sections::add);
    sections.addAll(history(request));
    sections.add(currentUserMessage(currentUserMessage));
    return List.copyOf(sections);
  }

  private PromptSection baseInstruction(ResolvedSystemPromptSnapshot promptSnapshot) {
    return ManagedSystemPromptSectionFactory.create(
        promptSnapshot,
        SystemPromptSectionKeys.PRACTICE_BASE_IDENTITY,
        PracticeChatPromptConstants.SECTION_BASE_INSTRUCTION,
        "平台与安全基线",
        PromptSlot.STATIC_INSTRUCTION,
        10,
        PromptCachePolicy.CACHEABLE_STATIC,
        PromptBudgetPolicy.FAIL_IF_OVER_BUDGET,
        PromptRenderMode.MARKDOWN,
        Map.of(),
        null);
  }

  private PromptSection coachStyle(ResolvedSystemPromptSnapshot promptSnapshot, PracticeCoachStyle style) {
    String instructionKey = style == PracticeCoachStyle.DIRECT
        ? SystemPromptSectionKeys.PRACTICE_COACH_DIRECT
        : SystemPromptSectionKeys.PRACTICE_COACH_GUIDED;
    String text = promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_COACH_FRAME).text()
        .formatted(style.label(), promptSnapshot.requireSection(instructionKey).text()).strip();
    return ManagedSystemPromptSectionFactory.create(
        promptSnapshot,
        instructionKey,
        PracticeChatPromptConstants.SECTION_COACH_STYLE,
        "教练风格策略",
        PromptSlot.SCENARIO_POLICY,
        20,
        PromptCachePolicy.CACHEABLE_BY_PROFILE,
        PromptBudgetPolicy.FAIL_IF_OVER_BUDGET,
        PromptRenderMode.MARKDOWN,
        Map.of(PracticeChatPromptConstants.METADATA_COACH_STYLE, style.name()),
        text);
  }

  private PromptSection responseLanguage(ResolvedSystemPromptSnapshot promptSnapshot, PracticeResponseLanguage language) {
    String text = promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_RESPONSE_LANGUAGE).text()
        .formatted(language.promptLabel()).strip();
    return ManagedSystemPromptSectionFactory.create(
        promptSnapshot,
        SystemPromptSectionKeys.PRACTICE_RESPONSE_LANGUAGE,
        PracticeChatPromptConstants.SECTION_RESPONSE_LANGUAGE,
        "回复语言策略",
        PromptSlot.SCENARIO_POLICY,
        30,
        PromptCachePolicy.CACHEABLE_BY_PROFILE,
        PromptBudgetPolicy.FAIL_IF_OVER_BUDGET,
        PromptRenderMode.MARKDOWN,
        Map.of(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE, language.name()),
        text);
  }

  private PromptSection scenarioPolicy(ResolvedSystemPromptSnapshot promptSnapshot) {
    String text = String.join("\n\n",
        promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_INTERACTION).text(),
        promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_CODE_REVIEW_TOOL_BOUNDARY).text(),
        promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_PROFILE_TOOL_BOUNDARY).text());
    return ManagedSystemPromptSectionFactory.create(
        promptSnapshot,
        SystemPromptSectionKeys.PRACTICE_INTERACTION,
        PracticeChatPromptConstants.SECTION_SCENARIO_POLICY,
        "题目聊天教学策略",
        PromptSlot.SCENARIO_POLICY,
        40,
        PromptCachePolicy.CACHEABLE_BY_PROFILE,
        PromptBudgetPolicy.FAIL_IF_OVER_BUDGET,
        PromptRenderMode.MARKDOWN,
        Map.of(),
        text);
  }

  private PromptSection runtimeContext(PracticeChatContext context) {
    return new PromptSection(
        PracticeChatPromptConstants.SECTION_RUNTIME_CONTEXT,
        "当前训练上下文",
        PromptSlot.RUNTIME_CONTEXT,
        LlmMessage.Role.SYSTEM,
        PromptTrustLevel.SERVER_VALIDATED,
        PromptSensitivity.USER_CONTENT,
        30,
        true,
        "v1",
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.EXTRACT_IF_NEEDED,
        PromptRenderMode.MARKDOWN,
        new PromptSourceRef(
            "practice-chat-context",
            String.valueOf(context.plan().id()),
            Map.of(
                PracticeChatPromptConstants.METADATA_PHASE_INDEX, context.phase().phaseIndex(),
                PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, context.planProblem().slug())),
        Map.of(TEXT, renderContext(context)));
  }

  private java.util.Optional<PromptSection> activeSummary(
      PromptAssemblyRequest request,
      ResolvedSystemPromptSnapshot promptSnapshot
  ) {
    String summary = stringVariable(request, PracticeChatPromptConstants.VARIABLE_ACTIVE_SUMMARY);
    if (summary.isBlank()) {
      return java.util.Optional.empty();
    }
    String text = promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_ACTIVE_SUMMARY_BOUNDARY).text()
        .formatted(summary).strip();
    return java.util.Optional.of(new PromptSection(
        PracticeChatPromptConstants.SECTION_ACTIVE_SUMMARY,
        "会话摘要",
        PromptSlot.MEMORY_SUMMARY,
        LlmMessage.Role.SYSTEM,
        PromptTrustLevel.MODEL_GENERATED,
        PromptSensitivity.USER_CONTENT,
        50,
        false,
        "v1",
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.DROP_IF_NEEDED,
        PromptRenderMode.MARKDOWN,
        new PromptSourceRef("agent-summary", "active-summary", Map.of("summaryPolicyVersion", "v1")),
        Map.of(TEXT, text)));
  }

  private List<PromptSection> history(PromptAssemblyRequest request) {
    return historyVariable(request).stream()
        .filter(this::isChatHistoryMessage)
        .map(this::historySection)
        .toList();
  }

  private PromptSection historySection(AgentMessage message) {
    LlmMessage.Role role = message.role() == AgentMessage.Role.USER
        ? LlmMessage.Role.USER
        : LlmMessage.Role.ASSISTANT;
    PromptTrustLevel trustLevel = message.role() == AgentMessage.Role.USER
        ? PromptTrustLevel.USER_INPUT
        : PromptTrustLevel.MODEL_GENERATED;
    return new PromptSection(
        PracticeChatPromptConstants.SECTION_HISTORY_PREFIX + "%020d".formatted(message.sequenceNo()),
        message.role() == AgentMessage.Role.USER ? "历史用户消息" : "历史教练回复",
        PromptSlot.HISTORY,
        role,
        trustLevel,
        PromptSensitivity.USER_CONTENT,
        70,
        false,
        "v1",
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.DROP_IF_NEEDED,
        PromptRenderMode.PLAIN_TEXT,
        new PromptSourceRef("agent-message", String.valueOf(message.id()), Map.of("sequenceNo", message.sequenceNo())),
        Map.of(TEXT, message.content()));
  }

  private PromptSection currentUserMessage(String currentUserMessage) {
    return new PromptSection(
        PracticeChatPromptConstants.SECTION_CURRENT_USER_MESSAGE,
        "当前用户消息",
        PromptSlot.CURRENT_USER_MESSAGE,
        LlmMessage.Role.USER,
        PromptTrustLevel.USER_INPUT,
        PromptSensitivity.USER_CONTENT,
        20,
        true,
        "v1",
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.TRUNCATE_IF_NEEDED,
        PromptRenderMode.PLAIN_TEXT,
        new PromptSourceRef("request", "current-user-message", Map.of()),
        Map.of(TEXT, currentUserMessage));
  }

  private String renderContext(PracticeChatContext context) {
    LearningPlanDraftPlan plan = context.plan().plan();
    LearningPlanPhaseDraft phase = context.phase();
    LearningPlanProblemDraft planProblem = context.planProblem();
    PracticeChatProblemDetail detail = context.problemDetail();

    String title = firstNonBlank(detail == null ? null : detail.title(), planProblem.title(), planProblem.titleCn());
    String difficulty = firstNonBlank(detail == null ? null : detail.difficulty(), planProblem.difficulty());
    List<String> tags = detail != null && !detail.tags().isEmpty() ? detail.tags() : planProblem.tags();
    String statement = detail == null || isBlank(detail.contentMarkdown())
        ? "题库暂未提供题面 Markdown。"
        : detail.contentMarkdown().replace("</problem_statement>", "<\\/problem_statement>").strip();

    return """
        学习计划：
        - planId: %s
        - goal: %s
        - level: %s
        - programmingLanguage: %s
        - locale: %s

        阶段：
        - phaseIndex: %s
        - title: %s
        - focus: %s

        题目：
        - slug: %s
        - frontendId: %s
        - title: %s
        - titleCn: %s
        - difficulty: %s
        - tags: %s
        - leetcodeUrl: %s

        题面：
        <problem_statement>
        %s
        </problem_statement>
        """.formatted(
        context.plan().id(),
        blankToPlaceholder(plan.goal()),
        plan.level(),
        blankToPlaceholder(plan.programmingLanguage()),
        context.locale(),
        phase.phaseIndex(),
        blankToPlaceholder(phase.title()),
        blankToPlaceholder(phase.focus()),
        blankToPlaceholder(planProblem.slug()),
        blankToPlaceholder(planProblem.frontendId()),
        blankToPlaceholder(title),
        blankToPlaceholder(planProblem.titleCn()),
        blankToPlaceholder(difficulty),
        tags == null || tags.isEmpty() ? "题库暂未提供标签。" : tags.stream().collect(Collectors.joining(", ")),
        blankToPlaceholder(detail == null ? null : detail.leetcodeUrl()),
        statement).strip();
  }

  private boolean isChatHistoryMessage(AgentMessage message) {
    Object messageType = message.metadata().get(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY);
    return !PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT.equals(messageType);
  }

  private PracticeChatContext context(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_CONTEXT);
    if (value instanceof PracticeChatContext context) {
      return context;
    }
    throw new IllegalArgumentException("Practice chat prompt context is required");
  }

  private ResolvedSystemPromptSnapshot promptSnapshot(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_SYSTEM_PROMPT_SNAPSHOT);
    if (value instanceof ResolvedSystemPromptSnapshot snapshot
        && ManagedSystemPromptDefinitions.PRACTICE_CHAT.typeCode().equals(snapshot.typeCode())) {
      return snapshot;
    }
    // Standalone assembly has no trusted user context and must not enter a user-scoped policy path.
    return ManagedSystemPrompts.defaultRegistry().codeDefaultSnapshot(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT, SystemPromptResolutionSource.CODE_POLICY_UNAVAILABLE);
  }

  private String stringVariable(PromptAssemblyRequest request, String key) {
    Object value = request.variables().get(key);
    return value instanceof String text ? text : "";
  }

  private PracticeCoachStyle coachStyle(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_COACH_STYLE);
    if (value == null) {
      value = request.metadata().get(PracticeChatPromptConstants.METADATA_COACH_STYLE);
    }
    return PracticeCoachStyle.from(value);
  }

  private PracticeResponseLanguage responseLanguage(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_RESPONSE_LANGUAGE);
    if (value == null) {
      value = request.metadata().get(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE);
    }
    return PracticeResponseLanguage.from(value);
  }

  @SuppressWarnings("unchecked")
  private List<AgentMessage> historyVariable(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_HISTORY);
    if (value instanceof List<?> list) {
      return list.stream()
          .filter(AgentMessage.class::isInstance)
          .map(AgentMessage.class::cast)
          .sorted(java.util.Comparator.comparingLong(AgentMessage::sequenceNo))
          .toList();
    }
    if (value instanceof AgentMessage message) {
      return List.of(message);
    }
    return List.of();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value;
      }
    }
    return "";
  }

  private String blankToPlaceholder(Object value) {
    if (value == null) {
      return "未提供";
    }
    String text = Objects.toString(value, "");
    return text.isBlank() ? "未提供" : text;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
