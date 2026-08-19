package org.congcong.algomentor.api.profile.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileDocumentClaimRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileDocumentEvidenceRow;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocumentProjectionRepository;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileProjectionSnapshot;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileStatementReferenceCodec.EvidenceCursor;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;

/** 文档投影的 PostgreSQL 批量只读适配器。 */
public final class MyBatisLearnerProfileDocumentProjectionRepository
    implements LearnerProfileDocumentProjectionRepository {

  private static final Comparator<LearnerProfileDocument.EvidenceItem> EVIDENCE_ORDER = Comparator
      .comparing(LearnerProfileDocument.EvidenceItem::occurredAt)
      .thenComparing(item -> item.type().name())
      .thenComparingLong(LearnerProfileDocument.EvidenceItem::sourceId);

  private final LearnerMemoryMapper mapper;

  public MyBatisLearnerProfileDocumentProjectionRepository(LearnerMemoryMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public LearnerProfileProjectionSnapshot loadSnapshot(long userId, String locale) {
    requirePositive(userId, "user id");
    List<LearnerProfileProjectionSnapshot.Claim> claims = mapper.findActiveDocumentClaims(userId).stream()
        .map(this::toClaim)
        .toList();
    if (claims.isEmpty()) {
      return new LearnerProfileProjectionSnapshot(List.of(), Map.of());
    }
    List<Long> revisionIds = claims.stream().map(claim -> claim.revision().id()).toList();
    Map<Long, List<LearnerProfileDocument.EvidenceItem>> evidenceByRevision = new LinkedHashMap<>();
    mapper.findDocumentEvidenceByRevisionIds(userId, revisionIds, null, locale).stream()
        .map(this::toEvidence)
        .forEach(evidence -> evidenceByRevision.computeIfAbsent(evidence.claimRevisionId(), unused -> new ArrayList<>())
            .add(evidence.item()));
    evidenceByRevision.replaceAll((revisionId, evidence) -> evidence.stream().sorted(EVIDENCE_ORDER).toList());
    return new LearnerProfileProjectionSnapshot(claims, evidenceByRevision);
  }

  @Override
  public boolean existsActiveStatement(long userId, long claimRevisionId) {
    requirePositive(userId, "user id");
    requirePositive(claimRevisionId, "claim revision id");
    return mapper.existsActiveDocumentClaim(userId, claimRevisionId);
  }

  @Override
  public List<LearnerProfileDocument.EvidenceItem> findActiveEvidence(
      long userId,
      long claimRevisionId,
      EvidenceCursor cursor,
      int limitPlusOne,
      String locale) {
    requirePositive(userId, "user id");
    requirePositive(claimRevisionId, "claim revision id");
    if (limitPlusOne <= 0) {
      throw new IllegalArgumentException("evidence limit must be positive");
    }
    return mapper.findActiveDocumentEvidencePage(
        userId,
        claimRevisionId,
        null,
        cursor == null ? null : cursor.occurredAt(),
        cursor == null ? null : cursor.type().name(),
        cursor == null ? null : cursor.sourceId(),
        limitPlusOne,
        locale).stream().map(row -> toEvidence(row).item()).toList();
  }

  private LearnerProfileProjectionSnapshot.Claim toClaim(LearnerProfileDocumentClaimRow row) {
    LearnerMemoryClaimRevision revision = new LearnerMemoryClaimRevision(
        row.id(),
        UUID.fromString(row.claimKey()),
        row.userId(),
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.valueOf(row.entryKind()),
            LearnerMemoryClaimContract.Dimension.valueOf(row.dimension()),
            row.tagId()),
        row.revisionNo(),
        LearnerMemoryClaimContract.RevisionStatus.valueOf(row.status()),
        row.claimText(),
        row.claimTextHash(),
        LearnerMemoryClaimContract.Origin.valueOf(row.originType()),
        LearnerMemoryEvidenceContract.Pattern.valueOf(row.evidencePattern()),
        LearnerMemoryEvidenceContract.Grade.valueOf(row.evidenceGrade()),
        row.decisionReason(),
        row.updateRunId(),
        row.supersedesRevisionId(),
        row.validFrom(),
        row.validTo(),
        row.createdAt(),
        row.updatedAt());
    return new LearnerProfileProjectionSnapshot.Claim(revision, row.tagLabelEn(), row.tagLabelZh());
  }

  private EvidenceRow toEvidence(LearnerProfileDocumentEvidenceRow row) {
    LearnerProfileDocument.EvidenceType type = LearnerProfileDocument.EvidenceType.valueOf(row.sourceType());
    LearnerProfileDocument.EvidenceItem item = switch (type) {
      case CODE_REVIEW -> new LearnerProfileDocument.EvidenceItem(
          type,
          row.sourceId(),
          requireOccurredAt(row.occurredAt()),
          LearnerMemoryEvidenceContract.ReviewRole.valueOf(row.reviewRole()),
          null,
          new LearnerProfileDocument.CodeReviewSource(
              row.sourceId(),
              requireValue(row.reviewSessionId(), "review session id"),
              requireValue(row.planId(), "plan id"),
              requireValue(row.phaseIndex(), "phase index"),
              row.problemSlug(),
              problemTitle(row),
              requireValue(row.versionNo(), "version no"),
              row.totalScore(),
              Boolean.TRUE.equals(row.passed())),
          null);
      case USER_MESSAGE -> new LearnerProfileDocument.EvidenceItem(
          type,
          row.sourceId(),
          requireOccurredAt(row.occurredAt()),
          null,
          LearnerMemoryEvidenceContract.MessageRole.valueOf(row.messageRole()),
          null,
          new LearnerProfileDocument.UserMessageSource(row.messageExcerpt()));
    };
    return new EvidenceRow(row.claimRevisionId(), item);
  }

  private static Instant requireOccurredAt(Instant value) {
    return Objects.requireNonNull(value, "evidence occurred at");
  }

  private static long requireValue(Long value, String name) {
    if (value == null || value <= 0) {
      throw new IllegalStateException(name + " is missing from learner profile evidence");
    }
    return value;
  }

  private static int requireValue(Integer value, String name) {
    if (value == null || value < 0) {
      throw new IllegalStateException(name + " is missing from learner profile evidence");
    }
    return value;
  }

  private static String problemTitle(LearnerProfileDocumentEvidenceRow row) {
    return row.problemTitle() == null || row.problemTitle().isBlank() ? row.problemSlug() : row.problemTitle();
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  private record EvidenceRow(long claimRevisionId, LearnerProfileDocument.EvidenceItem item) {
  }
}
