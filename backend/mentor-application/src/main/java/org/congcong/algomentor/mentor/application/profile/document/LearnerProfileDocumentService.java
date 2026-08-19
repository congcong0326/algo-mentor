package org.congcong.algomentor.mentor.application.profile.document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 文档读取和 statement evidence 分页入口；身份与 HMAC 都在服务端边界内校验。 */
public class LearnerProfileDocumentService {

  public static final int EVIDENCE_PAGE_MAX_SIZE = 20;

  private final LearnerProfileDocumentProjectionRepository repository;
  private final LearnerProfileDocumentProjector projector;
  private final LearnerProfileStatementReferenceCodec referenceCodec;
  private final LearnerMemoryMetrics metrics;

  public LearnerProfileDocumentService(
      LearnerProfileDocumentProjectionRepository repository,
      LearnerProfileDocumentProjector projector,
      LearnerProfileStatementReferenceCodec referenceCodec) {
    this(repository, projector, referenceCodec, LearnerMemoryMetrics.NOOP);
  }

  public LearnerProfileDocumentService(
      LearnerProfileDocumentProjectionRepository repository,
      LearnerProfileDocumentProjector projector,
      LearnerProfileStatementReferenceCodec referenceCodec,
      LearnerMemoryMetrics metrics) {
    this.repository = repository;
    this.projector = projector;
    this.referenceCodec = referenceCodec;
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  public LearnerProfileDocument getDocument(long userId, String locale) {
    try {
      String normalizedLocale = normalizeLocale(locale);
      LearnerProfileDocument document = projector.project(repository.loadSnapshot(userId, normalizedLocale), normalizedLocale);
      Map<Integer, LearnerProfileDocument.Citation> citations = new LinkedHashMap<>();
      document.citationMap().forEach((number, citation) -> citations.put(number,
          citation.withStatementRef(referenceCodec.encodeStatementRef(userId, citation.claimRevisionId()))));
      metrics.recordProfileProjection("SUCCEEDED", document.projectorVersion(), citations.size());
      return new LearnerProfileDocument(document.format(), document.projectorVersion(), document.locale(),
          document.documentRevision(), document.title(), document.blocks(), citations, document.updatedAt());
    } catch (RuntimeException exception) {
      metrics.recordProfileProjection("FAILED", "v1", 0);
      throw exception;
    }
  }

  public LearnerProfileDocument.EvidencePage getEvidence(
      long userId,
      String statementRef,
      String cursorValue,
      int limit,
      String locale) {
    if (limit <= 0 || limit > EVIDENCE_PAGE_MAX_SIZE) {
      throw new InvalidLearnerProfileDocumentRequestException("evidence limit must be between 1 and 20");
    }
    LearnerProfileStatementReferenceCodec.StatementReference reference = referenceCodec
        .decodeStatementRef(userId, statementRef)
        .orElseThrow(LearnerProfileStatementNotFoundException::new);
    if (!repository.existsActiveStatement(userId, reference.claimRevisionId())) {
      throw new LearnerProfileStatementNotFoundException();
    }
    LearnerProfileStatementReferenceCodec.EvidenceCursor cursor = cursorValue == null || cursorValue.isBlank()
        ? null
        : referenceCodec.decodeCursor(userId, reference.claimRevisionId(), limit, cursorValue)
            .orElseThrow(LearnerProfileStatementNotFoundException::new);
    List<LearnerProfileDocument.EvidenceItem> values = repository.findActiveEvidence(
        userId, reference.claimRevisionId(), cursor, limit + 1, normalizeLocale(locale));
    if (values.isEmpty()) {
      return new LearnerProfileDocument.EvidencePage(List.of(), null);
    }
    boolean hasNext = values.size() > limit;
    List<LearnerProfileDocument.EvidenceItem> items = hasNext ? values.subList(0, limit) : values;
    String nextCursor = null;
    if (hasNext) {
      LearnerProfileDocument.EvidenceItem last = items.get(items.size() - 1);
      nextCursor = referenceCodec.encodeCursor(userId, reference.claimRevisionId(), limit,
          new LearnerProfileStatementReferenceCodec.EvidenceCursor(last.occurredAt(), last.type(), last.sourceId()));
    }
    return new LearnerProfileDocument.EvidencePage(items, nextCursor);
  }

  private static String normalizeLocale(String locale) {
    return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en") ? "en-US" : "zh-CN";
  }

  public static class LearnerProfileStatementNotFoundException extends RuntimeException {
    public LearnerProfileStatementNotFoundException() {
      super("Learner profile statement was not found.");
    }
  }

  public static class InvalidLearnerProfileDocumentRequestException extends RuntimeException {
    public InvalidLearnerProfileDocumentRequestException(String message) {
      super(message);
    }
  }
}
