package org.congcong.algomentor.api.config;

import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallContracts;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Practice Chat claim recall 的安全开关与独立 bootstrap 预算。 */
@ConfigurationProperties(prefix = LearnerMemoryRecallProperties.PREFIX)
public class LearnerMemoryRecallProperties {

  public static final String PREFIX = "algo-mentor.learner-memory.recall.practice-chat";

  private boolean enabled;
  private int bootstrapTokenBudget = LearnerMemoryRecallContracts.DEFAULT_BOOTSTRAP_TOKEN_BUDGET;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getBootstrapTokenBudget() {
    return bootstrapTokenBudget;
  }

  public void setBootstrapTokenBudget(int bootstrapTokenBudget) {
    if (bootstrapTokenBudget < 1
        || bootstrapTokenBudget > LearnerMemoryRecallContracts.MAX_BOOTSTRAP_TOKEN_BUDGET) {
      throw new IllegalArgumentException(PREFIX + ".bootstrap-token-budget must be between 1 and "
          + LearnerMemoryRecallContracts.MAX_BOOTSTRAP_TOKEN_BUDGET);
    }
    this.bootstrapTokenBudget = bootstrapTokenBudget;
  }
}
