package org.congcong.algomentor.mentor.application.profile.review;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/** 构造受限 Review 窗口、当前 Claim 与容量事实的 Code Review Claim 更新 Prompt。 */
public final class LearnerMemoryCodeReviewPromptBuilder {

  private final ManagedSystemPromptResolver systemPromptResolver;

  public LearnerMemoryCodeReviewPromptBuilder() {
    this(ManagedSystemPrompts.defaultResolver());
  }

  public LearnerMemoryCodeReviewPromptBuilder(ManagedSystemPromptResolver systemPromptResolver) {
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public List<LlmMessage> build(LearnerMemoryCodeReviewUpdateAgentInput input, ResolvedSystemPromptSnapshot promptSnapshot) {
    if (input == null || promptSnapshot == null) {
      throw new IllegalArgumentException("Code review memory prompt requires trusted input and system prompt");
    }
    return List.of(
        ManagedSystemMessageFactory.system(promptSnapshot, SystemPromptSectionKeys.CODE_REVIEW_PROFILE_UPDATE_BASE),
        LlmMessage.user(render(input)));
  }

  public ResolvedSystemPromptSnapshot snapshot(long userId) {
    return systemPromptResolver.resolve(ManagedSystemPromptDefinitions.CODE_REVIEW_PROFILE_UPDATE, userId);
  }

  private String render(LearnerMemoryCodeReviewUpdateAgentInput input) {
    StringBuilder output = new StringBuilder();
    output.append("只输出 operations。证据不足时返回空数组。不得创建 declared claim 或 "
            + "LEARNING_INTERACTION_AND_INDEPENDENCE claim。\n")
        .append("general claim 必须有跨题或纵向 Review 证据；SINGLE_REVIEW 只允许 TAG_MASTERY。"
            + "同题修正应优先体现成长，不得把已解决问题写成稳定弱点。"
            + "同题多版不等于跨题复现；一个题目的多版不能增加 tag breadth。"
            + "容量为 SOFT_LIMIT 时优先 CONFIRM、REVISE、RETIRE 与去重，不新增判断。\n")
        .append("\n横向窗口的正式 Review：\n");
    for (LearnerMemoryCodeReviewFact fact : input.windowFacts()) {
      output.append("\nreviewId=").append(fact.reviewId())
          .append(" problemSlug=").append(fact.problemSlug())
          .append(" version=").append(fact.versionNo())
          .append(" score=").append(fact.totalScore())
          .append(" passed=").append(fact.passed())
          .append(" tags=").append(fact.affectedTagIds())
          .append("\nfindings=").append(fact.deductionReasons())
          .append("\nsuggestions=").append(fact.improvementSuggestions()).append('\n');
    }
    output.append("\n允许操作的当前 ACTIVE claim：\n");
    if (input.activeClaims().isEmpty()) {
      output.append("(无)\n");
    } else {
      for (LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim claim : input.activeClaims()) {
        output.append("\nrevisionId=").append(claim.revisionId())
            .append(" scope=").append(scope(claim.scope()))
            .append("\nclaimText=").append(escape(claim.claimText()))
            .append("\nexistingReviewEvidence=").append(claim.existingReviewEvidence()).append('\n');
      }
    }
    output.append("\n允许 ADD 的 scope 与容量：\n");
    for (LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity capacity : input.capacities()) {
      output.append(scope(capacity.scope()))
          .append(" active=").append(capacity.activeCount())
          .append('/').append(capacity.limit()).append('\n');
    }
    output.append("用户 ACTIVE 总数=").append(input.activeClaimCount())
        .append(" capacityState=").append(input.capacityState()).append('\n');
    return output.toString();
  }

  private String scope(org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope value) {
    return "kind=" + value.kind() + ",dimension=" + value.dimension() + ",tagId=" + value.tagId();
  }

  private String escape(String value) {
    return value.replace("</", "<\\/").trim();
  }
}
