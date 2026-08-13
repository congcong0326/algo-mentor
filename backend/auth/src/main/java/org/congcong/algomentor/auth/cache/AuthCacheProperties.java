package org.congcong.algomentor.auth.cache;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 认证和内测准入 Redis 缓存的 TTL 配置。 */
@ConfigurationProperties(prefix = "algo-mentor.auth.cache")
public class AuthCacheProperties {

  private Duration accessSnapshotTtl = Duration.ofMinutes(1);
  private Duration betaAccessSettingsTtl = Duration.ofSeconds(30);
  private Duration betaEmailMembershipTtl = Duration.ofMinutes(1);

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


  public Duration getBetaEmailMembershipTtl() {
    return betaEmailMembershipTtl;
  }

  public void setBetaEmailMembershipTtl(Duration betaEmailMembershipTtl) {
    this.betaEmailMembershipTtl = requirePositive(betaEmailMembershipTtl, "betaEmailMembershipTtl");
  }

  private static Duration requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }
}
