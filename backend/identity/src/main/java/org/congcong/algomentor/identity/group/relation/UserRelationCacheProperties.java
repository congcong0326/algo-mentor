package org.congcong.algomentor.identity.group.relation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** identity-user-relations 共享缓存的部署参数。 */
@ConfigurationProperties(prefix = "algo-mentor.identity.user-relations-cache")
public class UserRelationCacheProperties {

  private Duration ttl = Duration.ofMinutes(30);
  private long maximumSize = 1_000;

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
