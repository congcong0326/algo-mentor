package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemorySnapshotToken;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.junit.jupiter.api.Test;

class LearnerMemoryCodeReviewUpdateServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final LearnerMemoryCodeReviewStructuredOutputMapper mapper = new LearnerMemoryCodeReviewStructuredOutputMapper();

  @Test
  void mapsAllowedOperationsAndStableBatchIdempotencyKey() throws Exception {
    JsonNode output = objectMapper.readTree("""
        {"operations":[{
          "action":"REVISE","targetRevisionId":41,"claimText":"先明确边界条件",
          "pattern":"SAME_PROBLEM_PERSISTENCE","reason":"同题两版持续遗漏边界",
          "reviewEvidence":[{"reviewId":701,"role":"OBSERVED"},{"reviewId":702,"role":"PERSISTED"}]
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

    String prompt = promptBuilder.build(input(true), promptBuilder.snapshot(7L)).get(1).text();

    assertThat(prompt)
        .contains("唯一一次修复调用")
        .contains("SAME_PROBLEM_RECOVERY")
        .contains("CROSS_PROBLEM_LONGITUDINAL")
        .contains("reviewId=701 problemSlug=two-sum version=1")
        .contains("reviewId=702 problemSlug=two-sum version=2");
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

  private List<LearnerMemoryCodeReviewFact> batchFacts() {
    return List.of(
        fact(701L, "two-sum", 1),
        fact(702L, "two-sum", 2),
        fact(703L, "three-sum", 1),
        fact(704L, "four-sum", 1),
        fact(705L, "five-sum", 1));
  }

  private LearnerMemoryCodeReviewFact fact(long reviewId, String slug, int version) {
    return new LearnerMemoryCodeReviewFact(
        reviewId, slug, version, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
        BigDecimal.ONE, BigDecimal.ONE, false, List.of("boundary"), List.of("test"), List.of(9L), Instant.EPOCH);
  }
}
