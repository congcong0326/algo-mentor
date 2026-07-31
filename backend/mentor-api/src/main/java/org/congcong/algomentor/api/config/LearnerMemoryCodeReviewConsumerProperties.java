package org.congcong.algomentor.api.config;

import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewConsumerConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 学习者记忆 Code Review 消费者的安全开关；批量大小和窗口大小保持代码契约。 */
@ConfigurationProperties(prefix = LearnerMemoryCodeReviewConsumerProperties.PREFIX)
public class LearnerMemoryCodeReviewConsumerProperties {

  public static final String PREFIX = "algo-mentor.learner-memory.code-review-consumer";

  private boolean enabled;
  private int maxStaleRetries = LearnerMemoryCodeReviewConsumerConstants.MAX_STALE_RETRIES;

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
    if (maxStaleRetries < 0 || maxStaleRetries > LearnerMemoryCodeReviewConsumerConstants.MAX_STALE_RETRIES) {
      throw new IllegalArgumentException(
          "algo-mentor.learner-memory.code-review-consumer.max-stale-retries must be 0 or 1");
    }
    this.maxStaleRetries = maxStaleRetries;
  }
}
