package org.congcong.algomentor.auth.cache;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 认证和内测准入缓存的容量及 TTL 配置。 */
@ConfigurationProperties(prefix = "algo-mentor.auth.cache")
public class AuthCacheProperties {

  private long accessSnapshotMaximumSize = 500;
  private Duration accessSnapshotTtl = Duration.ofMinutes(1);
  private Duration betaAccessSettingsTtl = Duration.ofSeconds(30);
  private long betaEmailMembershipMaximumSize = 500;
  private Duration betaEmailMembershipTtl = Duration.ofMinutes(1);

  public long getAccessSnapshotMaximumSize() {
    return accessSnapshotMaximumSize;
  }

  public void setAccessSnapshotMaximumSize(long accessSnapshotMaximumSize) {
    this.accessSnapshotMaximumSize = requirePositive(accessSnapshotMaximumSize, "accessSnapshotMaximumSize");
  }

  public Duration getAccessSnapshotTtl() {
    return accessSnapshotTtl;
  }

  public void setAccessSnapshotTtl(Duration accessSnapshotTtl) {
    this.accessSnapshotTtl = requirePositive(accessSnapshotTtl, "accessSnapshotTtl");
  }

  public Duration getBetaAccessSettingsTtl() {
    return betaAccessSettingsTtl;
  }

  public void setBetaAccessSettingsTtl(Duration betaAccessSettingsTtl) {
    this.betaAccessSettingsTtl = requirePositive(betaAccessSettingsTtl, "betaAccessSettingsTtl");
  }

  public long getBetaEmailMembershipMaximumSize() {
    return betaEmailMembershipMaximumSize;
  }

  public void setBetaEmailMembershipMaximumSize(long betaEmailMembershipMaximumSize) {
    this.betaEmailMembershipMaximumSize = requirePositive(
        betaEmailMembershipMaximumSize, "betaEmailMembershipMaximumSize");
  }

  public Duration getBetaEmailMembershipTtl() {
    return betaEmailMembershipTtl;
  }

  public void setBetaEmailMembershipTtl(Duration betaEmailMembershipTtl) {
    this.betaEmailMembershipTtl = requirePositive(betaEmailMembershipTtl, "betaEmailMembershipTtl");
  }

  private static long requirePositive(long value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static Duration requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }
}
