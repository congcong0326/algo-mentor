package org.congcong.algomentor.mentor.application.profile.claim.model;

/** 规范化 claim 文本的 SHA-256 标识，不能与快照或文档版本混用。 */
public record LearnerMemoryClaimTextHash(String value) {

  private static final String SHA_256_HEX = "[0-9a-f]{64}";

  public LearnerMemoryClaimTextHash {
    if (value == null || !value.matches(SHA_256_HEX)) {
      throw new IllegalArgumentException("claim text hash 必须是 64 位小写 SHA-256。");
    }
  }
}
