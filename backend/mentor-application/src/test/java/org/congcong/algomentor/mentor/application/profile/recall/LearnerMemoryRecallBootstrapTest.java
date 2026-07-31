package org.congcong.algomentor.mentor.application.profile.recall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.junit.jupiter.api.Test;

class LearnerMemoryRecallBootstrapTest {

  @Test
  void rendersAStableBootstrapWithoutTruncatingPartialClaims() {
    LearnerMemoryRecallSnapshot snapshot = snapshot("first-complete-claim", "second-must-be-omitted-" + "x".repeat(500));
    LearnerMemoryRecallBootstrapBuilder builder = new LearnerMemoryRecallBootstrapBuilder(150);

    LearnerMemoryRecallBootstrapBuilder.Bootstrap first = builder.build(snapshot, "可信边界");
    LearnerMemoryRecallBootstrapBuilder.Bootstrap second = builder.build(snapshot, "可信边界");

    assertThat(first).isEqualTo(second);
    assertThat(first.text()).contains("first-complete-claim").doesNotContain("second-must-be-omitted-");
    assertThat(first.tokenEstimate()).isLessThanOrEqualTo(150);
    assertThat(first.trimmed()).isTrue();
  }

  @Test
  void enforcesTheBootstrapBudgetHardLimit() {
    assertThatThrownBy(() -> new LearnerMemoryRecallBootstrapBuilder(1_501))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static LearnerMemoryRecallSnapshot snapshot(String firstText, String secondText) {
    LearnerMemoryRecallSnapshot.Statement first = statement("statement-first", 1L, firstText);
    LearnerMemoryRecallSnapshot.Statement second = statement("statement-second", 2L, secondText);
    return new LearnerMemoryRecallSnapshot(
        "scope-ref",
        "a".repeat(64),
        "zh-CN",
        List.of(new LearnerMemoryRecallSnapshot.Section(
            "section-ref",
            "background-goals",
            "学习背景与目标",
            List.of(first, second),
            Instant.parse("2026-01-02T00:00:00Z"),
            0)),
        List.of(first, second));
  }

  private static LearnerMemoryRecallSnapshot.Statement statement(String statementRef, long id, String text) {
    Instant now = Instant.parse("2026-01-02T00:00:00Z");
    LearnerMemoryClaimRevision claim = new LearnerMemoryClaimRevision(
        id,
        new UUID(0L, id),
        7L,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        ("%064x").formatted(id),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        1L,
        null,
        now,
        null,
        now,
        now);
    return new LearnerMemoryRecallSnapshot.Statement(
        statementRef, claim, "用户明确自述", claim.evidenceGrade(), now, false);
  }
}
