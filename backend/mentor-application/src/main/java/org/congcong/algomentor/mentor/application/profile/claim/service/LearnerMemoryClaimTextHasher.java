package org.congcong.algomentor.mentor.application.profile.claim.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimTextHash;

/** 将 claim 正文规范化并计算固定小写 SHA-256。 */
public final class LearnerMemoryClaimTextHasher {

  public LearnerMemoryClaimTextHash hash(String claimText) {
    return new LearnerMemoryClaimTextHash(sha256(normalize(claimText)));
  }

  public String normalize(String claimText) {
    if (claimText == null) {
      throw new IllegalArgumentException("claim text 不能为空。");
    }
    String normalized = claimText.strip().replaceAll("[\\s\\p{Zs}]+", " ");
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("claim text 不能为空。");
    }
    return normalized;
  }

  private String sha256(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte valueByte : bytes) {
        hex.append(String.format("%02x", valueByte));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK SHA-256 不可用。", exception);
    }
  }
}
