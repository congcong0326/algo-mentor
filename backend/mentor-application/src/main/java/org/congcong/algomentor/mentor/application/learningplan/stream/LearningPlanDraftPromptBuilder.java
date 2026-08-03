package org.congcong.algomentor.mentor.application.learningplan.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.ArrayList;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
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

  private final ObjectMapper objectMapper;
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
    this(loadService, systemPromptResolver, new ObjectMapper());
  }

  LearningPlanDraftPromptBuilder(
      LearningPlanLoadService loadService,
      ManagedSystemPromptResolver systemPromptResolver,
      ObjectMapper objectMapper
  ) {
    this.objectMapper = objectMapper;
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public List<LlmMessage> build(LearningPlanBrief brief) {
    return build(brief, 1L);
  }

  public List<LlmMessage> build(LearningPlanBrief brief, long userId) {
    return build(brief, snapshot(userId));
  }

  public List<LlmMessage> build(
      LearningPlanBrief brief,
      ResolvedSystemPromptSnapshot promptSnapshot
  ) {
    return build(brief, promptSnapshot, null);
  }

  public List<LlmMessage> build(
      LearningPlanBrief brief,
      ResolvedSystemPromptSnapshot promptSnapshot,
      LearningPlanPersonalizationSnapshot personalizationSnapshot
  ) {
    List<LlmMessage> messages = new ArrayList<>();
    messages.add(systemMessage(promptSnapshot));
    if (personalizationSnapshot != null && !personalizationSnapshot.promptText().isBlank()) {
      messages.add(LlmMessage.system(personalizationSnapshot.promptText()));
    }
    messages.add(LlmMessage.user(toJson(brief)));
    return List.copyOf(messages);
  }

  public LlmMessage systemMessage(ResolvedSystemPromptSnapshot promptSnapshot) {
    return ManagedSystemMessageFactory.system(promptSnapshot, SystemPromptSectionKeys.LEARNING_PLAN_DRAFT_BASE);
  }

  public ResolvedSystemPromptSnapshot snapshot(long userId) {
    return systemPromptResolver.resolve(ManagedSystemPromptDefinitions.LEARNING_PLAN_DRAFT, userId);
  }

  private String toJson(LearningPlanBrief brief) {
    try {
      return objectMapper.writeValueAsString(brief);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_BRIEF_INVALID", "学习计划输入无法序列化。");
    }
  }
}
