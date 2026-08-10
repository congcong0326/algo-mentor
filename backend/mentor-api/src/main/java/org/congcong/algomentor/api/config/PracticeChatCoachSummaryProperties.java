package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Practice Chat 教练总结候选生成与采纳能力开关。 */
@ConfigurationProperties(prefix = PracticeChatCoachSummaryProperties.PREFIX)
public class PracticeChatCoachSummaryProperties {

  public static final String PREFIX = "algo-mentor.practice-chat.coach-summary";

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
