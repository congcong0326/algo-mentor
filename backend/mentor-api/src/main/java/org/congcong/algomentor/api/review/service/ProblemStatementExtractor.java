package org.congcong.algomentor.api.review.service;

import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProblemStatementExtractor {

  private static final Logger log = LoggerFactory.getLogger(ProblemStatementExtractor.class);

  private ProblemStatementExtractor() {
  }

  public static String summary(String contentMarkdown) {
    if (contentMarkdown == null || contentMarkdown.isBlank()) {
      return "";
    }
    for (String block : contentMarkdown.split("\\R\\s*\\R")) {
      String normalized = normalize(block);
      if (normalized.isBlank() || normalized.startsWith("#")) {
        continue;
      }
      if (isMetadataSection(normalized)) {
        break;
      }
      return clamp(normalized, ReviewContractConstants.STATEMENT_SUMMARY_MAX_CHARS);
    }
    log.debug("Problem statement summary extraction produced empty result. markdownLength={}",
        contentMarkdown.length());
    return "";
  }

  private static boolean isMetadataSection(String value) {
    String lower = value.toLowerCase();
    return lower.startsWith("示例")
        || lower.startsWith("example")
        || lower.startsWith("约束")
        || lower.startsWith("constraints");
  }

  private static String normalize(String markdown) {
    return markdown
        .replaceAll("(?m)^#{1,6}\\s*", "#")
        .replaceAll("[*_`>\\-]", "")
        .replaceAll("<[^>]+>", "")
        .replaceAll("\\s+", " ")
        .strip();
  }

  private static String clamp(String value, int maxChars) {
    if (value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars).strip();
  }
}
