package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeReviewTurnCapabilityTest {

  @Test
  void nonCandidateReturnsNotCodeLikeAndDoesNotCallService() {
    CountingReviewService service = new CountingReviewService(PracticeReviewResult.saved(review()));
    CodeReviewTurnCapability capability = new CodeReviewTurnCapability(service);
    PracticeTurnClassification classification = PracticeTurnClassification.notCodeLike(
        "这题怎么想？",
        null,
        Map.of());

    PracticeTurnCapabilityResult result = capability.afterTurn(context(), classification);

    assertThat(result.capabilityName()).isEqualTo(PracticeCodeReviewConstants.METADATA_CODE_REVIEW);
    assertThat(result.status()).isEqualTo(PracticeReviewStatus.NOT_CODE_LIKE);
    assertThat(result.metadata()).containsEntry("codeSubmissionCandidate", false);
    assertThat(service.calls).isZero();
  }

  @Test
  void failureIsolatedAsFailedResult() {
    PracticeCodeReviewService service = new PracticeCodeReviewService(
        context -> {
          throw new RuntimeException("provider timed out with secret details");
        });
    CodeReviewTurnCapability capability = new CodeReviewTurnCapability(service);

    PracticeTurnCapabilityResult result = capability.afterTurn(context(), candidate());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.metadata())
        .containsEntry("failureCode", CodeReviewTurnCapability.FAILURE_CODE_RUNTIME_EXCEPTION)
        .doesNotContainEntry("message", "provider timed out with secret details");
  }

  @Test
  void passesClassificationExtractionToReviewService() {
    CapturingReviewService service = new CapturingReviewService(PracticeReviewResult.saved(review()));
    CodeReviewTurnCapability capability = new CodeReviewTurnCapability(service);
    PracticeTurnClassification classification = PracticeTurnClassification.codeLike(
        "java",
        "class Solution { public int climbStairs(int n) { return 1; } }",
        "```java\nclass Solution { public int climbStairs(int n) { return 1; } }\n```",
        Map.of("languageHint", "java"),
        new PracticeCodeReviewEvidence(PracticeTurnClassifier.EVIDENCE_FENCED_CODE_BLOCK, "markdown"));

    PracticeTurnCapabilityResult result = capability.afterTurn(context(), classification);

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(service.capturedContext.extractedCode())
        .isEqualTo("class Solution { public int climbStairs(int n) { return 1; } }");
    assertThat(service.capturedContext.originalMessage()).startsWith("```java");
  }

  private PracticeTurnContext context() {
    return new PracticeTurnContext(
        7L,
        12L,
        1,
        "climbing-stairs",
        50L,
        701L,
        702L,
        501L,
        "Climbing Stairs",
        "动态规划入门阶段",
        "class Solution { public int climbStairs(int n) { return n; } }",
        "请 review 我的代码",
        "最近在讨论递推定义。",
        "zh-CN");
  }

  private PracticeTurnClassification candidate() {
    return PracticeTurnClassification.codeLike(
        "java",
        "class Solution { public int climbStairs(int n) { return n; } }",
        "原始消息",
        Map.of(),
        new PracticeCodeReviewEvidence(PracticeTurnClassifier.EVIDENCE_CLASS_SOLUTION, "class Solution"));
  }

  private static PracticeCodeReview review() {
    return PracticeCodeReviewServiceTest.review();
  }

  private static final class CountingReviewService extends PracticeCodeReviewService {
    private int calls;
    private final PracticeReviewResult result;

    private CountingReviewService(PracticeReviewResult result) {
      super(context -> result);
      this.result = result;
    }

    @Override
    public PracticeReviewResult review(PracticeTurnContext context) {
      calls++;
      return result;
    }
  }

  private static final class CapturingReviewService extends PracticeCodeReviewService {
    private final PracticeReviewResult result;
    private PracticeTurnContext capturedContext;

    private CapturingReviewService(PracticeReviewResult result) {
      super(context -> result);
      this.result = result;
    }

    @Override
    public PracticeReviewResult review(PracticeTurnContext context) {
      capturedContext = context;
      return result;
    }
  }
}
