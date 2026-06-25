package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;
import java.util.Optional;

public class PracticeCompletionGateService {

  private final PracticeCodeReviewRepository reviewRepository;

  public PracticeCompletionGateService(PracticeCodeReviewRepository reviewRepository) {
    if (reviewRepository == null) {
      throw new IllegalArgumentException("Practice code review repository must not be null");
    }
    this.reviewRepository = reviewRepository;
  }

  public PracticeCompletionGate evaluate(long userId, PracticeSession session) {
    if (session == null) {
      throw new IllegalArgumentException("Practice session must not be null");
    }
    return evaluate(userId, session, reviewRepository.findLatestSummary(userId, session.id()));
  }

  public PracticeCompletionGate evaluate(
      long userId,
      PracticeSession session,
      Optional<PracticeCodeReviewSummary> latest) {
    if (session == null) {
      throw new IllegalArgumentException("Practice session must not be null");
    }
    latest = latest == null ? Optional.empty() : latest;
    if (session.progressStatus() == PracticeProgressStatus.COMPLETED) {
      return gate(false, PracticeCompletionGate.ReasonCode.ALREADY_COMPLETED, "该题目已经标记完成。", Optional.empty());
    }
    if (latest.isEmpty()) {
      return gate(false, PracticeCompletionGate.ReasonCode.NO_REVIEW,
          "完成前需要先粘贴完整代码完成一次 AI Review。", Optional.empty());
    }
    BigDecimal score = latest.get().totalScore();
    if (!latest.get().passed() || score.compareTo(PracticeCodeReviewConstants.PASS_SCORE) < 0) {
      return gate(false, PracticeCompletionGate.ReasonCode.LATEST_REVIEW_FAILED,
          "最近一次 Review 为 %s/10，达到 6 分后可标记完成。".formatted(score.stripTrailingZeros().toPlainString()),
          Optional.of(score));
    }
    return gate(true, PracticeCompletionGate.ReasonCode.PASSED, "标记为已完成", Optional.of(score));
  }

  private PracticeCompletionGate gate(
      boolean canComplete,
      PracticeCompletionGate.ReasonCode reasonCode,
      String message,
      Optional<BigDecimal> latestScore
  ) {
    return new PracticeCompletionGate(canComplete, reasonCode, message, latestScore,
        PracticeCodeReviewConstants.PASS_SCORE);
  }
}
