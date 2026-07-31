package org.congcong.algomentor.mentor.application.profile.recall;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;

/** 基于 locale 与有序 ACTIVE revision ID 的稳定文档修订号。 */
public final class LearnerMemoryDocumentRevision {

  private LearnerMemoryDocumentRevision() {
  }

  public static String calculate(String locale, List<LearnerMemoryClaimRevision> claims) {
    String normalizedLocale = normalizeLocale(locale);
    StringBuilder source = new StringBuilder(LearnerMemoryRecallContracts.DOCUMENT_REVISION_VERSION)
        .append('\n').append(normalizedLocale).append('\n');
    if (claims != null) {
      claims.stream().filter(java.util.Objects::nonNull).forEach(claim -> source.append(claim.id()).append('\n'));
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(String.format("%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK SHA-256 不可用。", exception);
    }
  }

  static String normalizeLocale(String locale) {
    return locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
  }
}
