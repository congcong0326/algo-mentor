package org.congcong.algomentor.mentor.application.profile.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract.MessageRole;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.junit.jupiter.api.Test;

class LearnerProfileStatementRefCodecTest {

  @Test
  void rejectsCrossUserAndChangedPageSizeReferences() {
    LearnerProfileStatementReferenceCodec codec = new LearnerProfileStatementReferenceCodec("test-signing-secret");
    String ref = codec.encodeStatementRef(42L, 7L);
    String cursor = codec.encodeCursor(42L, 7L, 20,
        new LearnerProfileStatementReferenceCodec.EvidenceCursor(
            Instant.parse("2026-07-20T12:00:00Z"), LearnerProfileDocument.EvidenceType.USER_MESSAGE, 9L));

    assertThat(codec.decodeStatementRef(42L, ref)).isPresent();
    assertThat(codec.decodeStatementRef(43L, ref)).isEmpty();
    assertThat(codec.decodeStatementRef(42L, ref + "x")).isEmpty();
    assertThat(codec.decodeCursor(42L, 7L, 20, cursor)).isPresent();
    assertThat(codec.decodeCursor(42L, 7L, 10, cursor)).isEmpty();
    assertThat(codec.decodeCursor(42L, 8L, 20, cursor)).isEmpty();
  }

  @Test
  void preservesSubMillisecondTimestampPrecisionInTheCursor() {
    LearnerProfileStatementReferenceCodec codec = new LearnerProfileStatementReferenceCodec("test-signing-secret");
    Instant occurredAt = Instant.parse("2026-07-20T12:00:00.123456Z");
    String cursor = codec.encodeCursor(42L, 7L, 20,
        new LearnerProfileStatementReferenceCodec.EvidenceCursor(
            occurredAt, LearnerProfileDocument.EvidenceType.CODE_REVIEW, 9L));

    assertThat(codec.decodeCursor(42L, 7L, 20, cursor))
        .hasValue(new LearnerProfileStatementReferenceCodec.EvidenceCursor(
            occurredAt, LearnerProfileDocument.EvidenceType.CODE_REVIEW, 9L));
  }

  @Test
  void paginatesCompleteItemsAndUsesTheBoundCursor() {
    List<LearnerProfileDocument.EvidenceItem> evidence = new ArrayList<>();
    for (int index = 1; index <= 21; index++) {
      evidence.add(messageEvidence(index));
    }
    LearnerProfileDocumentProjectionRepository repository = new LearnerProfileDocumentProjectionRepository() {
      @Override
      public LearnerProfileProjectionSnapshot loadSnapshot(long userId) {
        return new LearnerProfileProjectionSnapshot(List.of(), Map.of());
      }

      @Override
      public boolean existsActiveStatement(long userId, long claimRevisionId) {
        return userId == 42L && claimRevisionId == 7L;
      }

      @Override
      public List<LearnerProfileDocument.EvidenceItem> findActiveEvidence(
          long userId,
          long claimRevisionId,
          LearnerProfileStatementReferenceCodec.EvidenceCursor cursor,
          int limitPlusOne) {
        return evidence.stream()
            .filter(item -> cursor == null || item.sourceId() > cursor.sourceId())
            .limit(limitPlusOne)
            .toList();
      }
    };
    LearnerProfileStatementReferenceCodec codec = new LearnerProfileStatementReferenceCodec("test-signing-secret");
    LearnerProfileDocumentService service = new LearnerProfileDocumentService(
        repository,
        new LearnerProfileDocumentProjector(new LearnerMemorySectionCatalog()),
        codec);
    String ref = codec.encodeStatementRef(42L, 7L);

    LearnerProfileDocument.EvidencePage first = service.getEvidence(42L, ref, null, 20);
    LearnerProfileDocument.EvidencePage second = service.getEvidence(42L, ref, first.nextCursor(), 20);

    assertThat(first.items()).hasSize(20);
    assertThat(first.nextCursor()).isNotBlank();
    assertThat(second.items()).extracting(LearnerProfileDocument.EvidenceItem::sourceId).containsExactly(21L);
    assertThat(second.nextCursor()).isNull();
  }

  private LearnerProfileDocument.EvidenceItem messageEvidence(long sourceId) {
    return new LearnerProfileDocument.EvidenceItem(
        LearnerProfileDocument.EvidenceType.USER_MESSAGE,
        sourceId,
        Instant.parse("2026-07-20T12:00:00Z"),
        null,
        MessageRole.DECLARED,
        null,
        new LearnerProfileDocument.UserMessageSource("受限消息摘录"));
  }
}
