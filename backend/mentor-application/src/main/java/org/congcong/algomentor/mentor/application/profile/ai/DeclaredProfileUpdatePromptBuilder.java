package org.congcong.algomentor.mentor.application.profile.ai;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/** 构造用户明确长期自述的批量画像判定 Prompt。 */
public final class DeclaredProfileUpdatePromptBuilder {

  private final ManagedSystemPromptResolver systemPromptResolver;

  public DeclaredProfileUpdatePromptBuilder() {
    this(ManagedSystemPrompts.defaultResolver());
  }

  public DeclaredProfileUpdatePromptBuilder(ManagedSystemPromptResolver systemPromptResolver) {
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public List<LlmMessage> build(List<Candidate> candidates) {
    return build(candidates, snapshot(1L));
  }

  public List<LlmMessage> build(List<Candidate> candidates, long userId) {
    return build(candidates, snapshot(userId));
  }

  public List<LlmMessage> build(List<Candidate> candidates, ResolvedSystemPromptSnapshot promptSnapshot) {
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("Declared profile prompt candidates must not be empty");
    }
    return List.of(
        ManagedSystemMessageFactory.system(promptSnapshot, SystemPromptSectionKeys.DECLARED_PROFILE_UPDATE_BASE),
        LlmMessage.user(render(candidates)));
  }

  public ResolvedSystemPromptSnapshot snapshot(long userId) {
    return systemPromptResolver.resolve(ManagedSystemPromptDefinitions.DECLARED_PROFILE_UPDATE, userId);
  }

  private String render(List<Candidate> candidates) {
    StringBuilder builder = new StringBuilder("待判定的用户自述：\n");
    for (Candidate candidate : candidates) {
      builder.append("\n维度：").append(candidate.dimension().name())
          .append("\n意图：").append(candidate.intent().name())
          .append("\n当前正文：\n<current_content>\n")
          .append(escapeBlock(candidate.currentContent()))
          .append("\n</current_content>\n用户明确自述：\n<user_statement>\n")
          .append(escapeBlock(candidate.statement()))
          .append("\n</user_statement>\n");
    }
    return builder.toString();
  }

  private String escapeBlock(String text) {
    if (text == null || text.isBlank()) {
      return "(无)";
    }
    return text.replace("</", "<\\/").trim();
  }

  /** 服务端已校验的单维 Prompt 输入，不包含用户身份、版本或标签。 */
  public record Candidate(
      LearnerProfileDimension dimension,
      String statement,
      DeclaredProfileUpdateIntent intent,
      String currentContent
  ) {
    public Candidate {
      if (dimension == null || statement == null || statement.isBlank() || intent == null) {
        throw new IllegalArgumentException("Invalid declared profile prompt candidate");
      }
      statement = statement.trim();
      currentContent = currentContent == null ? "" : currentContent.trim();
    }
  }
}
