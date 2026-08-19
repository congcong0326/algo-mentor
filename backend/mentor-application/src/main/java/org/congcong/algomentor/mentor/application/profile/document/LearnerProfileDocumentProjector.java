package org.congcong.algomentor.mentor.application.profile.document;

import static org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument.BlockType.HEADING;
import static org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument.BlockType.PARAGRAPH;
import static org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument.SpanType.SUPPORTED_TEXT;
import static org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument.SpanType.TEXT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Kind;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog.SectionDefinition;

/** 纯函数投影器：固定主题、稳定顺序和引用编号，不读取数据库或调用模型。 */
public final class LearnerProfileDocumentProjector {

  private static final String CLAIM_SEPARATOR = "；";

  private final LearnerMemorySectionCatalog sectionCatalog;

  public LearnerProfileDocumentProjector(LearnerMemorySectionCatalog sectionCatalog) {
    this.sectionCatalog = sectionCatalog;
  }

  public LearnerProfileDocument project(LearnerProfileProjectionSnapshot snapshot, String locale) {
    String normalizedLocale = normalizeLocale(locale);
    List<LearnerProfileDocument.Block> blocks = new ArrayList<>();
    Map<Integer, LearnerProfileDocument.Citation> citations = new LinkedHashMap<>();
    int nextCitation = 1;
    Instant updatedAt = null;

    for (SectionDefinition section : sectionCatalog.sections()) {
      List<LearnerProfileProjectionSnapshot.Claim> claims = snapshot.claims().stream()
          .filter(claim -> section.matches(claim.revision()))
          .sorted(claimComparator(section))
          .toList();
      if (claims.isEmpty()) {
        continue;
      }
      blocks.add(new LearnerProfileDocument.Block(HEADING, List.of(
          new LearnerProfileDocument.Span(TEXT, sectionTitle(section.id(), normalizedLocale), null))));
      List<LearnerProfileDocument.Span> spans = new ArrayList<>();
      for (LearnerProfileProjectionSnapshot.Claim claim : claims) {
        if (!spans.isEmpty()) {
          spans.add(new LearnerProfileDocument.Span(TEXT, CLAIM_SEPARATOR, null));
        }
        if (claim.revision().scope().kind() == Kind.TAG_ASSESSMENT) {
          spans.add(new LearnerProfileDocument.Span(TEXT, tagPrefix(claim, normalizedLocale), null));
        }
        long revisionId = claim.revision().id();
        int displayNumber = nextCitation++;
        List<LearnerProfileDocument.EvidenceItem> evidence = snapshot.evidenceByRevision()
            .getOrDefault(revisionId, List.of()).stream().sorted(evidenceComparator()).toList();
        spans.add(new LearnerProfileDocument.Span(
            SUPPORTED_TEXT, displayClaimText(claim.revision().claimText(), evidence), displayNumber));
        citations.put(displayNumber, new LearnerProfileDocument.Citation(
            displayNumber,
            null,
            revisionId,
            claim.revision().claimKey(),
            claim.revision().origin(),
            sourceSummary(evidence, normalizedLocale),
            evidence.size(),
            evidence.stream().limit(2).toList()));
        if (updatedAt == null || claim.revision().updatedAt().isAfter(updatedAt)) {
          updatedAt = claim.revision().updatedAt();
        }
      }
      blocks.add(new LearnerProfileDocument.Block(PARAGRAPH, spans));
    }
    return new LearnerProfileDocument(
        LearnerProfileDocument.FORMAT,
        LearnerProfileDocument.PROJECTOR_VERSION,
        normalizedLocale,
        documentRevision(normalizedLocale, snapshot),
        documentTitle(normalizedLocale),
        blocks,
        citations,
        updatedAt);
  }

  private Comparator<LearnerProfileProjectionSnapshot.Claim> claimComparator(SectionDefinition section) {
    return Comparator.comparingInt((LearnerProfileProjectionSnapshot.Claim claim) -> section.scopes().stream()
            .filter(scope -> scope.kind() == claim.revision().scope().kind()
                && scope.dimension() == claim.revision().scope().dimension())
            .findFirst()
            .map(section.scopes()::indexOf)
            .orElse(Integer.MAX_VALUE))
        .thenComparingInt(claim -> claim.revision().scope().kind() == Kind.DECLARED_FACT ? 0 : 1)
        .thenComparing(claim -> claim.revision().updatedAt(), Comparator.reverseOrder())
        .thenComparingLong(claim -> claim.revision().id());
  }

  private Comparator<LearnerProfileDocument.EvidenceItem> evidenceComparator() {
    return Comparator.comparing(LearnerProfileDocument.EvidenceItem::occurredAt)
        .thenComparing(item -> item.type().name())
        .thenComparingLong(LearnerProfileDocument.EvidenceItem::sourceId);
  }

  private String displayClaimText(String claimText, List<LearnerProfileDocument.EvidenceItem> evidence) {
    Map<String, String> titlesBySlug = new LinkedHashMap<>();
    evidence.stream()
        .filter(item -> item.type() == LearnerProfileDocument.EvidenceType.CODE_REVIEW)
        .map(LearnerProfileDocument.EvidenceItem::codeReview)
        .forEach(review -> titlesBySlug.putIfAbsent(review.problemSlug(), review.problemTitle()));
    String displayText = claimText;
    for (Map.Entry<String, String> entry : titlesBySlug.entrySet().stream()
        .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length).reversed()))
        .toList()) {
      Pattern slug = Pattern.compile("(?<![A-Za-z0-9-])" + Pattern.quote(entry.getKey()) + "(?![A-Za-z0-9-])");
      displayText = slug.matcher(displayText).replaceAll(Matcher.quoteReplacement(entry.getValue()));
    }
    return displayText;
  }

  private String sourceSummary(List<LearnerProfileDocument.EvidenceItem> evidence, String locale) {
    long reviews = evidence.stream().filter(item -> item.type() == LearnerProfileDocument.EvidenceType.CODE_REVIEW).count();
    long messages = evidence.stream().filter(item -> item.type() == LearnerProfileDocument.EvidenceType.USER_MESSAGE).count();
    if ("en-US".equals(locale)) {
      if (reviews == 0 && messages == 0) {
        return "No displayable evidence";
      }
      if (reviews == 0) {
        return "User message evidence: " + messages;
      }
      if (messages == 0) {
        return "Code review evidence: " + reviews;
      }
      return "User message evidence: " + messages + "; code review evidence: " + reviews;
    }
    if (reviews == 0 && messages == 0) {
      return "暂无可展示依据";
    }
    if (reviews == 0) {
      return "用户消息依据 " + messages + " 条";
    }
    if (messages == 0) {
      return "正式代码复盘依据 " + reviews + " 条";
    }
    return "用户消息依据 " + messages + " 条，正式代码复盘依据 " + reviews + " 条";
  }

  private String tagPrefix(LearnerProfileProjectionSnapshot.Claim claim, String locale) {
    String label = "en-US".equals(locale) ? claim.tagLabelEn() : claim.tagLabelZh();
    if (label == null || label.isBlank()) {
      label = "en-US".equals(locale) ? claim.tagLabelZh() : claim.tagLabelEn();
    }
    if (label == null || label.isBlank()) {
      return "en-US".equals(locale) ? "Tag: " : "标签：";
    }
    return LearnerProfilePlainTextPolicy.normalize(label) + ("en-US".equals(locale) ? ": " : "：");
  }

  private String documentRevision(String locale, LearnerProfileProjectionSnapshot snapshot) {
    StringBuilder source = new StringBuilder(LearnerProfileDocument.PROJECTOR_VERSION).append('|').append(locale);
    snapshot.claims().stream().map(claim -> claim.revision().id()).sorted().forEach(id -> source.append('|').append(id));
    snapshot.evidenceByRevision().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
        entry.getValue().stream()
            .filter(item -> item.type() == LearnerProfileDocument.EvidenceType.CODE_REVIEW)
            .sorted(evidenceComparator())
            .forEach(item -> source.append('|').append(entry.getKey()).append(':')
                .append(item.sourceId()).append(':').append(item.codeReview().problemTitle())));
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(String.format(Locale.ROOT, "%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String normalizeLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return "zh-CN";
    }
    return locale.toLowerCase(Locale.ROOT).startsWith("en") ? "en-US" : "zh-CN";
  }

  private String documentTitle(String locale) {
    return "en-US".equals(locale) ? "Learning profile" : "学习画像";
  }

  private String sectionTitle(String id, String locale) {
    if (!"en-US".equals(locale)) {
      return switch (id) {
        case "background-goals" -> "学习背景与目标";
        case "learning-conditions" -> "学习方式与条件";
        case "problem-solving" -> "解题与实现";
        case "review-growth" -> "复盘与成长";
        case "knowledge-performance" -> "知识点表现";
        default -> throw new IllegalArgumentException("Unknown learner memory section: " + id);
      };
    }
    return switch (id) {
      case "background-goals" -> "Background and goals";
      case "learning-conditions" -> "Learning preferences and constraints";
      case "problem-solving" -> "Problem solving and implementation";
      case "review-growth" -> "Review and growth";
      case "knowledge-performance" -> "Knowledge performance";
      default -> throw new IllegalArgumentException("Unknown learner memory section: " + id);
    };
  }
}
