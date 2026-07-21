package org.congcong.algomentor.api.config;

import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileConsumerConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Code Review 画像消费者的安全开关；批量大小和窗口大小保持代码契约。 */
@ConfigurationProperties(prefix = CodeReviewProfileConsumerProperties.PREFIX)
public class CodeReviewProfileConsumerProperties {

  public static final String PREFIX = "algo-mentor.learner-profile.code-review-consumer";

  private boolean enabled;
  private int maxStaleRetries = CodeReviewProfileConsumerConstants.MAX_STALE_RETRIES;

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
    if (maxStaleRetries < 0 || maxStaleRetries > CodeReviewProfileConsumerConstants.MAX_STALE_RETRIES) {
      throw new IllegalArgumentException("algo-mentor.learner-profile.code-review-consumer.max-stale-retries must be 0 or 1");
    }
    this.maxStaleRetries = maxStaleRetries;
  }
}
