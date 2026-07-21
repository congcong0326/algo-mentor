package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 画像正文存储的安全上限配置。 */
@ConfigurationProperties(prefix = "algo-mentor.learner-profile.content")
public class LearnerProfileProperties {

  private int maxChars = 4000;

  public int getMaxChars() {
    return maxChars;
  }

  public void setMaxChars(int maxChars) {
    if (maxChars < 1) {
      throw new IllegalArgumentException("algo-mentor.learner-profile.content.max-chars must be positive");
    }
    this.maxChars = maxChars;
  }
}
