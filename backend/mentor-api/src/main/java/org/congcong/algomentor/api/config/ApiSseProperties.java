package org.congcong.algomentor.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = MentorConfigurationKeys.API_SSE_PREFIX)
public class ApiSseProperties {

  private Duration learningPlanDraftTimeout = Duration.ofMinutes(6);
  private Duration practiceMessageTimeout = Duration.ofMinutes(6);

  public Duration getLearningPlanDraftTimeout() {
    return learningPlanDraftTimeout;
  }

  public void setLearningPlanDraftTimeout(Duration learningPlanDraftTimeout) {
    this.learningPlanDraftTimeout = learningPlanDraftTimeout;
  }

  public Duration getPracticeMessageTimeout() {
    return practiceMessageTimeout;
  }

  public void setPracticeMessageTimeout(Duration practiceMessageTimeout) {
    this.practiceMessageTimeout = practiceMessageTimeout;
  }

  public void validate() {
    if (learningPlanDraftTimeout == null
        || learningPlanDraftTimeout.isZero()
        || learningPlanDraftTimeout.isNegative()) {
      throw new IllegalArgumentException("Learning plan draft SSE timeout must be positive");
    }
    if (practiceMessageTimeout == null
        || practiceMessageTimeout.isZero()
        || practiceMessageTimeout.isNegative()) {
      throw new IllegalArgumentException("Practice message SSE timeout must be positive");
    }
  }

  public long learningPlanDraftTimeoutMillis() {
    validate();
    return learningPlanDraftTimeout.toMillis();
  }

  public long practiceMessageTimeoutMillis() {
    validate();
    return practiceMessageTimeout.toMillis();
  }
}
