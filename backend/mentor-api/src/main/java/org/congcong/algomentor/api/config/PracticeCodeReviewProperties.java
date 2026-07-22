package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 练习 Code Review 核心能力开关。开启后要求完整装配持久化、队列、AI 与 Agent Tool。 */
@ConfigurationProperties(prefix = PracticeCodeReviewProperties.PREFIX)
public class PracticeCodeReviewProperties {

  public static final String PREFIX = "algo-mentor.practice.code-review";
  public static final String ENABLED = "enabled";

  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
