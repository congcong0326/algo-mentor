package org.congcong.algomentor.policy.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** generic-policy-set 共享缓存的可部署参数。 */
@ConfigurationProperties(prefix = "algo-mentor.policy.cache")
public class GenericPolicyCacheProperties {

  private Duration ttl = Duration.ofMinutes(60);
  private long maximumSize = 100;

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(Duration ttl) {
    this.ttl = ttl;
  }

  public long getMaximumSize() {
    return maximumSize;
  }

  public void setMaximumSize(long maximumSize) {
    this.maximumSize = maximumSize;
  }
}
