package org.congcong.algomentor.mentor.application.profile.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.junit.jupiter.api.Test;

class LearnerProfileDocumentProjectorTest {

  private final LearnerProfileDocumentProjector projector =
      new LearnerProfileDocumentProjector(new LearnerMemorySectionCatalog());

  @Test
  void projectsClaimsInCatalogOrderRegardlessOfInputOrder() {
    LearnerProfileProjectionSnapshot.Claim goals = claim(
        12L, LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS, "准备后端面试", "2026-07-20T12:00:00Z");
    LearnerProfileProjectionSnapshot.Claim background = claim(
        11L, LearnerMemoryClaimContract.Dimension.LEARNER_BACKGROUND, "<script>已有 Java 基础</script>", "2026-07-19T12:00:00Z");

    LearnerProfileDocument first = projector.project(
        new LearnerProfileProjectionSnapshot(List.of(goals, background), Map.of()), "zh-CN");
    LearnerProfileDocument second = projector.project(
        new LearnerProfileProjectionSnapshot(List.of(background, goals), Map.of()), "zh");

    assertThat(first).isEqualTo(second);
    assertThat(first.blocks()).hasSize(2);
    assertThat(first.blocks().get(1).spans()).extracting(LearnerProfileDocument.Span::text)
        .containsExactly("<script>已有 Java 基础</script>", "；", "准备后端面试");
    assertThat(first.blocks().get(1).spans()).extracting(LearnerProfileDocument.Span::type)
        .containsExactly(
            LearnerProfileDocument.SpanType.SUPPORTED_TEXT,
            LearnerProfileDocument.SpanType.TEXT,
            LearnerProfileDocument.SpanType.SUPPORTED_TEXT);
    assertThat(first.citationMap()).extractingByKeys(1, 2)
        .extracting(LearnerProfileDocument.Citation::claimRevisionId)
        .containsExactly(11L, 12L);
    assertThat(first.citationMap().keySet()).containsExactly(1, 2);
    assertThat(first.documentRevision()).hasSize(64);
  }

  @Test
  void rendersTrustedBilingualTagLabelsWithoutChangingTheSupportedClaimText() {
    LearnerMemoryClaimRevision revision = new LearnerMemoryClaimRevision(
        21L,
        UUID.nameUUIDFromBytes("tag-claim-21".getBytes()),
        42L,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
            LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
            7L),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        "数组边界处理稳定",
        "0".repeat(64),
        LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED,
        LearnerMemoryEvidenceContract.Pattern.TAG_BREADTH,
        LearnerMemoryEvidenceContract.Grade.SUPPORTED,
        null,
        1L,
        null,
        Instant.parse("2026-07-20T12:00:00Z"),
        null,
        Instant.parse("2026-07-20T12:00:00Z"),
        Instant.parse("2026-07-20T12:00:00Z"));

    LearnerProfileDocument document = projector.project(new LearnerProfileProjectionSnapshot(
        List.of(new LearnerProfileProjectionSnapshot.Claim(revision, "Array", "数组")), Map.of()), "zh-CN");

    assertThat(document.blocks()).hasSize(2);
    assertThat(document.blocks().get(1).spans()).extracting(LearnerProfileDocument.Span::text)
        .containsExactly("数组：", "数组边界处理稳定");
    assertThat(document.blocks().get(1).spans().get(1).type())
        .isEqualTo(LearnerProfileDocument.SpanType.SUPPORTED_TEXT);
  }

  @Test
  void rendersNoBlocksOrCitationsForAnEmptyActiveSnapshot() {
    LearnerProfileDocument document = projector.project(
        new LearnerProfileProjectionSnapshot(List.of(), Map.of()), "en-US");

    assertThat(document.title()).isEqualTo("Learning profile");
    assertThat(document.blocks()).isEmpty();
    assertThat(document.citationMap()).isEmpty();
    assertThat(document.updatedAt()).isNull();
  }

  private LearnerProfileProjectionSnapshot.Claim claim(
      long id,
      LearnerMemoryClaimContract.Dimension dimension,
      String text,
      String updatedAt) {
    Instant timestamp = Instant.parse(updatedAt);
    LearnerMemoryClaimRevision revision = new LearnerMemoryClaimRevision(
        id,
        UUID.nameUUIDFromBytes(("claim-" + id).getBytes()),
        42L,
        new LearnerMemoryClaimScope(LearnerMemoryClaimContract.Kind.DECLARED_FACT, dimension, null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        "0".repeat(64),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        1L,
        null,
        timestamp,
        null,
        timestamp,
        timestamp);
    return new LearnerProfileProjectionSnapshot.Claim(revision, null, null);
  }
}
