package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemorySnapshotToken;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.LearnerReviewFactSnapshotBuilder;
import org.junit.jupiter.api.Test;

class LearnerMemoryCodeReviewUpdateServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final LearnerMemoryCodeReviewStructuredOutputMapper mapper = new LearnerMemoryCodeReviewStructuredOutputMapper();

  @Test
  void mapsAllowedOperationsAndStableBatchIdempotencyKey() throws Exception {
    JsonNode output = objectMapper.readTree("""
        {"operations":[{
          "action":"REVISE","targetRevisionId":41,"claimText":"先明确边界条件",
          "observationType":"CURRENT_STRENGTH","pattern":"SINGLE_REVIEW","reason":"当前提交通过",
          "reviewEvidence":[{"reviewId":702,"role":"RESOLVED"}]
        }]}""");

    List<LearnerMemoryOperation> operations = mapper.map(output, input());

    assertThat(operations).singleElement().isInstanceOf(LearnerMemoryOperation.Revise.class);
    assertThat(((LearnerMemoryOperation.Revise) operations.get(0)).targetRevisionId()).isEqualTo(41L);
    assertThat(LearnerMemoryCodeReviewUpdateService.backgroundIdempotencyKey(7L, batchFacts()))
        .isEqualTo(LearnerMemoryCodeReviewUpdateService.backgroundIdempotencyKey(7L, List.of(
            fact(705L, "five-sum", 1),
            fact(704L, "four-sum", 1),
            fact(703L, "three-sum", 1),
            fact(702L, "two-sum", 2),
            fact(701L, "two-sum", 1))));
  }

  @Test
  void rejectsUnknownFieldsDuplicateTargetsAndOutOfScopeTagsAsOneBatch() throws Exception {
    JsonNode unknownField = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"TAG_ASSESSMENT","dimension":"TAG_MASTERY","tagId":10},
          "claimText":"数组标签判断","pattern":"SINGLE_REVIEW","reason":"单次 Review",
          "reviewEvidence":[{"reviewId":701,"role":"OBSERVED"}],"grade":"STRONG"
        }]}""");
    JsonNode duplicateTarget = objectMapper.readTree("""
        {"operations":[
          {"action":"RETIRE","targetRevisionId":41,"pattern":"SAME_PROBLEM_PERSISTENCE","reason":"已解决",
           "reviewEvidence":[{"reviewId":701,"role":"OBSERVED"},{"reviewId":702,"role":"RESOLVED"}]},
          {"action":"REVISE","targetRevisionId":41,"claimText":"重复目标","pattern":"SAME_PROBLEM_PERSISTENCE","reason":"重复",
           "reviewEvidence":[{"reviewId":701,"role":"OBSERVED"},{"reviewId":702,"role":"PERSISTED"}]}
        ]}""");

    assertThatThrownBy(() -> mapper.map(unknownField, input())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map(duplicateTarget, input())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresExactlyFiveDistinctTriggerReviews() {
    assertThatThrownBy(() -> LearnerMemoryCodeReviewUpdateService.backgroundIdempotencyKey(7L, batchFacts().subList(0, 4)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void evidenceRepairPromptRestatesTrustedEvidenceAndPatternRules() {
    LearnerMemoryCodeReviewPromptBuilder promptBuilder = new LearnerMemoryCodeReviewPromptBuilder();

    String normalPrompt = promptBuilder.build(input(false), promptBuilder.snapshot(7L)).get(1).text();
    String prompt = promptBuilder.build(input(true), promptBuilder.snapshot(7L)).get(1).text();

    assertThat(normalPrompt)
        .contains("同题恢复使用 SAME_PROBLEM_RECOVERY")
        .contains("跨两题及以上恢复使用 CROSS_PROBLEM_RECOVERY")
        .contains("CURRENT_STRENGTH 的跨题通用观察使用 CROSS_PROBLEM_RECURRENCE");
    assertThat(prompt)
        .contains("唯一一次修复调用")
        .contains("SAME_PROBLEM_RECOVERY")
        .contains("CROSS_PROBLEM_RECOVERY")
        .contains("CROSS_PROBLEM_LONGITUDINAL")
        .contains("reviewId=701 problemSlug=two-sum version=1")
        .contains("reviewId=702 problemSlug=two-sum version=2");
  }

  @Test
  void rejectsStrongStabilityLanguageForAnEarlySample() throws Exception {
    JsonNode output = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"TAG_ASSESSMENT","dimension":"TAG_MASTERY","tagId":9},
          "claimText":"数组题表现稳定。","observationType":"CURRENT_STRENGTH",
          "pattern":"SINGLE_REVIEW","reason":"最新提交通过",
          "reviewEvidence":[{"reviewId":702,"role":"RESOLVED"}]
        }]}""");

    assertThatThrownBy(() -> mapper.map(output, input())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNewObservationsThatReferenceHistoryOutsideTheFactSnapshotScope() throws Exception {
    JsonNode output = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"TAG_ASSESSMENT","dimension":"TAG_MASTERY","tagId":9},
          "claimText":"当前已覆盖题目中的表现良好。","observationType":"CURRENT_STRENGTH",
          "pattern":"SINGLE_REVIEW","reason":"历史通过版本",
          "reviewEvidence":[{"reviewId":701,"role":"RESOLVED"}]
        }]}""");

    assertThatThrownBy(() -> mapper.map(output, inputWithUnscopedHistoricalEvidence()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInvalidRecoveredRiskAndTagObservationEvidence() throws Exception {
    JsonNode recoveredWithWrongPattern = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"GENERAL_OBSERVATION","dimension":"REVIEW_AND_GROWTH_PERFORMANCE"},
          "claimText":"曾在边界处理上出错，后续已修正。","observationType":"RECOVERED_CHALLENGE",
          "pattern":"SAME_PROBLEM_PERSISTENCE","reason":"同题失败后通过",
          "reviewEvidence":[{"reviewId":701,"role":"OBSERVED"},{"reviewId":702,"role":"RESOLVED"}]
        }]}""");
    JsonNode riskWithResolvedRole = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"TAG_ASSESSMENT","dimension":"TAG_MASTERY","tagId":9},
          "claimText":"当前仍需关注边界处理。","observationType":"ACTIVE_RISK",
          "pattern":"SINGLE_REVIEW","reason":"最新版本失败",
          "reviewEvidence":[{"reviewId":702,"role":"RESOLVED"}]
        }]}""");
    JsonNode tagWithUnrelatedEvidence = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"TAG_ASSESSMENT","dimension":"TAG_MASTERY","tagId":9},
          "claimText":"当前数组题已通过。","observationType":"CURRENT_STRENGTH",
          "pattern":"SINGLE_REVIEW","reason":"最新版本通过",
          "reviewEvidence":[{"reviewId":702,"role":"RESOLVED"}]
        }]}""");

    assertThatThrownBy(() -> mapper.map(recoveredWithWrongPattern, observationInput(true, 9L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map(riskWithResolvedRole, observationInput(false, 9L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map(tagWithUnrelatedEvidence, observationInput(true, 8L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsCrossProblemRecoveryOnlyWhenEveryProblemHasAFailedThenPassedTrajectory() throws Exception {
    JsonNode valid = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"GENERAL_OBSERVATION","dimension":"REVIEW_AND_GROWTH_PERFORMANCE"},
          "claimText":"两个题目的边界处理错误均已在后续提交中修正。","observationType":"RECOVERED_CHALLENGE",
          "pattern":"CROSS_PROBLEM_RECOVERY","reason":"两题均完成失败后修正",
          "reviewEvidence":[
            {"reviewId":701,"role":"OBSERVED"},{"reviewId":702,"role":"RESOLVED"},
            {"reviewId":703,"role":"OBSERVED"},{"reviewId":704,"role":"RESOLVED"}
          ]
        }]}""");
    JsonNode incomplete = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"GENERAL_OBSERVATION","dimension":"REVIEW_AND_GROWTH_PERFORMANCE"},
          "claimText":"两个题目的边界处理错误均已在后续提交中修正。","observationType":"RECOVERED_CHALLENGE",
          "pattern":"CROSS_PROBLEM_RECOVERY","reason":"第二题缺少修正版本",
          "reviewEvidence":[
            {"reviewId":701,"role":"OBSERVED"},{"reviewId":702,"role":"RESOLVED"},
            {"reviewId":703,"role":"OBSERVED"}
          ]
        }]}""");
    JsonNode reversedRoles = objectMapper.readTree("""
        {"operations":[{
          "action":"ADD","scope":{"kind":"GENERAL_OBSERVATION","dimension":"REVIEW_AND_GROWTH_PERFORMANCE"},
          "claimText":"两个题目的边界处理错误均已在后续提交中修正。","observationType":"RECOVERED_CHALLENGE",
          "pattern":"CROSS_PROBLEM_RECOVERY","reason":"第一题角色与通过状态不一致",
          "reviewEvidence":[
            {"reviewId":701,"role":"RESOLVED"},{"reviewId":702,"role":"OBSERVED"},
            {"reviewId":703,"role":"OBSERVED"},{"reviewId":704,"role":"RESOLVED"}
          ]
        }]}""");

    assertThat(mapper.map(valid, crossProblemRecoveryInput())).singleElement()
        .isInstanceOf(LearnerMemoryOperation.Add.class);
    assertThatThrownBy(() -> mapper.map(incomplete, crossProblemRecoveryInput()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map(reversedRoles, crossProblemRecoveryInput()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keepsPromptBoundedWhilePreservingWholeHistoryAggregates() {
    LearnerMemoryCodeReviewPromptBuilder promptBuilder = new LearnerMemoryCodeReviewPromptBuilder();

    String fortyProblems = promptBuilder.build(promptInput(40), promptBuilder.snapshot(7L)).get(1).text();
    String eightyProblems = promptBuilder.build(promptInput(80), promptBuilder.snapshot(7L)).get(1).text();
    String repairPrompt = promptBuilder.build(promptInput(80, true), promptBuilder.snapshot(7L)).get(1).text();

    assertThat(fortyProblems)
        .contains("coverage reviewCount=40 distinctProblemCount=40")
        .contains("problemTrajectories total=40 rendered=10 omitted=30")
        .contains("tagFacts total=40 rendered=20 omitted=20")
        .contains("findingSummary=")
        .doesNotContain("\nfindings=")
        .doesNotContain("\nsuggestions=");
    assertThat(eightyProblems.length() - fortyProblems.length()).isLessThan(500);
    assertThat(repairPrompt)
        .contains("本次修复可用的有界 Review 事实")
        .contains("reviewId=1079")
        .doesNotContain("reviewId=1000");
  }

  private LearnerMemoryCodeReviewUpdateAgentInput input() {
    return input(false);
  }

  private LearnerMemoryCodeReviewUpdateAgentInput input(boolean evidenceRepair) {
    LearnerMemoryClaimScope generalScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH,
        null);
    LearnerMemoryClaimScope tagScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
        9L);
    List<CodeReviewVerification> reviews = List.of(
        new CodeReviewVerification(701L, "two-sum", 1, List.of(9L), Instant.EPOCH),
        new CodeReviewVerification(702L, "two-sum", 2, List.of(9L), Instant.EPOCH.plusSeconds(1)));
    return new LearnerMemoryCodeReviewUpdateAgentInput(
        7L,
        List.of(fact(702L, "two-sum", 2)),
        reviews,
        reviews,
        List.of(new LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim(
            41L,
            generalScope,
            "旧判断",
            List.of(new LearnerMemoryCodeReviewUpdateAgentInput.ReviewEvidence(
                701L, LearnerMemoryEvidenceContract.ReviewRole.OBSERVED)))),
        List.of(
            new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(generalScope, 1, 10),
            new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(tagScope, 0, 5)),
        new LearnerMemorySnapshotToken("0".repeat(64)),
        1,
        LearnerMemoryClaimContract.CapacityState.NORMAL,
        "batch",
        null,
        evidenceRepair);
  }

  private LearnerMemoryCodeReviewUpdateAgentInput observationInput(boolean latestPassed, long evidenceTagId) {
    LearnerMemoryClaimScope growthScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.REVIEW_AND_GROWTH_PERFORMANCE,
        null);
    LearnerMemoryClaimScope implementationScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.IMPLEMENTATION_AND_ERROR_PATTERN,
        null);
    LearnerMemoryClaimScope tagScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
        9L);
    List<LearnerMemoryCodeReviewFact> facts = List.of(
        fact(701L, "two-sum", 1, false, evidenceTagId, Instant.EPOCH),
        fact(702L, "two-sum", 2, latestPassed, evidenceTagId, Instant.EPOCH.plusSeconds(1)));
    List<CodeReviewVerification> reviews = List.of(
        new CodeReviewVerification(701L, "two-sum", 1, false, List.of(evidenceTagId), Instant.EPOCH),
        new CodeReviewVerification(702L, "two-sum", 2, latestPassed, List.of(evidenceTagId), Instant.EPOCH.plusSeconds(1)));
    return new LearnerMemoryCodeReviewUpdateAgentInput(
        7L,
        List.of(facts.get(1)),
        new LearnerReviewFactSnapshotBuilder().build(facts),
        reviews,
        reviews,
        List.of(),
        List.of(
            new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(growthScope, 0, 10),
            new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(implementationScope, 0, 10),
            new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(tagScope, 0, 5)),
        new LearnerMemorySnapshotToken("0".repeat(64)),
        0,
        LearnerMemoryClaimContract.CapacityState.NORMAL,
        "observation-batch",
        null,
        false);
  }

  private LearnerMemoryCodeReviewUpdateAgentInput crossProblemRecoveryInput() {
    LearnerMemoryClaimScope growthScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.REVIEW_AND_GROWTH_PERFORMANCE,
        null);
    List<LearnerMemoryCodeReviewFact> facts = List.of(
        fact(701L, "two-sum", 1, false, 9L, Instant.EPOCH),
        fact(702L, "two-sum", 2, true, 9L, Instant.EPOCH.plusSeconds(1)),
        fact(703L, "three-sum", 1, false, 9L, Instant.EPOCH.plusSeconds(2)),
        fact(704L, "three-sum", 2, true, 9L, Instant.EPOCH.plusSeconds(3)));
    List<CodeReviewVerification> reviews = List.of(
        new CodeReviewVerification(701L, "two-sum", 1, false, List.of(9L), Instant.EPOCH),
        new CodeReviewVerification(702L, "two-sum", 2, true, List.of(9L), Instant.EPOCH.plusSeconds(1)),
        new CodeReviewVerification(703L, "three-sum", 1, false, List.of(9L), Instant.EPOCH.plusSeconds(2)),
        new CodeReviewVerification(704L, "three-sum", 2, true, List.of(9L), Instant.EPOCH.plusSeconds(3)));
    return new LearnerMemoryCodeReviewUpdateAgentInput(
        7L,
        List.of(facts.get(1), facts.get(3)),
        new LearnerReviewFactSnapshotBuilder().build(facts),
        reviews,
        reviews,
        List.of(),
        List.of(new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(growthScope, 0, 10)),
        new LearnerMemorySnapshotToken("0".repeat(64)),
        0,
        LearnerMemoryClaimContract.CapacityState.NORMAL,
        "cross-problem-recovery",
        null,
        false);
  }

  private LearnerMemoryCodeReviewUpdateAgentInput inputWithUnscopedHistoricalEvidence() {
    LearnerMemoryClaimScope tagScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
        9L);
    LearnerMemoryCodeReviewFact currentFact = fact(702L, "two-sum", 2, true, 9L, Instant.EPOCH.plusSeconds(1));
    CodeReviewVerification historicalReview = new CodeReviewVerification(
        701L, "three-sum", 1, true, List.of(9L), Instant.EPOCH);
    CodeReviewVerification currentReview = new CodeReviewVerification(
        702L, "two-sum", 2, true, List.of(9L), Instant.EPOCH.plusSeconds(1));
    return new LearnerMemoryCodeReviewUpdateAgentInput(
        7L,
        List.of(currentFact),
        new LearnerReviewFactSnapshotBuilder().build(List.of(currentFact)),
        List.of(currentReview),
        List.of(historicalReview, currentReview),
        List.of(),
        List.of(new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(tagScope, 0, 5)),
        new LearnerMemorySnapshotToken("0".repeat(64)),
        0,
        LearnerMemoryClaimContract.CapacityState.NORMAL,
        "unscoped-history",
        null,
        false);
  }

  private LearnerMemoryCodeReviewUpdateAgentInput promptInput(int problemCount) {
    return promptInput(problemCount, false);
  }

  private LearnerMemoryCodeReviewUpdateAgentInput promptInput(int problemCount, boolean evidenceRepair) {
    List<LearnerMemoryCodeReviewFact> facts = new ArrayList<>();
    List<CodeReviewVerification> reviews = new ArrayList<>();
    Instant start = Instant.parse("2026-08-01T00:00:00Z");
    for (int index = 0; index < problemCount; index++) {
      long reviewId = 1_000L + index;
      long tagId = 100L + index;
      Instant createdAt = start.plusSeconds(index);
      facts.add(fact(reviewId, "problem-" + index, 1, true, tagId, createdAt));
      reviews.add(new CodeReviewVerification(reviewId, "problem-" + index, 1, true, List.of(tagId), createdAt));
    }
    LearnerMemoryClaimScope scope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH,
        null);
    return new LearnerMemoryCodeReviewUpdateAgentInput(
        7L,
        List.of(facts.get(facts.size() - 1)),
        new LearnerReviewFactSnapshotBuilder().build(facts),
        reviews,
        reviews,
        List.of(),
        List.of(new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(scope, 0, 10)),
        new LearnerMemorySnapshotToken("0".repeat(64)),
        0,
        LearnerMemoryClaimContract.CapacityState.NORMAL,
        "prompt-batch-" + problemCount,
        null,
        evidenceRepair);
  }

  private List<LearnerMemoryCodeReviewFact> batchFacts() {
    return List.of(
        fact(701L, "two-sum", 1),
        fact(702L, "two-sum", 2),
        fact(703L, "three-sum", 1),
        fact(704L, "four-sum", 1),
        fact(705L, "five-sum", 1));
  }

  private LearnerMemoryCodeReviewFact fact(long reviewId, String slug, int version) {
    return fact(reviewId, slug, version, true, 9L, Instant.EPOCH);
  }

  private LearnerMemoryCodeReviewFact fact(
      long reviewId,
      String slug,
      int version,
      boolean passed,
      long tagId,
      Instant createdAt
  ) {
    return new LearnerMemoryCodeReviewFact(
        reviewId, slug, version, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
        BigDecimal.ONE, BigDecimal.ONE, passed, List.of("boundary"), List.of("test"), List.of(tagId), createdAt);
  }
}
