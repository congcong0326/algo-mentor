package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Practice Chat 读取当前题学习状态的独立能力开关。 */
@ConfigurationProperties(prefix = PracticeChatLearningStateProperties.PREFIX)
public class PracticeChatLearningStateProperties {

  public static final String PREFIX = "algo-mentor.practice-chat.learning-state";

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
