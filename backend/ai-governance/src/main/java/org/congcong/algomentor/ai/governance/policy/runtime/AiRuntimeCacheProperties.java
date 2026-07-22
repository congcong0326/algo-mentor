package org.congcong.algomentor.ai.governance.policy.runtime;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 动态策略缓存的容量和陈旧时间配置。 */
@ConfigurationProperties(prefix = "algo-mentor.ai-governance.runtime-cache")
public class AiRuntimeCacheProperties {

  private Duration settingsTtl = Duration.ofMinutes(2);
  private Duration userPolicyTtl = Duration.ofMinutes(2);
  private long userPolicyMaximumSize = 500;

  public Duration getSettingsTtl() {
    return settingsTtl;
  }

  public void setSettingsTtl(Duration settingsTtl) {
    this.settingsTtl = requirePositive(settingsTtl, "settingsTtl");
  }

  public Duration getUserPolicyTtl() {
    return userPolicyTtl;
  }

  public void setUserPolicyTtl(Duration userPolicyTtl) {
    this.userPolicyTtl = requirePositive(userPolicyTtl, "userPolicyTtl");
  }

  public long getUserPolicyMaximumSize() {
    return userPolicyMaximumSize;
  }

  public void setUserPolicyMaximumSize(long userPolicyMaximumSize) {
    if (userPolicyMaximumSize < 1) {
      throw new IllegalArgumentException("userPolicyMaximumSize must be positive");
    }
    this.userPolicyMaximumSize = userPolicyMaximumSize;
  }

  private static Duration requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }
}
