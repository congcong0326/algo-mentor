package org.congcong.algomentor.mentor.application.profile.review;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.LearnerReviewFactSnapshot;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.ProblemReviewTrajectory;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.ReviewFactSnapshotAttempt;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.TagReviewFacts;

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
        .append("题目引用规范：claimText 中提及题目时必须使用 problemTitle，不得输出 problemSlug；"
            + "problemSlug 仅用于机器定位和工具参数。\n")
        .append("general claim 必须有跨题或纵向 Review 证据；SINGLE_REVIEW 只允许 TAG_MASTERY。"
            + "同题修正应优先体现成长，不得把已解决问题写成稳定弱点。"
            + "同题多版不等于跨题复现；一个题目的多版不能增加 tag breadth。"
            + "容量为 SOFT_LIMIT 时优先 CONFIRM、REVISE、RETIRE 与去重，不新增判断。\n")
        .append("每个 ADD 和 REVISE 必须带 observationType：CURRENT_STRENGTH 只能引用每题最新通过版本并使用 "
            + "RESOLVED role；RECOVERED_CHALLENGE 必须引用同题失败 OBSERVED 后的通过 RESOLVED；"
            + "同题恢复使用 SAME_PROBLEM_RECOVERY，跨两题及以上恢复使用 CROSS_PROBLEM_RECOVERY；"
            + "CURRENT_STRENGTH 的跨题通用观察使用 CROSS_PROBLEM_RECURRENCE，标签表现使用 TAG_BREADTH；"
            + "ACTIVE_RISK 只能引用当前最新失败版本。\n");
    appendFactSnapshot(output, input.reviewFactSnapshot());
    Map<Long, String> findingSummaries = findingSummaries(input.reviewFactSnapshot());
    output.append("\n当前横向窗口的最新正式 Review：\n");
    for (LearnerMemoryCodeReviewFact fact : input.windowFacts()) {
      output.append("\nreviewId=").append(fact.reviewId())
          .append(" problemTitle=").append(fact.problemTitle())
          .append(" problemSlug=").append(fact.problemSlug())
          .append(" version=").append(fact.versionNo())
          .append(" score=").append(fact.totalScore())
          .append(" passed=").append(fact.passed())
          .append(" tags=").append(fact.affectedTagIds())
          .append(" findingSummary=").append(findingSummaries.getOrDefault(
              fact.reviewId(), "(not retained in the fact snapshot)"))
          .append('\n');
    }
    appendEvidenceRepairInstructions(output, input);
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

  private void appendFactSnapshot(StringBuilder output, LearnerReviewFactSnapshot snapshot) {
    LearnerReviewFactSnapshot.Coverage coverage = snapshot.coverage();
    LearnerReviewFactSnapshot.Overall overall = snapshot.overall();
    output.append("\n服务端全量 Review 事实快照（只读，不得改写数字、状态或时间含义）：\n")
        .append("coverage reviewCount=").append(coverage.reviewCount())
        .append(" distinctProblemCount=").append(coverage.distinctProblemCount())
        .append(" earliestReviewAt=").append(coverage.earliestReviewAt())
        .append(" latestReviewAt=").append(coverage.latestReviewAt())
        .append(" historyDepth=").append(coverage.historyDepth()).append('\n')
        .append("overall passedReviewCount=").append(overall.passedReviewCount())
        .append(" failedReviewCount=").append(overall.failedReviewCount())
        .append(" latestByProblem=").append(overall.latestByProblem().passedCount()).append('/')
        .append(overall.latestByProblem().totalCount())
        .append(" firstAttemptByProblem=").append(overall.firstAttemptByProblem().passedCount()).append('/')
        .append(overall.firstAttemptByProblem().totalCount())
        .append(" functionalFailureCount=").append(overall.functionalFailureCount())
        .append(" recoveredFailureCount=").append(overall.recoveredFailureCount())
        .append(" unresolvedFailureCount=").append(overall.unresolvedFailureCount())
        .append(" latestAverageScore=").append(overall.latestAverageScore()).append('\n');
    List<ProblemReviewTrajectory> trajectories = renderedTrajectories(snapshot);
    output.append("problemTrajectories total=").append(snapshot.problemTrajectories().size())
        .append(" rendered=").append(trajectories.size())
        .append(" omitted=").append(snapshot.problemTrajectories().size() - trajectories.size())
        .append(" (omitted trajectories remain included in coverage, overall, and tag facts):\n");
    for (ProblemReviewTrajectory trajectory : trajectories) {
      output.append("problemTitle=").append(trajectory.problemTitle())
          .append(" problemSlug=").append(trajectory.problemSlug())
          .append(" tags=").append(trajectory.tagIds())
          .append(" attempts=").append(trajectory.attemptCount())
          .append(" passed=").append(trajectory.passedAttemptCount())
          .append(" failed=").append(trajectory.failedAttemptCount())
          .append(" recoveredFailures=").append(trajectory.recoveredFailureCount())
          .append(" unresolvedFailures=").append(trajectory.unresolvedFailureCount())
          .append(" compressedEarlierAttempts=").append(trajectory.compressedEarlierAttemptCount())
          .append(" currentStatus=").append(trajectory.currentStatus()).append('\n');
      for (ReviewFactSnapshotAttempt attempt : trajectory.attempts()) {
        output.append("  reviewId=").append(attempt.reviewId())
            .append(" version=").append(attempt.versionNo())
            .append(" passed=").append(attempt.passed())
            .append(" score=").append(attempt.score())
            .append(" findingSummary=").append(attempt.normalizedFindingSummary()).append('\n');
      }
    }
    List<TagReviewFacts> tags = renderedTagFacts(snapshot, trajectories);
    output.append("tagFacts total=").append(snapshot.tagFacts().size())
        .append(" rendered=").append(tags.size())
        .append(" omitted=").append(snapshot.tagFacts().size() - tags.size()).append(":\n");
    for (TagReviewFacts tag : tags) {
      output.append("tagId=").append(tag.tagId())
          .append(" problemCount=").append(tag.problemCount())
          .append(" reviewCount=").append(tag.reviewCount())
          .append(" latestByProblem=").append(tag.latestByProblem().passedCount()).append('/')
          .append(tag.latestByProblem().totalCount())
          .append(" firstAttemptByProblem=").append(tag.firstAttemptByProblem().passedCount()).append('/')
          .append(tag.firstAttemptByProblem().totalCount())
          .append(" functionalFailureCount=").append(tag.functionalFailureCount())
          .append(" recoveredFailureCount=").append(tag.recoveredFailureCount())
          .append(" unresolvedFailureCount=").append(tag.unresolvedFailureCount()).append('\n');
    }
  }

  private List<ProblemReviewTrajectory> renderedTrajectories(LearnerReviewFactSnapshot snapshot) {
    return snapshot.problemTrajectories().stream()
        .sorted(Comparator.comparingInt(this::trajectoryPriority)
            .thenComparing(trajectory -> trajectory.latestAttempt().createdAt(), Comparator.reverseOrder())
            .thenComparing(ProblemReviewTrajectory::problemSlug))
        .limit(LearnerMemoryCodeReviewConsumerConstants.MAX_SNAPSHOT_TRAJECTORIES)
        .toList();
  }

  private int trajectoryPriority(ProblemReviewTrajectory trajectory) {
    return switch (trajectory.currentStatus()) {
      case ACTIVE_RISK -> 0;
      case RECOVERED, RECOVERED_AFTER_REGRESSION -> 1;
      case PASSED_FIRST_ATTEMPT -> 2;
    };
  }

  private List<TagReviewFacts> renderedTagFacts(
      LearnerReviewFactSnapshot snapshot,
      List<ProblemReviewTrajectory> trajectories
  ) {
    Set<Long> prioritizedTagIds = new LinkedHashSet<>();
    for (ProblemReviewTrajectory trajectory : trajectories) {
      prioritizedTagIds.addAll(trajectory.tagIds());
    }
    return snapshot.tagFacts().stream()
        .sorted(Comparator.comparing((TagReviewFacts tag) -> !prioritizedTagIds.contains(tag.tagId()))
            .thenComparing(TagReviewFacts::unresolvedFailureCount, Comparator.reverseOrder())
            .thenComparing(TagReviewFacts::recoveredFailureCount, Comparator.reverseOrder())
            .thenComparing(TagReviewFacts::reviewCount, Comparator.reverseOrder())
            .thenComparing(TagReviewFacts::tagId))
        .limit(LearnerMemoryCodeReviewConsumerConstants.MAX_SNAPSHOT_TAG_FACTS)
        .toList();
  }

  private Map<Long, String> findingSummaries(LearnerReviewFactSnapshot snapshot) {
    Map<Long, String> summaries = new LinkedHashMap<>();
    for (ProblemReviewTrajectory trajectory : snapshot.problemTrajectories()) {
      for (ReviewFactSnapshotAttempt attempt : trajectory.attempts()) {
        summaries.put(attempt.reviewId(), attempt.normalizedFindingSummary());
      }
    }
    return summaries;
  }

  private void appendEvidenceRepairInstructions(StringBuilder output, LearnerMemoryCodeReviewUpdateAgentInput input) {
    if (!input.evidenceRepair()) {
      return;
    }
    output.append("\n上一次候选 operations 未通过服务端证据校验。这是唯一一次修复调用：重新输出完整 operations，"
            + "删除不能被下列受信 Review 严格支持的操作；不要解释校验过程。\n")
        .append("所有 reviewEvidence 只能引用下列 reviewId。所有 pattern 只能使用 Review 证据：\n")
        .append("- SINGLE_REVIEW：恰好一条 Review，且仅可用于该 Review 含有目标 tag 的 TAG_ASSESSMENT。\n")
        .append("- SAME_PROBLEM_PERSISTENCE：同一 problemSlug 至少两条不同 version。\n")
        .append("- SAME_PROBLEM_RECOVERY：同题至少两版，版本顺序中的角色为 OBSERVED 后 RESOLVED。\n")
        .append("- SAME_PROBLEM_REGRESSION：同题至少三版，版本顺序中的角色为 OBSERVED、RESOLVED、REGRESSED。\n")
        .append("- CROSS_PROBLEM_RECOVERY：至少两个不同 problemSlug，且每题都必须有 OBSERVED 后 RESOLVED 的完整版本轨迹。\n")
        .append("- CROSS_PROBLEM_RECURRENCE：至少两个不同 problemSlug。\n")
        .append("- CROSS_PROBLEM_LONGITUDINAL：至少两个不同 problemSlug，且其中一题至少两版。\n")
        .append("- TAG_BREADTH：至少两个不同 problemSlug，且每条 Review 都含目标 tag。\n")
        .append("若快照 historyDepth=EARLY_SAMPLE，claimText 禁止使用长期、一贯、持续、通常、稳定、快速识别等强措辞；"
            + "请改用“当前已覆盖题目”或“本窗口”。\n")
        .append("本次修复可用的有界 Review 事实：\n");
    Map<String, String> problemTitles = problemTitles(input.reviewFactSnapshot());
    for (CodeReviewVerification review : repairEvidenceReviews(input)) {
      output.append("reviewId=").append(review.reviewId())
          .append(" problemTitle=").append(problemTitles.getOrDefault(review.problemSlug(), review.problemSlug()))
          .append(" problemSlug=").append(review.problemSlug())
          .append(" version=").append(review.versionNo())
          .append(" tags=").append(review.affectedTagIds())
          .append('\n');
    }
  }

  private List<CodeReviewVerification> repairEvidenceReviews(LearnerMemoryCodeReviewUpdateAgentInput input) {
    Set<Long> visibleReviewIds = new LinkedHashSet<>();
    for (ProblemReviewTrajectory trajectory : renderedTrajectories(input.reviewFactSnapshot())) {
      for (ReviewFactSnapshotAttempt attempt : trajectory.attempts()) {
        visibleReviewIds.add(attempt.reviewId());
      }
    }
    input.windowFacts().forEach(fact -> visibleReviewIds.add(fact.reviewId()));
    input.activeClaims().forEach(claim -> claim.existingReviewEvidence()
        .forEach(evidence -> visibleReviewIds.add(evidence.reviewId())));
    return input.evidenceReviews().stream()
        .filter(review -> visibleReviewIds.contains(review.reviewId()))
        .toList();
  }

  private Map<String, String> problemTitles(LearnerReviewFactSnapshot snapshot) {
    Map<String, String> titles = new LinkedHashMap<>();
    snapshot.problemTrajectories().forEach(trajectory ->
        titles.putIfAbsent(trajectory.problemSlug(), trajectory.problemTitle()));
    return titles;
  }

  private String scope(org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope value) {
    return "kind=" + value.kind() + ",dimension=" + value.dimension() + ",tagId=" + value.tagId();
  }

  private String escape(String value) {
    return value.replace("</", "<\\/").trim();
  }
}
