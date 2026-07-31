package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Practice Chat 读取当前题正式 Review 轨迹的独立能力开关。 */
@ConfigurationProperties(prefix = PracticeChatReviewTrajectoryProperties.PREFIX)
public class PracticeChatReviewTrajectoryProperties {

  public static final String PREFIX = "algo-mentor.practice-chat.review-trajectory";

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
