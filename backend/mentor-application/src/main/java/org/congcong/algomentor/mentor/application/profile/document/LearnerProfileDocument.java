package org.congcong.algomentor.mentor.application.profile.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Origin;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract.MessageRole;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract.ReviewRole;

/** 对外展示的受限学习画像文档 AST，不承载写入行为。 */
public record LearnerProfileDocument(
    String format,
    String projectorVersion,
    String locale,
    String documentRevision,
    String title,
    List<Block> blocks,
    Map<Integer, Citation> citationMap,
    Instant updatedAt
) {

  public static final String FORMAT = "MARKDOWN_DOCUMENT_V1";
  public static final String PROJECTOR_VERSION = "v1";

  public LearnerProfileDocument {
    requireText(format, "format");
    requireText(projectorVersion, "projector version");
    requireText(locale, "locale");
    requireText(documentRevision, "document revision");
    requireText(title, "title");
    blocks = blocks == null ? List.of() : List.copyOf(blocks);
    citationMap = citationMap == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(citationMap));
  }

  public record Block(BlockType type, List<Span> spans) {
    public Block {
      if (type == null || spans == null || spans.isEmpty()) {
        throw new IllegalArgumentException("document block is incomplete");
      }
      spans = List.copyOf(spans);
    }
  }

  public record Span(SpanType type, String text, Integer citationDisplayNumber) {
    public Span {
      if (type == null) {
        throw new IllegalArgumentException("document span type is required");
      }
      text = LearnerProfilePlainTextPolicy.normalize(text);
      if (text.isEmpty()) {
        throw new IllegalArgumentException("document span text is required");
      }
      if (type == SpanType.SUPPORTED_TEXT && (citationDisplayNumber == null || citationDisplayNumber <= 0)) {
        throw new IllegalArgumentException("supported text requires one citation");
      }
      if (type == SpanType.TEXT && citationDisplayNumber != null) {
        throw new IllegalArgumentException("plain text cannot carry a citation");
      }
    }
  }

  public record Citation(
      int displayNumber,
      String statementRef,
      long claimRevisionId,
      UUID claimKey,
      Origin origin,
      String sourceSummary,
      int evidenceCount,
      List<EvidenceItem> previewEvidence
  ) {
    public Citation {
      if (displayNumber <= 0 || claimRevisionId <= 0 || claimKey == null || origin == null || evidenceCount < 0) {
        throw new IllegalArgumentException("citation is incomplete");
      }
      statementRef = statementRef == null ? null : requireText(statementRef, "statement ref");
      sourceSummary = requireText(sourceSummary, "source summary");
      previewEvidence = previewEvidence == null ? List.of() : List.copyOf(previewEvidence);
      if (previewEvidence.size() > 2 || previewEvidence.size() > evidenceCount) {
        throw new IllegalArgumentException("citation preview is invalid");
      }
    }

    public Citation withStatementRef(String value) {
      return new Citation(displayNumber, value, claimRevisionId, claimKey, origin, sourceSummary,
          evidenceCount, previewEvidence);
    }
  }

  public record EvidencePage(List<EvidenceItem> items, String nextCursor) {
    public EvidencePage {
      items = items == null ? List.of() : List.copyOf(items);
      nextCursor = nextCursor == null || nextCursor.isBlank() ? null : nextCursor;
    }
  }

  public record EvidenceItem(
      EvidenceType type,
      long sourceId,
      Instant occurredAt,
      ReviewRole reviewRole,
      MessageRole messageRole,
      CodeReviewSource codeReview,
      UserMessageSource userMessage
  ) {
    public EvidenceItem {
      if (type == null || sourceId <= 0 || occurredAt == null) {
        throw new IllegalArgumentException("evidence item is incomplete");
      }
      if (type == EvidenceType.CODE_REVIEW && (reviewRole == null || codeReview == null || messageRole != null
          || userMessage != null)) {
        throw new IllegalArgumentException("code review evidence is invalid");
      }
      if (type == EvidenceType.USER_MESSAGE && (messageRole == null || userMessage == null || reviewRole != null
          || codeReview != null)) {
        throw new IllegalArgumentException("user message evidence is invalid");
      }
    }
  }

  public record CodeReviewSource(
      long reviewId,
      long sessionId,
      long planId,
      int phaseIndex,
      String problemSlug,
      String problemTitle,
      int versionNo,
      BigDecimal totalScore,
      boolean passed
  ) {
    public CodeReviewSource {
      if (reviewId <= 0 || sessionId <= 0 || planId <= 0 || phaseIndex < 0 || versionNo <= 0
          || totalScore == null || totalScore.signum() < 0 || totalScore.compareTo(BigDecimal.TEN) > 0) {
        throw new IllegalArgumentException("code review source is invalid");
      }
      problemSlug = requireText(problemSlug, "problem slug");
      problemTitle = requireText(problemTitle, "problem title");
    }
  }

  public record UserMessageSource(String excerpt) {
    public UserMessageSource {
      excerpt = LearnerProfilePlainTextPolicy.normalize(excerpt);
      if (excerpt.isEmpty()) {
        throw new IllegalArgumentException("message excerpt is required");
      }
    }
  }

  public enum BlockType {
    HEADING,
    PARAGRAPH
  }

  public enum SpanType {
    TEXT,
    SUPPORTED_TEXT
  }

  public enum EvidenceType {
    CODE_REVIEW,
    USER_MESSAGE
  }

  private static String requireText(String value, String field) {
    String normalized = LearnerProfilePlainTextPolicy.normalize(value);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return normalized;
  }
}
