package org.congcong.algomentor.mentor.application.profile.review;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/** 构造基于轻量 Review 事实的批量观察 Prompt。 */
public final class CodeReviewProfilePromptBuilder {

  private final ManagedSystemPromptResolver systemPromptResolver;

  public CodeReviewProfilePromptBuilder() {
    this(ManagedSystemPrompts.defaultResolver());
  }

  public CodeReviewProfilePromptBuilder(ManagedSystemPromptResolver systemPromptResolver) {
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public List<LlmMessage> build(List<CodeReviewProfileFact> facts, List<Candidate> candidates) {
    return build(facts, candidates, snapshot(1L));
  }

  public List<LlmMessage> build(List<CodeReviewProfileFact> facts, List<Candidate> candidates, long userId) {
    return build(facts, candidates, snapshot(userId));
  }

  public List<LlmMessage> build(
      List<CodeReviewProfileFact> facts,
      List<Candidate> candidates,
      ResolvedSystemPromptSnapshot promptSnapshot
  ) {
    if (facts == null || facts.isEmpty() || candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("Code review profile prompt requires facts and candidates");
    }
    return List.of(
        ManagedSystemMessageFactory.system(promptSnapshot, SystemPromptSectionKeys.CODE_REVIEW_PROFILE_UPDATE_BASE),
        LlmMessage.user(render(facts, candidates)));
  }

  public ResolvedSystemPromptSnapshot snapshot(long userId) {
    return systemPromptResolver.resolve(ManagedSystemPromptDefinitions.CODE_REVIEW_PROFILE_UPDATE, userId);
  }

  private String render(List<CodeReviewProfileFact> facts, List<Candidate> candidates) {
    StringBuilder output = new StringBuilder("近期不同题目的正式 Review 事实：\n");
    for (CodeReviewProfileFact fact : facts) {
      output.append("\n题目：").append(fact.problemSlug())
          .append("\n版本：").append(fact.versionNo())
          .append("\n评分：total=").append(fact.totalScore())
          .append(", correctness=").append(fact.correctnessScore())
          .append(", complexity=").append(fact.complexityScore())
          .append(", edgeCases=").append(fact.edgeCaseScore())
          .append(", codeQuality=").append(fact.codeQualityScore())
          .append(", problemFit=").append(fact.problemFitScore())
          .append("\n通过判定：").append(fact.passed())
          .append("\n扣分原因：").append(fact.deductionReasons())
          .append("\n改进建议：").append(fact.improvementSuggestions())
          .append("\n受信标签 ID：").append(fact.affectedTagIds()).append('\n');
    }
    output.append("\n允许更新的画像候选：\n");
    for (Candidate candidate : candidates) {
      output.append("\nkind=").append(candidate.snapshot().identity().entryKind())
          .append(" dimension=").append(candidate.snapshot().identity().dimension())
          .append(" tagId=").append(candidate.snapshot().identity().tagId())
          .append("\n当前正文：").append(escape(candidate.currentContent())).append('\n');
    }
    return output.toString();
  }

  private String escape(String value) {
    return value == null || value.isBlank() ? "(无)" : value.replace("</", "<\\/").trim();
  }

  /** 服务端生成的白名单候选，模型不得自行扩大。 */
  public record Candidate(LearnerProfileSnapshot snapshot, String currentContent) {
    public Candidate {
      if (snapshot == null) {
        throw new IllegalArgumentException("Code review profile candidate snapshot is required");
      }
      currentContent = currentContent == null ? "" : currentContent.trim();
    }
  }
}
