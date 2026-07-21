package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** PRACTICE_CHAT 学习者画像召回的安全开关与独立 Prompt 预算。 */
@ConfigurationProperties(prefix = LearnerProfileRecallProperties.PREFIX)
public class LearnerProfileRecallProperties {

  public static final String PREFIX = "algo-mentor.learner-profile.recall.practice-chat";

  private boolean enabled;
  private int maxTokenBudget = 800;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxTokenBudget() {
    return maxTokenBudget;
  }

  public void setMaxTokenBudget(int maxTokenBudget) {
    if (maxTokenBudget < 1) {
      throw new IllegalArgumentException(
          "algo-mentor.learner-profile.recall.practice-chat.max-token-budget must be positive");
    }
    this.maxTokenBudget = maxTokenBudget;
  }
}
