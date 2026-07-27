package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/**
 * 学习计划草案生成 prompt 构造器。
 */
public class LearningPlanDraftPromptBuilder {

  private final LearningPlanLoadService loadService;
  private final ManagedSystemPromptResolver systemPromptResolver;

  public LearningPlanDraftPromptBuilder() {
    this(new LearningPlanLoadService());
  }

  public LearningPlanDraftPromptBuilder(LearningPlanLoadService loadService) {
    this(loadService, ManagedSystemPrompts.defaultResolver());
  }

  public LearningPlanDraftPromptBuilder(
      LearningPlanLoadService loadService,
      ManagedSystemPromptResolver systemPromptResolver
  ) {
    this.loadService = loadService;
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public List<LlmMessage> build(LearningPlanDraftCommand command) {
    return build(command, 1L);
  }

  public List<LlmMessage> build(LearningPlanDraftCommand command, long userId) {
    return build(command, snapshot(userId));
  }

  public List<LlmMessage> build(
      LearningPlanDraftCommand command,
      ResolvedSystemPromptSnapshot promptSnapshot
  ) {
    return List.of(
        ManagedSystemMessageFactory.system(promptSnapshot, SystemPromptSectionKeys.LEARNING_PLAN_DRAFT_BASE),
        LlmMessage.user(userPrompt(command)));
  }

  public ResolvedSystemPromptSnapshot snapshot(long userId) {
    return systemPromptResolver.resolve(ManagedSystemPromptDefinitions.LEARNING_PLAN_DRAFT, userId);
  }

  private String userPrompt(LearningPlanDraftCommand command) {
    return """
        请为以下用户生成学习计划草案：

        intent: %s
        goal: %s
        durationWeeks: %s
        level: %s
        weeklyHours: %s
        weeklyCapacityPoints: %.1f
        totalCapacityPoints: %.1f
        targetLoadRange: %.1f-%.1f
        loadPolicy: FIT_USER_BUDGET
        programmingLanguage: %s
        difficultyPreference: %s
        interviewOriented: %s
        topicPreferences: %s
        """.formatted(
        command.intent(),
        command.goal(),
        command.durationWeeks(),
        command.level(),
        command.weeklyHours(),
        loadService.weeklyCapacityPoints(command.weeklyHours()),
        totalCapacityPoints(command),
        targetLoadLower(command),
        targetLoadUpper(command),
        command.programmingLanguage(),
        command.difficultyPreference(),
        command.interviewOriented(),
        command.topicPreferences());
  }

  private double totalCapacityPoints(LearningPlanDraftCommand command) {
    int durationWeeks = command.durationWeeks() == null ? 0 : Math.max(0, command.durationWeeks());
    return round1(durationWeeks * loadService.weeklyCapacityPoints(command.weeklyHours()));
  }

  private double targetLoadLower(LearningPlanDraftCommand command) {
    return round1(totalCapacityPoints(command) * 0.75D);
  }

  private double targetLoadUpper(LearningPlanDraftCommand command) {
    return round1(totalCapacityPoints(command) * 1.10D);
  }

  private double round1(double value) {
    return Math.round(value * 10D) / 10D;
  }
}
