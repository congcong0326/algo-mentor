package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 用户自述画像 Agent 工具的安全开关与有界重算配置。 */
@ConfigurationProperties(prefix = LearnerProfileAgentProperties.PREFIX)
public class LearnerProfileAgentProperties {

  public static final String PREFIX = "algo-mentor.learner-profile.declared-update";

  private boolean enabled;
  private int maxStaleRetries = 1;
  private int resultSummaryMaxChars = 300;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxStaleRetries() {
    return maxStaleRetries;
  }

  public void setMaxStaleRetries(int maxStaleRetries) {
    if (maxStaleRetries < 0 || maxStaleRetries > 1) {
      throw new IllegalArgumentException("algo-mentor.learner-profile.declared-update.max-stale-retries must be 0 or 1");
    }
    this.maxStaleRetries = maxStaleRetries;
  }

  public int getResultSummaryMaxChars() {
    return resultSummaryMaxChars;
  }

  public void setResultSummaryMaxChars(int resultSummaryMaxChars) {
    if (resultSummaryMaxChars < 1) {
      throw new IllegalArgumentException(
          "algo-mentor.learner-profile.declared-update.result-summary-max-chars must be positive");
    }
    this.resultSummaryMaxChars = resultSummaryMaxChars;
  }
}
