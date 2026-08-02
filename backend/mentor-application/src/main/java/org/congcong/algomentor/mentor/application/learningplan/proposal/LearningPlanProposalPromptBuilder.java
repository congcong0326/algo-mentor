package org.congcong.algomentor.mentor.application.learningplan.proposal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinition;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/**
 * 学习计划提案 prompt 构造器。
 */
public class LearningPlanProposalPromptBuilder {

  private final ObjectMapper objectMapper;
  private final ManagedSystemPromptResolver systemPromptResolver;

  public LearningPlanProposalPromptBuilder(ObjectMapper objectMapper) {
    this(objectMapper, ManagedSystemPrompts.defaultResolver());
  }

  public LearningPlanProposalPromptBuilder(
      ObjectMapper objectMapper,
      ManagedSystemPromptResolver systemPromptResolver
  ) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public List<LlmMessage> buildDraftRevisionPrompt(
      String instruction,
      LearningPlanDraftCommand command,
      LearningPlanDraftPlan currentPlan
  ) {
    return buildDraftRevisionPrompt(instruction, command, currentPlan, 1L);
  }

  public List<LlmMessage> buildDraftRevisionPrompt(
      String instruction,
      LearningPlanDraftCommand command,
      LearningPlanDraftPlan currentPlan,
      long userId
  ) {
    return List.of(
        ManagedSystemMessageFactory.system(snapshot(ManagedSystemPromptDefinitions.LEARNING_PLAN_REVISION, userId),
            SystemPromptSectionKeys.LEARNING_PLAN_REVISION_BASE),
        LlmMessage.user("""
            用户修订要求：
            %s

            原始生成命令 JSON：
            %s

            outputLocale: %s
            problemToolLocale: %s

            当前学习计划草案 JSON：
            %s
            """.formatted(
            instruction,
            toJson(command),
            command.contentLocale().languageTag(),
            command.contentLocale().languageTag(),
            toJson(currentPlan))));
  }

  public List<LlmMessage> buildExtensionPrompt(
      String instruction,
      LearningPlan currentPlan,
      List<PracticeProgress> progress
  ) {
    return buildExtensionPrompt(instruction, currentPlan, progress, 1L);
  }

  public List<LlmMessage> buildExtensionPrompt(
      String instruction,
      LearningPlan currentPlan,
      List<PracticeProgress> progress,
      long userId
  ) {
    LearningPlanContentLocale contentLocale = currentPlan.plan().contentLocale();
    return List.of(
        ManagedSystemMessageFactory.system(snapshot(ManagedSystemPromptDefinitions.LEARNING_PLAN_EXTENSION, userId),
            SystemPromptSectionKeys.LEARNING_PLAN_EXTENSION_BASE),
        LlmMessage.user("""
            请基于当前学习计划和练习进度生成学习计划扩展草案。

            用户扩展要求：
            %s

            outputLocale: %s
            problemToolLocale: %s

            当前学习计划 JSON：
            %s

            练习进度 JSON：
            %s
            """.formatted(
            instruction,
            contentLocale.languageTag(),
            contentLocale.languageTag(),
            toJson(currentPlan.plan()),
            toJson(progressSummary(progress)))));
  }

  public List<LlmMessage> buildExtensionRevisionPrompt(
      String instruction,
      LearningPlan currentPlan,
      List<PracticeProgress> progress,
      LearningPlanExtensionDraft previousExtension
  ) {
    return buildExtensionRevisionPrompt(instruction, currentPlan, progress, previousExtension, 1L);
  }

  public List<LlmMessage> buildExtensionRevisionPrompt(
      String instruction,
      LearningPlan currentPlan,
      List<PracticeProgress> progress,
      LearningPlanExtensionDraft previousExtension,
      long userId
  ) {
    LearningPlanContentLocale contentLocale = currentPlan.plan().contentLocale();
    return List.of(
        ManagedSystemMessageFactory.system(snapshot(ManagedSystemPromptDefinitions.LEARNING_PLAN_EXTENSION, userId),
            SystemPromptSectionKeys.LEARNING_PLAN_EXTENSION_BASE),
        LlmMessage.assistant("""
            上一版扩展草案 JSON：
            %s
            """.formatted(toJson(previousExtension))),
        LlmMessage.user("""
            请基于当前学习计划、练习进度和上一版扩展草案，生成新的学习计划扩展草案。

            用户修订要求：
            %s

            outputLocale: %s
            problemToolLocale: %s

            当前学习计划 JSON：
            %s

            练习进度 JSON：
            %s
            """.formatted(
            instruction,
            contentLocale.languageTag(),
            contentLocale.languageTag(),
            toJson(currentPlan.plan()),
            toJson(progressSummary(progress)))));
  }

  public ResolvedSystemPromptSnapshot snapshot(ManagedSystemPromptDefinition definition, long userId) {
    return systemPromptResolver.resolve(definition, userId);
  }

  private List<Map<String, Object>> progressSummary(List<PracticeProgress> progress) {
    if (progress == null) {
      return List.of();
    }
    return progress.stream()
        .map(item -> {
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("phaseIndex", item.phaseIndex());
          summary.put("problemSlug", item.problemSlug());
          summary.put("status", item.status().name());
          return summary;
        })
        .toList();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_PROMPT_BUILD_FAILED", "学习计划提案上下文无法序列化。");
    }
  }
}
