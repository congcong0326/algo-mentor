package org.congcong.algomentor.mentor.application.profile.claim.model;

/** 当前 ACTIVE claim 集合的受信快照令牌。 */
public record LearnerMemorySnapshotToken(String value) {

  private static final String SHA_256_HEX = "[0-9a-f]{64}";

  public LearnerMemorySnapshotToken {
    if (value == null || !value.matches(SHA_256_HEX)) {
      throw new IllegalArgumentException("snapshot token 必须是 64 位小写 SHA-256。");
    }
  }
}
